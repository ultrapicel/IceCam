\
package com.icecam.dev.hook;

import android.util.Log;
import java.io.*;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IceCamHook implements IXposedHookLoadPackage {
    private static final String TAG = "IceCam/Hook";
    private static final String LOG = "/data/adb/icecam/logs/hook.log";
    private static final String CONFIG = "/data/adb/icecam/config/app_config.json";

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        final String pkg = lpparam.packageName == null ? "unknown" : lpparam.packageName;
        log("[LOAD] package=" + pkg + " process=" + lpparam.processName);
        tryHookCameraManager(lpparam);
        tryHookLegacyCamera(lpparam);
    }

    private void tryHookCameraManager(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> cls = XposedHelpers.findClass("android.hardware.camera2.CameraManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(cls, "getCameraIdList", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera2] getCameraIdList before pkg=" + lpparam.packageName + " cfg=" + mode());
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    log("[Camera2] getCameraIdList after pkg=" + lpparam.packageName + " result=" + safe(param.getResult()));
                    // v5 does not change ID list by default. virtual-stub mode keeps real IDs for compatibility.
                }
            });

            XposedHelpers.findAndHookMethod(cls, "getCameraCharacteristics", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera2] getCameraCharacteristics pkg=" + lpparam.packageName + " id=" + param.args[0] + " cfg=" + readConfigShort());
                }
            });

            for (Method m : cls.getDeclaredMethods()) {
                if (!"openCamera".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object id = param.args != null && param.args.length > 0 ? param.args[0] : "?";
                        String md = mode();
                        log("[Camera2] openCamera pkg=" + lpparam.packageName + " id=" + id + " mode=" + md);
                        if ("block-open-test".equals(md)) {
                            log("[Camera2] block-open-test active; blocking camera open for pkg=" + lpparam.packageName);
                            param.setResult(null);
                        }
                    }
                });
            }

            log("[OK] CameraManager hooks installed pkg=" + lpparam.packageName);
        } catch(Throwable t) {
            log("[ERR] CameraManager hook failed pkg=" + lpparam.packageName + " err=" + t);
        }
    }

    private void tryHookLegacyCamera(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cam = XposedHelpers.findClass("android.hardware.Camera", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cam, "open", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera1] open pkg=" + lpparam.packageName + " id=" + param.args[0] + " mode=" + mode());
                }
            });
            XposedHelpers.findAndHookMethod(cam, "open", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera1] open default pkg=" + lpparam.packageName + " mode=" + mode());
                }
            });
            log("[OK] Camera1 hooks installed pkg=" + lpparam.packageName);
        } catch(Throwable t) {
            log("[ERR] Camera1 hook failed pkg=" + lpparam.packageName + " err=" + t);
        }
    }

    private static String mode() {
        String c = readConfig();
        if (c.contains("\"mode\":\"block-open-test\"")) return "block-open-test";
        if (c.contains("\"mode\":\"virtual-stub\"")) return "virtual-stub";
        return "log-only";
    }

    private static String readConfigShort() {
        String c = readConfig();
        if (c.length() > 180) return c.substring(0, 180) + "...";
        return c;
    }

    private static String readConfig() {
        try {
            File f = new File(CONFIG);
            if (!f.exists()) return "{}";
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            fis.close();
            return bos.toString();
        } catch(Throwable t) {
            return "{readConfigError:" + t + "}";
        }
    }

    private static String safe(Object o) {
        if (o == null) return "null";
        if (o instanceof String[]) {
            String[] a=(String[])o;
            StringBuilder sb=new StringBuilder("[");
            for(int i=0;i<a.length;i++){ if(i>0)sb.append(","); sb.append(a[i]); }
            return sb.append("]").toString();
        }
        return String.valueOf(o);
    }

    private static synchronized void log(String s) {
        String line = System.currentTimeMillis() + " " + s;
        Log.i(TAG, line);
        try {
            File f = new File(LOG);
            File p = f.getParentFile();
            if (p != null) p.mkdirs();
            FileWriter fw = new FileWriter(f, true);
            fw.write(line + "\n");
            fw.close();
        } catch(Throwable ignored) {}
        try { XposedBridge.log("IceCam " + line); } catch(Throwable ignored) {}
    }
}
