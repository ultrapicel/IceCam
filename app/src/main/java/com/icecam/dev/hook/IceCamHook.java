package com.icecam.dev.hook;

import android.util.Log;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.content.Context;
import android.app.Application;
import android.hardware.Camera;
import android.database.Cursor;
import android.net.Uri;
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
    private static final String VERSION = "v9.6.2-continuous-surface-renderer";
    private static final String PROVIDER_CONFIG_URI = "content://com.icecam.dev.provider/config";
    private static final String PROVIDER_STATE_URI = "content://com.icecam.dev.provider/state";
    private static final String PROVIDER_MEDIA_URI = "content://com.icecam.dev.provider/media-meta";
    private static final boolean DIRECT_DATA_ADB_IO = false;
    private static volatile Context attachedContext;

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
                + " provider=" + trim(providerConfig(), 256)
                + " mediaReady=" + mediaReady());

        accessProbe(lp);

        safeInit("hookApplication.attach", new Runnable() { public void run() { hookApplicationAttach(lp); } });
        safeInit("hookCamera2.CameraManager", new Runnable() { public void run() { hookCameraManager(lp); } });
        safeInit("hookCamera2.CameraDeviceImpl", new Runnable() { public void run() { hookCameraDeviceImpl(lp); } });
        safeInit("hookCamera2.CameraCaptureSessionImpl", new Runnable() { public void run() { hookCameraSessionImpl(lp); } });
        safeInit("hookCamera1", new Runnable() { public void run() { hookCamera1(lp); } });
        safeInit("hookSurfaceTrace", new Runnable() { public void run() { hookSurfaceTrace(lp); } });
        safeInit("hookSurfaceOwnershipTrace", new Runnable() { public void run() { hookSurfaceOwnershipTrace(lp); } });
    }


    private void hookApplicationAttach(final XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> app = findClassBoot("android.app.Application");
        if (app == null) return;
        hookAll(app, "attach", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object ctx = arg(p, 0, null);
                if (ctx instanceof Context) {
                    attachedContext = (Context) ctx;
                    log("[Context] Application.attach captured package=" + lp.packageName
                            + " process=" + lp.processName
                            + " ctx=" + className(ctx));
                }
            }
        });
    }

    private static void ensureRenderer(XC_LoadPackage.LoadPackageParam lp, String reason) {
        // v9.4.3: do not gate renderer startup on /data/adb active read.
        // On Android 13+ target app contexts can hit SELinux EACCES on /data/adb,
        // which made v9.4 report active=false and never start the sandbox.
        try {
            RendererSandbox.ensureStarted(lp.packageName, lp.processName, providerConfig());
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
                maybeTraceSessionSurfaces(lp, p, "createCaptureSession.before");
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
                logAutoPipeline(lp, "CameraCaptureSession.setRepeatingRequest", p);
            }
        });
        hookAll(cs, "capture", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                logEvent(lp, "[Camera2] CameraCaptureSession.capture", p);
                sessionEvent(lp, "capture", p);
                logAutoPipeline(lp, "CameraCaptureSession.capture", p);
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
        String json = "{\"version\":\"" + VERSION + "\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
                + "\",\"package\":\"" + esc(lp.packageName) + "\",\"process\":\"" + esc(lp.processName)
                + "\",\"action\":\"" + esc(action) + "\",\"active\":" + active()
                + ",\"mode\":\"" + esc(mode()) + "\",\"mediaReady\":" + mediaReady()
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
                    stopSurfaceRender(arg(p, 0, null), "Surface.release.this-missing");
                    stopSurfaceRender(p.thisObject, "Surface.release");
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
        String json = "{\"version\":\"" + VERSION + "\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
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
                    maybeStartSurfaceShadow(lp, arg(p, 0, null), "CaptureRequest.Builder.addTarget");
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
                    logAutoPipeline(lp, "CaptureRequest.Builder.build.before", p);
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
        String json = "{\"version\":\"" + VERSION + "\",\"ts\":\"" + esc(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
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

    private static String objectDetail(Object o) {
        return surfaceOrObjectDetails(o);
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


    private static boolean surfaceShadowEnabled() {
        if (!active()) return false;
        String m = mode();
        return "auto-pipeline".equals(m) || "camera2-surface-shadow".equals(m) || "experimental-frame-injection".equals(m);
    }

    private static void logAutoPipeline(XC_LoadPackage.LoadPackageParam lp, String action, XC_MethodHook.MethodHookParam p) {
        if (!surfaceShadowEnabled()) return;
        String json = "{\"version\":\"" + VERSION + "\",\"package\":\"" + esc(lp.packageName)
                + "\",\"process\":\"" + esc(lp.processName) + "\",\"action\":\"" + esc(action)
                + "\",\"mode\":\"" + esc(mode()) + "\",\"mediaReady\":" + mediaReady()
                + ",\"args\":\"" + esc(argsToString(p == null ? null : p.args)) + "\"}";
        try { Log.i(TAG, "AutoPipelineJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook AutoPipelineJson " + json);
    }

    private static void maybeTraceSessionSurfaces(XC_LoadPackage.LoadPackageParam lp, XC_MethodHook.MethodHookParam p, String action) {
        if (!surfaceShadowEnabled()) return;
        try {
            StringBuilder sb = new StringBuilder();
            Object[] args = p == null ? null : p.args;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a instanceof java.util.List) sb.append("arg").append(i).append("=").append(surfaceCollectionDetails((java.util.Collection)a)).append(' ');
                    else sb.append("arg").append(i).append('=').append(objectDetail(a)).append(' ');
                }
            }
            String json = "{\"version\":\"" + VERSION + "\",\"package\":\"" + esc(lp.packageName)
                    + "\",\"process\":\"" + esc(lp.processName) + "\",\"action\":\"" + esc(action)
                    + "\",\"mode\":\"" + esc(mode()) + "\",\"surfaces\":\"" + esc(sb.toString()) + "\"}";
            Log.i(TAG, "Camera2SurfaceShadowJson " + json);
            xlog("IceCam/Hook Camera2SurfaceShadowJson " + json);
        } catch (Throwable t) {
            log("[ERR] maybeTraceSessionSurfaces " + shortErr(t));
        }
    }

    private static final java.util.Set<Integer> PAINTED_SURFACES = java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());
    private static void maybeStartSurfaceShadow(final XC_LoadPackage.LoadPackageParam lp, Object surfaceObj, final String source) {
        if (!surfaceShadowEnabled()) return;
        if (!(surfaceObj instanceof Surface)) return;
        final Surface s = (Surface) surfaceObj;
        final int id = System.identityHashCode(s);
        if (!PAINTED_SURFACES.add(id)) return;
        String json = "{\"version\":\"" + VERSION + "\",\"package\":\"" + esc(lp.packageName)
                + "\",\"process\":\"" + esc(lp.processName) + "\",\"action\":\"candidate\",\"source\":\"" + esc(source)
                + "\",\"surface\":\"" + esc(objectDetail(s)) + "\",\"mode\":\"" + esc(mode()) + "\"}";
        try { Log.i(TAG, "Camera2SurfaceShadowJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook Camera2SurfaceShadowJson " + json);
        // v9.6.2 starts a bounded continuous renderer. It is still experimental and non-destructive:
        // if lockCanvas() fails or the Surface becomes invalid, the loop stops and the app falls back
        // to the normal camera pipeline.
        startContinuousSurfaceRenderer(lp, s, source, id);
    }

    private static final java.util.Map<Integer, SurfaceRenderLoop> SURFACE_RENDERERS = new java.util.concurrent.ConcurrentHashMap<Integer, SurfaceRenderLoop>();

    private static void startContinuousSurfaceRenderer(XC_LoadPackage.LoadPackageParam lp, Surface s, String source, int id) {
        if (SURFACE_RENDERERS.containsKey(id)) return;
        SurfaceRenderLoop loop = new SurfaceRenderLoop(lp.packageName, lp.processName, s, source, id);
        SURFACE_RENDERERS.put(id, loop);
        loop.start();
    }

    private static void stopSurfaceRender(Object surfaceObj, String reason) {
        if (!(surfaceObj instanceof Surface)) return;
        int id = System.identityHashCode(surfaceObj);
        PAINTED_SURFACES.remove(id);
        SurfaceRenderLoop loop = SURFACE_RENDERERS.remove(id);
        if (loop != null) loop.stop(reason);
    }

    private static class SurfaceRenderLoop implements Runnable {
        final String pkg;
        final String proc;
        final Surface surface;
        final String source;
        final int id;
        final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread thread;
        int frames;
        int errors;
        long startedAt;

        SurfaceRenderLoop(String pkg, String proc, Surface surface, String source, int id) {
            this.pkg = pkg;
            this.proc = proc;
            this.surface = surface;
            this.source = source;
            this.id = id;
        }

        void start() {
            startedAt = System.currentTimeMillis();
            thread = new Thread(this, "IceCamSurfaceRenderer-" + id);
            thread.setDaemon(true);
            logSurfaceRender("surface_render_start", "source=" + source + " surface=" + surfaceOrObjectDetails(surface));
            try { thread.start(); } catch (Throwable t) { running.set(false); logSurfaceRender("surface_render_error", shortErr(t)); }
        }

        void stop(String reason) {
            running.set(false);
            logSurfaceRender("surface_render_stop", "reason=" + reason + " frames=" + frames + " errors=" + errors);
        }

        public void run() {
            // Bounded first experiment: enough time to observe visibility, not enough to hang a target app forever.
            final long maxMs = 45000L;
            final int frameDelayMs = 66; // ~15 FPS, safer than 30 FPS for lockCanvas probes.
            while (running.get() && surfaceShadowEnabled() && (System.currentTimeMillis() - startedAt) < maxMs) {
                Canvas c = null;
                try {
                    if (!surface.isValid()) { errors++; logSurfaceRender("surface_render_error", "invalid-surface"); break; }
                    c = surface.lockCanvas(null);
                    if (c == null) { errors++; logSurfaceRender("surface_render_error", "lock-null"); break; }
                    drawContinuousPattern(c, frames);
                    frames++;
                    if (frames == 1 || frames == 15 || frames == 60 || frames % 150 == 0) {
                        logSurfaceRender("surface_render_frame", "frame=" + frames + " size=" + c.getWidth() + "x" + c.getHeight());
                    }
                } catch (Throwable t) {
                    errors++;
                    logSurfaceRender("surface_render_error", shortErr(t));
                    if (errors >= 3) break;
                } finally {
                    try { if (c != null) surface.unlockCanvasAndPost(c); } catch (Throwable t) { errors++; logSurfaceRender("surface_render_error", "unlock=" + shortErr(t)); }
                }
                try { Thread.sleep(frameDelayMs); } catch (Throwable ignored) {}
            }
            running.set(false);
            SURFACE_RENDERERS.remove(id);
            PAINTED_SURFACES.remove(id);
            logSurfaceRender("surface_render_stop", "frames=" + frames + " errors=" + errors + " ageMs=" + (System.currentTimeMillis()-startedAt));
        }

        void logSurfaceRender(String action, String detail) {
            String json = "{\"version\":\"" + VERSION + "\",\"package\":\"" + esc(pkg)
                    + "\",\"process\":\"" + esc(proc) + "\",\"action\":\"" + esc(action)
                    + "\",\"surfaceId\":" + id + ",\"source\":\"" + esc(source)
                    + "\",\"frames\":" + frames + ",\"errors\":" + errors
                    + ",\"mode\":\"" + esc(mode()) + "\",\"detail\":\"" + esc(detail) + "\"}";
            try { Log.i(TAG, "Camera2SurfaceRenderJson " + json); } catch (Throwable ignored) {}
            xlog("IceCam/Hook Camera2SurfaceRenderJson " + json);
        }
    }

    private static void drawContinuousPattern(Canvas c, int frame) {
        int w = Math.max(1, c.getWidth());
        int h = Math.max(1, c.getHeight());
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int phase = frame % 255;
        c.drawColor(Color.rgb((phase / 3) % 80, 12, 28 + (phase % 80)));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(20, 110 + (phase % 100), 220));
        int box = Math.max(80, Math.min(w, h) / 5);
        int x = 20 + ((frame * 17) % Math.max(1, w - box - 40));
        int y = 70 + ((frame * 11) % Math.max(1, h - box - 100));
        c.drawRect(x, y, x + box, y + box, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(4f, w / 240f));
        p.setColor(Color.WHITE);
        c.drawRect(18, 18, w - 18, h - 18, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(Math.max(28f, w / 24f));
        p.setColor(Color.WHITE);
        c.drawText("IceCam v9.6.2", 48, Math.min(h - 80, 110), p);
        p.setTextSize(Math.max(20f, w / 42f));
        c.drawText("continuous Surface renderer · frame " + frame, 48, Math.min(h - 40, 160), p);
    }

    private static void tryPaintSurfaceOnce(XC_LoadPackage.LoadPackageParam lp, Surface s, String source, int id) {
        Canvas c = null;
        String result = "unknown";
        try {
            c = s.lockCanvas(null);
            if (c == null) result = "lock-null";
            else {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                c.drawColor(Color.rgb(8, 12, 24));
                p.setColor(Color.rgb(30, 144, 255));
                p.setStrokeWidth(8f);
                c.drawRect(20, 20, Math.max(60, c.getWidth()-20), Math.max(60, c.getHeight()-20), p);
                p.setTextSize(Math.max(28f, c.getWidth() / 24f));
                p.setColor(Color.WHITE);
                c.drawText("IceCam v9.6.2", 48, Math.min(c.getHeight()-60, 110), p);
                p.setTextSize(Math.max(20f, c.getWidth() / 40f));
                c.drawText("Camera2 surface single paint fallback", 48, Math.min(c.getHeight()-30, 160), p);
                result = "paint-ok:" + c.getWidth() + "x" + c.getHeight();
            }
        } catch (Throwable t) {
            result = shortErr(t);
        } finally {
            try { if (c != null) s.unlockCanvasAndPost(c); } catch (Throwable t) { result = result + ":unlock=" + shortErr(t); }
        }
        String json = "{\"version\":\"" + VERSION + "\",\"package\":\"" + esc(lp.packageName)
                + "\",\"process\":\"" + esc(lp.processName) + "\",\"action\":\"paint-probe\",\"source\":\"" + esc(source)
                + "\",\"surfaceId\":" + id + ",\"result\":\"" + esc(result) + "\",\"mode\":\"" + esc(mode()) + "\"}";
        try { Log.i(TAG, "Camera2SurfaceShadowJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook Camera2SurfaceShadowJson " + json);
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
                        + " active=" + active() + " injection=" + injectionEnabled());
            }
        });
        hookAll(cam, "setPreviewCallback", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                wrapPreviewCallback(lp, p, "setPreviewCallback");
            }
        });
        hookAll(cam, "setOneShotPreviewCallback", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                wrapPreviewCallback(lp, p, "setOneShotPreviewCallback");
            }
        });
        hookAll(cam, "setPreviewCallbackWithBuffer", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                wrapPreviewCallback(lp, p, "setPreviewCallbackWithBuffer");
            }
        });
        hookAll(cam, "addCallbackBuffer", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object buf = arg(p, 0, null);
                log("[Camera1Injection] addCallbackBuffer package=" + lp.packageName
                        + " len=" + byteArrayLength(buf) + " injection=" + injectionEnabled());
            }
        });
    }

    private static void wrapPreviewCallback(final XC_LoadPackage.LoadPackageParam lp, XC_MethodHook.MethodHookParam p, final String method) {
        try {
            Object original = arg(p, 0, null);
            if (!(original instanceof Camera.PreviewCallback)) {
                log("[Camera1Injection] " + method + " callback=null package=" + lp.packageName);
                return;
            }
            final Camera.PreviewCallback orig = (Camera.PreviewCallback) original;
            p.args[0] = new Camera.PreviewCallback() {
                @Override public void onPreviewFrame(byte[] data, Camera camera) {
                    if (!injectionEnabled()) {
                        orig.onPreviewFrame(data, camera);
                        return;
                    }
                    try {
                        Camera.Size s = previewSize(camera);
                        int w = s == null ? 640 : s.width;
                        int h = s == null ? 480 : s.height;
                        byte[] frame = makeNv21TestFrame(w, h, data == null ? 0 : data.length);
                        logCamera1Injection(lp, method, w, h, data, frame);
                        orig.onPreviewFrame(frame, camera);
                    } catch (Throwable t) {
                        log("[ERR] Camera1Injection fallback " + method + " " + stack(t));
                        try { orig.onPreviewFrame(data, camera); } catch (Throwable ignored) {}
                    }
                }
            };
            log("[Camera1Injection] wrapped " + method + " package=" + lp.packageName
                    + " process=" + lp.processName + " active=" + active() + " mode=" + mode());
        } catch (Throwable t) {
            log("[ERR] Camera1Injection wrap " + method + " " + stack(t));
        }
    }

    private static Camera.Size previewSize(Camera camera) {
        try {
            Camera.Parameters p = camera == null ? null : camera.getParameters();
            return p == null ? null : p.getPreviewSize();
        } catch (Throwable ignored) { return null; }
    }

    private static byte[] makeNv21TestFrame(int width, int height, int requestedLen) {
        int ySize = Math.max(1, width * height);
        int uvSize = Math.max(1, ySize / 2);
        int len = ySize + uvSize;
        if (requestedLen >= len) len = requestedLen;
        byte[] out = new byte[len];
        long t = (System.currentTimeMillis() / 80L) & 0xff;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int band = ((x / Math.max(1, width / 8)) + (y / Math.max(1, height / 6)) + (int)(t / 8)) & 1;
                int v = band == 0 ? 48 : 205;
                if (x < 6 || y < 6 || x >= width - 6 || y >= height - 6) v = 235;
                out[row + x] = (byte) v;
            }
        }
        for (int i = ySize; i + 1 < out.length; i += 2) {
            out[i] = (byte) 128;      // V
            out[i + 1] = (byte) 128;  // U
        }
        return out;
    }

    private static long lastInjectionLogTs;
    private static int injectionFrameCount;
    private static void logCamera1Injection(XC_LoadPackage.LoadPackageParam lp, String method, int w, int h, byte[] in, byte[] out) {
        injectionFrameCount++;
        long now = System.currentTimeMillis();
        if (now - lastInjectionLogTs < 1000) return;
        lastInjectionLogTs = now;
        String json = "{\"version\":\"" + VERSION + "\",\"package\":\"" + esc(lp.packageName)
                + "\",\"process\":\"" + esc(lp.processName) + "\",\"method\":\"" + esc(method)
                + "\",\"width\":" + w + ",\"height\":" + h
                + ",\"inputBytes\":" + byteArrayLength(in) + ",\"outputBytes\":" + byteArrayLength(out)
                + ",\"frameCount\":" + injectionFrameCount + ",\"mode\":\"" + esc(mode()) + "\"}";
        try { Log.i(TAG, "Camera1InjectionJson " + json); } catch (Throwable ignored) {}
        xlog("IceCam/Hook Camera1InjectionJson " + json);
    }

    private static int byteArrayLength(Object o) { return o instanceof byte[] ? ((byte[])o).length : -1; }

    private static boolean injectionEnabled() {
        if (!active()) return false;
        String m = mode();
        return "camera1-nv21-test".equals(m) || "experimental-frame-injection".equals(m) || "auto-pipeline".equals(m);
    }

    private static void accessProbe(XC_LoadPackage.LoadPackageParam lp) {
        if (isSelfPackage(lp)) return;
        String config = providerConfig();
        String state = providerState();
        String media = providerMediaMeta();
        StringBuilder sb = new StringBuilder();
        sb.append("[ProviderProbe] package=").append(lp.packageName)
          .append(" process=").append(lp.processName)
          .append(" providerConfig=").append(trim(config, 512))
          .append(" providerState=").append(trim(state, 256))
          .append(" providerMedia=").append(trim(media, 256))
          .append(" providerStats=").append(providerStats())
          .append(" mediaReady=").append(mediaReady());
        if (DIRECT_DATA_ADB_IO) {
            sb.append(" activeRead=").append(probeRead(ACTIVE))
              .append(" configRead=").append(probeRead(CONFIG))
              .append(" mediaExists=").append(probeExists(MEDIA));
        } else {
            sb.append(" directDataAdbIo=disabled");
        }
        log(sb.toString());
        try { Log.i(TAG, "ProviderBridgeJson " + config); } catch (Throwable ignored) {}
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
        field(sb, "version", VERSION, true);
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
        String c = providerConfig();
        String v = jsonString(c, "compatibilityMode", null);
        if (v != null && v.length() > 0) return v;
        if (DIRECT_DATA_ADB_IO) {
            c = readSmall(CONFIG);
            v = jsonString(c, "compatibilityMode", null);
            if (v != null && v.length() > 0) return v;
        }
        return "strict-real";
    }

    private static void writeFile(String path, String data) {
        if (!DIRECT_DATA_ADB_IO) return;
        try {
            File f = new File(path); File d = f.getParentFile(); if (d != null && !d.exists()) d.mkdirs();
            FileOutputStream out = new FileOutputStream(f, false); out.write(data.getBytes("UTF-8")); out.close();
        } catch (Throwable t) { log("[WARN] writeFile " + path + " " + shortErr(t)); }
    }
    private static void appendFile(String path, String data) {
        if (!DIRECT_DATA_ADB_IO) return;
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
                + " mediaReady=" + mediaReady()
                + " provider=" + trim(providerConfig(), 512)
                + " providerStats=" + providerStats());
    }

    private static Object arg(XC_MethodHook.MethodHookParam p, int index, Object fallback) {
        return p != null && p.args != null && p.args.length > index ? p.args[index] : fallback;
    }

    private static boolean active() {
        String c = providerConfig();
        String v = jsonRaw(c, "active", null);
        if ("true".equalsIgnoreCase(v)) return true;
        if ("false".equalsIgnoreCase(v)) return false;
        return DIRECT_DATA_ADB_IO && "1".equals(readSmall(ACTIVE).trim());
    }

    private static String mode() {
        String c = providerConfig();
        String v = jsonString(c, "mode", null);
        if (v != null && v.length() > 0) return v;
        if (DIRECT_DATA_ADB_IO) {
            v = jsonString(readSmall(CONFIG), "mode", null);
            if (v != null && v.length() > 0) return v;
        }
        return "unknown";
    }

    private static boolean mediaReady() {
        String m = providerMediaMeta();
        String c = providerConfig();
        String mv = jsonRaw(m, "mediaReady", null);
        if ("true".equalsIgnoreCase(mv)) return true;
        if ("false".equalsIgnoreCase(mv)) return false;
        String cv = jsonRaw(c, "mediaReady", null);
        if ("true".equalsIgnoreCase(cv)) return true;
        if ("false".equalsIgnoreCase(cv)) return false;
        return false;
    }

    private static boolean directMediaExists() {
        if (!DIRECT_DATA_ADB_IO) return false;
        try { return new File(MEDIA).exists(); } catch (Throwable ignored) { return false; }
    }

    private static final long PROVIDER_CACHE_TTL_MS = 750L;
    private static final Object PROVIDER_LOCK = new Object();
    private static ProviderEntry providerConfigCache = new ProviderEntry(PROVIDER_CONFIG_URI);
    private static ProviderEntry providerStateCache = new ProviderEntry(PROVIDER_STATE_URI);
    private static ProviderEntry providerMediaCache = new ProviderEntry(PROVIDER_MEDIA_URI);
    private static long providerOkCount;
    private static long providerFailCount;
    private static long providerCacheHitCount;
    private static long providerNoContextCount;

    private static final class ProviderEntry {
        final String uri;
        long ts;
        String value;
        ProviderEntry(String uri) { this.uri = uri; }
    }

    private static String providerConfig() { return queryProviderCached(providerConfigCache); }
    private static String providerState() { return queryProviderCached(providerStateCache); }
    private static String providerMediaMeta() { return queryProviderCached(providerMediaCache); }

    private static String providerStats() {
        synchronized (PROVIDER_LOCK) {
            return "ok=" + providerOkCount + ",fail=" + providerFailCount + ",cacheHit=" + providerCacheHitCount + ",noContext=" + providerNoContextCount;
        }
    }

    private static String queryProviderCached(ProviderEntry e) {
        long now = System.currentTimeMillis();
        synchronized (PROVIDER_LOCK) {
            if (e.value != null && now - e.ts < PROVIDER_CACHE_TTL_MS) {
                providerCacheHitCount++;
                return e.value;
            }
        }
        String value = queryProviderUncached(e.uri);
        synchronized (PROVIDER_LOCK) {
            e.value = value;
            e.ts = now;
            if (value != null && value.startsWith("provider:no-context")) providerNoContextCount++;
            if (value != null && value.startsWith("provider:")) providerFailCount++; else providerOkCount++;
        }
        return value;
    }

    private static String queryProviderUncached(String uri) {
        Cursor c = null;
        try {
            Context ctx = currentContext();
            if (ctx == null) return "provider:no-context";
            c = ctx.getContentResolver().query(Uri.parse(uri), null, null, null, null);
            if (c == null) return "provider:null-cursor";
            if (!c.moveToFirst()) return "provider:empty";
            int idx = c.getColumnIndex("json");
            return idx >= 0 ? c.getString(idx) : "provider:no-json-column";
        } catch (Throwable t) {
            return "provider:" + shortErr(t);
        } finally {
            try { if (c != null) c.close(); } catch (Throwable ignored) {}
        }
    }

    private static Context currentContext() {
        try {
            Class<?> helper = Class.forName("de.robv.android.xposed.AndroidAppHelper");
            Object app = helper.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {}
        if (attachedContext != null) return attachedContext;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {}
        return null;
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
        if (!DIRECT_DATA_ADB_IO) return;
        try {
            File f = new File(LOG);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileOutputStream out = new FileOutputStream(f, true);
            out.write((line + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Throwable t) {
            try { Log.w(TAG, "file-log failed: " + shortErr(t)); } catch (Throwable ignored) {}
        }
    }
}
