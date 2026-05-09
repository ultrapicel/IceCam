package com.icecam.dev;

import android.Manifest;
import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private TextView diagnostics;
    private final String ctl = "/data/adb/icecam/bin/icecamctl";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 23) {
            try { requestPermissions(new String[]{Manifest.permission.CAMERA}, 10); } catch (Throwable ignored) {}
        }
        buildUi();
        appendDiag("IceCam v4 dev ready");
        appendDiag("Package: " + getPackageName());
        appendDiag("Device: " + Build.DEVICE + " sdk=" + Build.VERSION.SDK_INT + " abi=" + Build.SUPPORTED_ABIS[0]);
        dumpCameras();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 40);
        root.setBackgroundColor(0xff05090d);
        sv.addView(root);
        setContentView(sv);

        TextView title = tv("IceCam", 42, 0xffeef4ff);
        root.addView(title);
        root.addView(tv("System camera replacement · v4 dev hook layer", 24, 0xffdce7f7));

        status = tv("Status: booting", 20, 0xffffffff);
        status.setPadding(16, 16, 16, 16);
        status.setBackgroundColor(0xff101820);
        root.addView(status);

        addButton("Request Root Check", v -> runCtl("status"));
        addButton("Prepare Hook Layer", v -> runCtl("prepare-hooks"));
        addButton("Export Debug Bundle", v -> runCtl("logs"));
        addButton("Show Hook Log", v -> showFile("/data/adb/icecam/logs/hook.log"));

        diagnostics = tv("", 18, 0xffeef4ff);
        diagnostics.setPadding(16, 16, 16, 16);
        diagnostics.setBackgroundColor(0xff101820);
        root.addView(diagnostics);
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private void addButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private void runCtl(String arg) {
        new Thread(() -> {
            String result = execRoot(ctl + " " + arg);
            runOnUiThread(() -> status.setText(result));
        }).start();
    }

    private String execRoot(String cmd) {
        StringBuilder out = new StringBuilder();
        int code = -999;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
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

    private void showFile(String path) {
        new Thread(() -> {
            String result = execRoot("cat " + path);
            runOnUiThread(() -> status.setText(result));
        }).start();
    }

    private void appendDiag(String s) {
        diagnostics.append(s + "\n");
    }

    private void dumpCameras() {
        try {
            CameraManager cm = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                Integer orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Integer level = cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                appendDiag("Camera " + id + " facing=" + facing + " orientation=" + orientation + " hwLevel=" + level);
            }
        } catch (Throwable t) {
            appendDiag("Camera dump failed: " + t);
        }
    }

    static class StreamGobbler extends Thread {
        private final InputStream is;
        String data = "";
        StreamGobbler(InputStream is) { this.is = is; }
        public void run() {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                data = bos.toString();
            } catch (Throwable ignored) {}
        }
    }
}
