package dev.icecam.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

public class FloatService extends Service {
    private static final long QUIET_TRANSFORM_MS = 850L;
    private static final long POST_REPLAY_COOLDOWN_MS = 450L;
    private static final boolean AUTO_APPLY_TRANSFORMS = true;

    private WindowManager wm;
    private View panel;
    private WindowManager.LayoutParams lp;
    private SharedPreferences prefs;
    private AppLogger log;
    private VliveBinderClient binder;
    private RootBootstrap root;
    private TextView state;
    private int lastX, lastY;
    private float touchX, touchY;

    private final Object backendLock = new Object();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean transformWorker = new AtomicBoolean(false);
    private volatile boolean transformPending = false;
    private volatile String pendingReason = "float";

    @Override public IBinder onBind(Intent i){ return null; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        log = new AppLogger(this);
        root = new RootBootstrap(this, log);
        binder = new VliveBinderClient(log);
        binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && "stop".equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        show();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { if (wm != null && panel != null) wm.removeView(panel); } catch (Throwable ignored) {}
        panel = null;
        super.onDestroy();
    }

    private void show() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("Overlay permission is not granted");
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            return;
        }
        if (panel != null) return;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(dp(292), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("FloatX", dp(22));
        lp.y = prefs.getInt("FloatY", dp(120));
        panel = buildPanel();
        wm.addView(panel, lp);
        refresh();
        log.log("float", "v21 stable-canvas floating controls started autoApply=" + AUTO_APPLY_TRANSFORMS);
    }

    private View buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(bg(0xee151b28, dp(26), 0x66ffffff));
        box.setOnTouchListener((v, e) -> drag(e));

        TextView title = tv("IceCam Controls", 15, true);
        title.setGravity(Gravity.CENTER);
        title.setOnTouchListener((v, e) -> drag(e));
        box.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));

        state = tv("", 10, false);
        state.setGravity(Gravity.CENTER);
        state.setTextColor(0xffdbe7f4);
        box.addView(state);

        LinearLayout r0 = row();
        r0.addView(btn("Start", v -> playFile()), weight());
        r0.addView(btn("Restore", v -> restoreCamera()), weight());
        box.addView(r0);

        LinearLayout r1 = row();
        r1.addView(btn("Zoom +", v -> mutate("zoom+")), weight());
        r1.addView(btn("Up", v -> mutate("up")), weight());
        r1.addView(btn("Zoom -", v -> mutate("zoom-")), weight());
        box.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("Left", v -> mutate("left")), weight());
        r2.addView(btn("Center", v -> mutate("center")), weight());
        r2.addView(btn("Right", v -> mutate("right")), weight());
        box.addView(r2);

        LinearLayout r3 = row();
        r3.addView(btn("Fit/Fill", v -> mutate("fit-fill")), weight());
        r3.addView(btn("Down", v -> mutate("down")), weight());
        r3.addView(btn("Crop", v -> mutate("crop")), weight());
        box.addView(r3);

        LinearLayout r4 = row();
        r4.addView(btn("Rotate", v -> mutate("rotate")), weight());
        r4.addView(btn("Mirror", v -> mutate("mirror")), weight());
        r4.addView(btn("Force", v -> forceApply()), weight());
        box.addView(r4);

        LinearLayout r5 = row();
        r5.addView(btn("Open app", v -> openApp()), weight());
        r5.addView(btn("Close", v -> stopSelf()), weight());
        box.addView(r5);
        return box;
    }

    private boolean drag(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = lp.x; lastY = lp.y; touchX = e.getRawX(); touchY = e.getRawY(); return true;
            case MotionEvent.ACTION_MOVE:
                lp.x = lastX + (int)(e.getRawX() - touchX);
                lp.y = lastY + (int)(e.getRawY() - touchY);
                wm.updateViewLayout(panel, lp);
                return true;
            case MotionEvent.ACTION_UP:
                prefs.edit().putInt("FloatX", lp.x).putInt("FloatY", lp.y).apply(); return true;
        }
        return false;
    }

    private void mutate(String op) {
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
            case "rotate": s.rotate90(); break;
            case "mirror": s.toggleMirrorH(); break;
        }
        s.save(prefs);
        log.log("float", "transform state-updated " + op + " " + s.summary() + " autoApply=" + AUTO_APPLY_TRANSFORMS);
        prefs.edit().putString("IceCamState", "FLOAT_TRANSFORM_DIRTY").apply();
        if (AUTO_APPLY_TRANSFORMS) scheduleBake(op);
        refresh();
    }

    private void forceApply() {
        transformPending = false;
        log.log("float", "manual explicit bake/replay requested");
        scheduleBake("manual-apply");
    }

    private void scheduleBake(String reason) {
        pendingReason = reason;
        transformPending = true;
        if (!transformWorker.compareAndSet(false, true)) {
            log.log("float", "coalesced latest transform: " + reason);
            return;
        }
        new Thread(() -> {
            try {
                while (true) {
                    transformPending = false;
                    String r = pendingReason;
                    prefs.edit().putString("IceCamState", "FLOAT_WAITING_FOR_STABLE_TRANSFORM").apply();
                    sleepMs(QUIET_TRANSFORM_MS);
                    if (transformPending) continue;
                    TransformState snapshot = TransformState.load(prefs);
                    String original = prefs.getString("OriginalPlayFileMp4", prefs.getString("PlayFileMp4", ""));
                    if (original == null || original.length() == 0) break;
                    if (!MediaTransformer.isImagePath(original)) {
                        log.log("float", r + " saved for video; no baked replay");
                        break;
                    }
                    prefs.edit().putString("IceCamState", "FLOAT_RENDERING_FRAME").apply();
                    String baked = MediaTransformer.bakeImage(this, original, snapshot, log);
                    prefs.edit().putString("PlayFileMp4", baked).putString("BakedPlayFileMp4", baked).apply();
                    requestApply(baked, "float-transform-" + r, prefs.getBoolean("ReplacementActive", false));
                    sleepMs(POST_REPLAY_COOLDOWN_MS);
                    if (!transformPending) break;
                }
            } finally {
                transformWorker.set(false);
                if (transformPending) scheduleBake("float-coalesced-after-worker");
                refresh();
            }
        }, "icecam-float-transform").start();
    }

    private void playFile() {
        String p = prefs.getString("PlayFileMp4", "");
        if (p == null || p.trim().isEmpty()) { toast("Select media in main app first"); return; }
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                prefs.edit().putString("IceCamState", "STARTING").apply();
                binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                if (!binder.connected()) {
                    root.bootstrap();
                    binder.clearCache();
                    binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                    sleepMs(350);
                }
                requestApply(p, "float-start", true);
            } finally { busy.set(false); refresh(); }
        }, "icecam-float-start").start();
    }

    private void requestApply(String path, String source, boolean force) {
        BackendApplyQueue.get(this).enqueue(path, source, force);
        refresh();
    }

    private boolean replayOnce(String p, String source) {
        synchronized (backendLock) {
            try {
                prefs.edit().putString("IceCamState", "FLOAT_APPLYING_MEDIA").apply();
                log.log("float", "replay start source=" + source + " path=" + p);
                binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                if (!binder.connected()) { binder.clearCache(); sleepMs(250); }
                int mode = binder.setModeString(1, p);
                sleepMs(520);
                TransformState tx = TransformState.load(prefs);
                int play = binder.playSource(p, tx.mirrorH(), prefs.getBoolean("PlayisLoop", true));
                boolean active = mode >= 0 && play >= 0;
                prefs.edit().putBoolean("ReplacementActive", active).putString("IceCamState", active ? "REPLACEMENT_ACTIVE" : "PLAY_ERROR").apply();
                log.log("float", "replay done TX14=" + mode + " TX11=" + play + " active=" + active);
                if (!active) binder.clearCache();
                return active;
            } catch (Throwable t) {
                prefs.edit().putBoolean("ReplacementActive", false).putString("IceCamState", "PLAY_ERROR").apply();
                binder.clearCache();
                log.log("float", "replay exception: " + t);
                return false;
            }
        }
    }

    private void restoreCamera() {
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            synchronized (backendLock) {
                try {
                    prefs.edit().putString("IceCamState", "RESTORING_CAMERA").apply();
                    log.log("float", "restore camera requested");
                    root.restoreCamera();
                    binder.clearCache();
                    sleepMs(350);
                    boolean stillConnected = binder.connected();
                    prefs.edit().putBoolean("ReplacementActive", false).putString("IceCamState", stillConnected ? "RESTORE_CHECK_SERVICE_STILL_VISIBLE" : "CAMERA_RESTORED").apply();
                    log.log("float", "restore done serviceStillVisible=" + stillConnected);
                } finally { busy.set(false); refresh(); }
            }
        }, "icecam-float-restore").start();
    }

    private void openApp() {
        Intent it = new Intent(this, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(it);
    }

    private void refresh() {
        if (state == null) return;
        boolean active = prefs.getBoolean("ReplacementActive", false);
        String phase = prefs.getString("IceCamState", "IDLE");
        TransformState s = TransformState.load(prefs);
        state.setText((active ? "ON" : "OFF") + " · " + phase + "\n" + s.modeName() + " z=" + String.format(java.util.Locale.US, "%.2f", s.zoomX) + " pan=" + String.format(java.util.Locale.US, "%.2f,%.2f", s.panX, s.panY));
        state.setTextColor(active ? 0xff62ff91 : 0xffdbe7f4);
    }

    private static void sleepMs(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(10); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0); b.setMinHeight(0); b.setMinimumHeight(0); b.setBackground(bg(0xaa6d7c92, dp(15), 0x66ffffff)); b.setOnClickListener(l); return b;
    }
    private TextView tv(String s, int sp, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); return lp; }
    private GradientDrawable bg(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(1, stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); log.log("float", s); }
}
