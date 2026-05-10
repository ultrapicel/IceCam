package com.icecam.dev;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;

/**
 * v9.4.6 read-only IPC bridge for LSPosed target processes.
 *
 * Target apps must not read /data/adb/icecam directly: SELinux blocks
 * untrusted_app/isolated_app domains on Android 12-15. This provider exposes
 * only non-sensitive state/config metadata through normal Android IPC.
 */
public class IceCamStateProvider extends ContentProvider {
    public static final String AUTHORITY = "com.icecam.dev.provider";
    public static final Uri CONFIG_URI = Uri.parse("content://" + AUTHORITY + "/config");
    public static final Uri STATE_URI = Uri.parse("content://" + AUTHORITY + "/state");
    public static final Uri MEDIA_META_URI = Uri.parse("content://" + AUTHORITY + "/media-meta");
    private static final String VERSION = "v9.4.6-provider-cache-cleanup";

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String path = uri == null ? "" : String.valueOf(uri.getPath());
        SharedPreferences p = getContext().getSharedPreferences("icecam_settings", android.content.Context.MODE_PRIVATE);
        String payload;
        if ("/state".equals(path)) payload = stateJson(p);
        else if ("/media-meta".equals(path)) payload = mediaMetaJson(p);
        else payload = configJson(p);
        MatrixCursor c = new MatrixCursor(new String[]{"kind", "json"});
        String kind = path == null || path.length() <= 1 ? "config" : path.substring(1);
        c.addRow(new Object[]{kind, payload});
        android.util.Log.i("IceCam/Provider", "ProviderBridgeJson kind=" + kind + " json=" + payload);
        return c;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.com.icecam.dev.state"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private static String configJson(SharedPreferences p) {
        return "{"
                + q("version") + ":" + q(VERSION) + ","
                + q("active") + ":" + p.getBoolean("replacementActive", false) + ","
                + q("mode") + ":" + q(p.getString("mode", "log-only")) + ","
                + q("cameraMode") + ":" + q(p.getString("cameraMode", "auto")) + ","
                + q("compatibilityMode") + ":" + q(p.getString("compatibilityMode", "strict-real")) + ","
                + q("mediaType") + ":" + q(p.getString("mediaType", "none")) + ","
                + q("mediaReady") + ":" + (p.getString("mediaUri", "").length() > 0) + ","
                + q("mediaUri") + ":" + q(p.getString("mediaUri", "")) + ","
                + q("mediaPath") + ":" + q("/data/adb/icecam/media/source") + ","
                + q("mediaMetaPath") + ":" + q("/data/adb/icecam/media/source.meta.json") + ","
                + q("pipelineStage") + ":" + q("provider-cache-renderer-sandbox-passive") + ","
                + q("loop") + ":" + p.getBoolean("loop", true) + ","
                + q("mirror") + ":" + p.getBoolean("mirror", false) + ","
                + q("zoom") + ":" + p.getFloat("zoom", 1.0f) + ","
                + q("rotation") + ":" + p.getInt("rotation", 0)
                + "}";
    }

    private static String stateJson(SharedPreferences p) {
        return "{"
                + q("version") + ":" + q(VERSION) + ","
                + q("active") + ":" + p.getBoolean("replacementActive", false) + ","
                + q("providerBridge") + ":" + q("ok") + ","
                + q("sdk") + ":" + Build.VERSION.SDK_INT + ","
                + q("manufacturer") + ":" + q(Build.MANUFACTURER) + ","
                + q("model") + ":" + q(Build.MODEL)
                + "}";
    }

    private static String mediaMetaJson(SharedPreferences p) {
        return "{"
                + q("version") + ":" + q(VERSION) + ","
                + q("mediaType") + ":" + q(p.getString("mediaType", "none")) + ","
                + q("sourceUri") + ":" + q(p.getString("mediaUri", "")) + ","
                + q("mediaReady") + ":" + (p.getString("mediaUri", "").length() > 0) + ","
                + q("bytes") + ":" + p.getLong("mediaBytes", 0L) + ","
                + q("loop") + ":" + p.getBoolean("loop", true) + ","
                + q("mirror") + ":" + p.getBoolean("mirror", false) + ","
                + q("zoom") + ":" + p.getFloat("zoom", 1.0f) + ","
                + q("rotation") + ":" + p.getInt("rotation", 0) + ","
                + q("fitMode") + ":" + q("fill-center-crop")
                + "}";
    }

    private static String q(String s) { return "\"" + esc(s) + "\""; }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
