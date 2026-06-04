package dev.icecam.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NativeExtractor {

    private static final String TAG = "NativeExtractor";

    public static final class Result {
        public final String abi;
        public final File dir;
        public final boolean ok;
        public final String log;

        public Result(String abi, File dir, boolean ok, String log) {
            this.abi = abi;
            this.dir = dir;
            this.ok = ok;
            this.log = log;
        }
    }

    public static Result extract(Context ctx) {
        StringBuilder sb = new StringBuilder();
        String abi = "arm64-v8a"; // Default, can be improved later
        File outDir = new File(ctx.getFilesDir(), "native-" + abi);
        boolean ok = true;

        try {
            if (!outDir.exists() && !outDir.mkdirs()) {
                sb.append("Failed to create directory\n");
            }

            ZipFile zip = new ZipFile(ctx.getApplicationInfo().sourceDir);

            String[] libs = {"libvc.so", "libshadowhook.so", "vcplax.so"};
            for (String lib : libs) {
                String entryName = "lib/" + abi + "/" + lib;
                ZipEntry entry = zip.getEntry(entryName);

                if (entry == null) {
                    ok = false;
                    sb.append("Missing library: ").append(entryName).append("\n");
                    continue;
                }

                File dst = new File(outDir, lib);
                copyStream(zip.getInputStream(entry), dst);
                sb.append("Extracted: ").append(lib).append("\n");
            }

            zip.close();
        } catch (Exception e) {
            ok = false;
            sb.append("Error: ").append(e.getMessage()).append("\n");
            Log.e(TAG, "Extraction failed", e);
        }

        return new Result(abi, outDir, ok, sb.toString());
    }

    private static void copyStream(InputStream in, File dst) throws Exception {
        try (InputStream input = in; FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = input.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }
}