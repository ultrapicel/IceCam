package dev.icecam.app;

import android.app.Service;
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
    private boolean collapsed;
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
        collapsed = prefs.getBoolean("FloatCollapsed", false);
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
        lp = new WindowManager.LayoutParams(collapsed ? dp(86) : dp(334), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("FloatX", dp(22));
        lp.y = prefs.getInt("FloatY", dp(120));
        panel = collapsed ? buildBubble() : buildPanel();
        wm.addView(panel, lp);
        refresh();
        log.log("float", "v12 floating controls started collapsed=" + collapsed);
    }

    private View buildBubble() {
        TextView bubble = tv("IceCam\nCTL", 12, true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(8), dp(8), dp(8), dp(8));
        bubble.setBackground(bg(0xea1a2333, dp(28), 0x88ffffff));
        bubble.setOnClickListener(v -> { collapsed = false; prefs.edit().putBoolean("FloatCollapsed", false).apply(); redraw(); });
        bubble.setOnTouchListener((v, e) -> drag(e));
        return bubble;
    }

    private View buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        box.setBackground(bg(0xee161d29, dp(26), 0x66ffffff));

        LinearLayout head = row();
        TextView title = tv("IceCam Controls", 15, true);
        title.setOnTouchListener((v, e) -> drag(e));
        head.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        head.addView(btn("—", v -> { collapsed = true; prefs.edit().putBoolean("FloatCollapsed", true).apply(); redraw(); }), new LinearLayout.LayoutParams(dp(42), dp(34)));
        box.addView(head);

        state = tv("", 10, false);
        state.setTextColor(0xffdbe7f4);
        state.setPadding(0, 0, 0, dp(5));
        box.addView(state);

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
        r2.addView(btn("Crop", v -> { tx.cycleCrop(); apply("crop"); }), weight());
        box.addView(r2);

        LinearLayout r3 = row();
        r3.addView(btn("Play", v -> playFile()), weight());
        r3.addView(btn("↓", v -> { tx.move(0f, -0.10f); apply("move-down"); }), weight());
        r3.addView(btn(loop ? "Loop On" : "Loop Off", v -> { loop = !loop; prefs.edit().putBoolean("PlayisLoop", loop).apply(); log.log("float", "loop=" + loop); redraw(); }), weight());
        r3.addView(btn("Status", v -> { root.status(); refresh(); }), weight());
        box.addView(r3);

        LinearLayout r4 = row();
        r4.addView(btn("Rotate", v -> { tx.rotate90(); apply("rotate90"); }), weight());
        r4.addView(btn("Mirror", v -> { tx.toggleMirrorH(); apply("mirrorH"); }), weight());
        r4.addView(btn("SoftStop", v -> stopNative()), weight());
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
        return false;
    }

    private void playFile() {
        tx.save(prefs);
        String p = prefs.getString("PlayFileMp4", "");
        if (p == null || p.trim().isEmpty()) { toast("Select media in main app first"); return; }
        new Thread(() -> {
            prefs.edit().putString("ServerName", RootBootstrap.FIXED_SERVICE_NAME).apply();
            binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
            log.log("float", "legacy play start path=" + p + " loop=" + loop + " service=" + binder.preferredService());
            // v12: last known working order. No TX25/TX22. TX24 only when explicitly enabled.
            int mode = binder.setModeString(1, p);
            sleepMs(220);
            int play = binder.playSource(p, tx.mirrorH(), loop);
            sleepMs(260);
            int tr = -1000;
            if (prefs.getBoolean("EnableTx24Color", false)) tr = binder.setTransform(tx);
            else log.log("tx24", "float auto TX24 skipped; EnableTx24Color=false " + tx.summary());
            log.log("float", "legacy play done TX14=" + mode + " TX11=" + play + " TX24=" + tr + " path=" + p);
            refresh();
        }, "icecam-float-play").start();
    }

    private void stopNative() {
        // TX25 is destructive on this native build and can cause black camera clients.
        int r = binder.simple(VliveBinderClient.TX_GET_INT);
        log.log("float", "soft stop: TX25 disabled, TX15/status=" + r);
        refresh();
    }

    private void apply(String reason) {
        tx.save(prefs);
        prefs.edit().putBoolean("PlayisLoop", loop).apply();
        String current = prefs.getString("OriginalPlayFileMp4", prefs.getString("PlayFileMp4", ""));
        if (current == null || current.length() == 0) {
            log.log("float", reason + " saved only; no selected media " + tx.summary());
            refresh();
            return;
        }
        if (!MediaTransformer.isImagePath(current)) {
            log.log("float", reason + " saved only for video/non-image; TX24 is color debug, not pan/zoom. " + tx.summary());
            refresh();
            return;
        }
        new Thread(() -> {
            String baked = MediaTransformer.bakeImage(this, current, tx, log);
            prefs.edit().putString("PlayFileMp4", baked).putString("BakedPlayFileMp4", baked).apply();
            binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
            log.log("float", reason + " baked/replay path=" + baked + " " + tx.summary());
            int mode = binder.setModeString(1, baked);
            sleepMs(220);
            int play = binder.playSource(baked, tx.mirrorH(), loop);
            int tr = -1000;
            if (prefs.getBoolean("EnableTx24Color", false)) tr = binder.setTransform(tx);
            else log.log("tx24", "float TX24 color debug skipped; EnableTx24Color=false");
            log.log("float", reason + " replay done TX14=" + mode + " TX11=" + play + " TX24=" + tr);
            refresh();
        }, "icecam-float-bake").start();
    }

    private void refresh() {
        if (state == null) return;
        String media = prefs.getString("PlayFileMp4", "");
        if (media == null || media.length() == 0) media = "<not selected>";
        state.setText(tx.summary() + "\nmedia=" + shortPath(media) + "\nservice=" + binder.preferredService() + "\n" + binder.lastError());
    }

    private String shortPath(String p) { return p.length() > 46 ? "…" + p.substring(p.length() - 46) : p; }
    private static void sleepMs(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(bg(0xaa6d7c92, dp(16), 0x66ffffff));
        b.setOnClickListener(l);
        return b;
    }

    private TextView tv(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); return lp; }
    private GradientDrawable bg(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(1, stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); log.log("float", s); }
}
