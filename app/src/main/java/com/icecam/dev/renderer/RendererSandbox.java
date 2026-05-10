package com.icecam.dev.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * v9.4 passive renderer sandbox.
 *
 * This class intentionally does not replace app camera frames and does not attach
 * to target Camera2 output surfaces. It only prepares the internal primitives
 * required for later experiments: renderer thread, placeholder frame producer,
 * SurfaceTexture ownership and timing/event telemetry.
 */
public final class RendererSandbox {
    private static final String TAG = "IceCam/RendererSandbox";
    private static final String LOG_PREFIX = "RendererSandboxJson ";
    private static final String VERSION = "v9.4.6-provider-cache-cleanup";
    private static final String CONFIG = "/data/adb/icecam/config/app_config.json";
    private static final String ACTIVE = "/data/adb/icecam/state/active";
    private static final String CACHE_DIR = "/data/adb/icecam/cache";
    private static final String EVENTS = CACHE_DIR + "/renderer_events.jsonl";

    private static final Object LOCK = new Object();
    private static HandlerThread thread;
    private static Handler handler;
    private static SurfaceTexture ownedTexture;
    private static Surface ownedSurface;
    private static Bitmap placeholder;
    private static boolean started;
    private static long frameCounter;

    private RendererSandbox() {}

    public static void ensureStarted(final String ownerPackage, final String ownerProcess) {
        ensureStarted(ownerPackage, ownerProcess, "provider:local-ui");
    }

    public static void ensureStarted(final String ownerPackage, final String ownerProcess, final String providerSnapshot) {
        synchronized (LOCK) {
            if (started) {
                event("already-started", ownerPackage, ownerProcess, "provider=" + trim(providerSnapshot, 512));
                return;
            }
            started = true;
            thread = new HandlerThread("IceCamRendererSandbox");
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(new Runnable() {
                @Override public void run() {
                    initOnThread(ownerPackage, ownerProcess, providerSnapshot);
                }
            });
        }
    }

    public static void stop(final String reason) {
        synchronized (LOCK) {
            started = false;
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override public void run() { releaseOnThread(reason); }
                });
            }
            if (thread != null) {
                try { thread.quitSafely(); } catch (Throwable ignored) {}
            }
            handler = null;
            thread = null;
        }
    }

    public static String snapshot() {
        synchronized (LOCK) {
            return "started=" + started
                    + " thread=" + (thread == null ? "null" : thread.getName())
                    + " texture=" + objectId(ownedTexture)
                    + " surface=" + objectId(ownedSurface)
                    + " placeholder=" + (placeholder == null ? "null" : placeholder.getWidth() + "x" + placeholder.getHeight())
                    + " frames=" + frameCounter;
        }
    }

    private static void initOnThread(String ownerPackage, String ownerProcess, String providerSnapshot) {
        try {
            new File(CACHE_DIR).mkdirs();
            placeholder = makePlaceholder(1280, 720);
            ownedTexture = createSurfaceTexture();
            ownedTexture.setDefaultBufferSize(1280, 720);
            ownedSurface = new Surface(ownedTexture);
            frameCounter = 0;
            event("init", ownerPackage, ownerProcess, snapshot() + " provider=" + trim(providerSnapshot, 512));
            scheduleTick(ownerPackage, ownerProcess, providerSnapshot);
        } catch (Throwable t) {
            event("init-error", ownerPackage, ownerProcess, shortErr(t));
            Log.e(TAG, "init failed", t);
        }
    }

    private static SurfaceTexture createSurfaceTexture() throws Exception {
        try {
            java.lang.reflect.Constructor<SurfaceTexture> c = SurfaceTexture.class.getConstructor(boolean.class);
            return c.newInstance(false);
        } catch (Throwable ignored) {
            return new SurfaceTexture(0);
        }
    }

    private static void scheduleTick(final String ownerPackage, final String ownerProcess, final String providerSnapshot) {
        Handler h;
        synchronized (LOCK) { h = handler; }
        if (h == null) return;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) {
                    if (!started || handler == null) return;
                    frameCounter++;
                }
                if ((frameCounter % 60) == 1) {
                    event("tick", ownerPackage, ownerProcess, snapshot() + " providerActive=" + jsonRaw(providerSnapshot, "active", "unknown") + " providerMode=" + jsonString(providerSnapshot, "mode", "unknown") + " providerMediaReady=" + jsonRaw(providerSnapshot, "mediaReady", "unknown"));
                }
                scheduleTick(ownerPackage, ownerProcess, providerSnapshot);
            }
        }, 33);
    }

    private static Bitmap makePlaceholder(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(Color.rgb(5, 12, 24));
        p.setColor(Color.rgb(30, 144, 255));
        p.setStrokeWidth(6f);
        c.drawRect(24, 24, w - 24, h - 24, p);
        p.setTextSize(48f);
        p.setColor(Color.WHITE);
        c.drawText("IceCam v9.4.6 renderer sandbox", 72, 120, p);
        p.setTextSize(30f);
        c.drawText("passive placeholder producer — no frame injection", 72, 175, p);
        p.setTextSize(24f);
        c.drawText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), 72, 225, p);
        return b;
    }

    private static void releaseOnThread(String reason) {
        event("release", "", "", reason + " " + snapshot());
        try { if (ownedSurface != null) ownedSurface.release(); } catch (Throwable ignored) {}
        try { if (ownedTexture != null) ownedTexture.release(); } catch (Throwable ignored) {}
        try { if (placeholder != null) placeholder.recycle(); } catch (Throwable ignored) {}
        ownedSurface = null;
        ownedTexture = null;
        placeholder = null;
    }

    private static boolean active() {
        return "1".equals(readSmall(ACTIVE).trim());
    }

    private static String configValue(String key) {
        String c = readSmall(CONFIG);
        int i = c.indexOf('"' + key + '"');
        if (i < 0) return "unknown";
        int colon = c.indexOf(':', i);
        int q = c.indexOf('"', colon + 1);
        int e = c.indexOf('"', q + 1);
        return (q >= 0 && e > q) ? c.substring(q + 1, e) : "unknown";
    }

    private static String readSmall(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) Math.min(f.length(), 16384)];
            int n = in.read(b);
            in.close();
            return n > 0 ? new String(b, 0, n, "UTF-8") : "";
        } catch (Throwable t) { return ""; }
    }

    private static void event(String action, String ownerPackage, String ownerProcess, String detail) {
        String json = "{\"version\":\"" + VERSION
                + "\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                + "\",\"action\":\"" + esc(action)
                + "\",\"package\":\"" + esc(ownerPackage)
                + "\",\"process\":\"" + esc(ownerProcess)
                + "\",\"thread\":\"" + esc(Thread.currentThread().getName())
                + "\",\"detail\":\"" + esc(detail) + "\"}";
        try { Log.i(TAG, LOG_PREFIX + json); } catch (Throwable ignored) {}
        appendFile(EVENTS, json + "\n");
    }

    private static void appendFile(String path, String data) {
        // v9.4.6: target app processes must not write /data/adb under SELinux enforcing.
        // Renderer telemetry is exported from logcat instead.
    }


    private static String trim(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String jsonString(String json, String key, String fallback) {
        if (json == null) return fallback;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return fallback;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return fallback;
        int q = json.indexOf('\"', colon + 1);
        if (q < 0) return fallback;
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int x = q + 1; x < json.length(); x++) {
            char ch = json.charAt(x);
            if (esc) { out.append(ch); esc = false; continue; }
            if (ch == '\\') { esc = true; continue; }
            if (ch == '\"') return out.toString();
            out.append(ch);
        }
        return fallback;
    }

    private static String jsonRaw(String json, String key, String fallback) {
        if (json == null) return fallback;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return fallback;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return fallback;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim().replace("\"", "");
    }

    private static String objectId(Object o) {
        return o == null ? "null" : o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }

    private static String shortErr(Throwable t) {
        return t == null ? "null" : t.getClass().getSimpleName() + ":" + t.getMessage();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
