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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 7101;
    private static final int BG = 0xfff8f9ff;
    private static final int CARD = 0xffebeaf4;
    private static final int CARD_DARK = 0xffe1e0ea;
    private static final int PRIMARY = 0xff5466a1;
    private static final int TEXT = 0xff242636;
    private static final int MUTED = 0xff656979;

    private AppLogger logger;
    private RootBootstrap root;
    private VliveBinderClient binder;
    private SharedPreferences prefs;

    private LinearLayout body, bottomNav;
    private TextView status, logView, mediaLabel;
    private EditText source, rtmp, serviceName;
    private RadioButton videoMode, rtmpMode;
    private CheckBox floatWindow, correctFrame, loop, colorInject, mirror, autoRotate;
    private SeekBar xBar, yBar, scaleBar, angleBar;
    private int tab = 1;
    private float posX = 0, posY = 0, scale = 1f, angle = 0f;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        logger = new AppLogger(this);
        root = new RootBootstrap(this, logger);
        binder = new VliveBinderClient(logger);
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        binder.setPreferredService(root.serverName());
        requestBasicPermissions();
        buildShell();
        logger.setListener(text -> runOnUiThread(() -> { if (logView != null) logView.setText(trimLog(text)); }));
        logger.log("app", "IceCam v8 transform reconstruction started");
        logger.log("app", "ServerName=" + root.serverName());
        logger.log("app", "sourceDir=" + getApplicationInfo().sourceDir);
        new Thread(() -> { root.bootstrap(); binder.setPreferredService(root.serverName()); runOnUiThread(this::refreshStatus); }).start();
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

    private void buildShell() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(12), dp(12), dp(16));
        scroll.addView(body);
        rootLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(4), dp(3), dp(4), dp(3));
        bottomNav.setBackgroundColor(0xffffffff);
        rootLayout.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(70)));
        setContentView(rootLayout);
        render();
    }

    private void render() {
        body.removeAllViews();
        bottomNav.removeAllViews();
        if (tab == 0) renderHome();
        else if (tab == 1) renderControl();
        else renderSettings();
        nav("⌂\nHome", 0);
        nav("▦\nControl", 1);
        nav("⚙\nSettings", 2);
        refreshStatus();
    }

    private void renderHome() {
        body.addView(title("IceCam"));
        body.addView(infoCard("Offline native camera replacement panel. Root daemon and Binder bridge are reconstructed from the original APK."));
        LinearLayout c = card();
        c.addView(section("Status"));
        status = text("initializing...", 15, false, MUTED);
        c.addView(status);
        LinearLayout r = row();
        r.addView(primaryBtn("Start root service", v -> runBg(() -> { root.bootstrap(); binder.setPreferredService(root.serverName()); refreshStatus(); })), weight());
        r.addView(primaryBtn("Connect Binder", v -> { binder.setPreferredService(root.serverName()); logger.logBlock("binder", binder.diagnostics()); refreshStatus(); }), weight());
        c.addView(r);
        body.addView(c);

        LinearLayout p = card();
        p.addView(section("Current source"));
        mediaLabel = text(currentSourceLabel(), 14, false, TEXT);
        mediaLabel.setPadding(0, dp(8), 0, dp(8));
        p.addView(mediaLabel);
        p.addView(primaryBtn("Pick photo/video", v -> pickMedia()), fullHeight());
        body.addView(p);
    }

    private void renderControl() {
        body.addView(infoCard("Choose local media, start the native service, then use the floating transform panel for zoom / pan / crop-fit / rotate / mirror."));
        LinearLayout c = card();
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.HORIZONTAL);
        videoMode = radio("Video replacement", prefs.getInt("PlayFileType", 1) == 1);
        rtmpMode = radio("RTMP replacement", prefs.getInt("PlayFileType", 1) == 2);
        modes.addView(videoMode, weightAuto()); modes.addView(rtmpMode, weightAuto());
        modes.setOnCheckedChangeListener((g, id) -> saveMode());
        c.addView(modes);

        source = edit(prefs.getString("PlayFileMp4", ""), "File path");
        rtmp = edit(prefs.getString("PlayRtmpUrl", "rtmp://ns8.indexforce.com/home/mystream"), "RTMP URL");
        c.addView(label("File path:")); c.addView(source);
        c.addView(label("RTMP URL:")); c.addView(rtmp);
        c.addView(primaryBtn("Select photo/video", v -> pickMedia()), fullHeight());
        c.addView(primaryBtn(replaceText(), v -> replaceCamera()), fullHeight());
        body.addView(c);

        LinearLayout opts = card();
        opts.addView(section("Options"));
        opts.addView(primaryBtn("Preview placeholder", v -> toast("Preview channel is logged. Native Surface preview still needs runtime binding.")), fullHeight());
        floatWindow = switchRow(opts, "Floating window", "FloatWindow", false);
        correctFrame = switchRow(opts, "Frame correction", "CorrectFrame", false);
        loop = switchRow(opts, "Loop playback", "PlayisLoop", true);
        colorInject = switchRow(opts, "Three-color injection", "PlayAutoColor_mode", false);
        mirror = switchRow(opts, "Mirror", "PlayMirror", false);
        autoRotate = switchRow(opts, "Auto rotate", "PlayAutoRotate", true);
        body.addView(opts);

        LinearLayout transform = card();
        transform.addView(section("Transform / TX24 controls"));
        xBar = slider(-100, 100, prefs.getInt("AutoColor_X", 0), v -> posX = v / 100f);
        yBar = slider(-100, 100, prefs.getInt("AutoColor_Y", 0), v -> posY = v / 100f);
        scaleBar = slider(5, 3200, prefs.getInt("Scale", 100), v -> scale = v / 100f);
        angleBar = slider(0, 270, prefs.getInt("PlayAngle", 0), v -> angle = Math.round(v / 90f) * 90);
        transform.addView(labeledSlider("X", xBar));
        transform.addView(labeledSlider("Y", yBar));
        transform.addView(labeledSlider("Zoom", scaleBar));
        transform.addView(labeledSlider("Rotation quadrant", angleBar));
        LinearLayout tr = row();
        tr.addView(primaryBtn("Apply transform", v -> applyTransform()), weight());
        tr.addView(primaryBtn("Reset", v -> resetTransform()), weight());
        transform.addView(tr);
        body.addView(transform);
    }

    private void renderSettings() {
        body.addView(title("IceCam settings"));
        LinearLayout svc = card();
        svc.addView(section("Service & diagnostics"));
        serviceName = edit(root.serverName(), "Generated Binder service name");
        svc.addView(label("ServerName argument passed to /data/vcplax:"));
        svc.addView(serviceName);
        LinearLayout r1 = row();
        r1.addView(primaryBtn("Root bootstrap", v -> runBg(() -> { saveServiceName(); root.bootstrap(); binder.setPreferredService(root.serverName()); refreshStatus(); })), weight());
        r1.addView(primaryBtn("Connect", v -> { saveServiceName(); logger.logBlock("binder", binder.diagnostics()); refreshStatus(); }), weight());
        svc.addView(r1);
        LinearLayout r2 = row();
        r2.addView(primaryBtn("Full status", v -> runBg(() -> { root.status(); logger.logBlock("binder", binder.diagnostics()); refreshStatus(); })), weight());
        r2.addView(primaryBtn("New service name", v -> { String s = root.resetServerName(); binder.setPreferredService(s); render(); }), weight());
        svc.addView(r2);
        LinearLayout r3 = row();
        r3.addView(primaryBtn("Android settings", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))), weight());
        r3.addView(primaryBtn("Share log", v -> shareLog()), weight());
        svc.addView(r3);
        body.addView(svc);

        LinearLayout tx = card();
        tx.addView(section("Recovered native transactions"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout line = new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
        int[] codes = {12,13,15,16,17,18,19,22,24,25};
        for (int code : codes) line.addView(smallBtn("TX" + code, v -> sendRecovered(((Button)v).getText().toString())));
        hs.addView(line); tx.addView(hs); body.addView(tx);

        LinearLayout logs = card();
        logs.addView(section("Runtime log"));
        LinearLayout lr = row();
        lr.addView(primaryBtn("Refresh", v -> runBg(() -> { root.status(); refreshStatus(); })), weight());
        lr.addView(primaryBtn("Share log", v -> shareLog()), weight());
        logs.addView(lr);
        logView = text(trimLog(logger.text()), 11, false, 0xffedf2ff);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logView.setBackground(round(0xff3e4654, dp(16), 0x44ffffff));
        logs.addView(logView, new LinearLayout.LayoutParams(-1, -2));
        body.addView(logs);
    }


    private void startFloatPanel() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(it);
            toast("Allow Display over other apps, then enable Floating window again");
            return;
        }
        Intent svc = new Intent(this, FloatService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundServiceCompat(svc); else startService(svc);
        logger.log("ui", "floating panel requested");
    }

    private void startForegroundServiceCompat(Intent svc) {
        try { startService(svc); } catch (Throwable t) { logger.log("ui", "start float service failed: " + t); }
    }

    private void saveServiceName() {
        if (serviceName == null) return;
        String s = serviceName.getText().toString().trim();
        if (s.length() == 0) return;
        prefs.edit().putString("ServerName", s).apply();
        binder.setPreferredService(s);
        logger.log("binder", "ServerName=" + s);
    }

    private void saveMode() {
        prefs.edit().putInt("PlayFileType", rtmpMode != null && rtmpMode.isChecked() ? 2 : 1).apply();
        if (body != null) render();
    }

    private String replaceText() { return prefs.getBoolean("Active", false) ? "Restore camera" : "Replace camera"; }

    private String currentSourceLabel() {
        String p = prefs.getString("PlayFileMp4", "");
        String r = prefs.getString("PlayRtmpUrl", "rtmp://ns8.indexforce.com/home/mystream");
        return p.length() > 0 ? "Local media selected:\n" + p : "No local file selected\nRTMP: " + r;
    }

    private void pickMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) {}
            String path = MediaResolver.resolveToReadableFile(this, uri, logger);
            prefs.edit().putString("PlayFileMp4", path).putInt("PlayFileType", 1).apply();
            if (source != null) source.setText(path);
            toast("Media selected");
            applyFile(path);
            render();
        }
    }

    private void replaceCamera() {
        saveVisiblePrefs();
        if (prefs.getBoolean("Active", false)) {
            int r = binder.simple(VliveBinderClient.TX_25);
            prefs.edit().putBoolean("Active", false).apply();
            logger.log("source", "restore camera TX25=" + r);
            toast("Restore command sent");
        } else if (prefs.getInt("PlayFileType", 1) == 2) {
            applyRtmp(); prefs.edit().putBoolean("Active", true).apply();
        } else {
            applyFile(source != null ? source.getText().toString() : prefs.getString("PlayFileMp4", ""));
            prefs.edit().putBoolean("Active", true).apply();
        }
        render();
    }

    private void saveVisiblePrefs() {
        SharedPreferences.Editor e = prefs.edit();
        if (source != null) e.putString("PlayFileMp4", source.getText().toString());
        if (rtmp != null) e.putString("PlayRtmpUrl", rtmp.getText().toString());
        if (videoMode != null) e.putInt("PlayFileType", rtmpMode.isChecked() ? 2 : 1);
        if (mirror != null) e.putBoolean("PlayMirror", mirror.isChecked());
        if (loop != null) e.putBoolean("PlayisLoop", loop.isChecked());
        if (autoRotate != null) e.putBoolean("PlayAutoRotate", autoRotate.isChecked());
        if (colorInject != null) e.putBoolean("PlayAutoColor_mode", colorInject.isChecked());
        if (xBar != null) e.putInt("AutoColor_X", seekValue(xBar, -100));
        if (yBar != null) e.putInt("AutoColor_Y", seekValue(yBar, -100));
        if (scaleBar != null) e.putInt("Scale", seekValue(scaleBar, 25));
        if (angleBar != null) e.putInt("PlayAngle", seekValue(angleBar, -180));
        e.apply();
    }

    private void applyFile(String path) {
        String p = path == null ? "" : path.trim();
        if (p.length() == 0) { toast("Select a photo or video first"); return; }
        int mode = binder.setModeString(1, p);
        int play = binder.playSource(p, prefs.getBoolean("PlayMirror", false), prefs.getBoolean("PlayisLoop", true));
        logger.log("source", "file TX14=" + mode + " TX11=" + play + " path=" + p);
        if (mode == -999 || play == -999) logger.log("hint", "Binder service not connected. Start root service, then check Full status.");
        refreshStatus();
    }

    private void applyRtmp() {
        String u = rtmp != null ? rtmp.getText().toString().trim() : prefs.getString("PlayRtmpUrl", "");
        if (u.length() == 0) { toast("Enter RTMP URL"); return; }
        prefs.edit().putString("PlayRtmpUrl", u).putInt("PlayFileType", 2).apply();
        int mode = binder.setModeString(2, u);
        int play = binder.playSource(u, prefs.getBoolean("PlayMirror", false), prefs.getBoolean("PlayisLoop", true));
        logger.log("source", "rtmp TX14=" + mode + " TX11=" + play + " url=" + u);
        refreshStatus();
    }

    private void applyTransform() {
        saveVisiblePrefs();
        TransformState tx = TransformState.load(prefs);
        tx.panX = posX;
        tx.panY = posY;
        tx.zoomX = scale <= 0f ? 1f : scale;
        tx.zoomY = tx.lockAspect() ? tx.zoomX : tx.zoomY;
        int q = ((Math.round(angle / 90f) % 4) + 4) % 4;
        tx.flags = (tx.flags & ~TransformState.FLAG_ROT_MASK) | (q << TransformState.FLAG_ROT_SHIFT);
        tx.save(prefs);
        int r = binder.setTransform(tx);
        logger.log("transform", "TX24=" + r + " " + tx.summary());
        refreshStatus();
    }

    private void resetTransform() {
        TransformState tx = new TransformState();
        tx.reset();
        tx.save(prefs);
        posX = 0; posY = 0; scale = 1f; angle = 0;
        render();
        int r = binder.setTransform(tx);
        logger.log("transform", "reset TX24=" + r + " " + tx.summary());
    }

    private void sendRecovered(String label) {
        try {
            int c = Integer.parseInt(label.replace("TX", ""));
            int r;
            if (c == 16 || c == 17 || c == 19) r = binder.sendBoolCode(c, false);
            else if (c == 18) r = binder.sendIntCode(c, 0);
            else if (c == 22) r = binder.setRange(prefs.getLong("ActionRangebgin0", 0), prefs.getLong("ActionRangeEnd0", 0));
            else if (c == 24) { applyTransform(); return; }
            else r = binder.simple(c);
            logger.log("tx", label + " result=" + r);
        } catch (Throwable t) { logger.log("tx", label + " failed " + t); }
        refreshStatus();
    }

    private void refreshStatus() {
        runOnUiThread(() -> {
            if (status != null) {
                boolean c = binder.connected();
                status.setText("root bootstrap: auto\nbinder: " + c + "\nservice name: " + binder.preferredService() + "\n" + binder.lastError());
            }
            if (mediaLabel != null) mediaLabel.setText(currentSourceLabel());
        });
    }

    private void shareLog() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, logger.text());
        startActivity(Intent.createChooser(send, "Share IceCam log"));
    }

    private void runBg(Runnable r) { new Thread(r).start(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); logger.log("ui", s); }
    private String trimLog(String s) { return s.length() <= 24000 ? s : s.substring(0, 24000) + "\n...trimmed..."; }

    private TextView title(String s) { TextView v = text(s, 28, true, TEXT); v.setPadding(dp(6), dp(8), dp(6), dp(6)); return v; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView label(String s) { TextView v = text(s, 14, false, MUTED); v.setPadding(dp(2), dp(8), 0, 0); return v; }
    private TextView section(String s) { TextView v = text(s, 18, true, TEXT); v.setPadding(0, 0, 0, dp(10)); return v; }
    private TextView infoCard(String s) { TextView v = text(s, 16, false, TEXT); v.setPadding(dp(12), dp(14), dp(12), dp(14)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(8)); v.setLayoutParams(lp); v.setBackground(round(CARD, dp(14), 0x00ffffff)); return v; }
    private LinearLayout card() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(12), dp(14), dp(12), dp(14)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(10), 0, dp(8)); l.setLayoutParams(lp); l.setBackground(round(CARD, dp(14), 0x00ffffff)); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); return l; }
    private Button primaryBtn(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setOnClickListener(l); b.setBackground(round(PRIMARY, dp(24), 0x22000000)); return b; }
    private Button smallBtn(String s, View.OnClickListener l) { Button b = primaryBtn(s, l); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(52)); lp.setMargins(dp(5), dp(5), dp(5), dp(5)); b.setLayoutParams(lp); return b; }
    private EditText edit(String value, String hint) { EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setSingleLine(true); e.setTextColor(TEXT); e.setHintTextColor(0xff7a7d88); e.setTextSize(16); e.setPadding(dp(8), 0, dp(8), 0); e.setBackgroundColor(Color.TRANSPARENT); return e; }
    private RadioButton radio(String s, boolean checked) { RadioButton r = new RadioButton(this); r.setText(s); r.setTextColor(TEXT); r.setTextSize(16); r.setChecked(checked); return r; }
    private CheckBox switchRow(LinearLayout parent, String label, String pref, boolean def) {
        CheckBox cb = new CheckBox(this); cb.setText(label); cb.setTextColor(TEXT); cb.setTextSize(15); cb.setChecked(prefs.getBoolean(pref, def));
        cb.setPadding(0, dp(8), 0, dp(8));
        cb.setOnCheckedChangeListener((CompoundButton b, boolean is) -> {
            prefs.edit().putBoolean(pref, is).apply();
            if ("FloatWindow".equals(pref)) {
                if (is) startFloatPanel(); else stopService(new Intent(this, FloatService.class));
            } else {
                applyTransform();
            }
        });
        parent.addView(cb, new LinearLayout.LayoutParams(-1, -2));
        return cb;
    }
    private SeekBar slider(int min, int max, int initial, final Slider cb) {
        SeekBar s = new SeekBar(this);
        if (Build.VERSION.SDK_INT >= 26) { s.setMin(min); s.setMax(max); s.setProgress(initial); }
        else { s.setMax(max - min); s.setProgress(initial - min); }
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int p,boolean f){ int v = Build.VERSION.SDK_INT >= 26 ? p : p + min; cb.on(v); } public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){ applyTransform(); }});
        return s;
    }
    private int seekValue(SeekBar b, int min) { return Build.VERSION.SDK_INT >= 26 ? b.getProgress() : b.getProgress() + min; }
    private LinearLayout labeledSlider(String lab, SeekBar seek) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); TextView t = text(lab, 13, true, MUTED); l.addView(t); l.addView(seek); return l; }
    private void nav(String s, int target) { Button b = new Button(this); b.setText(s); b.setTextSize(13); b.setTextColor(target == tab ? PRIMARY : 0xff6c707b); b.setAllCaps(false); b.setBackgroundColor(Color.TRANSPARENT); b.setOnClickListener(v -> { tab = target; render(); }); bottomNav.addView(b, new LinearLayout.LayoutParams(0, -1, 1)); }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1); lp.setMargins(dp(4), dp(6), dp(4), dp(6)); return lp; }
    private LinearLayout.LayoutParams weightAuto() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(4), dp(4), dp(4), dp(4)); return lp; }
    private LinearLayout.LayoutParams fullHeight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58)); lp.setMargins(0, dp(8), 0, dp(8)); return lp; }
    private GradientDrawable round(int color, int radius, int strokeColor) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if ((strokeColor >>> 24) != 0) g.setStroke(1, strokeColor); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private interface Slider { void on(int value); }
}
