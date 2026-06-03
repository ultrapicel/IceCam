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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 7101;
    private static final int BG = 0xff0f141f;
    private static final int CARD = 0xff182130;
    private static final int CARD2 = 0xff202a3a;
    private static final int PRIMARY = 0xff5d78ff;
    private static final int CYAN = 0xff10c8de;
    private static final int GREEN = 0xff2bd889;
    private static final int RED = 0xffff5e73;
    private static final int TEXT = 0xffedf3ff;
    private static final int MUTED = 0xffaab5c8;
    private static final boolean MAIN_AUTO_COMMIT = true;

    private AppLogger logger;
    private RootBootstrap root;
    private VliveBinderClient binder;
    private TransformController controller;
    private SharedPreferences prefs;
    private TransformState tx;

    private LinearLayout body;
    private TextView status, mediaLabel, transformLabel;
    private Button[] slotButtons = new Button[4];
    private int pendingPickSlot = 1;

    private final Object backendLock = new Object();
    private final AtomicBoolean actionBusy = new AtomicBoolean(false);
    private final AtomicBoolean transformWorker = new AtomicBoolean(false);
    private volatile boolean transformPending = false;
    private volatile String pendingReason = "pending";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        logger = new AppLogger(this);
        root = new RootBootstrap(this, logger);
        binder = new VliveBinderClient(logger);
        controller = TransformController.get(this);
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        prefs.edit()
                .putString("ServerName", RootBootstrap.FIXED_SERVICE_NAME)
                .putBoolean("EnableTx24Color", false)
                .putInt("ActiveSlot", Math.max(1, Math.min(4, prefs.getInt("ActiveSlot", 1))))
                .apply();
        tx = TransformState.load(prefs);
        binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
        requestBasicPermissions();
        buildUi();
        logger.log("app", "IceCam Core v22 unified transform controller started mainAutoCommit=" + MAIN_AUTO_COMMIT);
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
        body.addView(text("v22 unified transform controller: MainActivity and floating overlay route through the same TransformController/BackendApplyQueue.", 13, false, MUTED));

        LinearLayout stateCard = card();
        stateCard.addView(section("Status"));
        status = text("", 13, false, TEXT);
        status.setTextIsSelectable(true);
        stateCard.addView(status);
        LinearLayout sr = row();
        sr.addView(primaryBtn("Start", v -> startReplacement(), GREEN), weight());
        sr.addView(primaryBtn("Stop / Restore", v -> restoreCamera(), RED), weight());
        stateCard.addView(sr);
        body.addView(stateCard);

        LinearLayout media = card();
        media.addView(section("Media slots"));
        media.addView(text("Tap a slot to select it. Long-press or use + to replace media. Switching a slot while active replays it without restarting the native service.", 11, false, MUTED));
        for (int r = 0; r < 2; r++) {
            LinearLayout row = row();
            for (int c = 0; c < 2; c++) {
                int slot = r * 2 + c + 1;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(round(CARD2, dp(18), 0x556a7892));
                TextView lab = text("M" + slot, 12, true, TEXT);
                lab.setGravity(Gravity.CENTER);
                Button b = smallBtn("", v -> selectSlot(slot));
                b.setOnLongClickListener(v -> { pickIntoSlot(slot); return true; });
                slotButtons[slot - 1] = b;
                Button plus = smallBtn("+ replace", v -> pickIntoSlot(slot));
                cell.addView(lab, new LinearLayout.LayoutParams(-1, dp(24)));
                cell.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));
                cell.addView(plus, new LinearLayout.LayoutParams(-1, dp(36)));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(116), 1);
                cp.setMargins(dp(5), dp(5), dp(5), dp(5));
                row.addView(cell, cp);
            }
            media.addView(row);
        }
        mediaLabel = text("", 12, false, TEXT);
        mediaLabel.setTextIsSelectable(true);
        media.addView(mediaLabel);
        LinearLayout mr = row();
        mr.addView(primaryBtn("Select / Replace active", v -> pickIntoSlot(activeSlot()), PRIMARY), weight());
        mr.addView(primaryBtn("Replay active", v -> startReplacement(), PRIMARY), weight());
        media.addView(mr);
        body.addView(media);

        LinearLayout controls = card();
        controls.addView(section("Image controls"));
        transformLabel = text("", 12, false, TEXT);
        transformLabel.setTextIsSelectable(true);
        controls.addView(transformLabel);
        controls.addView(text("v22: floating controls no longer own Binder/TX/bake. They send commands to the same controller used by these buttons.", 11, false, MUTED));

        LinearLayout c1 = row();
        c1.addView(primaryBtn("Zoom +", v -> { tx.zoom(1.12f); applyTransform("zoom+"); }, PRIMARY), weight());
        c1.addView(primaryBtn("Up", v -> { tx.move(0f, 0.04f); applyTransform("up"); }, PRIMARY), weight());
        c1.addView(primaryBtn("Zoom -", v -> { tx.zoom(1f / 1.12f); applyTransform("zoom-"); }, PRIMARY), weight());
        controls.addView(c1);

        LinearLayout c2 = row();
        c2.addView(primaryBtn("Left", v -> { tx.move(-0.04f, 0f); applyTransform("left"); }, PRIMARY), weight());
        c2.addView(primaryBtn("Center", v -> { tx.center(); applyTransform("center"); }, PRIMARY), weight());
        c2.addView(primaryBtn("Right", v -> { tx.move(0.04f, 0f); applyTransform("right"); }, PRIMARY), weight());
        controls.addView(c2);

        LinearLayout c3 = row();
        c3.addView(primaryBtn("Fit / Fill", v -> { tx.toggleFitFill(); applyTransform("fit-fill"); }, PRIMARY), weight());
        c3.addView(primaryBtn("Down", v -> { tx.move(0f, -0.04f); applyTransform("down"); }, PRIMARY), weight());
        c3.addView(primaryBtn("Crop", v -> { tx.cycleCrop(); applyTransform("crop"); }, PRIMARY), weight());
        controls.addView(c3);

        LinearLayout c4 = row();
        c4.addView(primaryBtn("Rotate", v -> { tx.rotate90(); applyTransform("rotate"); }, PRIMARY), weight());
        c4.addView(primaryBtn("Mirror", v -> { tx.toggleMirrorH(); applyTransform("mirror"); }, PRIMARY), weight());
        c4.addView(primaryBtn("Reset", v -> { tx.reset(); applyTransform("reset"); }, PRIMARY), weight());
        controls.addView(c4);
        LinearLayout c5 = row();
        c5.addView(primaryBtn("Commit / Apply", v -> forceApplyTransform("manual-apply"), CYAN), weight());
        c5.addView(primaryBtn("Open floating controls", v -> startFloatPanel(), CYAN), weight());
        controls.addView(c5);
        body.addView(controls);

        LinearLayout tools = card();
        tools.addView(section("Tools"));
        LinearLayout tr = row();
        tr.addView(primaryBtn("Overlay permission", v -> openOverlaySettings(), PRIMARY), weight());
        tr.addView(primaryBtn("Export log", v -> shareLog(), PRIMARY), weight());
        tools.addView(tr);
        body.addView(tools);
        refreshAll();
    }

    private int activeSlot() { return Math.max(1, Math.min(4, prefs.getInt("ActiveSlot", 1))); }
    private String slotKey(int slot) { return "Slot" + slot + "Path"; }

    private void pickIntoSlot(int slot) {
        pendingPickSlot = Math.max(1, Math.min(4, slot));
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        startActivityForResult(i, REQ_PICK);
    }

    private void selectSlot(int slot) {
        String path = prefs.getString(slotKey(slot), "");
        if (path == null || path.length() == 0) { pickIntoSlot(slot); return; }
        prefs.edit()
                .putInt("ActiveSlot", slot)
                .putString("OriginalPlayFileMp4", path)
                .putString("PlayFileMp4", path)
                .putString("IceCamState", "MEDIA_SELECTED")
                .apply();
        logger.log("media", "active slot M" + slot + " path=" + path);
        refreshAll();
        if (prefs.getBoolean("ReplacementActive", false)) startReplacement();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) {}
            String path = MediaResolver.resolveToReadableFile(this, uri, logger);
            int slot = Math.max(1, Math.min(4, pendingPickSlot));
            tx.reset();
            tx.save(prefs);
            prefs.edit()
                    .putInt("ActiveSlot", slot)
                    .putString(slotKey(slot), path)
                    .putString("OriginalPlayFileMp4", path)
                    .putString("PlayFileMp4", path)
                    .putInt("PlayFileType", 1)
                    .putString("IceCamState", "MEDIA_SELECTED")
                    .apply();
            logger.log("media", "selected slot M" + slot + " path=" + path);
            refreshAll();
            if (prefs.getBoolean("ReplacementActive", false)) startReplacement();
        }
    }

    private void startReplacement() {
        String p = prefs.getString("PlayFileMp4", "");
        if (p == null || p.length() == 0) { toast("Select media first"); return; }
        controller.startReplacement(TransformController.Source.MAIN);
        refreshAll();
    }

    private void requestBackendApply(String path, String source, boolean force) {
        BackendApplyQueue.get(this).enqueue(path, source, force);
        refreshAll();
    }

    private boolean legacyApplyMediaOnce(String p, String source) {
        synchronized (backendLock) {
            try {
                prefs.edit().putString("IceCamState", "APPLYING_MEDIA").apply();
                logger.log("ui", "media apply start source=" + source + " path=" + p);
                binder.setPreferredService(RootBootstrap.FIXED_SERVICE_NAME);
                if (!binder.connected()) {
                    binder.clearCache();
                    sleepMs(250);
                }
                int mode = binder.setModeString(1, p);
                sleepMs(520);
                TransformState current = TransformState.load(prefs);
                int play = binder.playSource(p, current.mirrorH(), prefs.getBoolean("PlayisLoop", true));
                boolean active = mode >= 0 && play >= 0;
                prefs.edit().putBoolean("ReplacementActive", active).putString("IceCamState", active ? "REPLACEMENT_ACTIVE" : "PLAY_ERROR").apply();
                logger.log("ui", "media apply done TX14=" + mode + " TX11=" + play + " active=" + active + " path=" + p);
                if (!active) {
                    binder.clearCache();
                    logger.log("ui", "binder apply failed; cache cleared lastError=" + binder.lastError());
                }
                return active;
            } catch (Throwable t) {
                prefs.edit().putBoolean("ReplacementActive", false).putString("IceCamState", "PLAY_ERROR").apply();
                binder.clearCache();
                logger.log("ui", "media apply exception: " + t);
                return false;
            }
        }
    }

    private void applyTransform(String reason) {
        controller.updateState(TransformController.Source.MAIN, reason, tx, MAIN_AUTO_COMMIT);
        refreshAll();
    }

    private void forceApplyTransform(String reason) {
        tx.save(prefs);
        controller.commit(TransformController.Source.MAIN, reason);
        refreshAll();
    }

    private void restoreCamera() {
        controller.restoreCamera(TransformController.Source.MAIN);
        refreshAll();
    }

    private void startFloatPanel() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            toast("Allow Display over other apps, then open floating controls again");
            return;
        }
        try { startService(new Intent(this, FloatService.class)); logger.log("ui", "floating controls requested"); }
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
            int activeSlot = activeSlot();
            for (int i = 1; i <= 4; i++) {
                Button b = slotButtons[i - 1];
                if (b == null) continue;
                String p = prefs.getString(slotKey(i), "");
                String label = (p == null || p.length() == 0) ? "Empty" : shortName(p);
                b.setText((i == activeSlot ? "● " : "") + label);
                b.setTextColor(i == activeSlot ? Color.WHITE : 0xffdbe7f4);
                b.setBackground(round(i == activeSlot ? CYAN : 0xaa6d7c92, dp(14), 0x66ffffff));
            }
            if (mediaLabel != null) {
                String p = prefs.getString("PlayFileMp4", "");
                mediaLabel.setText(p == null || p.length() == 0 ? "No active media" : "Active M" + activeSlot + ": " + shortPath(p));
            }
            if (transformLabel != null) transformLabel.setText(tx.summary() + (controller.isRendering() ? "\nRendering/applying via TransformController…" : "\nUnified controller ready. Floating overlay is state-only until Commit."));
        });
    }

    private String shortName(String p) {
        if (p == null) return "Empty";
        int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        String n = slash >= 0 ? p.substring(slash + 1) : p;
        return n.length() > 26 ? n.substring(0, 23) + "…" : n;
    }
    private String shortPath(String p) { return p.length() > 96 ? "…" + p.substring(p.length() - 96) : p; }
    private void runBg(Runnable r) { new Thread(() -> { try { r.run(); } catch (Throwable t) { logger.log("thread", String.valueOf(t)); } }, "icecam-bg").start(); }
    private static void sleepMs(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private void shareLog() {
        try {
            logger.logDivider("diag", "export requested");
            String diag = DiagnosticDumper.build(this, logger, binder);
            logger.log("diag", "snapshot built chars=" + diag.length());
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, diag + "\n\n--- runtime-log-ring ---\n" + logger.text());
            startActivity(Intent.createChooser(i, "Export IceCam diagnostics"));
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
    private Button smallBtn(String s, View.OnClickListener l) { Button b = primaryBtn(s, l, 0xaa6d7c92); b.setTextSize(10); return b; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(1, stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}
