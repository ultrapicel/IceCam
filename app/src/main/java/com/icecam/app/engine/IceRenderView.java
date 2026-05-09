package com.icecam.app.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.media.MediaMetadataRetriever;

public class IceRenderView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private Thread renderThread;
    private volatile boolean running;
    private Bitmap bitmap;
    private Uri mediaUri;
    private boolean mirror;
    private String fitMode = "Fill";
    private float scale = 1f, tx = 0f, ty = 0f, rotation = 0f;
    private float lastX, lastY;
    private int frames;
    private long fpsWindow = System.currentTimeMillis();
    private float fps;

    public IceRenderView(Context c) { super(c); init(); }
    public IceRenderView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { getHolder().addCallback(this); setFocusable(true); }

    public void setMedia(Uri uri) {
        this.mediaUri = uri;
        decodePreviewFrame(uri);
        resetTransform();
    }

    public void setMirror(boolean mirror) { this.mirror = mirror; }
    public void setFitMode(String mode) { this.fitMode = mode == null ? "Fill" : mode; }
    public void resetTransform() { scale = 1f; tx = 0f; ty = 0f; rotation = 0f; }
    public float getFps() { return fps; }
    public String getRendererState() {
        return "uri=" + mediaUri + " fps=" + fps + " scale=" + scale + " tx=" + tx + " ty=" + ty + " rot=" + rotation + " mirror=" + mirror + " fit=" + fitMode;
    }

    private void decodePreviewFrame(Uri uri) {
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(getContext(), uri);
            Bitmap b = r.getFrameAtTime(0);
            r.release();
            if (b != null) { bitmap = b; return; }
        } catch (Throwable ignored) {}
        try {
            bitmap = BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(uri));
        } catch (Throwable ignored) {}
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getPointerCount() == 1) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { lastX = e.getX(); lastY = e.getY(); return true; }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) { tx += e.getX() - lastX; ty += e.getY() - lastY; lastX = e.getX(); lastY = e.getY(); return true; }
        } else if (e.getPointerCount() >= 2 && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX(1) - e.getX(0);
            float dy = e.getY(1) - e.getY(0);
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            if (dist > 80) scale = Math.max(0.25f, Math.min(8f, dist / 280f));
            rotation = (float)Math.toDegrees(Math.atan2(dy, dx));
            return true;
        }
        return true;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { running = true; renderThread = new Thread(this, "IceCamRenderer"); renderThread.start(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { running = false; try { if (renderThread != null) renderThread.join(1000); } catch (InterruptedException ignored) {} }

    @Override public void run() {
        while (running) {
            drawFrame();
            frames++;
            long now = System.currentTimeMillis();
            if (now - fpsWindow >= 1000) { fps = frames * 1000f / (now - fpsWindow); frames = 0; fpsWindow = now; }
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
    }

    private void drawFrame() {
        Canvas c = null;
        try {
            c = getHolder().lockCanvas();
            if (c == null) return;
            c.drawColor(Color.rgb(7, 10, 14));
            if (bitmap == null) {
                paint.setColor(Color.rgb(170, 185, 196));
                paint.setTextSize(38f);
                c.drawText("Select photo/video", 40, c.getHeight() / 2f, paint);
                return;
            }
            RectF src = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            RectF dst = new RectF(0, 0, c.getWidth(), c.getHeight());
            matrix.reset();
            matrix.setRectToRect(src, dst, "Fit".equals(fitMode) ? Matrix.ScaleToFit.CENTER : Matrix.ScaleToFit.FILL);
            matrix.postScale(mirror ? -scale : scale, scale, c.getWidth()/2f, c.getHeight()/2f);
            matrix.postRotate(rotation, c.getWidth()/2f, c.getHeight()/2f);
            matrix.postTranslate(tx, ty);
            c.drawBitmap(bitmap, matrix, paint);
        } catch (Throwable ignored) {
        } finally {
            if (c != null) getHolder().unlockCanvasAndPost(c);
        }
    }
}
