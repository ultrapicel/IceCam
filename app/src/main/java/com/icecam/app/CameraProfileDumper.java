package com.icecam.app;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Size;
import android.graphics.ImageFormat;

public final class CameraProfileDumper {
    private CameraProfileDumper() {}

    public static String dump(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Integer level = cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                float[] focal = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                Range<Integer>[] fps = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                sb.append("Camera ").append(id)
                        .append(" facing=").append(facingToText(facing))
                        .append(" orientation=").append(orientation)
                        .append(" hwLevel=").append(levelToText(level))
                        .append('\n');
                if (focal != null) {
                    sb.append("  focal=");
                    for (float f : focal) sb.append(f).append("mm ");
                    sb.append('\n');
                }
                if (fps != null) {
                    sb.append("  fps=");
                    int count = 0;
                    for (Range<Integer> r : fps) {
                        if (count++ >= 8) { sb.append("..."); break; }
                        sb.append(r.toString()).append(' ');
                    }
                    sb.append('\n');
                }
                if (map != null) {
                    appendSizes(sb, "  yuv", map.getOutputSizes(ImageFormat.YUV_420_888));
                    appendSizes(sb, "  jpeg", map.getOutputSizes(ImageFormat.JPEG));
                    appendSizes(sb, "  surface", map.getOutputSizes(android.view.SurfaceHolder.class));
                }
            }
        } catch (Throwable t) {
            IceLog.e("Camera2", "Profile dump failed", t);
            sb.append("Camera profile dump failed: ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private static void appendSizes(StringBuilder sb, String label, Size[] sizes) {
        if (sizes == null) return;
        sb.append(label).append('=');
        int count = 0;
        for (Size s : sizes) {
            if (count++ >= 10) { sb.append("..."); break; }
            sb.append(s.getWidth()).append('x').append(s.getHeight()).append(' ');
        }
        sb.append('\n');
    }

    private static String facingToText(Integer facing) {
        if (facing == null) return "unknown";
        if (facing == CameraCharacteristics.LENS_FACING_BACK) return "back";
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return "front";
        if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return "external";
        return String.valueOf(facing);
    }

    private static String levelToText(Integer level) {
        if (level == null) return "unknown";
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "legacy";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "limited";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "full";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "level3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "external";
            default: return String.valueOf(level);
        }
    }
}
