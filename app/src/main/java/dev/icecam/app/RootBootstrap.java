package dev.icecam.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import java.util.Random;

public final class RootBootstrap {
    private final Context ctx;
    private final AppLogger log;
    public RootBootstrap(Context c, AppLogger logger) { ctx = c.getApplicationContext(); log = logger; }

    public String serverName() {
        SharedPreferences p = ctx.getSharedPreferences("app_config", Context.MODE_PRIVATE);
        String s = p.getString("ServerName", "");
        if (s == null || s.trim().isEmpty()) {
            s = makeName();
            p.edit().putString("ServerName", s).apply();
        }
        return s;
    }

    public String resetServerName() {
        String s = makeName();
        ctx.getSharedPreferences("app_config", Context.MODE_PRIVATE).edit().putString("ServerName", s).apply();
        log.log("root", "new ServerName=" + s);
        return s;
    }

    private String makeName() {
        String abc = "abcdefghijklmnopqrstuvwxyz";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(abc.charAt(rnd.nextInt(abc.length())));
        return sb.toString();
    }

    public String bootstrap() {
        NativeExtractor.Result ex = NativeExtractor.extract(ctx, log);
        String server = serverName();
        String src = ex.dir.getAbsolutePath();
        String script = "set -x\n" +
                "SRC=" + Shell.q(src) + "\n" +
                "SERVER=" + Shell.q(server) + "\n" +
                "echo selected_abi=" + Shell.q(ex.abi) + " server=$SERVER src=$SRC\n" +
                "id\n" +
                "getenforce 2>/dev/null || true\n" +
                "setenforce 0 2>/dev/null || true\n" +
                "killall vcplax 2>/dev/null || true\n" +
                "rm -rf /data/camera /data/samera\n" +
                "mkdir -p /data/camera /data/local/tmp/icecam\n" +
                "chattr -i /data/camera 2>/dev/null || true\n" +
                "cp -f $SRC/libvc.so /data/libvc.so\n" +
                "cp -f $SRC/libshadowhook.so /data/libvc++.so\n" +
                "cp -f $SRC/libshadowhook.so /data/camera/libshadowhook.so\n" +
                "cp -f $SRC/libvc.so /data/camera/libvc.so\n" +
                "cp -f $SRC/vcplax.so /data/camera/vcplax\n" +
                "cp -f $SRC/vcplax.so /data/vcplax\n" +
                "chmod 700 /data/vcplax /data/camera/vcplax\n" +
                "chmod 644 /data/libvc.so /data/libvc++.so /data/camera/libvc.so /data/camera/libshadowhook.so\n" +
                "rm -f /data/camera/vcplax.log /data/camera/vcplax.err\n" +
                "export LD_LIBRARY_PATH=/data/camera:/data:/system/lib64:/system_ext/lib64:/vendor/lib64:/system/lib:/system_ext/lib:/vendor/lib:$LD_LIBRARY_PATH\n" +
                "export ICECAM_SERVER=$SERVER\n" +
                "echo ---launch /data/vcplax $SERVER---\n" +
                "nohup /data/vcplax $SERVER >/data/camera/vcplax.log 2>/data/camera/vcplax.err &\n" +
                "echo spawned_pid=$!\n" +
                "sleep 2\n" +
                "echo ---process---\nps -A | grep -i vcplax || ps | grep -i vcplax || true\n" +
                "echo ---expected-service---\nservice check $SERVER 2>&1 || true\n" +
                "echo ---service-list-filtered---\nservice list 2>/dev/null | grep -iE \"$SERVER|vlive|camera|media|ice|vcplax\" || true\n" +
                "echo ---files---\nls -l /data/camera /data/vcplax /data/libvc.so /data/libvc++.so 2>&1\n" +
                "echo ---vcplax.log---\ncat /data/camera/vcplax.log 2>/dev/null || true\n" +
                "echo ---vcplax.err---\ncat /data/camera/vcplax.err 2>/dev/null || true\n" +
                "echo ---selinux-after---\ngetenforce 2>/dev/null || true\n";
        Shell.Result r = Shell.su(script);
        String all = r.all();
        log.logBlock("root", all);
        return all;
    }

    public String status() {
        String server = serverName();
        String script = "SERVER=" + Shell.q(server) + "\n" +
                "id\n" +
                "echo server=$SERVER\n" +
                "echo ---selinux---\ngetenforce 2>/dev/null || true\n" +
                "echo ---process---\nps -A | grep -i vcplax || ps | grep -i vcplax || true\n" +
                "echo ---expected-service---\nservice check $SERVER 2>&1 || true\n" +
                "echo ---service-list-filtered---\nservice list 2>/dev/null | grep -iE \"$SERVER|vlive|camera|media|ice|vcplax\" || true\n" +
                "echo ---files---\nls -l /data/camera /data/vcplax /data/libvc.so /data/libvc++.so 2>&1\n" +
                "echo ---vcplax-log---\ntail -160 /data/camera/vcplax.log 2>/dev/null || true\n" +
                "echo ---vcplax-err---\ntail -160 /data/camera/vcplax.err 2>/dev/null || true\n" +
                "echo ---logcat-native---\nlogcat -d -t 220 2>/dev/null | grep -iE \"icecam|vcplax|vlive|libvc|shadowhook|binder|servicemanager|avc: denied|Parcel\" || true\n";
        Shell.Result r = Shell.su(script);
        log.logBlock("status", r.all());
        return r.all();
    }
}
