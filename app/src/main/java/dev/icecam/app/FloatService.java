package dev.icecam.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatService extends Service {
    private WindowManager wm;
    private View panel;
    private WindowManager.LayoutParams lp;
    private SharedPreferences prefs;
    private AppLogger log;
    private VliveBinderClient binder;
    private RootBootstrap root;
    private TextView state;
    private TransformState tx;
    private boolean loop;
    private int lastX, lastY;
    private float touchX, touchY;

    @Override public IBinder onBind(Intent i){ return null; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        log = new AppLogger(this);
        root = new RootBootstrap(this, log);
        binder = new VliveBinderClient(log);
        binder.setPreferredService(root.serverName());
        tx = TransformState.load(prefs);
        loop = prefs.getBoolean("PlayisLoop", true);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && "stop".equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        show();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { if (wm != null && panel != null) wm.removeView(panel); } catch (Throwable ignored) {}
        panel = null;
        super.onDestroy();
    }

    private void show() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("Overlay permission is not granted");
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            return;
        }
        if (panel != null) return;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(dp(318), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("FloatX", dp(22));
        lp.y = prefs.getInt("FloatY", dp(120));
        panel = buildPanel();
        wm.addView(panel, lp);
        refresh();
        log.log("float", "v8 floating transform panel started");
    }

    private View buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(bg(0xe61e2733, dp(24), 0x55ffffff));

        TextView title = tv("IceCam Transform", 16, true);
        title.setPadding(0, 0, 0, dp(4));
        title.setOnTouchListener((v, e) -> drag(e));
        box.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));

        state = tv("", 11, false);
        state.setTextColor(0xffdbe7f4);
        box.addView(state);

        // 4 x 4 grid based on original control layout, but remapped:
        // Eye=>Zoom+, Mouth=>Center, Face=>Zoom-, arrows=>pan image.
        LinearLayout r1 = row();
        r1.addView(btn("Zoom +", v -> { tx.zoom(1.25f); apply("zoom+"); }), weight());
        r1.addView(btn("↑", v -> { tx.move(0f, 0.10f); apply("move-up"); }), weight());
        r1.addView(btn("Zoom -", v -> { tx.zoom(1f / 1.25f); apply("zoom-"); }), weight());
        r1.addView(btn("Fit/Fill", v -> { tx.toggleFitFill(); apply("fit-fill"); }), weight());
        box.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("←", v -> { tx.move(-0.10f, 0f); apply("move-left"); }), weight());
        r2.addView(btn("Center", v -> { tx.center(); apply("center"); }), weight());
        r2.addView(btn("→", v -> { tx.move(0.10f, 0f); apply("move-right"); }), weight());
        r2.addView(btn("Reset", v -> { tx.reset(); apply("reset"); }), weight());
        box.addView(r2);

        LinearLayout r3 = row();
        r3.addView(btn("Play", v -> playFile()), weight());
        r3.addView(btn("↓", v -> { tx.move(0f, -0.10f); apply("move-down"); }), weight());
        r3.addView(btn(loop ? "Loop On" : "Loop Off", v -> { loop = !loop; prefs.edit().putBoolean("PlayisLoop", loop).apply(); apply("loop"); redraw(); }), weight());
        r3.addView(btn("Status", v -> { root.status(); refresh(); }), weight());
        box.addView(r3);

        LinearLayout r4 = row();
        r4.addView(btn("Rotate", v -> { tx.rotate90(); apply("rotate90"); }), weight());
        r4.addView(btn("Mirror", v -> { tx.toggleMirrorH(); apply("mirrorH"); }), weight());
        r4.addView(btn("Stop", v -> stopNative()), weight());
        r4.addView(btn("Close", v -> stopSelf()), weight());
        box.addView(r4);
        return box;
    }

    private void redraw() {
        try { if (wm != null && panel != null) wm.removeView(panel); } catch (Throwable ignored) {}
        panel = null;
        show();
    }

    private boolean drag(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = lp.x; lastY = lp.y; touchX = e.getRawX(); touchY = e.getRawY(); return true;
            case MotionEvent.ACTION_MOVE:
                lp.x = lastX + (int)(e.getRawX() - touchX);
                lp.y = lastY + (int)(e.getRawY() - touchY);
                wm.updateViewLayout(panel, lp);
                return true;
            case MotionEvent.ACTION_UP:
                prefs.edit().putInt("FloatX", lp.x).putInt("FloatY", lp.y).apply(); return true;
        }
        return true;
    }

    private void playFile() {
        tx.save(prefs);
        String p = prefs.getString("PlayFileMp4", "");
        if (p == null || p.trim().isEmpty()) { toast("Select media in main app first"); return; }
        int mode = binder.setModeString(1, p);
        int play = binder.playSource(p, tx.mirrorH(), loop);
        log.log("float", "play file TX14=" + mode + " TX11=" + play + " path=" + p);
        apply("after-play");
        refresh();
    }

    private void stopNative() {
        int r = binder.simple(VliveBinderClient.TX_25);
        log.log("float", "stop TX25=" + r);
        refresh();
    }

    private void apply(String reason) {
        tx.save(prefs);
        prefs.edit().putBoolean("PlayisLoop", loop).apply();
        int r = binder.setTransform(tx);
        log.log("float", reason + " TX24=" + r + " " + tx.summary());
        refresh();
    }

    private void refresh() {
        if (state == null) return;
        state.setText(tx.summary() + "\nservice: " + binder.preferredService() + "\n" + binder.lastError());
    }

    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(11); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0,0,0,0);
        b.setBackground(bg(0xaa728093, dp(16), 0x66ffffff)); b.setOnClickListener(l);
        return b;
    }
    private TextView tv(String s, int sp, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); return lp; }
    private GradientDrawable bg(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(1, stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); log.log("float", s); }
}
