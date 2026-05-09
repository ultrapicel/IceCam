package com.icecam.hooks;

/**
 * Placeholder for LSPosed/Zygisk hook layer.
 * v2 only records target APIs and module readiness.
 * Future builds will add CameraManager/CameraDevice interception here.
 */
public final class IceCamHookSkeleton {
    public static final String[] TARGETS = new String[] {
            "android.hardware.Camera",
            "android.hardware.camera2.CameraManager",
            "androidx.camera.core.CameraX",
            "android.hardware.camera2.CameraDevice",
            "NDK ACameraManager"
    };
    private IceCamHookSkeleton() {}
}
