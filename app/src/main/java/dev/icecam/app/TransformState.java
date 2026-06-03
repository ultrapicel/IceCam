package dev.icecam.app;

import android.content.SharedPreferences;
import java.util.Locale;

/**
 * Clean-room transform model for recovered TX24(int,float,float,float,float,int).
 * Wire format: mode, panX, panY, zoomX, zoomY, flags.
 */
public final class TransformState {
    public static final int MODE_FREE = 0;
    public static final int MODE_FIT = 1;
    public static final int MODE_FILL = 2;
    public static final int MODE_STRETCH = 3;
    public static final int MODE_NATIVE = 4;

    public static final int FLAG_MIRROR_H = 1;
    public static final int FLAG_MIRROR_V = 1 << 1;
    public static final int FLAG_ROT_SHIFT = 2;       // bits 2..3: 0/90/180/270
    public static final int FLAG_ROT_MASK = 0x3 << FLAG_ROT_SHIFT;
    public static final int FLAG_AUTO_ROTATE = 1 << 5;
    public static final int FLAG_LOCK_ASPECT = 1 << 6;

    public int mode = MODE_FIT;
    public float panX = 0f;
    public float panY = 0f;
    public float zoomX = 1f;
    public float zoomY = 1f;
    public int flags = FLAG_LOCK_ASPECT;

    public static TransformState load(SharedPreferences p) {
        TransformState s = new TransformState();
        s.mode = clampInt(p.getInt("TransformMode", MODE_FIT), MODE_FREE, MODE_NATIVE);
        s.panX = clampFloat(p.getInt("AutoColor_X", 0) / 100f, -8f, 8f);
        s.panY = clampFloat(p.getInt("AutoColor_Y", 0) / 100f, -8f, 8f);
        float z = clampFloat(p.getInt("Scale", 100) / 100f, 0.05f, 32f);
        s.zoomX = z;
        s.zoomY = p.getBoolean("LockAspect", true) ? z : clampFloat(p.getInt("ScaleY", Math.round(z * 100)) / 100f, 0.05f, 32f);
        int angle = ((p.getInt("PlayAngle", 0) / 90) % 4 + 4) % 4;
        int f = (angle << FLAG_ROT_SHIFT);
        if (p.getBoolean("PlayMirror", false)) f |= FLAG_MIRROR_H;
        if (p.getBoolean("PlayMirrorV", false)) f |= FLAG_MIRROR_V;
        if (p.getBoolean("PlayAutoRotate", false)) f |= FLAG_AUTO_ROTATE;
        if (p.getBoolean("LockAspect", true)) f |= FLAG_LOCK_ASPECT;
        s.flags = f;
        return s;
    }

    public void save(SharedPreferences p) {
        p.edit()
                .putInt("TransformMode", mode)
                .putInt("AutoColor_X", Math.round(panX * 100))
                .putInt("AutoColor_Y", Math.round(panY * 100))
                .putInt("Scale", Math.round(zoomX * 100))
                .putInt("ScaleY", Math.round(zoomY * 100))
                .putInt("PlayAngle", rotationQuadrant() * 90)
                .putBoolean("PlayMirror", mirrorH())
                .putBoolean("PlayMirrorV", mirrorV())
                .putBoolean("PlayAutoRotate", autoRotate())
                .putBoolean("LockAspect", lockAspect())
                .apply();
    }

    public int rotationQuadrant() { return (flags & FLAG_ROT_MASK) >>> FLAG_ROT_SHIFT; }
    public boolean mirrorH() { return (flags & FLAG_MIRROR_H) != 0; }
    public boolean mirrorV() { return (flags & FLAG_MIRROR_V) != 0; }
    public boolean autoRotate() { return (flags & FLAG_AUTO_ROTATE) != 0; }
    public boolean lockAspect() { return (flags & FLAG_LOCK_ASPECT) != 0; }

    public void zoom(float factor) {
        zoomX = clampFloat(zoomX * factor, 0.05f, 32f);
        if (lockAspect()) zoomY = zoomX;
        else zoomY = clampFloat(zoomY * factor, 0.05f, 32f);
    }
    public void move(float dx, float dy) { panX = clampFloat(panX + dx, -8f, 8f); panY = clampFloat(panY + dy, -8f, 8f); }
    public void center() { panX = 0f; panY = 0f; }
    public void reset() { mode = MODE_FIT; panX = 0f; panY = 0f; zoomX = 1f; zoomY = 1f; flags = FLAG_LOCK_ASPECT; }
    public void rotate90() { int q = (rotationQuadrant() + 1) & 3; flags = (flags & ~FLAG_ROT_MASK) | (q << FLAG_ROT_SHIFT); }
    public void toggleMirrorH() { flags ^= FLAG_MIRROR_H; }
    public void toggleMirrorV() { flags ^= FLAG_MIRROR_V; }
    public void toggleFitFill() { mode = (mode == MODE_FIT) ? MODE_FILL : MODE_FIT; }
    public void toggleAutoRotate() { flags ^= FLAG_AUTO_ROTATE; }
    public void toggleLockAspect() { flags ^= FLAG_LOCK_ASPECT; if (lockAspect()) zoomY = zoomX; }

    public String modeName() {
        switch (mode) {
            case MODE_FIT: return "FIT";
            case MODE_FILL: return "FILL";
            case MODE_STRETCH: return "STRETCH";
            case MODE_NATIVE: return "NATIVE";
            default: return "FREE";
        }
    }
    public String summary() {
        return String.format(Locale.US, "%s pan=(%.2f,%.2f) zoom=(%.2f,%.2f) rot=%d mirrorH=%s flags=0x%08X",
                modeName(), panX, panY, zoomX, zoomY, rotationQuadrant() * 90, mirrorH(), flags);
    }
    private static float clampFloat(float v, float mn, float mx) { if (Float.isNaN(v) || Float.isInfinite(v)) return mn; return Math.max(mn, Math.min(mx, v)); }
    private static int clampInt(int v, int mn, int mx) { return Math.max(mn, Math.min(mx, v)); }
}
