package dev.icecam.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 7101;
    private static final int BG = 0xff0f141f;
    private static final int CARD = 0xff182130;
    private static final int CARD_2 = 0xff202b3c;
    private static final int PRIMARY = 0xff7f94ff;
    private static final int TEXT = 0xffedf3ff;
    private static final int MUTED = 0xffaab5c8;

    private AppLogger logger;
    private RootBootstrap root;
    private VliveBinderClient binder;
    private SharedPreferences prefs;
    private TransformState tx;

    private LinearLayout body;
    private TextView status, logView, mediaLabel, transformLabel;
    private EditText serviceName;
    private CheckBox loop, mirror, autoRotate;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        logger = new AppLogger(this);
        root = new RootBootstrap(this, logger);
        binder = new VliveBinderClient(logger);
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        tx = TransformState.load(prefs);
        binder.setPreferredService(root.serverName());
        requestBasicPermissions();
        buildUi();
        logger.setListener(text -> runOnUiThread(() -> { if (logView != null) logView.setText(trimLog(text)); }));
        logger.log("app", "IceCam v10 stable-service reconstruction started");
        logger.log("app", "ServerName=" + root.serverName());
        runBg(() -> { root.bootstrap(); binder.setPreferredService(root.serverName()); refreshAll(); });
    }

    private void requestBasicPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> ps = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (!ps.isEmpty()) requestPermissions(ps.toArray(new String[0]), 7);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(20));
        body.setBackgroundColor(BG);
        scroll.addView(body);
        setContentView(scroll);
        render();
    }

    private void render() {
        body.removeAllViews();
        body.addView(title("IceCam"));
        body.addView(text("v10 stable control layer. Fixed service name: privsam_service. Native binaries are kept untouched; this build focuses on reliable media switching, floating controls, and runtime diagnostics.", 13, false, MUTED));

        LinearLayout stateCard = card();
        stateCard.addView(section("Status"));
        status = text("", 12, false, TEXT);
        status.setTextIsSelectable(true);
        stateCard.addView(status);
        LinearLayout sr = row();
        sr.addView(primaryBtn("Root bootstrap", v -> runBg(() -> { saveServiceName(); root.bootstrap(); refreshAll(); })), weight());
        sr.addView(primaryBtn("Full status", v -> runBg(() -> { root.status(); logger.logBlock("binder", binder.diagnostics()); refreshAll(); })), weight());
        stateCard.addView(sr);
        body.addView(stateCard);

        LinearLayout media = card();
        media.addView(section("Local photo/video source"));
        mediaLabel = text("", 12, false, TEXT);
        mediaLabel.setTextIsSelectable(true);
        media.addView(mediaLabel);
        LinearLayout mr = row();
        mr.addView(primaryBtn("Select media", v -> pickMedia()), weight());
        mr.addView(primaryBtn("Play selected", v -> playSelected()), weight());
        mr.addView(primaryBtn("Stop", v -> stopNative()), weight());
        media.addView(mr);
        loop = check("Loop playback", prefs.getBoolean("PlayisLoop", true), (b, v) -> { prefs.edit().putBoolean("PlayisLoop", v).apply(); logger.log("ui", "loop=" + v); });
        media.addView(loop);
        body.addView(media);

        LinearLayout controls = card();
        controls.addView(section("TX24 transform controls"));
        transformLabel = text("", 12, false, TEXT);
        transformLabel.setTextIsSelectable(true);
        controls.addView(transformLabel);
        LinearLayout c1 = row();
        c1.addView(primaryBtn("Zoom +", v -> { tx.zoom(1.25f); applyTransform("zoom+"); }), weight());
        c1.addView(primaryBtn("Up", v -> { tx.move(0f, 0.10f); applyTransform("up"); }), weight());
        c1.addView(primaryBtn("Zoom -", v -> { tx.zoom(1f / 1.25f); applyTransform("zoom-"); }), weight());
        controls.addView(c1);
        LinearLayout c2 = row();
        c2.addView(primaryBtn("Left", v -> { tx.move(-0.10f, 0f); applyTransform("left"); }), weight());
        c2.addView(primaryBtn("Center", v -> { tx.center(); applyTransform("center"); }), weight());
        c2.addView(primaryBtn("Right", v -> { tx.move(0.10f, 0f); applyTransform("right"); }), weight());
        controls.addView(c2);
        LinearLayout c3 = row();
        c3.addView(primaryBtn("Fit/Fill", v -> { tx.toggleFitFill(); applyTransform("fit-fill"); }), weight());
        c3.addView(primaryBtn("Down", v -> { tx.move(0f, -0.10f); applyTransform("down"); }), weight());
        c3.addView(primaryBtn("Crop", v -> { tx.cycleCrop(); applyTransform("crop"); }), weight());
        controls.addView(c3);
        LinearLayout c4 = row();
        c4.addView(primaryBtn("Rotate 90", v -> { tx.rotate90(); applyTransform("rotate"); }), weight());
        c4.addView(primaryBtn("Mirror", v -> { tx.toggleMirrorH(); applyTransform("mirror"); }), weight());
        c4.addView(primaryBtn("Reset", v -> { tx.reset(); applyTransform("reset"); }), weight());
        controls.addView(c4);
        mirror = check("Mirror H", tx.mirrorH(), (b, v) -> { if (tx.mirrorH() != v) { tx.toggleMirrorH(); applyTransform("mirror-checkbox"); } });
        autoRotate = check("Auto rotate flag", tx.autoRotate(), (b, v) -> { if (tx.autoRotate() != v) { tx.toggleAutoRotate(); applyTransform("auto-rotate"); } });
        controls.addView(mirror);
        controls.addView(autoRotate);
        body.addView(controls);

        LinearLayout floating = card();
        floating.addView(section("Floating menu"));
        floating.addView(text("Remap based on original layout: Eye=Zoom+, Face=Zoom-, Mouth=Center, arrows=pan image. The native libraries are not patched.", 12, false, MUTED));
        LinearLayout fr = row();
        fr.addView(primaryBtn("Open floating controls", v -> startFloatPanel()), weight());
        fr.addView(primaryBtn("Overlay permission", v -> openOverlaySettings()), weight());
        floating.addView(fr);
        body.addView(floating);

        LinearLayout service = card();
        service.addView(section("Service name / Binder"));
        serviceName = edit(root.serverName(), "Fixed service name passed to /data/vcplax");
        service.addView(serviceName);
        LinearLayout br = row();
        br.addView(primaryBtn("Save + connect", v -> { saveServiceName(); logger.logBlock("binder", binder.diagnostics()); refreshAll(); }), weight());
        br.addView(primaryBtn("Reset to privsam_service", v -> { String s = root.resetServerName(); binder.setPreferredService(s); render(); }), weight());
        service.addView(br);
        body.addView(service);

        LinearLayout logs = card();
        logs.addView(section("Runtime log"));
        LinearLayout lr = row();
        lr.addView(primaryBtn("Refresh status", v -> runBg(() -> { root.status(); refreshAll(); })), weight());
        lr.addView(primaryBtn("Share log", v -> shareLog()), weight());
        logs.addView(lr);
        logView = text(trimLog(logger.text()), 11, false, 0xffedf2ff);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logView.setBackground(round(0xff111826, dp(16), 0x334d5d73));
        logs.addView(logView);
        body.addView(logs);
        refreshAll();
    }

    private void pickMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        startActivityForResult(i, REQ_PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) {}
            String path = MediaResolver.resolveToReadableFile(this, uri, logger);
            prefs.edit().putString("PlayFileMp4", path).putInt("PlayFileType", 1).apply();
            logger.log("media", "selected=" + path);
            refreshAll();
        }
    }

    private void playSelected() {
        String p = prefs.getString("PlayFileMp4", "");
        if (p == null || p.length() == 0) { toast("Select media first"); return; }
        safeApplyMedia(p, "main");
    }

    private void safeApplyMedia(String p, String source) {
        tx.save(prefs);
        runBg(() -> {
            logger.log("ui", "soft media apply start source=" + source + " path=" + p + " service=" + binder.preferredService());
            // v11 soft-switch: TX25 can close/reset the native endpoint on some builds.
            // Do not send it during normal media switching. Keep TX25 only for manual Stop.
            int range = binder.setRange(0L, -1L);
            sleepMs(80);
            int mode = binder.setModeString(1, p);
            sleepMs(120);
            int play = binder.playSource(p, tx.mirrorH(), prefs.getBoolean("PlayisLoop", true));
            sleepMs(180);
            int tr = binder.setTransform(tx);
            logger.log("ui", "soft media apply done TX22=" + range + " TX14=" + mode + " TX11=" + play + " TX24=" + tr + " path=" + p);
            refreshAll();
        });
    }

    private void stopNative() {
        int r = binder.simple(VliveBinderClient.TX_25);
        logger.log("ui", "stop TX25=" + r);
        refreshAll();
    }

    private void applyTransform(String reason) {
        tx.save(prefs);
        int r = binder.setTransform(tx);
        logger.log("tx24", reason + " result=" + r + " " + tx.summary());
        refreshAll();
    }

    private void startFloatPanel() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            toast("Allow Display over other apps, then open controls again");
            return;
        }
        try {
            startService(new Intent(this, FloatService.class));
            logger.log("ui", "floating panel requested");
        } catch (Throwable t) { logger.log("ui", "start float failed: " + t); }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void saveServiceName() {
        String s = RootBootstrap.FIXED_SERVICE_NAME;
        prefs.edit().putString("ServerName", s).apply();
        binder.setPreferredService(s);
        logger.log("binder", "ServerName=" + s + " (fixed)");
        if (serviceName != null) serviceName.setText(s);
    }

    private void refreshAll() {
        runOnUiThread(() -> {
            if (status != null) status.setText("server=" + root.serverName() + "\nconnected=" + binder.connected() + "\n" + binder.lastError());
            if (mediaLabel != null) {
                String p = prefs.getString("PlayFileMp4", "");
                mediaLabel.setText("selected=" + (p == null || p.length() == 0 ? "<none>" : p));
            }
            tx = TransformState.load(prefs);
            if (transformLabel != null) transformLabel.setText(tx.summary());
            if (logView != null) logView.setText(trimLog(logger.text()));
        });
    }

    private void runBg(Runnable r) { new Thread(() -> { try { r.run(); } catch (Throwable t) { logger.log("thread", String.valueOf(t)); } }, "icecam-bg").start(); }
    private static void sleepMs(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private void shareLog() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, logger.text());
            startActivity(Intent.createChooser(i, "Share IceCam log"));
        } catch (Throwable t) { toast("Share failed: " + t.getMessage()); }
    }

    private String trimLog(String s) { return s == null ? "" : (s.length() > 16000 ? s.substring(0, 16000) : s); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); logger.log("toast", s); }

    private TextView title(String s) { TextView t = text(s, 24, true, TEXT); t.setPadding(0, 0, 0, dp(8)); return t; }
    private TextView section(String s) { TextView t = text(s, 13, true, TEXT); t.setPadding(0, 0, 0, dp(8)); return t; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private LinearLayout card() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(14), dp(12), dp(14), dp(12)); l.setBackground(round(CARD, dp(22), 0x334d5d73)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(12)); l.setLayoutParams(p); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1); p.setMargins(dp(3), dp(3), dp(3), dp(3)); return p; }
    private Button primaryBtn(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(11); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round(PRIMARY, dp(16), 0x66ffffff)); b.setOnClickListener(l); b.setMinHeight(0); b.setMinimumHeight(0); return b; }
    private CheckBox check(String s, boolean checked, CompoundButton.OnCheckedChangeListener l) { CheckBox c = new CheckBox(this); c.setText(s); c.setTextColor(TEXT); c.setTextSize(12); c.setChecked(checked); c.setOnCheckedChangeListener(l); return c; }
    private EditText edit(String value, String hint) { EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setSingleLine(true); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setBackground(round(CARD_2, dp(14), 0x334d5d73)); e.setPadding(dp(10), 0, dp(10), 0); return e; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(1, stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}
