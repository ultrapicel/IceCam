package com.icecam.hooks;

/**
 * IceCam hook skeleton.
 * Real LSPosed/Zygisk implementation will hook:
 * - android.hardware.Camera
 * - android.hardware.camera2.CameraManager
 * - androidx.camera.* wrappers when loaded by target app
 * - NDK camera path through native layer
 */
public final class IceCamHook {
    public static final String[] TAGS = {
            "IceCam/Hook", "IceCam/Camera1", "IceCam/Camera2", "IceCam/CameraX", "IceCam/NDK"
    };

    private IceCamHook() {}

    public static void log(String message) {
        android.util.Log.d("IceCam/Hook", message);
    }
}
