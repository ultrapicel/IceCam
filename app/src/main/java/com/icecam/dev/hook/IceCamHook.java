package com.icecam.dev.hook;

import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IceCamHook implements IXposedHookLoadPackage {
    private static final String TAG = "IceCam/Hook";
    private static final String LOG = "/data/adb/icecam/logs/hook.log";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String pkg = lpparam.packageName == null ? "unknown" : lpparam.packageName;
        log("[LOAD] package=" + pkg + " process=" + lpparam.processName);

        tryHookCameraManager(lpparam);
        tryHookLegacyCamera(lpparam);
    }

    private void tryHookCameraManager(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass("android.hardware.camera2.CameraManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(cls, "getCameraIdList", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera2] getCameraIdList before pkg=" + lpparam.packageName);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    log("[Camera2] getCameraIdList after pkg=" + lpparam.packageName + " result=" + safeToString(r));
                }
            });

            XposedHelpers.findAndHookMethod(cls, "getCameraCharacteristics", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera2] getCameraCharacteristics pkg=" + lpparam.packageName + " id=" + param.args[0]);
                }
            });

            hookOpenCameraOverloads(cls, lpparam);
            log("[OK] CameraManager hooks installed pkg=" + lpparam.packageName);
        } catch (Throwable t) {
            log("[ERR] CameraManager hook failed pkg=" + lpparam.packageName + " " + t);
        }
    }

    private void hookOpenCameraOverloads(Class<?> cls, final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (!"openCamera".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object id = param.args != null && param.args.length > 0 ? param.args[0] : "?";
                        log("[Camera2] openCamera pkg=" + lpparam.packageName + " id=" + id);
                    }
                });
            }
            log("[OK] openCamera overload hooks installed pkg=" + lpparam.packageName);
        } catch (Throwable t) {
            log("[ERR] openCamera hook failed pkg=" + lpparam.packageName + " " + t);
        }
    }

    private void tryHookLegacyCamera(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cam = XposedHelpers.findClass("android.hardware.Camera", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cam, "open", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera1] open pkg=" + lpparam.packageName + " id=" + param.args[0]);
                }
            });
            XposedHelpers.findAndHookMethod(cam, "open", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    log("[Camera1] open default pkg=" + lpparam.packageName);
                }
            });
            log("[OK] Camera1 hooks installed pkg=" + lpparam.packageName);
        } catch (Throwable t) {
            log("[ERR] Camera1 hook failed pkg=" + lpparam.packageName + " " + t);
        }
    }

    private static String safeToString(Object o) {
        if (o == null) return "null";
        if (o instanceof String[]) {
            String[] a = (String[]) o;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(a[i]);
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(o);
    }

    private static synchronized void log(String s) {
        String line = System.currentTimeMillis() + " " + s;
        Log.i(TAG, line);
        try {
            File f = new File(LOG);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            FileWriter fw = new FileWriter(f, true);
            fw.write(line + "\n");
            fw.close();
        } catch (Throwable ignored) {}
        try { XposedBridge.log("IceCam " + line); } catch (Throwable ignored) {}
    }
}
