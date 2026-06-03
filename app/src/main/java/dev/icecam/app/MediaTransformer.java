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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public final class MediaTransformer {
    private MediaTransformer() {}
    private static final int MAX_OUTPUT_DIM = 2560;
    private static final int MAX_BAKED_FILES = 24;
    private static final long MAX_BAKED_AGE_MS = 6L * 60L * 60L * 1000L;

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

            int srcW0 = probe.outWidth;
            int srcH0 = probe.outHeight;
            int sample = 1;
            while (Math.max(srcW0 / sample, srcH0 / sample) > MAX_OUTPUT_DIM) sample *= 2;

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opt.inDither = true;
            opt.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(sourcePath, opt);
            if (src == null) {
                if (log != null) log.log("bake", "decode failed source=" + sourcePath);
                return sourcePath;
            }

            int srcW = src.getWidth();
            int srcH = src.getHeight();
            boolean swap = (s.rotationQuadrant() & 1) != 0;
            int outW = swap ? srcH : srcW;
            int outH = swap ? srcW : srcH;
            outW = Math.max(16, outW);
            outH = Math.max(16, outH);

            Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawColor(Color.BLACK);

            float sw = src.getWidth();
            float sh = src.getHeight();
            float base;
            if (s.mode == TransformState.MODE_FILL) base = Math.max(outW / sw, outH / sh);
            else if (s.mode == TransformState.MODE_STRETCH) base = 1f;
            else base = Math.min(outW / sw, outH / sh);

            Matrix m = new Matrix();
            m.postTranslate(-sw / 2f, -sh / 2f);
            if (s.mirrorH()) m.postScale(-1f, 1f);
            if (s.mirrorV()) m.postScale(1f, -1f);
            m.postRotate(s.rotationQuadrant() * 90f);
            if (s.mode == TransformState.MODE_STRETCH) {
                m.postScale((outW / sw) * s.zoomX, (outH / sh) * s.zoomY);
            } else {
                m.postScale(base * s.zoomX, base * s.zoomY);
            }
            m.postTranslate(outW / 2f + s.panX * (outW / 2f), outH / 2f - s.panY * (outH / 2f));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            c.drawBitmap(src, m, paint);

            File dir = new File(ctx.getExternalFilesDir(null), "baked");
            if (!dir.exists()) dir.mkdirs();
            pruneBakedCache(dir, log);
            File dst = new File(dir, String.format(Locale.US, "icecam_%dx%d_%d.jpg", outW, outH, System.currentTimeMillis()));
            FileOutputStream fos = new FileOutputStream(dst);
            out.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
            src.recycle();
            out.recycle();
            pruneBakedCache(dir, log);
            if (log != null) log.log("bake", "image baked high-quality " + outW + "x" + outH + " " + s.summary() + " -> " + dst.getAbsolutePath());
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            if (log != null) log.log("bake", "failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return sourcePath;
        }
    }
    private static void pruneBakedCache(File dir, AppLogger log) {
        try {
            File[] files = dir.listFiles((d, name) -> name != null && name.startsWith("icecam_") && name.toLowerCase(Locale.US).endsWith(".jpg"));
            if (files == null || files.length == 0) return;

            long now = System.currentTimeMillis();
            int deleted = 0;
            for (File f : files) {
                if (now - f.lastModified() > MAX_BAKED_AGE_MS && f.delete()) deleted++;
            }

            files = dir.listFiles((d, name) -> name != null && name.startsWith("icecam_") && name.toLowerCase(Locale.US).endsWith(".jpg"));
            if (files == null || files.length <= MAX_BAKED_FILES) {
                if (deleted > 0 && log != null) log.log("bake", "cache cleanup deleted=" + deleted);
                return;
            }

            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int keepFrom = Math.max(0, files.length - MAX_BAKED_FILES);
            for (int i = 0; i < keepFrom; i++) {
                if (files[i].delete()) deleted++;
            }
            if (deleted > 0 && log != null) log.log("bake", "cache cleanup deleted=" + deleted + " remaining<= " + MAX_BAKED_FILES);
        } catch (Throwable t) {
            if (log != null) log.log("bake", "cache cleanup failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

}
