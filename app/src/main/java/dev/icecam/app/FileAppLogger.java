package dev.icecam.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileAppLogger {

    private static final String LOG_FILE_NAME = "icecam_v2_log.txt";
    private static File logFile;
    private static boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;

        try {
            File logDir = new File(context.getFilesDir(), "logs");
            if (!logDir.exists() && !logDir.mkdirs()) {
                android.util.Log.e("IceCamLogger", "Failed to create log directory");
                return;
            }

            logFile = new File(logDir, LOG_FILE_NAME);
            initialized = true;

            log("SYSTEM", "=== IceCam v2.0 logging started ===");
            log("SYSTEM", "Log file: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("IceCamLogger", "Init error: " + e.getMessage());
        }
    }

    public static synchronized void log(String tag, String message) {
        if (!initialized || logFile == null) {
            android.util.Log.i("IceCam_" + tag, message);
            return;
        }

        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            writer.append(String.format("[%s] [%s] %s\n", timestamp, tag, message));
        } catch (IOException e) {
            android.util.Log.e("IceCamLogger", "Write error: " + e.getMessage());
        }

        // Also print to logcat for convenience
        android.util.Log.i("IceCam_" + tag, message);
    }

    public static File getLogFile() {
        return logFile;
    }

    public static void shareLog(Context context) {
        if (logFile == null || !logFile.exists()) {
            log("SYSTEM", "No log file found to share");
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    logFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(shareIntent, "Share IceCam Logs"));
        } catch (Exception e) {
            log("SYSTEM", "Error sharing log: " + e.getMessage());
        }
    }

    public static void clearLogs() {
        if (logFile != null && logFile.exists()) {
            logFile.delete();
            initialized = false;
        }
    }
}