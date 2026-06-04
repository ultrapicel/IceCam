package dev.icecam.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class RootBootstrap {

    public static final String FIXED_SERVICE_NAME = "icecam_service_v2";

    private final Context ctx;
    private final AppLogger log;

    public RootBootstrap(Context context, AppLogger logger) {
        this.ctx = context.getApplicationContext();
        this.log = logger;
    }

    public String getServiceName() {
        SharedPreferences prefs = ctx.getSharedPreferences("icecam_config", Context.MODE_PRIVATE);
        return prefs.getString("service_name", FIXED_SERVICE_NAME);
    }

    public String bootstrap() {
        log.log("RootBootstrap", "Starting bootstrap...");

        NativeExtractor.Result extraction = NativeExtractor.extract(ctx, log);
        if (!extraction.ok) {
            log.log("RootBootstrap", "Native extraction failed:\n" + extraction.log);
            return "EXTRACTION_FAILED";
        }

        String script = buildBootstrapScript(extraction);
        Shell.Result result = Shell.su(script);

        log.logBlock("RootBootstrap", result.all());
        return result.all();
    }

    private String buildBootstrapScript(NativeExtractor.Result ex) {
        String src = ex.dir.getAbsolutePath();
        String server = getServiceName();

        return "set -x\n" +
                "SRC=" + Shell.q(src) + "\n" +
                "SERVER=" + Shell.q(server) + "\n" +
                "echo \"=== IceCam v2.0 Bootstrap ===\"\n" +
                "id\n" +
                "getenforce 2>/dev/null || true\n" +
                "setenforce 0 2>/dev/null || true\n" +
                "killall vcplax 2>/dev/null || true\n" +
                "rm -rf /data/icecam /data/camera\n" +
                "mkdir -p /data/icecam /data/local/tmp/icecam\n" +
                "cp -f $SRC/libvc.so /data/icecam/libvc.so\n" +
                "cp -f $SRC/libshadowhook.so /data/icecam/libshadowhook.so\n" +
                "cp -f $SRC/vcplax.so /data/icecam/vcplax\n" +
                "chmod 755 /data/icecam/*\n" +
                "export LD_LIBRARY_PATH=/data/icecam:$LD_LIBRARY_PATH\n" +
                "export ICECAM_SERVER=$SERVER\n" +
                "nohup /data/icecam/vcplax $SERVER > /data/icecam/vcplax.log 2>&1 &\n" +
                "echo spawned\n" +
                "sleep 2\n" +
                "service check $SERVER 2>&1 || true\n";
    }

    public String getStatus() {
        String script = "echo \"=== IceCam Status ===\"\n" +
                "ps -A | grep -E 'vcplax|icecam' || true\n" +
                "service check " + getServiceName() + " 2>&1 || true\n" +
                "ls -l /data/icecam 2>/dev/null || true\n";
        return Shell.su(script).all();
    }
}