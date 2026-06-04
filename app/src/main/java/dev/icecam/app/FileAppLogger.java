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

    public static void init(Context context) {
        if (initialized) return;

        File logDir = new File(context.getFilesDir(), "logs");
        if (!logDir.exists()) logDir.mkdirs();

        logFile = new File(logDir, LOG_FILE_NAME);
        initialized = true;

        log("SYSTEM", "IceCam v2.0 logging initialized. File: " + logFile.getAbsolutePath());
    }

    public static void log(String tag, String message) {
        if (!initialized || logFile == null) return;

        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            writer.append(String.format("[%s] [%s] %s\n", timestamp, tag, message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File getLogFile() {
        return logFile;
    }

    public static void shareLog(Context context) {
        if (logFile == null || !logFile.exists()) {
            log("SYSTEM", "No log file to share");
            return;
        }

        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", logFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Share IceCam Logs"));
    }
}