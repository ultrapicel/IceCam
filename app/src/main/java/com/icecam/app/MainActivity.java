package com.icecam.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private LinearLayout logBox;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        IceLog.i("App", "IceCam v1.1 dev start");
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 100);
        }
        setContentView(buildUi());
        dumpCameraInfo();
        appendLog("Root check: press REQUEST ROOT CHECK");
    }

    private View buildUi() {
        ScrollView root = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(28, 28, 28, 28);
        ll.setBackgroundColor(Color.rgb(11,15,20));
        root.addView(ll);

        TextView title = tv("IceCam", 34, true);
        TextView sub = tv("System camera replacement dev build", 15, false);
        ll.addView(title); ll.addView(sub);
        ll.addView(card("Status", "Mode: DEV v1.1\nRoot module: explicit check required\nHooks: LSPosed/Zygisk skeleton\nCamera path: Camera1/Camera2/CameraX/NDK planned"));
        ll.addView(card("Source", "Media input: placeholder\nBack camera profile: auto-copy\nFront camera profile: auto-copy\nStream engine: MediaCodec/OpenGL planned"));

        Button rootCheck = button("Request Root Check");
        rootCheck.setOnClickListener(v -> checkRootAndModule());
        ll.addView(rootCheck);

        Button export = button("Export Debug Bundle");
        export.setOnClickListener(v -> exportDebugBundle());
        ll.addView(export);

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        ll.addView(cardView("Diagnostics", logBox));
        return root;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private TextView tv(String s, int sp, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(244,248,251));
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v;
    }
    private View card(String h, String body) { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); TextView ht=tv(h,22,true); TextView bt=tv(body,14,false); c.addView(ht); c.addView(bt); return cardView(null,c); }
    private View cardView(String h, View child) { LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(24,24,24,24); wrap.setBackgroundColor(Color.rgb(21,27,34)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,24,0,0); wrap.setLayoutParams(p); if (h!=null) wrap.addView(tv(h,22,true)); wrap.addView(child); return wrap; }
    private void appendLog(String s) { TextView v = tv(s, 13, false); v.setGravity(Gravity.START); if (logBox != null) logBox.addView(v); IceLog.d("App", s); }

    private void dumpCameraInfo() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                Integer orient = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                appendLog("Camera " + id + " facing=" + facing + " orientation=" + orient);
            }
        } catch (Throwable t) { IceLog.e("Camera2", "Camera dump failed", t); appendLog("Camera dump failed: " + t.getClass().getSimpleName()); }
    }

    private void checkRootAndModule() {
        appendLog("Root request: executing su -c id");
        ShellResult id = runShell(new String[]{"su", "-c", "id"}, 8000);
        appendLog("Root exit=" + id.exitCode + " timeout=" + id.timeout);
        appendLog("Root stdout: " + safeOneLine(id.stdout));
        if (id.stderr.length() > 0) appendLog("Root stderr: " + safeOneLine(id.stderr));

        ShellResult module = runShell(new String[]{"su", "-c", "if [ -d /data/adb/icecam/logs ]; then echo MODULE_OK; ls -la /data/adb/icecam/logs; else echo MODULE_MISSING; fi"}, 8000);
        appendLog("Module exit=" + module.exitCode + " timeout=" + module.timeout);
        appendLog("Module stdout: " + safeOneLine(module.stdout));
        if (module.stderr.length() > 0) appendLog("Module stderr: " + safeOneLine(module.stderr));
    }

    private void exportDebugBundle() {
        IceLog.i("App", "Export debug requested");
        ShellResult r = runShell(new String[]{"su", "-c", "icecamctl logs"}, 12000);
        appendLog("Export exit=" + r.exitCode + " timeout=" + r.timeout);
        appendLog("Export stdout: " + safeOneLine(r.stdout));
        if (r.stderr.length() > 0) appendLog("Export stderr: " + safeOneLine(r.stderr));
        appendLog("App log: /sdcard/Download/IceCamLogs/icecam_app.log");
    }

    private static String safeOneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "<empty>";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private ShellResult runShell(String[] cmd, long timeoutMs) {
        ShellResult result = new ShellResult();
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                result.timeout = true;
                p.destroyForcibly();
                result.exitCode = -999;
            } else {
                result.exitCode = p.exitValue();
            }
            result.stdout = readAll(p.getInputStream());
            result.stderr = readAll(p.getErrorStream());
        } catch (Throwable t) {
            result.exitCode = -998;
            result.stderr = t.getClass().getSimpleName() + ": " + t.getMessage();
            IceLog.e("Root", "Shell command failed", t);
        }
        return result;
    }

    private String readAll(java.io.InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private static final class ShellResult {
        int exitCode = -1;
        boolean timeout = false;
        String stdout = "";
        String stderr = "";
    }
}
