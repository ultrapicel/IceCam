package com.icecam.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.icecam.app.engine.IceRenderView;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA = 2001;
    private LinearLayout logBox;
    private IceRenderView renderView;
    private TextView status;
    private boolean mirror;
    private String activeCamera = "Auto";
    private String fitMode = "Fill";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        IceLog.i("App", "IceCam v2.0 dev start");
        requestRuntimePermissions();
        setContentView(buildUi());
        appendLog("IceCam v2 dev ready");
        appendLog("Device id=" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        appendCameraProfiles();
    }

    private void requestRuntimePermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 100);
        }
    }

    private View buildUi() {
        ScrollView root = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(24, 28, 24, 28);
        ll.setBackgroundColor(Color.rgb(7,10,14));
        root.addView(ll);

        TextView title = tv("IceCam", 36, true);
        TextView sub = tv("System camera replacement · v2 dev", 15, false);
        ll.addView(title); ll.addView(sub);

        status = tv("Mode: DEV · Camera: Auto · Source: none", 14, false);
        ll.addView(cardView(null, status));

        renderView = new IceRenderView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(420));
        renderView.setLayoutParams(rp);
        ll.addView(cardView("Preview Canvas", renderView));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        Button pick = button("Select Photo / Video");
        pick.setOnClickListener(v -> pickMedia());
        Button rootCheck = button("Request Root Check");
        rootCheck.setOnClickListener(v -> checkRootAndModule());
        Button export = button("Export Debug Bundle");
        export.setOnClickListener(v -> exportDebugBundle());
        actions.addView(pick); actions.addView(rootCheck); actions.addView(export);
        ll.addView(cardView("Actions", actions));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.addView(row(button("Camera: Auto", v -> { activeCamera = "Auto"; updateStatus(); appendLog("Camera profile mode=Auto"); }), button("Back", v -> { activeCamera = "Back"; updateStatus(); appendLog("Camera profile mode=Back"); }), button("Front", v -> { activeCamera = "Front"; updateStatus(); appendLog("Camera profile mode=Front"); })));
        controls.addView(row(button("Mirror", v -> { mirror = !mirror; renderView.setMirror(mirror); updateStatus(); appendLog("Mirror=" + mirror); }), button("Fit", v -> { fitMode = "Fit"; renderView.setFitMode(fitMode); updateStatus(); appendLog("Fit mode"); }), button("Fill", v -> { fitMode = "Fill"; renderView.setFitMode(fitMode); updateStatus(); appendLog("Fill mode"); })));
        controls.addView(row(button("Reset Transform", v -> { renderView.resetTransform(); appendLog("Transform reset"); }), button("Renderer State", v -> appendLog(renderView.getRendererState()))));
        ll.addView(cardView("Control Panel", controls));

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        ll.addView(cardView("Diagnostics", logBox));
        return root;
    }

    private void pickMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_MEDIA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) {}
            renderView.setMedia(uri);
            IceLog.i("Stream", "Selected media " + uri);
            appendLog("Selected media: " + uri);
            updateStatus();
        }
    }

    private void appendCameraProfiles() {
        String dump = CameraProfileDumper.dump(this);
        IceLog.i("Camera2", dump);
        for (String line : dump.split("\\n")) if (!line.trim().isEmpty()) appendLog(line);
    }

    private void checkRootAndModule() {
        appendLog("Root request: su -c id");
        Shell.Result id = Shell.run(new String[]{"su", "-c", "id"}, 8000);
        appendLog("Root exit=" + id.exitCode + " timeout=" + id.timeout + " stdout=" + Shell.oneLine(id.stdout));
        if (id.stderr.length() > 0) appendLog("Root stderr=" + Shell.oneLine(id.stderr));

        Shell.Result status = Shell.run(new String[]{"su", "-c", "icecamctl status"}, 8000);
        appendLog("Module exit=" + status.exitCode + " timeout=" + status.timeout + " stdout=" + Shell.oneLine(status.stdout));
        if (status.stderr.length() > 0) appendLog("Module stderr=" + Shell.oneLine(status.stderr));

        Shell.Result prep = Shell.run(new String[]{"su", "-c", "icecamctl prepare"}, 12000);
        appendLog("Prepare exit=" + prep.exitCode + " timeout=" + prep.timeout + " stdout=" + Shell.oneLine(prep.stdout));
        if (prep.stderr.length() > 0) appendLog("Prepare stderr=" + Shell.oneLine(prep.stderr));
    }

    private void exportDebugBundle() {
        IceLog.i("App", "Export debug requested");
        Shell.Result r = Shell.run(new String[]{"su", "-c", "icecamctl logs"}, 12000);
        appendLog("Export exit=" + r.exitCode + " timeout=" + r.timeout + " stdout=" + Shell.oneLine(r.stdout));
        if (r.stderr.length() > 0) appendLog("Export stderr=" + Shell.oneLine(r.stderr));
        appendLog("App log: /sdcard/Download/IceCamLogs/icecam_app.log");
    }

    private void updateStatus() {
        status.setText("Mode: DEV · Camera: " + activeCamera + " · Fit: " + fitMode + " · Mirror: " + mirror);
    }

    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b; }
    private Button button(String label, View.OnClickListener l) { Button b = button(label); b.setOnClickListener(l); return b; }
    private LinearLayout row(View... views) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); for (View v: views) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(4,4,4,4); r.addView(v, p); } return r; }
    private TextView tv(String s, int sp, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(244,248,251)); if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v; }
    private View cardView(String h, View child) { LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(22,22,22,22); wrap.setBackgroundColor(Color.rgb(18,24,31)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,20,0,0); wrap.setLayoutParams(p); if (h!=null) wrap.addView(tv(h,21,true)); wrap.addView(child); return wrap; }
    private void appendLog(String s) { TextView v = tv(s, 12, false); v.setGravity(Gravity.START); if (logBox != null) logBox.addView(v); IceLog.d("App", s); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
