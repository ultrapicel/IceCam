package dev.icecam.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public final class RootBootstrap {

    private static final String TAG = "RootBootstrap";
    public static final String FIXED_SERVICE_NAME = "icecam_service_v2";

    private final Context ctx;

    public RootBootstrap(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public String getServiceName() {
        SharedPreferences prefs = ctx.getSharedPreferences("icecam_config", Context.MODE_PRIVATE);
        return prefs.getString("service_name", FIXED_SERVICE_NAME);
    }

    public String bootstrap() {
        Log.i(TAG, "Starting bootstrap...");

        NativeExtractor.Result extraction = NativeExtractor.extract(ctx);
        if (!extraction.ok) {
            Log.e(TAG, "Native extraction failed:\n" + extraction.log);
            return "EXTRACTION_FAILED";
        }

        // TODO: Implement actual root script execution here
        Log.i(TAG, "Bootstrap script would run here (root required)");
        return "BOOTSTRAP_PLACEHOLDER";
    }

    public String getStatus() {
        // TODO: Implement status check
        return "STATUS_CHECK_NOT_IMPLEMENTED";
    }
}