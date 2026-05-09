package com.icecam.app;

import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class IceLog {
    private static final String ROOT = "IceCam";
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private IceLog() {}

    public static void d(String area, String msg) { write("D", area, msg, null); }
    public static void i(String area, String msg) { write("I", area, msg, null); }
    public static void w(String area, String msg) { write("W", area, msg, null); }
    public static void e(String area, String msg, Throwable t) { write("E", area, msg, t); }

    private static synchronized void write(String level, String area, String msg, Throwable t) {
        String tag = ROOT + "/" + area;
        String line = FMT.format(new Date()) + " " + level + " " + tag + " " + msg;
        if ("E".equals(level)) Log.e(tag, msg, t); else if ("W".equals(level)) Log.w(tag, msg); else Log.d(tag, msg);
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IceCamLogs");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "icecam_app.log");
            FileWriter fw = new FileWriter(out, true);
            fw.write(line + "\n");
            if (t != null) fw.write(Log.getStackTraceString(t) + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }
}
