package com.icecam.dev;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final String ctl = "/data/adb/icecam/bin/icecamctl";
    private TextView status;
    private TextView diagnostics;
    private TextView selectedMedia;
    private ImageView preview;
    private String mode = "log-only";
    private String cameraMode = "auto";
    private boolean mirror = false;
    private Uri mediaUri = null;
    private float scale = 1f, posX = 0f, posY = 0f, rotation = 0f;
    private static final int PICK_MEDIA = 200;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 23) {
            try { requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, 10); } catch(Throwable ignored){}
        }
        buildUi();
        writeAppConfig();
        dumpCameras();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 36, 24, 36);
        root.setBackgroundColor(0xff05090d);
        sv.addView(root);
        setContentView(sv);

        root.addView(tv("IceCam", 44, 0xffeef4ff));
        root.addView(tv("System camera replacement · v5 dev", 24, 0xffdce7f7));

        status = card("Status: ready");
        root.addView(status);

        preview = new ImageView(this);
        preview.setBackgroundColor(0xff03060a);
        preview.setMinimumHeight(520);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 560));

        selectedMedia = card("Media source: none");
        root.addView(selectedMedia);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        root.addView(actions);
        addButton(actions, "Select Photo / Video", v -> pickMedia());
        addButton(actions, "Write Config + Prepare Hooks", v -> { writeAppConfig(); runCtl("prepare-hooks"); });
        addButton(actions, "Request Root Check", v -> runCtl("status"));
        addButton(actions, "Export Debug Bundle", v -> runCtl("logs"));
        addButton(actions, "Show Hook Log", v -> showFile("/data/adb/icecam/logs/hook.log"));

        root.addView(tv("Hook Mode", 26, 0xffeef4ff));
        LinearLayout modes = row();
        root.addView(modes);
        addButton(modes, "Log", v -> { mode="log-only"; writeAppConfig(); status.setText("mode=log-only"); });
        addButton(modes, "Block Test", v -> { mode="block-open-test"; writeAppConfig(); status.setText("mode=block-open-test"); });
        addButton(modes, "Virtual Stub", v -> { mode="virtual-stub"; writeAppConfig(); status.setText("mode=virtual-stub"); });

        root.addView(tv("Camera Profile", 26, 0xffeef4ff));
        LinearLayout cam = row();
        root.addView(cam);
        addButton(cam, "Auto", v -> { cameraMode="auto"; writeAppConfig(); });
        addButton(cam, "Back", v -> { cameraMode="back"; writeAppConfig(); });
        addButton(cam, "Front", v -> { cameraMode="front"; writeAppConfig(); });

        root.addView(tv("Transform", 26, 0xffeef4ff));
        LinearLayout tr = row();
        root.addView(tr);
        addButton(tr, "Mirror", v -> { mirror=!mirror; applyTransform(); writeAppConfig(); });
        addButton(tr, "Zoom +", v -> { scale+=0.1f; applyTransform(); writeAppConfig(); });
        addButton(tr, "Zoom -", v -> { scale=Math.max(0.2f, scale-0.1f); applyTransform(); writeAppConfig(); });
        addButton(tr, "Rotate", v -> { rotation+=90f; applyTransform(); writeAppConfig(); });
        addButton(tr, "Reset", v -> { scale=1f; posX=posY=rotation=0f; mirror=false; applyTransform(); writeAppConfig(); });

        diagnostics = card("Diagnostics\n");
        root.addView(diagnostics);
        diag("Package: " + getPackageName());
        diag("Device: " + Build.DEVICE + " sdk=" + Build.VERSION.SDK_INT + " abi=" + Build.SUPPORTED_ABIS[0]);
        diag("v5 includes: media source config, hook telemetry, log/block/virtual-stub modes");
        diag("Frame replacement is not fully active yet; v5 prepares the injection contract.");
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private TextView card(String s) {
        TextView t = tv(s, 18, 0xffeef4ff);
        t.setPadding(18, 18, 18, 18);
        t.setBackgroundColor(0xff101820);
        return t;
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setOnClickListener(l);
        parent.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void pickMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        startActivityForResult(i, PICK_MEDIA);
    }

    @Override protected void onActivityResult(int r, int c, Intent data) {
        super.onActivityResult(r, c, data);
        if (r == PICK_MEDIA && c == RESULT_OK && data != null) {
            mediaUri = data.getData();
            try { getContentResolver().takePersistableUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Throwable ignored){}
            selectedMedia.setText("Media source:\n" + mediaUri);
            try { preview.setImageURI(mediaUri); } catch(Throwable t) { status.setText("Preview set failed: " + t); }
            writeAppConfig();
        }
    }

    private void applyTransform() {
        preview.setScaleX(scale * (mirror ? -1f : 1f));
        preview.setScaleY(scale);
        preview.setRotation(rotation);
        preview.setTranslationX(posX);
        preview.setTranslationY(posY);
        status.setText("mode=" + mode + " camera=" + cameraMode + " mirror=" + mirror + " scale=" + scale + " rotation=" + rotation);
    }

    private void writeAppConfig() {
        String media = mediaUri == null ? "" : mediaUri.toString();
        String json = "{"
            + "\"enabled\":true,"
            + "\"version\":\"0.5.0-dev\","
            + "\"mode\":\"" + mode + "\","
            + "\"cameraMode\":\"" + cameraMode + "\","
            + "\"mediaUri\":\"" + esc(media) + "\","
            + "\"mirror\":" + mirror + ","
            + "\"scale\":" + scale + ","
            + "\"rotation\":" + rotation
            + "}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes());
        execRoot("mkdir -p /data/adb/icecam/config && echo " + encoded + " | base64 -d > /data/adb/icecam/config/app_config.json && chmod 666 /data/adb/icecam/config/app_config.json");
    }

    private String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private void runCtl(String arg) {
        new Thread(() -> {
            String result = execRoot(ctl + " " + arg);
            runOnUiThread(() -> status.setText(result));
        }).start();
    }

    private void showFile(String path) {
        new Thread(() -> {
            String result = execRoot("cat " + path);
            runOnUiThread(() -> status.setText(result));
        }).start();
    }

    private String execRoot(String cmd) {
        StringBuilder out = new StringBuilder();
        int code = -999;
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            StreamGobbler so = new StreamGobbler(p.getInputStream());
            StreamGobbler se = new StreamGobbler(p.getErrorStream());
            so.start(); se.start();
            code = p.waitFor();
            so.join(); se.join();
            out.append(so.data);
            if (se.data.length() > 0) out.append("\nstderr:\n").append(se.data);
        } catch (Throwable e) {
            out.append(e.toString());
        }
        out.append("\nexit=").append(code);
        return out.toString();
    }

    private void diag(String s) { diagnostics.append(s + "\n"); }

    private void dumpCameras() {
        try {
            CameraManager cm = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Integer level = cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                diag("Camera " + id + " facing=" + facing + " orientation=" + orientation + " hwLevel=" + level);
            }
        } catch(Throwable t) { diag("Camera dump failed: " + t); }
    }

    static class StreamGobbler extends Thread {
        private final InputStream is;
        String data="";
        StreamGobbler(InputStream is){this.is=is;}
        public void run(){
            try{
                ByteArrayOutputStream bos=new ByteArrayOutputStream();
                byte[] buf=new byte[4096]; int n;
                while((n=is.read(buf))>0) bos.write(buf,0,n);
                data=bos.toString();
            }catch(Throwable ignored){}
        }
    }
}
