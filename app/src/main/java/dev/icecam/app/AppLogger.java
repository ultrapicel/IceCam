package dev.icecam.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLogger {
    public interface Listener { void onLogChanged(String text); }
    private static final int MAX = 48000;
    private final StringBuilder buffer = new StringBuilder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File file;
    private Listener listener;

    public AppLogger(Context ctx) {
        file = new File(ctx.getExternalFilesDir(null), "icecam-runtime.log");
        log("logger", "file=" + file.getAbsolutePath());
    }

    public void setListener(Listener l) { listener = l; if (l != null) l.onLogChanged(buffer.toString()); }
    public File file() { return file; }
    public String text() { return buffer.toString(); }

    public void log(String tag, String msg) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + " [" + tag + "] " + String.valueOf(msg).replace('\r', ' ') + "\n";
        synchronized (buffer) {
            buffer.insert(0, line);
            if (buffer.length() > MAX) buffer.setLength(MAX);
            try (FileOutputStream out = new FileOutputStream(file, true)) { out.write(line.getBytes("UTF-8")); } catch (Throwable ignored) {}
        }
        main.post(() -> { if (listener != null) listener.onLogChanged(buffer.toString()); });
    }

    public void logBlock(String tag, String block) {
        if (block == null || block.length() == 0) { log(tag, "<empty>"); return; }
        String[] lines = block.split("\\n");
        for (String l : lines) if (l.trim().length() > 0) log(tag, l);
    }
}
