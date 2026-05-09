package com.icecam.dev.hook;

import android.util.Log;
import java.io.*;
import java.lang.reflect.Member;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IceCamHook implements IXposedHookLoadPackage {
    private static final String TAG = "IceCam/Hook";
    private static final String LOG = "/data/adb/icecam/logs/hook.log";
    private static final String CONFIG = "/data/adb/icecam/config/app_config.json";
    private static final String ACTIVE = "/data/adb/icecam/state/active";
    private static final String MEDIA = "/data/adb/icecam/media/source";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        log("[LOAD] package=" + lp.packageName
                + " process=" + lp.processName
                + " thread=" + Thread.currentThread().getName()
                + " classLoader=" + safe(lp.classLoader)
                + " active=" + active()
                + " mode=" + mode()
                + " mediaPath=" + MEDIA
                + " mediaExists=" + new File(MEDIA).exists());

        safeInit("hookCamera2.CameraManager", new Runnable() { public void run() { hookCameraManager(lp); } });
        safeInit("hookCamera2.CameraDevice", new Runnable() { public void run() { hookCameraDevice(lp); } });
        safeInit("hookCamera1", new Runnable() { public void run() { hookCamera1(lp); } });
    }

    private void safeInit(String name, Runnable r) {
        try {
            r.run();
            log("[INIT] " + name + " ok");
        } catch (Throwable t) {
            log("[ERR] " + name + " " + stack(t));
            xlog(t);
        }
    }

    private void hookCameraManager(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> cm = findClassBoot("android.hardware.camera2.CameraManager");
        if (cm == null) return;

        hookAll(cm, "getCameraIdList", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] getCameraIdList before", p);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                log("[Camera2] getCameraIdList after package=" + lp.packageName
                        + " result=" + safe(p.getResult())
                        + " active=" + active() + " mode=" + mode());
            }
        });

        hookAll(cm, "getCameraCharacteristics", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "?");
                logEvent(lp, "[Camera2] getCameraCharacteristics id=" + id, p);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "?");
                log("[Camera2] getCameraCharacteristics after id=" + id
                        + " result=" + className(p.getResult())
                        + " active=" + active());
            }
        });

        hookAll(cm, "openCamera", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "?");
                logEvent(lp, "[Camera2] openCamera id=" + id, p);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "?");
                log("[Camera2] openCamera after id=" + id
                        + " throwable=not-readable-in-stub"
                        + " active=" + active());
            }
        });

        hookAll(cm, "registerAvailabilityCallback", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] registerAvailabilityCallback", p);
            }
        });

        hookAll(cm, "unregisterAvailabilityCallback", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] unregisterAvailabilityCallback", p);
            }
        });
    }

    private void hookCameraDevice(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> cd = findClassBoot("android.hardware.camera2.CameraDevice");
        if (cd == null) return;

        hookAll(cd, "createCaptureSession", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraDevice.createCaptureSession", p);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                log("[Camera2] CameraDevice.createCaptureSession after package=" + lp.packageName
                        + " active=" + active() + " mode=" + mode());
            }
        });

        hookAll(cd, "createCaptureRequest", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraDevice.createCaptureRequest", p);
            }
        });

        hookAll(cd, "close", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraDevice.close", p);
            }
        });
    }

    private void hookCamera1(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> cam = findClassBoot("android.hardware.Camera");
        if (cam == null) return;
        hookAll(cam, "open", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "default");
                logEvent(lp, "[Camera1] open id=" + id, p);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "default");
                log("[Camera1] open after id=" + id + " result=" + className(p.getResult())
                        + " active=" + active());
            }
        });
    }

    private static Class<?> findClassBoot(String name) {
        try {
            Class<?> c = Class.forName(name, false, ClassLoader.getSystemClassLoader());
            log("[CLASS] " + name + " via system loader ok loader=" + safe(c.getClassLoader()));
            return c;
        } catch (Throwable t1) {
            try {
                Class<?> c = Class.forName(name);
                log("[CLASS] " + name + " via default loader ok loader=" + safe(c.getClassLoader()));
                return c;
            } catch (Throwable t2) {
                log("[ERR] class unavailable " + name + " system=" + shortErr(t1) + " default=" + shortErr(t2));
                return null;
            }
        }
    }

    private static void hookAll(Class<?> cls, String methodName, XC_MethodHook cb) {
        try {
            Set<?> hooks = XposedBridge.hookAllMethods(cls, methodName, cb);
            log("[HOOKED] " + cls.getName() + "." + methodName + " count=" + (hooks == null ? "null" : String.valueOf(hooks.size())));
        } catch (Throwable t) {
            log("[ERR] hookAllMethods " + cls.getName() + "." + methodName + " " + stack(t));
            xlog(t);
        }
    }

    private static void logEvent(XC_LoadPackage.LoadPackageParam lp, String event, XC_MethodHook.MethodHookParam p) {
        log(event
                + " package=" + lp.packageName
                + " process=" + lp.processName
                + " thread=" + Thread.currentThread().getName()
                + " method=" + methodSig(p == null ? null : p.method)
                + " args=" + argsToString(p == null ? null : p.args)
                + " this=" + className(p == null ? null : p.thisObject)
                + " active=" + active()
                + " mode=" + mode()
                + " mediaPath=" + MEDIA
                + " mediaExists=" + new File(MEDIA).exists()
                + " config=" + trim(readSmall(CONFIG), 384));
    }

    private static Object arg(XC_MethodHook.MethodHookParam p, int index, Object fallback) {
        return p != null && p.args != null && p.args.length > index ? p.args[index] : fallback;
    }

    private static boolean active() {
        return "1".equals(readSmall(ACTIVE).trim());
    }

    private static String mode() {
        String c = readSmall(CONFIG);
        int i = c.indexOf("\"mode\"");
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
            return n > 0 ? new String(b, 0, n) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String argsToString(Object[] args) {
        if (args == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object a = args[i];
            sb.append(i).append('=').append(className(a));
            if (a instanceof String || a instanceof Number || a instanceof Boolean) sb.append(':').append(a);
        }
        return sb.append(']').toString();
    }

    private static String safe(Object o) {
        if (o == null) return "null";
        if (o instanceof String[]) return java.util.Arrays.toString((String[]) o);
        return String.valueOf(o);
    }

    private static String className(Object o) {
        if (o == null) return "null";
        return o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }

    private static String methodSig(Member m) {
        if (m == null) return "null";
        return m.toString();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String shortErr(Throwable t) {
        if (t == null) return "null";
        return t.getClass().getName() + ":" + t.getMessage();
    }

    private static String stack(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            return trim(sw.toString(), 4096);
        } catch (Throwable ignored) {
            return String.valueOf(t);
        }
    }

    private static void xlog(Throwable t) {
        try { XposedBridge.log(t); } catch (Throwable ignored) {}
    }

    private static void xlog(String s) {
        try { XposedBridge.log(s); } catch (Throwable ignored) {}
    }

    private static void log(String msg) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " " + msg;
        try { Log.i(TAG, line); } catch (Throwable ignored) {}
        xlog("IceCam/Hook " + line);

        // Primary: normal append. Works when /data/adb/icecam/logs/hook.log is chmod 666 and SELinux allows it.
        boolean wrote = false;
        try {
            File f = new File(LOG);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileOutputStream out = new FileOutputStream(f, true);
            out.write((line + "\n").getBytes("UTF-8"));
            out.close();
            wrote = true;
        } catch (Throwable t) {
            try { Log.w(TAG, "file-log primary failed: " + shortErr(t)); } catch (Throwable ignored) {}
        }

        // Fallback: shell append without su. This sometimes succeeds where direct FileOutputStream is blocked by app context quirks.
        if (!wrote) {
            try {
                String q = line.replace("'", "'\\''");
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "echo '" + q + "' >> " + LOG}).waitFor();
            } catch (Throwable ignored) {}
        }
    }
}
