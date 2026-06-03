package dev.icecam.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public final class MediaTransformer {
    private MediaTransformer() {}

    public static boolean isImagePath(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.US);
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp") || p.endsWith(".bmp");
    }

    public static boolean isVideoPath(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.US);
        return p.endsWith(".mp4") || p.endsWith(".mkv") || p.endsWith(".webm") || p.endsWith(".mov") || p.endsWith(".avi") || p.endsWith(".3gp");
    }

    public static String bakeImage(Context ctx, String sourcePath, TransformState s, AppLogger log) {
        if (sourcePath == null || sourcePath.trim().isEmpty()) return sourcePath;
        if (!isImagePath(sourcePath)) {
            if (log != null) log.log("bake", "skip non-image source=" + sourcePath);
            return sourcePath;
        }
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(sourcePath, probe);
            if (probe.outWidth <= 0 || probe.outHeight <= 0) {
                if (log != null) log.log("bake", "decode bounds failed source=" + sourcePath);
                return sourcePath;
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            int maxDim = Math.max(probe.outWidth, probe.outHeight);
            int sample = 1;
            while (maxDim / sample > 1920) sample *= 2;
            opt.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(sourcePath, opt);
            if (src == null) {
                if (log != null) log.log("bake", "decode failed source=" + sourcePath);
                return sourcePath;
            }

            int outW = 640;
            int outH = 480;
            // Keep camera-client compatibility: original native often reports 640x480. For portrait sources,
            // we still bake into 640x480 and use fit/fill/crop inside this canvas.
            Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawColor(Color.BLACK);

            float sw = src.getWidth();
            float sh = src.getHeight();
            float base;
            if (s.mode == TransformState.MODE_FILL) base = Math.max(outW / sw, outH / sh);
            else if (s.mode == TransformState.MODE_STRETCH) base = 1f; // handled below
            else base = Math.min(outW / sw, outH / sh);

            Matrix m = new Matrix();
            float cx = sw / 2f;
            float cy = sh / 2f;
            m.postTranslate(-cx, -cy);
            if (s.mirrorH()) m.postScale(-1f, 1f);
            if (s.mirrorV()) m.postScale(1f, -1f);
            m.postRotate(s.rotationQuadrant() * 90f);
            if (s.mode == TransformState.MODE_STRETCH) {
                m.postScale(outW / sw * s.zoomX, outH / sh * s.zoomY);
            } else {
                m.postScale(base * s.zoomX, base * s.zoomY);
            }
            // panX/panY are normalized: 1.0 means one half output extent.
            m.postTranslate(outW / 2f + s.panX * (outW / 2f), outH / 2f - s.panY * (outH / 2f));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            c.drawBitmap(src, m, paint);

            File dir = new File(ctx.getExternalFilesDir(null), "baked");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, String.format(Locale.US, "icecam_baked_%d.jpg", System.currentTimeMillis()));
            FileOutputStream fos = new FileOutputStream(dst);
            out.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
            fos.close();
            src.recycle();
            out.recycle();
            if (log != null) log.log("bake", "image baked " + s.summary() + " -> " + dst.getAbsolutePath());
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            if (log != null) log.log("bake", "failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return sourcePath;
        }
    }
}
