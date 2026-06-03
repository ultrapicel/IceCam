package dev.icecam.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide transform/control plane.
 *
 * v23 goal: MainActivity and FloatService must not own separate backend paths.
 * Floating controls are only a remote UI source; all transform commits and legacy
 * TX14/TX11 replay go through this controller and BackendApplyQueue.
 */
public final class TransformController {
    public enum Source { MAIN, FLOAT }

    private static final long MAIN_QUIET_TRANSFORM_MS = 650L;
    private static final long FLOAT_QUIET_TRANSFORM_MS = 950L;
    private static final long POST_RENDER_COOLDOWN_MS = 500L;
    private static volatile TransformController instance;

    public static TransformController get(Context context) {
        Context app = context.getApplicationContext();
        TransformController local = instance;
        if (local == null) {
            synchronized (TransformController.class) {
                local = instance;
                if (local == null) {
                    local = new TransformController(app);
                    instance = local;
                }
            }
        }
        return local;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final AppLogger log;
    private final RootBootstrap root;
    private final VliveBinderClient binder;
    private final AtomicBoolean renderWorker = new AtomicBoolean(false);
    private final AtomicBoolean actionBusy = new AtomicBoolean(false);

    private volatile boolean pendingRender;
    private volatile String pendingReason = "pending";
    private volatile Source pendingSource = Source.MAIN;
    private volatile boolean pendingForce;

    private TransformController(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE);
        this.log = new AppLogger(context);
        this.root = new RootBootstrap(context, log);
        this.binder = new VliveBinderClient(log);
        this.binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
    }

    public boolean isRendering() { return renderWorker.get(); }
    public boolean isBusy() { return actionBusy.get() || renderWorker.get() || BackendApplyQueue.get(context).isRunning(); }

    public TransformState mutate(Source source, String op, boolean autoCommit) {
        TransformState s = TransformState.load(prefs);
        switch (op) {
            case "zoom+": s.zoom(1.12f); break;
            case "zoom-": s.zoom(1f / 1.12f); break;
            case "up": s.move(0f, 0.04f); break;
            case "down": s.move(0f, -0.04f); break;
            case "left": s.move(-0.04f, 0f); break;
            case "right": s.move(0.04f, 0f); break;
            case "center": s.center(); break;
            case "fit-fill": s.toggleFitFill(); break;
            case "crop": s.cycleCrop(); break;
            case "rotate":
            case "rot+90": s.rotate90(); break;
            case "rot-90": s.rotateMinus90(); break;
            case "mirror":
            case "mirror-x": s.toggleMirrorH(); break;
            case "mirror-y": s.toggleMirrorV(); break;
            case "reset": s.reset(); break;
            default: log.log("txctl", "unknown transform op source=" + source + " op=" + op); break;
        }
        updateState(source, op, s, autoCommit);
        return s;
    }

    public void updateState(Source source, String reason, TransformState state, boolean autoCommit) {
        state.save(prefs);
        prefs.edit()
                .putString("IceCamState", source == Source.FLOAT ? "FLOAT_TRANSFORM_DIRTY" : "TRANSFORM_DIRTY")
                .putString("LastTransformSource", source.name())
                .putString("LastTransformReason", reason)
                .apply();
        log.log("txctl", "state source=" + source + " reason=" + reason + " autoCommit=" + autoCommit + " " + state.summary());

        String original = originalPath();
        if (original.length() == 0) return;
        if (!MediaTransformer.isImagePath(original)) {
            log.log("txctl", "state saved for video; legacy image bake skipped source=" + source + " reason=" + reason);
            return;
        }
        if (autoCommit) scheduleRender(source, reason, false);
    }

    public void commit(Source source, String reason) {
        TransformState snapshot = TransformState.load(prefs);
        snapshot.save(prefs);
        log.log("txctl", "commit requested source=" + source + " reason=" + reason + " " + snapshot.summary());
        scheduleRender(source, reason, true);
    }

    public void startReplacement(Source source) {
        String path = prefs.getString("PlayFileMp4", "");
        if (path == null || path.trim().isEmpty()) {
            log.log("txctl", "start ignored: no media source=" + source);
            return;
        }
        if (!actionBusy.compareAndSet(false, true)) {
            log.log("txctl", "start ignored: action busy source=" + source);
            return;
        }
        new Thread(() -> {
            try {
                prefs.edit().putString("IceCamState", "STARTING").apply();
                log.log("txctl", "start source=" + source + " path=" + path);
                binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                if (!binder.connected()) {
                    root.bootstrap();
                    binder.clearCache();
                    binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                    sleepMs(350);
                }
                String original = originalPath();
                TransformState snapshot = TransformState.load(prefs);
                if (original.length() > 0 && MediaTransformer.isImagePath(original)) {
                    log.log("txctl", "start will render current transform before TX source=" + source + " " + snapshot.summary());
                    scheduleRender(source, "start-current-state", true);
                } else {
                    BackendApplyQueue.get(context).enqueue(path, "txctl-start-" + source.name().toLowerCase(), true);
                }
            } catch (Throwable t) {
                log.log("txctl", "start exception source=" + source + ": " + t);
            } finally {
                actionBusy.set(false);
            }
        }, "icecam-txctl-start").start();
    }

    public void restoreCamera(Source source) {
        if (!actionBusy.compareAndSet(false, true)) {
            log.log("txctl", "restore ignored: action busy source=" + source);
            return;
        }
        new Thread(() -> {
            try {
                prefs.edit().putString("IceCamState", "RESTORING_CAMERA").apply();
                log.log("txctl", "restore requested source=" + source);
                root.restoreCamera();
                binder.clearCache();
                sleepMs(350);
                boolean stillConnected = binder.connected();
                prefs.edit()
                        .putBoolean("ReplacementActive", false)
                        .putString("IceCamState", stillConnected ? "RESTORE_CHECK_SERVICE_STILL_VISIBLE" : "CAMERA_RESTORED")
                        .apply();
                log.log("txctl", "restore done source=" + source + " serviceStillVisible=" + stillConnected + " " + binder.lastError());
            } catch (Throwable t) {
                log.log("txctl", "restore exception source=" + source + ": " + t);
            } finally {
                actionBusy.set(false);
            }
        }, "icecam-txctl-restore").start();
    }

    private void scheduleRender(Source source, String reason, boolean force) {
        String original = originalPath();
        if (original.length() == 0) {
            log.log("txctl", "render skipped: no original source=" + source + " reason=" + reason);
            return;
        }
        if (!MediaTransformer.isImagePath(original)) {
            log.log("txctl", "render skipped: non-image original source=" + source + " reason=" + reason + " path=" + original);
            return;
        }
        pendingReason = reason;
        pendingSource = source;
        pendingForce = pendingForce || force;
        pendingRender = true;
        prefs.edit().putString("IceCamState", force ? "COMMIT_QUEUED" : "TRANSFORM_DIRTY").apply();
        if (!renderWorker.compareAndSet(false, true)) {
            log.log("txctl", "render coalesced source=" + source + " reason=" + reason + " force=" + force);
            return;
        }
        new Thread(this::drainRender, "icecam-transform-controller").start();
    }

    private void drainRender() {
        try {
            while (true) {
                pendingRender = false;
                String reason = pendingReason;
                Source source = pendingSource;
                boolean force = pendingForce;
                pendingForce = false;

                long quiet = force ? 80L : (source == Source.FLOAT ? FLOAT_QUIET_TRANSFORM_MS : MAIN_QUIET_TRANSFORM_MS);
                prefs.edit().putString("IceCamState", force ? "COMMIT_RENDERING_SOON" : "WAITING_FOR_STABLE_TRANSFORM").apply();
                sleepMs(quiet);
                if (pendingRender) {
                    log.log("txctl", "quiet window restarted latestSource=" + pendingSource + " latestReason=" + pendingReason);
                    continue;
                }

                TransformState snapshot = TransformState.load(prefs);
                String original = originalPath();
                if (original.length() == 0 || !MediaTransformer.isImagePath(original)) break;

                prefs.edit().putString("IceCamState", "RENDERING_FRAME").apply();
                long t0 = android.os.SystemClock.elapsedRealtime();
                String baked = MediaTransformer.bakeImage(context, original, snapshot, log);
                prefs.edit()
                        .putString("PlayFileMp4", baked)
                        .putString("BakedPlayFileMp4", baked)
                        .putString("IceCamState", "FRAME_RENDERED")
                        .apply();
                log.log("txctl", "rendered source=" + source + " reason=" + reason + " force=" + force + " ms=" + (android.os.SystemClock.elapsedRealtime() - t0) + " " + snapshot.summary() + " -> " + baked);

                boolean active = prefs.getBoolean("ReplacementActive", false);
                if (force || active) {
                    BackendApplyQueue.get(context).enqueue(baked, "txctl-" + source.name().toLowerCase() + "-" + reason, force || active);
                } else {
                    log.log("txctl", "render retained without backend apply; replacement inactive source=" + source + " reason=" + reason);
                }
                sleepMs(POST_RENDER_COOLDOWN_MS);
                if (!pendingRender) break;
            }
        } catch (Throwable t) {
            log.log("txctl", "render worker exception: " + t);
        } finally {
            renderWorker.set(false);
            if (pendingRender) scheduleRender(pendingSource, "coalesced-after-worker", pendingForce);
        }
    }

    private String originalPath() {
        String original = prefs.getString("OriginalPlayFileMp4", "");
        if (original == null || original.length() == 0) original = prefs.getString("PlayFileMp4", "");
        return original == null ? "" : original;
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
