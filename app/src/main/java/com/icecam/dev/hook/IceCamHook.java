package com.icecam.dev.hook;

import android.util.Log;
import java.io.*;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        log("[LOAD] package=" + lp.packageName + " process=" + lp.processName
                + " active=" + active() + " mode=" + mode() + " mediaPath=" + MEDIA);
        safeInit("hookCamera2", new Runnable() { public void run() { hookCamera2(lp); } });
        safeInit("hookCamera1", new Runnable() { public void run() { hookCamera1(lp); } });
    }

    private void safeInit(String name, Runnable r) {
        try {
            r.run();
            log("[INIT] " + name + " ok");
        } catch (Throwable t) {
            log("[ERR] " + name + " " + stack(t));
            XposedBridge.log(t);
        }
    }

    private void hookCamera2(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> cm;
        try {
            cm = Class.forName("android.hardware.camera2.CameraManager");
        } catch (Throwable t) {
            log("[ERR] CameraManager class unavailable " + stack(t));
            return;
        }

        hookAll(cm, "getCameraIdList", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                log("[Camera2] getCameraIdList before package=" + lp.packageName
                        + " active=" + active() + " mode=" + mode() + " mediaPath=" + MEDIA);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                log("[Camera2] getCameraIdList after result=" + safe(p.getResult()));
            }
        });

        hookAll(cm, "getCameraCharacteristics", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Object id = p.args != null && p.args.length > 0 ? p.args[0] : "?";
                log("[Camera2] getCameraCharacteristics id=" + id
                        + " active=" + active() + " mode=" + mode() + " config=" + trim(readSmall(CONFIG), 512));
            }
        });

        hookAll(cm, "openCamera", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Object id = p.args != null && p.args.length > 0 ? p.args[0] : "?";
                log("[Camera2] openCamera id=" + id
                        + " signature=" + methodSig(p.method)
                        + " active=" + active() + " mode=" + mode() + " mediaPath=" + MEDIA);
            }
        });
    }

    private void hookCamera1(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> cam;
        try {
            cam = Class.forName("android.hardware.Camera");
        } catch (Throwable t) {
            log("[WARN] Camera1 class unavailable " + t);
            return;
        }
        hookAll(cam, "open", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Object id = (p.args != null && p.args.length > 0) ? p.args[0] : "default";
                log("[Camera1] open id=" + id
                        + " signature=" + methodSig(p.method)
                        + " active=" + active() + " mode=" + mode());
            }
        });
    }

    private static void hookAll(Class<?> cls, String methodName, XC_MethodHook cb) {
        try {
            Set<?> hooks = XposedBridge.hookAllMethods(cls, methodName, cb);
            log("[HOOKED] " + cls.getName() + "." + methodName + " count=" + (hooks == null ? "?" : hooks.size()));
        } catch (Throwable t) {
            log("[ERR] hookAllMethods " + cls.getName() + "." + methodName + " " + stack(t));
            XposedBridge.log(t);
        }
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
            byte[] b = new byte[(int) Math.min(f.length(), 8192)];
            int n = in.read(b);
            in.close();
            return n > 0 ? new String(b, 0, n) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String safe(Object o) {
        if (o == null) return "null";
        if (o instanceof String[]) return java.util.Arrays.toString((String[]) o);
        return String.valueOf(o);
    }

    private static String methodSig(Object m) {
        if (!(m instanceof Method)) return String.valueOf(m);
        Method method = (Method) m;
        Class<?>[] p = method.getParameterTypes();
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        for (int i = 0; i < p.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(p[i].getName());
        }
        return sb.append(')').toString();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String stack(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            return trim(sw.toString(), 2048);
        } catch (Throwable ignored) {
            return String.valueOf(t);
        }
    }

    private static void log(String msg) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " " + msg;
        Log.i(TAG, line);
        try {
            File f = new File(LOG);
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line + "\n");
            w.close();
        } catch (Throwable ignored) {}
    }
}
