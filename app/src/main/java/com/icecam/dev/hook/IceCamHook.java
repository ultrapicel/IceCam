package com.icecam.dev.hook;

import android.util.Log;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import com.icecam.dev.renderer.RendererSandbox;
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
    private static final String CACHE_DIR = "/data/adb/icecam/cache";
    private static final String PROFILE_CACHE = CACHE_DIR + "/camera_profiles.json";
    private static final String PROFILE_EVENTS = CACHE_DIR + "/camera_profiles.jsonl";
    private static final String SESSION_EVENTS = CACHE_DIR + "/capture_session_events.jsonl";
    private static final String SURFACE_EVENTS = CACHE_DIR + "/surface_events.jsonl";
    private static final String SURFACE_OWNERSHIP_EVENTS = CACHE_DIR + "/surface_ownership_events.jsonl";
    private static final String VERSION = "9.4.2-root-bootstrap-cleanup";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (isSelfPackage(lp)) {
            try { Log.i(TAG, "[SKIP_SELF] package=" + lp.packageName + " process=" + lp.processName); } catch (Throwable ignored) {}
            return;
        }
        log("[LOAD] package=" + lp.packageName
                + " process=" + lp.processName
                + " thread=" + Thread.currentThread().getName()
                + " classLoader=" + safe(lp.classLoader)
                + " active=" + active()
                + " mode=" + mode()
                + " mediaPath=" + MEDIA
                + " mediaExists=" + new File(MEDIA).exists());

        accessProbe(lp);

        safeInit("hookCamera2.CameraManager", new Runnable() { public void run() { hookCameraManager(lp); } });
        safeInit("hookCamera2.CameraDeviceImpl", new Runnable() { public void run() { hookCameraDeviceImpl(lp); } });
        safeInit("hookCamera2.CameraCaptureSessionImpl", new Runnable() { public void run() { hookCameraSessionImpl(lp); } });
        safeInit("hookCamera1", new Runnable() { public void run() { hookCamera1(lp); } });
        safeInit("hookSurfaceTrace", new Runnable() { public void run() { hookSurfaceTrace(lp); } });
        safeInit("hookSurfaceOwnershipTrace", new Runnable() { public void run() { hookSurfaceOwnershipTrace(lp); } });
    }

    private static void ensureRenderer(XC_LoadPackage.LoadPackageParam lp, String reason) {
        // v9.4.2: do not gate renderer startup on /data/adb active read.
        // On Android 13+ target app contexts can hit SELinux EACCES on /data/adb,
        // which made v9.4 report active=false and never start the sandbox.
        try {
            RendererSandbox.ensureStarted(lp.packageName, lp.processName);
            log("[RendererSandbox] ensure reason=" + reason + " " + RendererSandbox.snapshot());
        } catch (Throwable t) {
            log("[ERR] RendererSandbox ensure " + reason + " " + stack(t));
            xlog(t);
        }
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
                Object result = p.getResult();
                log("[Camera2] getCameraCharacteristics after id=" + id
                        + " result=" + className(result)
                        + " active=" + active());
                cacheCameraProfile(lp, String.valueOf(id), result);
            }
        });

        hookAll(cm, "openCamera", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "?");
                ensureRenderer(lp, "CameraManager.openCamera");
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

    private void hookCameraDeviceImpl(final XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cd = findClassBoot("android.hardware.camera2.impl.CameraDeviceImpl");
        if (cd == null) {
            log("[WARN] CameraDeviceImpl unavailable; falling back to CameraDevice with abstract-safe hook filter");
            cd = findClassBoot("android.hardware.camera2.CameraDevice");
        }
        if (cd == null) return;

        hookAll(cd, "createCaptureSession", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                ensureRenderer(lp, "CameraDevice.createCaptureSession");
                logEvent(lp, "[Camera2] CameraDeviceImpl.createCaptureSession", p);
                surfaceOwnerEvent(lp, "CameraDeviceImpl.createCaptureSession", p, null);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                log("[Camera2] CameraDeviceImpl.createCaptureSession after package=" + lp.packageName
                        + " active=" + active() + " mode=" + mode());
                surfaceOwnerEvent(lp, "CameraDeviceImpl.createCaptureSession.after", p, p.getResult());
            }
        });

        hookAll(cd, "createCaptureRequest", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraDeviceImpl.createCaptureRequest", p);
                surfaceOwnerEvent(lp, "CameraDeviceImpl.createCaptureRequest", p, null);
            }
        });

        hookAll(cd, "close", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraDeviceImpl.close", p);
            }
        });
    }

    private void hookCameraSessionImpl(final XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cs = findClassBoot("android.hardware.camera2.impl.CameraCaptureSessionImpl");
        if (cs == null) {
            log("[WARN] CameraCaptureSessionImpl unavailable; falling back to CameraCaptureSession with abstract-safe hook filter");
            cs = findClassBoot("android.hardware.camera2.CameraCaptureSession");
        }
        if (cs == null) return;
        hookAll(cs, "setRepeatingRequest", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.setRepeatingRequest", p);
                sessionEvent(lp, "setRepeatingRequest", p);
            }
        });
        hookAll(cs, "capture", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.capture", p);
                sessionEvent(lp, "capture", p);
            }
        });
        hookAll(cs, "captureBurst", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.captureBurst", p);
                sessionEvent(lp, "captureBurst", p);
            }
        });
        hookAll(cs, "stopRepeating", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.stopRepeating", p);
                sessionEvent(lp, "stopRepeating", p);
            }
        });
        hookAll(cs, "abortCaptures", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.abortCaptures", p);
                sessionEvent(lp, "abortCaptures", p);
            }
        });
        hookAll(cs, "close", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.close", p);
                sessionEvent(lp, "close", p);
            }
        });
    }

    private static void sessionEvent(XC_LoadPackage.LoadPackageParam lp, String action, XC_MethodHook.MethodHookParam p) {
        if (!shouldTrace(lp, action)) return;
        String json = "{\"version\":\"9.4.2-root-bootstrap-cleanup\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                + "\",\"package\":\"" + esc(lp.packageName) + "\",\"process\":\"" + esc(lp.processName)
                + "\",\"action\":\"" + esc(action) + "\",\"active\":" + active()
                + ",\"mode\":\"" + esc(mode()) + "\",\"mediaExists\":" + new File(MEDIA).exists()
                + ",\"args\":\"" + esc(argsSummary(p)) + "\""
                + ",\"requestTargets\":\"" + esc(requestTargetsFromArgs(p)) + "\"}";
        try { Log.i(TAG, "CaptureSessionJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook CaptureSessionJson " + json);
        appendFile(SESSION_EVENTS, json + "\n");
    }



    private void hookSurfaceTrace(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> surface = findClassBoot("android.view.Surface");
        if (surface != null) {
            hookAll(surface, "release", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "Surface.release", p);
                }
            });
        }

        final Class<?> surfaceTexture = findClassBoot("android.graphics.SurfaceTexture");
        if (surfaceTexture != null) {
            hookAll(surfaceTexture, "setDefaultBufferSize", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "SurfaceTexture.setDefaultBufferSize", p);
                }
            });
            hookAll(surfaceTexture, "updateTexImage", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "SurfaceTexture.updateTexImage", p);
                }
            });
            hookAll(surfaceTexture, "release", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "SurfaceTexture.release", p);
                }
            });
        }

        final Class<?> imageReader = findClassBoot("android.media.ImageReader");
        if (imageReader != null) {
            hookAll(imageReader, "newInstance", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "ImageReader.newInstance", p);
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "ImageReader.newInstance.after", p);
                }
            });
            hookAll(imageReader, "getSurface", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "ImageReader.getSurface.after", p);
                }
            });
            hookAll(imageReader, "close", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceEvent(lp, "ImageReader.close", p);
                }
            });
        }
    }

    private static void surfaceEvent(XC_LoadPackage.LoadPackageParam lp, String action, XC_MethodHook.MethodHookParam p) {
        if (!shouldTrace(lp, action)) return;
        String json = "{\"version\":\"9.4.2-root-bootstrap-cleanup\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                + "\",\"package\":\"" + esc(lp.packageName) + "\",\"process\":\"" + esc(lp.processName)
                + "\",\"action\":\"" + esc(action) + "\",\"thread\":\"" + esc(Thread.currentThread().getName())
                + "\",\"active\":" + active() + ",\"mode\":\"" + esc(mode())
                + "\",\"this\":\"" + esc(objectId(p == null ? null : p.thisObject))
                + "\",\"result\":\"" + esc(objectId(p == null ? null : p.getResult()))
                + "\",\"args\":\"" + esc(argsSummary(p)) + "\"}";
        try { Log.i(TAG, "SurfaceJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook SurfaceJson " + json);
        appendFile(SURFACE_EVENTS, json + "\n");
    }


    private void hookSurfaceOwnershipTrace(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> builder = findClassBoot("android.hardware.camera2.CaptureRequest$Builder");
        if (builder != null) {
            hookAll(builder, "addTarget", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "CaptureRequest.Builder.addTarget", p, arg(p, 0, null));
                }
            });
            hookAll(builder, "removeTarget", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "CaptureRequest.Builder.removeTarget", p, arg(p, 0, null));
                }
            });
            hookAll(builder, "build", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "CaptureRequest.Builder.build.before", p, null);
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "CaptureRequest.Builder.build.after", p, p.getResult());
                }
            });
        }

        final Class<?> outputConfig = findClassBoot("android.hardware.camera2.params.OutputConfiguration");
        if (outputConfig != null) {
            hookAll(outputConfig, "addSurface", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "OutputConfiguration.addSurface", p, arg(p, 0, null));
                }
            });
            hookAll(outputConfig, "removeSurface", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "OutputConfiguration.removeSurface", p, arg(p, 0, null));
                }
            });
            hookAll(outputConfig, "getSurfaces", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    surfaceOwnerEvent(lp, "OutputConfiguration.getSurfaces.after", p, p.getResult());
                }
            });
        }
    }

    private static void surfaceOwnerEvent(XC_LoadPackage.LoadPackageParam lp, String action, XC_MethodHook.MethodHookParam p, Object focus) {
        if (!shouldTrace(lp, action)) return;
        String json = "{\"version\":\"9.4.2-root-bootstrap-cleanup\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                + "\",\"package\":\"" + esc(lp.packageName) + "\",\"process\":\"" + esc(lp.processName)
                + "\",\"action\":\"" + esc(action) + "\",\"thread\":\"" + esc(Thread.currentThread().getName())
                + "\",\"active\":" + active() + ",\"mode\":\"" + esc(mode())
                + "\",\"this\":\"" + esc(objectId(p == null ? null : p.thisObject))
                + "\",\"focus\":\"" + esc(surfaceOrObjectDetails(focus))
                + "\",\"args\":\"" + esc(argsDetailed(p == null ? null : p.args))
                + "\",\"requestTargets\":\"" + esc(requestTargetsFromObject(focus)) + "\"}";
        try { Log.i(TAG, "SurfaceOwnerJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook SurfaceOwnerJson " + json);
        appendFile(SURFACE_OWNERSHIP_EVENTS, json + "\n");
    }

    private static String requestTargetsFromArgs(XC_MethodHook.MethodHookParam p) {
        if (p == null || p.args == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.args.length; i++) {
            Object a = p.args[i];
            String t = requestTargetsFromObject(a);
            if (t.length() > 0) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("arg").append(i).append('=').append(t);
            }
        }
        return sb.toString();
    }


    private static java.util.Collection getCaptureRequestTargetsReflective(Object request) {
        try {
            java.lang.reflect.Method m = request.getClass().getDeclaredMethod("getTargets");
            m.setAccessible(true);
            Object r = m.invoke(request);
            if (r instanceof java.util.Collection) return (java.util.Collection) r;
        } catch (Throwable t) {
            // getTargets is not public on some Android SDK stubs; keep tracing without failing build/runtime.
        }
        return null;
    }

    private static String requestTargetsFromObject(Object o) {
        try {
            if (o == null) return "";
            if (o instanceof CaptureRequest) {
                return surfaceCollectionDetails(getCaptureRequestTargetsReflective(o));
            }
            if (o instanceof java.util.Collection) return surfaceCollectionDetails((java.util.Collection)o);
            if (o.getClass().isArray()) {
                int n = java.lang.reflect.Array.getLength(o);
                StringBuilder sb = new StringBuilder("[");
                for (int i=0; i<n; i++) { if (i>0) sb.append(','); sb.append(surfaceOrObjectDetails(java.lang.reflect.Array.get(o, i))); }
                return sb.append(']').toString();
            }
            return "";
        } catch (Throwable t) { return shortErr(t); }
    }

    private static String surfaceCollectionDetails(java.util.Collection c) {
        if (c == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Object o : c) { if (i++ > 0) sb.append(','); sb.append(surfaceOrObjectDetails(o)); }
        return sb.append(']').toString();
    }

    private static String argsDetailed(Object[] args) {
        if (args == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(i).append('=').append(surfaceOrObjectDetails(args[i]));
        }
        return sb.append(']').toString();
    }

    private static String surfaceOrObjectDetails(Object o) {
        if (o == null) return "null";
        try {
            String base = objectId(o);
            if (o instanceof Surface) {
                return base + ":Surface:" + safeToString(o);
            }
            if (o instanceof CaptureRequest) {
                return base + ":CaptureRequest targets=" + requestTargetsFromObject(o);
            }
            if (o instanceof java.util.Collection) {
                return base + ":Collection" + requestTargetsFromObject(o);
            }
            String cn = o.getClass().getName();
            if (cn.contains("ImageReader")) {
                return base + ":" + cn + ":" + reflectNoArg(o, "getWidth") + "x" + reflectNoArg(o, "getHeight") + ":fmt=" + reflectNoArg(o, "getImageFormat");
            }
            return base;
        } catch (Throwable t) { return objectId(o) + ":" + shortErr(t); }
    }

    private static String reflectNoArg(Object o, String method) {
        try { return String.valueOf(o.getClass().getMethod(method).invoke(o)); }
        catch (Throwable t) { return "?"; }
    }

    private static String safeToString(Object o) {
        try { return String.valueOf(o); } catch (Throwable t) { return shortErr(t); }
    }


    private static boolean isSelfPackage(XC_LoadPackage.LoadPackageParam lp) {
        return lp != null && ("com.icecam.dev".equals(lp.packageName) || "com.icecam.dev".equals(lp.processName));
    }

    private static boolean shouldTrace(XC_LoadPackage.LoadPackageParam lp, String action) {
        if (isSelfPackage(lp)) return false;
        if (action == null) return true;
        if ("Surface.isValid".equals(action)) return false;
        return true;
    }

    private void hookCamera1(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> cam = findClassBoot("android.hardware.Camera");
        if (cam == null) return;
        hookAll(cam, "open", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "default");
                ensureRenderer(lp, "Camera1.open");
                logEvent(lp, "[Camera1] open id=" + id, p);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object id = arg(p, 0, "default");
                log("[Camera1] open after id=" + id + " result=" + className(p.getResult())
                        + " active=" + active());
            }
        });
    }

    private static void accessProbe(XC_LoadPackage.LoadPackageParam lp) {
        if (isSelfPackage(lp)) return;
        StringBuilder sb = new StringBuilder();
        sb.append("[AccessProbe] package=").append(lp.packageName)
          .append(" process=").append(lp.processName)
          .append(" activeRead=").append(probeRead(ACTIVE))
          .append(" configRead=").append(probeRead(CONFIG))
          .append(" mediaExists=").append(probeExists(MEDIA))
          .append(" hookLogWrite=").append(probeAppend(LOG))
          .append(" cacheWrite=").append(probeAppend(PROFILE_EVENTS));
        log(sb.toString());
        try { Log.i(TAG, sb.toString()); } catch (Throwable ignored) {}
    }

    private static String probeRead(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return "missing";
            FileInputStream in = new FileInputStream(f);
            int b = in.read();
            in.close();
            return "ok:" + b;
        } catch (Throwable t) { return shortErr(t); }
    }

    private static String probeExists(String path) {
        try { return String.valueOf(new File(path).exists()); }
        catch (Throwable t) { return shortErr(t); }
    }

    private static String probeAppend(String path) {
        try {
            FileOutputStream out = new FileOutputStream(path, true);
            out.write(("# probe " + System.currentTimeMillis() + "\n").getBytes("UTF-8"));
            out.close();
            return "ok";
        } catch (Throwable t) { return shortErr(t); }
    }

    private static void cacheCameraProfile(XC_LoadPackage.LoadPackageParam lp, String id, Object obj) {
        try {
            if (!(obj instanceof CameraCharacteristics)) {
                log("[ProfileCache] skip id=" + id + " result=" + className(obj));
                return;
            }
            CameraCharacteristics cc = (CameraCharacteristics) obj;
            String json = profileJson(lp, id, cc);
            try { Log.i(TAG, "ProfileCacheJson " + json); } catch (Throwable ignored) {}
            xlog("IceCam/Hook ProfileCacheJson " + json);
            appendFile(PROFILE_EVENTS, json + "\n");
            writeFile(PROFILE_CACHE, json + "\n");
            log("[ProfileCache] saved id=" + id
                    + " facing=" + val(cc, CameraCharacteristics.LENS_FACING)
                    + " orientation=" + val(cc, CameraCharacteristics.SENSOR_ORIENTATION)
                    + " hw=" + val(cc, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    + " fps=" + safe(val(cc, CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));
        } catch (Throwable t) {
            log("[ERR] ProfileCache " + stack(t));
            xlog(t);
        }
    }

    private static String profileJson(XC_LoadPackage.LoadPackageParam lp, String id, CameraCharacteristics cc) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        field(sb, "version", "9.4.2-root-bootstrap-cleanup", true);
        field(sb, "ts", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()), false);
        field(sb, "package", lp.packageName, false);
        field(sb, "process", lp.processName, false);
        field(sb, "cameraId", id, false);
        field(sb, "role", role(cc), false);
        fieldRaw(sb, "facing", String.valueOf(val(cc, CameraCharacteristics.LENS_FACING)), false);
        fieldRaw(sb, "sensorOrientation", String.valueOf(val(cc, CameraCharacteristics.SENSOR_ORIENTATION)), false);
        fieldRaw(sb, "hardwareLevel", String.valueOf(val(cc, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)), false);
        field(sb, "focalLengths", valueString(val(cc, CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)), false);
        field(sb, "fpsRanges", valueString(val(cc, CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)), false);
        try {
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            field(sb, "jpegSizes", sizes(map == null ? null : map.getOutputSizes(android.graphics.ImageFormat.JPEG), 32), false);
            field(sb, "surfaceTextureSizes", sizes(map == null ? null : map.getOutputSizes(android.graphics.SurfaceTexture.class), 32), false);
            field(sb, "mediaRecorderSizes", sizes(map == null ? null : map.getOutputSizes(android.media.MediaRecorder.class), 32), false);
        } catch (Throwable t) {
            field(sb, "streamConfigError", shortErr(t), false);
        }
        field(sb, "compatibilityMode", compatibilityMode(), false);
        sb.append("}");
        return sb.toString();
    }

    private static String role(CameraCharacteristics cc) {
        try {
            Integer f = cc.get(CameraCharacteristics.LENS_FACING);
            if (f == null) return "unknown";
            if (f == CameraCharacteristics.LENS_FACING_BACK) return "back";
            if (f == CameraCharacteristics.LENS_FACING_FRONT) return "front";
            if (BuildCompat.EXTERNAL_FACING == f) return "external";
        } catch (Throwable ignored) {}
        return "other";
    }

    private static class BuildCompat { static final int EXTERNAL_FACING = 2; }

    private static <T> T val(CameraCharacteristics cc, CameraCharacteristics.Key<T> key) {
        try { return cc.get(key); } catch (Throwable ignored) { return null; }
    }

    private static String valueString(Object o) {
        if (o == null) return "null";
        try {
            if (o instanceof float[]) {
                float[] a = (float[]) o; StringBuilder sb = new StringBuilder("[");
                for (int i=0;i<a.length;i++){ if(i>0) sb.append(','); sb.append(a[i]); }
                return sb.append(']').toString();
            }
            if (o instanceof int[]) {
                int[] a = (int[]) o; StringBuilder sb = new StringBuilder("[");
                for (int i=0;i<a.length;i++){ if(i>0) sb.append(','); sb.append(a[i]); }
                return sb.append(']').toString();
            }
            if (o instanceof Object[]) {
                Object[] a = (Object[]) o; StringBuilder sb = new StringBuilder("[");
                for (int i=0;i<a.length;i++){ if(i>0) sb.append(','); sb.append(String.valueOf(a[i])); }
                return sb.append(']').toString();
            }
            return String.valueOf(o);
        } catch (Throwable t) { return shortErr(t); }
    }

    private static String sizes(Size[] ss, int max) {
        if (ss == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int n = Math.min(ss.length, max);
        for (int i=0;i<n;i++) { if (i>0) sb.append(','); sb.append(ss[i].getWidth()).append('x').append(ss[i].getHeight()); }
        if (ss.length > max) sb.append(",...").append(ss.length).append(" total");
        return sb.append(']').toString();
    }

    private static void field(StringBuilder sb, String k, String v, boolean first) {
        if (!first) sb.append(',');
        sb.append('\"').append(esc(k)).append('\"').append(':').append('\"').append(esc(v)).append('\"');
    }
    private static void fieldRaw(StringBuilder sb, String k, String v, boolean first) {
        if (!first) sb.append(',');
        sb.append('\"').append(esc(k)).append('\"').append(':').append(v == null || "null".equals(v) ? "null" : v);
    }
    private static String esc(String s) { return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," "); }

    private static String compatibilityMode() {
        String c = readSmall(CONFIG);
        int i = c.indexOf("\"compatibilityMode\"");
        if (i < 0) return "strict-real";
        int colon = c.indexOf(':', i);
        int q = c.indexOf('\"', colon + 1);
        int e = c.indexOf('\"', q + 1);
        return (q >= 0 && e > q) ? c.substring(q + 1, e) : "strict-real";
    }

    private static void writeFile(String path, String data) {
        try {
            File f = new File(path); File d = f.getParentFile(); if (d != null && !d.exists()) d.mkdirs();
            FileOutputStream out = new FileOutputStream(f, false); out.write(data.getBytes("UTF-8")); out.close();
        } catch (Throwable t) { log("[WARN] writeFile " + path + " " + shortErr(t)); }
    }
    private static void appendFile(String path, String data) {
        try {
            File f = new File(path); File d = f.getParentFile(); if (d != null && !d.exists()) d.mkdirs();
            FileOutputStream out = new FileOutputStream(f, true); out.write(data.getBytes("UTF-8")); out.close();
        } catch (Throwable t) { log("[WARN] appendFile " + path + " " + shortErr(t)); }
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
            if (hasOnlyAbstractMethods(cls, methodName)) {
                log("[SKIP_ABSTRACT] " + cls.getName() + "." + methodName + " all matching methods are abstract");
                return;
            }
            Set<?> hooks = XposedBridge.hookAllMethods(cls, methodName, cb);
            log("[HOOKED] " + cls.getName() + "." + methodName + " count=" + (hooks == null ? "null" : String.valueOf(hooks.size())));
        } catch (Throwable t) {
            log("[ERR] hookAllMethods " + cls.getName() + "." + methodName + " " + stack(t));
            xlog(t);
        }
    }

    private static boolean hasOnlyAbstractMethods(Class<?> cls, String methodName) {
        boolean found = false;
        try {
            java.lang.reflect.Method[] methods = cls.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!methodName.equals(m.getName())) continue;
                found = true;
                if (!java.lang.reflect.Modifier.isAbstract(m.getModifiers())) return false;
            }
        } catch (Throwable ignored) {}
        return found;
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

    private static String argsSummary(XC_MethodHook.MethodHookParam p) { return p == null ? "" : argsToString(p.args); }

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

    private static String objectId(Object o) { return o == null ? "null" : o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o)); }

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
