package dev.icecam.app;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NativeExtractor {

    public static final class Result {
        public final String abi;
        public final File dir;
        public final boolean ok;
        public final String log;

        Result(String abi, File dir, boolean ok, String log) {
            this.abi = abi;
            this.dir = dir;
            this.ok = ok;
            this.log = log;
        }
    }

    public static Result extract(Context ctx, AppLogger logger) {
        StringBuilder sb = new StringBuilder();
        String abi = selectBestAbi();
        File outDir = new File(ctx.getFilesDir(), "native-" + abi);
        boolean ok = true;

        try {
            if (!outDir.exists() && !outDir.mkdirs()) {
                sb.append("Failed to create directory: ").append(outDir).append("\n");
            }

            ZipFile zip = new ZipFile(ctx.getApplicationInfo().sourceDir);

            String[] libs = {"libvc.so", "libshadowhook.so", "vcplax.so"};
            for (String lib : libs) {
                String entryName = "lib/" + abi + "/" + lib;
                ZipEntry entry = zip.getEntry(entryName);

                if (entry == null) {
                    ok = false;
                    sb.append("Missing: ").append(entryName).append("\n");
                    continue;
                }

                File dst = new File(outDir, lib);
                copyStream(zip.getInputStream(entry), dst);
                sb.append("Extracted: ").append(entryName)
                  .append(" -> ").append(dst.getAbsolutePath())
                  .append(" (").append(dst.length()).append(" bytes)\n");
            }

            zip.close();
        } catch (Exception e) {
            ok = false;
            sb.append("Extraction error: ").append(e.getMessage()).append("\n");
        }

        Result result = new Result(abi, outDir, ok, sb.toString());
        if (logger != null) {
            logger.log("NativeExtractor", result.log);
        }
        return result;
    }

    private static void copyStream(InputStream in, File dst) throws Exception {
        try (InputStream input = in;
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[128 * 1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private static String selectBestAbi() {
        // Prefer arm64-v8a if available
        try {
            Shell.Result result = Shell.sh("getprop ro.product.cpu.abi");
            String abi = result.out.trim().toLowerCase();
            if (abi.contains("arm64")) {
                return "arm64-v8a";
            }
        } catch (Exception ignored) {}

        // Fallback: check cameraserver
        try {
            Shell.Result result = Shell.sh("file /system/bin/cameraserver");
            if (result.out.toLowerCase().contains("32-bit")) {
                return "armeabi-v7a";
            }
        } catch (Exception ignored) {}

        return "arm64-v8a"; // Default
    }
}