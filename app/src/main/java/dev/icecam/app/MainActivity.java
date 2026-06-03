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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 7101;
    private static final int BG = 0xff0f141f;
    private static final int CARD = 0xff182130;
    private static final int PRIMARY = 0xff7f94ff;
    private static final int GREEN = 0xff2bd889;
    private static final int RED = 0xffff5e73;
    private static final int TEXT = 0xffedf3ff;
    private static final int MUTED = 0xffaab5c8;

    private AppLogger logger;
    private RootBootstrap root;
    private VliveBinderClient binder;
    private SharedPreferences prefs;
    private TransformState tx;

    private LinearLayout body;
    private TextView status, mediaLabel, transformLabel;
    private Button startBtn, restoreBtn;

    private final AtomicBoolean playBusy = new AtomicBoolean(false);
    private final AtomicBoolean transformBusy = new AtomicBoolean(false);
    private volatile boolean transformPending = false;
    private volatile String pendingReason = "pending";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        logger = new AppLogger(this);
        root = new RootBootstrap(this, logger);
        binder = new VliveBinderClient(logger);
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        prefs.edit()
                .putString("ServerName", RootBootstrap.FIXED_SERVICE_NAME)
                .putBoolean("EnableTx24Color", false)
                .apply();
        tx = TransformState.load(prefs);
        binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
        requestBasicPermissions();
        buildUi();
        logger.log("app", "IceCam v16 stabilized Core started");
        runBg(() -> { root.bootstrap(); binder.clearCache(); binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME); refreshAll(); });
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
        body.setPadding(dp(16), dp(16), dp(16), dp(22));
        body.setBackgroundColor(BG);
        scroll.addView(body);
        setContentView(scroll);
        render();
    }

    private void render() {
        body.removeAllViews();
        body.addView(title("IceCam"));
        body.addView(text("Camera stream replacement controller", 13, false, MUTED));

        LinearLayout stateCard = card();
        stateCard.addView(section("Status"));
        status = text("", 13, false, TEXT);
        status.setTextIsSelectable(true);
        stateCard.addView(status);
        LinearLayout sr = row();
        startBtn = primaryBtn("Start replacement", v -> startReplacement(), GREEN);
        restoreBtn = primaryBtn("Stop / restore camera", v -> restoreCamera(), RED);
        sr.addView(startBtn, weight());
        sr.addView(restoreBtn, weight());
        stateCard.addView(sr);
        body.addView(stateCard);

        LinearLayout media = card();
        media.addView(section("Media"));
        mediaLabel = text("", 12, false, TEXT);
        mediaLabel.setTextIsSelectable(true);
        media.addView(mediaLabel);
        LinearLayout mr = row();
        mr.addView(primaryBtn("Select photo/video", v -> pickMedia(), PRIMARY), weight());
        mr.addView(primaryBtn("Replay selected", v -> startReplacement(), PRIMARY), weight());
        media.addView(mr);
        body.addView(media);

        LinearLayout controls = card();
        controls.addView(section("Image controls"));
        transformLabel = text("", 12, false, TEXT);
        transformLabel.setTextIsSelectable(true);
        controls.addView(transformLabel);
        controls.addView(text("For photos IceCam creates a high-quality transformed frame and replays it. Rapid taps are automatically queued to protect the backend service. Video realtime transforms require the next renderer stage.", 11, false, MUTED));

        LinearLayout c1 = row();
        c1.addView(primaryBtn("Zoom +", v -> { tx.zoom(1.15f); applyTransform("zoom+"); }, PRIMARY), weight());
        c1.addView(primaryBtn("Up", v -> { tx.move(0f, 0.06f); applyTransform("up"); }, PRIMARY), weight());
        c1.addView(primaryBtn("Zoom -", v -> { tx.zoom(1f / 1.15f); applyTransform("zoom-"); }, PRIMARY), weight());
        controls.addView(c1);

        LinearLayout c2 = row();
        c2.addView(primaryBtn("Left", v -> { tx.move(-0.06f, 0f); applyTransform("left"); }, PRIMARY), weight());
        c2.addView(primaryBtn("Center", v -> { tx.center(); applyTransform("center"); }, PRIMARY), weight());
        c2.addView(primaryBtn("Right", v -> { tx.move(0.06f, 0f); applyTransform("right"); }, PRIMARY), weight());
        controls.addView(c2);

        LinearLayout c3 = row();
        c3.addView(primaryBtn("Fit / Fill", v -> { tx.toggleFitFill(); applyTransform("fit-fill"); }, PRIMARY), weight());
        c3.addView(primaryBtn("Down", v -> { tx.move(0f, -0.06f); applyTransform("down"); }, PRIMARY), weight());
        c3.addView(primaryBtn("Crop", v -> { tx.cycleCrop(); applyTransform("crop"); }, PRIMARY), weight());
        controls.addView(c3);

        LinearLayout c4 = row();
        c4.addView(primaryBtn("Rotate", v -> { tx.rotate90(); applyTransform("rotate"); }, PRIMARY), weight());
        c4.addView(primaryBtn("Mirror", v -> { tx.toggleMirrorH(); applyTransform("mirror"); }, PRIMARY), weight());
        c4.addView(primaryBtn("Reset", v -> { tx.reset(); applyTransform("reset"); }, PRIMARY), weight());
        controls.addView(c4);
        body.addView(controls);

        LinearLayout floatCard = card();
        floatCard.addView(section("Floating switch"));
        floatCard.addView(text("Floating overlay now contains only Start / Stop / Restore buttons to avoid accidental stream resets.", 12, false, MUTED));
        LinearLayout fr = row();
        fr.addView(primaryBtn("Open floating switch", v -> startFloatPanel(), PRIMARY), weight());
        fr.addView(primaryBtn("Overlay permission", v -> openOverlaySettings(), PRIMARY), weight());
        floatCard.addView(fr);
        body.addView(floatCard);

        LinearLayout tools = card();
        tools.addView(section("Tools"));
        LinearLayout tr = row();
        tr.addView(primaryBtn("Refresh status", v -> runBg(() -> { root.status(); refreshAll(); }), PRIMARY), weight());
        tr.addView(primaryBtn("Export log", v -> shareLog(), PRIMARY), weight());
        tools.addView(tr);
        body.addView(tools);
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
            tx.reset();
            tx.save(prefs);
            prefs.edit()
                    .putString("OriginalPlayFileMp4", path)
                    .putString("PlayFileMp4", path)
                    .putInt("PlayFileType", 1)
                    .putString("IceCamState", "MEDIA_SELECTED")
                    .apply();
            logger.log("media", "selected=" + path);
            refreshAll();
        }
    }

    private void startReplacement() {
        String p = prefs.getString("PlayFileMp4", "");
        if (p == null || p.length() == 0) { toast("Select media first"); return; }
        if (!playBusy.compareAndSet(false, true)) { logger.log("ui", "start ignored: backend busy"); return; }
        runBg(() -> {
            try {
                prefs.edit().putString("IceCamState", "STARTING").apply();
                binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                if (!binder.connected()) {
                    root.bootstrap();
                    binder.clearCache();
                    binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                    sleepMs(250);
                }
                legacyApplyMedia(p, "start");
            } finally {
                playBusy.set(false);
                refreshAll();
            }
        });
    }

    private void legacyApplyMedia(String p, String source) {
        logger.log("ui", "media apply start source=" + source + " path=" + p);
        int mode = binder.setModeString(1, p);
        sleepMs(240);
        int play = binder.playSource(p, tx.mirrorH(), prefs.getBoolean("PlayisLoop", true));
        boolean active = mode >= 0 && play >= 0;
        prefs.edit().putBoolean("ReplacementActive", active).putString("IceCamState", active ? "REPLACEMENT_ACTIVE" : "PLAY_ERROR").apply();
        logger.log("ui", "media apply done TX14=" + mode + " TX11=" + play + " active=" + active + " path=" + p);
    }

    private void applyTransform(String reason) {
        tx.save(prefs);
        refreshAll();
        String original = prefs.getString("OriginalPlayFileMp4", prefs.getString("PlayFileMp4", ""));
        if (original == null || original.length() == 0) return;
        if (!MediaTransformer.isImagePath(original)) {
            logger.log("transform", reason + " saved for video; realtime video transform not enabled yet. " + tx.summary());
            return;
        }
        scheduleImageBake(reason);
    }

    private void scheduleImageBake(String reason) {
        pendingReason = reason;
        transformPending = true;
        if (!transformBusy.compareAndSet(false, true)) {
            logger.log("transform", "queued latest transform: " + reason);
            return;
        }
        runBg(() -> {
            try {
                while (transformPending) {
                    transformPending = false;
                    String r = pendingReason;
                    sleepMs(260); // debounce rapid taps
                    TransformState snapshot = TransformState.load(prefs);
                    String original = prefs.getString("OriginalPlayFileMp4", prefs.getString("PlayFileMp4", ""));
                    if (original == null || original.length() == 0 || !MediaTransformer.isImagePath(original)) continue;
                    prefs.edit().putString("IceCamState", "RENDERING_FRAME").apply();
                    String baked = MediaTransformer.bakeImage(this, original, snapshot, logger);
                    prefs.edit().putString("PlayFileMp4", baked).putString("BakedPlayFileMp4", baked).apply();
                    logger.log("transform", r + " baked/replay " + snapshot.summary());
                    legacyApplyMedia(baked, "transform-" + r);
                    sleepMs(220);
                }
            } finally {
                transformBusy.set(false);
                refreshAll();
                if (transformPending) scheduleImageBake("coalesced");
            }
        });
    }

    private void restoreCamera() {
        if (!playBusy.compareAndSet(false, true)) { logger.log("ui", "restore ignored: backend busy"); return; }
        runBg(() -> {
            try {
                prefs.edit().putString("IceCamState", "RESTORING_CAMERA").apply();
                logger.log("ui", "restore camera requested");
                root.restoreCamera();
                binder.clearCache();
                sleepMs(250);
                boolean stillConnected = binder.connected();
                prefs.edit().putBoolean("ReplacementActive", false).putString("IceCamState", stillConnected ? "RESTORE_CHECK_SERVICE_STILL_VISIBLE" : "CAMERA_RESTORED").apply();
                logger.log("ui", "restore camera done serviceStillVisible=" + stillConnected + " " + binder.lastError());
            } finally {
                playBusy.set(false);
                refreshAll();
            }
        });
    }

    private void startFloatPanel() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            toast("Allow Display over other apps, then open floating switch again");
            return;
        }
        try { startService(new Intent(this, FloatService.class)); logger.log("ui", "floating switch requested"); }
        catch (Throwable t) { logger.log("ui", "start float failed: " + t); }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void refreshAll() {
        runOnUiThread(() -> {
            tx = TransformState.load(prefs);
            boolean active = prefs.getBoolean("ReplacementActive", false);
            String phase = prefs.getString("IceCamState", "IDLE");
            boolean connected = binder.connected();
            if (status != null) {
                status.setText("State: " + phase + "\nReplacement: " + (active ? "ON" : "OFF") + "\nBackend: " + (connected ? "ready" : "not connected"));
                status.setTextColor(active ? GREEN : (connected ? 0xffffcc66 : TEXT));
            }
            if (mediaLabel != null) {
                String p = prefs.getString("PlayFileMp4", "");
                mediaLabel.setText(p == null || p.length() == 0 ? "No media selected" : shortPath(p));
            }
            if (transformLabel != null) transformLabel.setText(tx.summary() + (transformBusy.get() ? "\nRendering queued frame…" : ""));
        });
    }

    private String shortPath(String p) { return p.length() > 96 ? "…" + p.substring(p.length() - 96) : p; }
    private void runBg(Runnable r) { new Thread(() -> { try { r.run(); } catch (Throwable t) { logger.log("thread", String.valueOf(t)); } }, "icecam-bg").start(); }
    private static void sleepMs(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private void shareLog() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, logger.text());
            startActivity(Intent.createChooser(i, "Export IceCam log"));
        } catch (Throwable t) { toast("Export failed: " + t.getMessage()); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); logger.log("toast", s); }
    private TextView title(String s) { TextView t = text(s, 26, true, TEXT); t.setPadding(0, 0, 0, dp(4)); return t; }
    private TextView section(String s) { TextView t = text(s, 14, true, TEXT); t.setPadding(0, 0, 0, dp(8)); return t; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private LinearLayout card() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(15), dp(13), dp(15), dp(13)); l.setBackground(round(CARD, dp(24), 0x334d5d73)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(13)); l.setLayoutParams(p); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(4), dp(4), dp(4), dp(4)); return p; }
    private Button primaryBtn(String s, View.OnClickListener l, int color) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(11); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round(color, dp(16), 0x55ffffff)); b.setOnClickListener(l); b.setMinHeight(0); b.setMinimumHeight(0); return b; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(1, stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}
