
package com.icecam.dev;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.hardware.camera2.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Base64;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 201;
    private static final int PICK_VIDEO = 202;
    private final String ctl = "/data/adb/icecam/bin/icecamctl";

    private LinearLayout main;
    private TextView status, output;
    private ImageView imagePreview;
    private VideoView videoPreview;

    private String tab = "Dashboard";
    private String mode = "log-only";
    private String cameraMode = "auto";
    private String mediaType = "none";
    private boolean mirror = false;
    private boolean loop = true;
    private boolean replacementActive = false;
    private float scale = 1f;
    private float rotation = 0f;
    private Uri mediaUri = null;

    private final int BG=0xff05090d, CARD=0xff101820, CARD2=0xff162231, TXT=0xffeef4ff, MUTED=0xffaebbd0, ACCENT=0xff8ab4f8;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 23) {
            try { requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE}, 10); } catch(Throwable ignored){}
        }
        render();
        writeAppConfig();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(18), dp(14), dp(10));
        setContentView(root);

        root.addView(tv("IceCam", 38, TXT));
        root.addView(tv("v6.1 dev · media preview + replacement state", 16, MUTED));
        status = card("Status: " + (replacementActive ? "replacement active" : "idle"));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        hsv.addView(tabs);
        root.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
        addTab(tabs, "Dashboard"); addTab(tabs, "Root"); addTab(tabs, "Media"); addTab(tabs, "Hooks"); addTab(tabs, "Logs"); addTab(tabs, "Diagnostics");

        ScrollView sv = new ScrollView(this);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        sv.addView(main);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        drawTab();
    }

    private void addTab(LinearLayout parent, String name) {
        Button b = new Button(this);
        b.setText(name); b.setTextSize(13); b.setAllCaps(false);
        b.setTextColor(name.equals(tab) ? Color.BLACK : TXT);
        b.setBackgroundColor(name.equals(tab) ? ACCENT : CARD2);
        b.setOnClickListener(v -> { tab = name; render(); });
        parent.addView(b, new LinearLayout.LayoutParams(dp(116), dp(48)));
    }

    private void drawTab() {
        main.removeAllViews();
        if ("Dashboard".equals(tab)) drawDashboard();
        else if ("Root".equals(tab)) drawRoot();
        else if ("Media".equals(tab)) drawMedia();
        else if ("Hooks".equals(tab)) drawHooks();
        else if ("Logs".equals(tab)) drawLogs();
        else drawDiagnostics();
    }

    private void drawDashboard() {
        main.addView(section("Overview"));
        main.addView(info("Replacement", replacementActive ? "ACTIVE" : "STOPPED"));
        main.addView(info("Mode", mode));
        main.addView(info("Camera profile", cameraMode));
        main.addView(info("Media type", mediaType));
        main.addView(info("Media URI", mediaUri == null ? "none" : mediaUri.toString()));
        addButton(main, "Start Replacement", v -> startReplacement());
        addButton(main, "Stop Replacement", v -> stopReplacement());
        addButton(main, "Write Config + Prepare Hooks", v -> { writeAppConfig(); runCtl("prepare-hooks"); });
        addButton(main, "Export Debug Bundle", v -> runCtl("logs"));
    }

    private void drawRoot() {
        main.addView(section("Root & Module"));
        addButton(main, "Request Root Check", v -> runCtl("status"));
        addButton(main, "Prepare Hook Layer", v -> { writeAppConfig(); runCtl("prepare-hooks"); });
        addButton(main, "Clear Logs", v -> runCtl("clear-logs"));
        addButton(main, "Export Debug Bundle", v -> runCtl("logs"));
        main.addView(info("Control path", ctl));
        main.addView(info("Module path", "/data/adb/icecam"));
    }

    private void drawMedia() {
        main.addView(section("Media Source"));
        drawPreview();
        LinearLayout pick = row();
        addSmall(pick, "Select Photo", v -> pick("image/*", PICK_IMAGE));
        addSmall(pick, "Select Video", v -> pick("video/*", PICK_VIDEO));
        main.addView(pick);

        LinearLayout action = row();
        addSmall(action, "Start", v -> startReplacement());
        addSmall(action, "Stop", v -> stopReplacement());
        addSmall(action, loop ? "Loop ON" : "Loop OFF", v -> { loop=!loop; writeAppConfig(); render(); });
        main.addView(action);

        LinearLayout row1=row();
        addSmall(row1,"Mirror",v->{mirror=!mirror;applyTransform();writeAppConfig();});
        addSmall(row1,"Zoom +",v->{scale+=0.1f;applyTransform();writeAppConfig();});
        addSmall(row1,"Zoom -",v->{scale=Math.max(0.2f,scale-0.1f);applyTransform();writeAppConfig();});
        main.addView(row1);

        LinearLayout row2=row();
        addSmall(row2,"Rotate",v->{rotation+=90f;applyTransform();writeAppConfig();});
        addSmall(row2,"Reset",v->{mirror=false;scale=1f;rotation=0f;applyTransform();writeAppConfig();});
        main.addView(row2);

        main.addView(info("Selected media", mediaUri == null ? "none" : mediaUri.toString()));
        main.addView(info("Working copy", "/data/adb/icecam/media/source"));
        addButton(main, "Export Debug Bundle", v -> runCtl("logs"));
    }

    private void drawPreview() {
        if ("video".equals(mediaType)) {
            videoPreview = new VideoView(this);
            videoPreview.setBackgroundColor(0xff020408);
            if (mediaUri != null) {
                try {
                    videoPreview.setVideoURI(mediaUri);
                    videoPreview.setOnPreparedListener(mp -> { mp.setLooping(loop); videoPreview.start(); });
                } catch(Throwable t) { status.setText("Video preview failed: " + t); }
            }
            main.addView(videoPreview, new LinearLayout.LayoutParams(-1, dp(300)));
        } else {
            imagePreview = new ImageView(this);
            imagePreview.setBackgroundColor(0xff020408);
            imagePreview.setMinimumHeight(dp(260));
            imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (mediaUri != null) { try { imagePreview.setImageURI(mediaUri); } catch(Throwable ignored){} applyTransform(); }
            main.addView(imagePreview, new LinearLayout.LayoutParams(-1, dp(300)));
        }
    }

    private void drawHooks() {
        main.addView(section("Hook Layer"));
        main.addView(info("Replacement active", String.valueOf(replacementActive)));
        main.addView(info("Current mode", mode));
        LinearLayout modes=row();
        addSmall(modes,"Log",v->{mode="log-only";writeAppConfig();render();});
        addSmall(modes,"Block",v->{mode="block-open-test";writeAppConfig();render();});
        addSmall(modes,"Stub",v->{mode="virtual-stub";writeAppConfig();render();});
        main.addView(modes);

        main.addView(info("Camera target", cameraMode));
        LinearLayout cams=row();
        addSmall(cams,"Auto",v->{cameraMode="auto";writeAppConfig();render();});
        addSmall(cams,"Back",v->{cameraMode="back";writeAppConfig();render();});
        addSmall(cams,"Front",v->{cameraMode="front";writeAppConfig();render();});
        main.addView(cams);

        addButton(main, "Write Config + Prepare Hooks", v -> { writeAppConfig(); runCtl("prepare-hooks"); });
        addButton(main, "Show Hook Log", v -> showFile("/data/adb/icecam/logs/hook.log"));
    }

    private void drawLogs() {
        main.addView(section("Logs"));
        addButton(main, "Show Hook Log", v -> showFile("/data/adb/icecam/logs/hook.log"));
        addButton(main, "Show Module Log", v -> showFile("/data/adb/icecam/logs/module.log"));
        addButton(main, "Export Debug Bundle", v -> runCtl("logs"));
        output = card("Log output will appear here.");
        main.addView(output, new LinearLayout.LayoutParams(-1, -2));
    }

    private void drawDiagnostics() {
        main.addView(section("Diagnostics"));
        main.addView(info("Package", getPackageName()));
        main.addView(info("Device", Build.DEVICE + " sdk=" + Build.VERSION.SDK_INT + " abi=" + Build.SUPPORTED_ABIS[0]));
        main.addView(info("State files", "/data/adb/icecam/state/active\n/data/adb/icecam/config/app_config.json"));
        dumpCameras();
        addButton(main, "Refresh Diagnostics", v -> { tab="Diagnostics"; render(); });
    }

    private void pick(String mime, int code) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        startActivityForResult(i, code);
    }

    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if ((r==PICK_IMAGE || r==PICK_VIDEO) && c==RESULT_OK && data!=null) {
            mediaUri=data.getData();
            mediaType = r==PICK_VIDEO ? "video" : "image";
            try { getContentResolver().takePersistableUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Throwable ignored){}
            writeAppConfig();
            copyMediaToWorkingPath();
            status.setText("Media selected:\n" + mediaUri);
            tab="Media"; render();
        }
    }

    private void startReplacement() {
        if (mediaUri == null) {
            status.setText("Select photo/video first.");
            return;
        }
        replacementActive = true;
        writeAppConfig();
        copyMediaToWorkingPath();
        runCtl("start");
    }

    private void stopReplacement() {
        replacementActive = false;
        writeAppConfig();
        runCtl("stop");
    }

    private void copyMediaToWorkingPath() {
        if (mediaUri == null) return;
        new Thread(() -> {
            String ext = "video".equals(mediaType) ? ".mp4" : ".img";
            File tmp = new File(getCacheDir(), "icecam_source" + ext);
            try {
                InputStream in = getContentResolver().openInputStream(mediaUri);
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[1024 * 256];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close(); out.close();
                execRoot("mkdir -p /data/adb/icecam/media && cp " + tmp.getAbsolutePath() + " /data/adb/icecam/media/source && chmod 666 /data/adb/icecam/media/source");
            } catch(Throwable e) {
                runOnUiThread(() -> status.setText("Media copy failed: " + e));
            }
        }).start();
    }

    private void applyTransform(){
        if(imagePreview!=null){ imagePreview.setScaleX(scale*(mirror?-1f:1f)); imagePreview.setScaleY(scale); imagePreview.setRotation(rotation); }
        if(videoPreview!=null){ videoPreview.setScaleX(scale*(mirror?-1f:1f)); videoPreview.setScaleY(scale); videoPreview.setRotation(rotation); }
    }

    private void writeAppConfig(){
        String media=mediaUri==null?"":mediaUri.toString();
        String json="{\"enabled\":true,\"version\":\"0.6.1-dev\",\"active\":"+replacementActive+",\"mode\":\""+mode+"\",\"cameraMode\":\""+cameraMode+"\",\"mediaType\":\""+mediaType+"\",\"mediaUri\":\""+esc(media)+"\",\"mediaPath\":\"/data/adb/icecam/media/source\",\"mirror\":"+mirror+",\"loop\":"+loop+",\"scale\":"+scale+",\"rotation\":"+rotation+"}";
        String encoded=Base64.getEncoder().encodeToString(json.getBytes());
        execRoot("mkdir -p /data/adb/icecam/config /data/adb/icecam/state && echo "+encoded+" | base64 -d > /data/adb/icecam/config/app_config.json && echo "+(replacementActive?"1":"0")+" > /data/adb/icecam/state/active && chmod 666 /data/adb/icecam/config/app_config.json /data/adb/icecam/state/active");
    }

    private String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

    private void runCtl(String arg){ new Thread(()->{String result=execRoot(ctl+" "+arg); runOnUiThread(()->{status.setText(result); if(output!=null)output.setText(result);});}).start(); }
    private void showFile(String path){ new Thread(()->{String result=execRoot("cat "+path); runOnUiThread(()->{status.setText("Loaded: "+path); if(output!=null)output.setText(result); else {tab="Logs"; render(); if(output!=null)output.setText(result);}});}).start(); }
    private String execRoot(String cmd){
        StringBuilder out=new StringBuilder(); int code=-999;
        try{
            java.lang.Process p=Runtime.getRuntime().exec(new String[]{"su","-c",cmd});
            StreamGobbler so=new StreamGobbler(p.getInputStream()); StreamGobbler se=new StreamGobbler(p.getErrorStream());
            so.start(); se.start(); code=p.waitFor(); so.join(); se.join();
            out.append(so.data); if(se.data.length()>0)out.append("\nstderr:\n").append(se.data);
        }catch(Throwable e){ out.append(e.toString());}
        out.append("\nexit=").append(code); return out.toString();
    }

    private void dumpCameras(){
        try{
            CameraManager cm=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
            for(String id:cm.getCameraIdList()){
                CameraCharacteristics cc=cm.getCameraCharacteristics(id);
                main.addView(info("Camera "+id, "facing="+cc.get(CameraCharacteristics.LENS_FACING)+" orientation="+cc.get(CameraCharacteristics.SENSOR_ORIENTATION)+" hwLevel="+cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));
            }
        }catch(Throwable t){main.addView(info("Camera dump failed",String.valueOf(t)));}
    }

    private TextView section(String s){ TextView t=tv(s,24,TXT); t.setPadding(0,dp(18),0,dp(8)); return t; }
    private TextView info(String k,String v){ TextView t=card(k+"\n"+v); t.setTextSize(16); return t; }
    private TextView tv(String s,int sp,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(0,dp(5),0,dp(5)); return t; }
    private TextView card(String s){ TextView t=tv(s,16,TXT); t.setPadding(dp(14),dp(12),dp(14),dp(12)); t.setBackgroundColor(CARD); return t; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,dp(4),0,dp(4)); return l; }
    private void addButton(LinearLayout p,String label,View.OnClickListener l){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setOnClickListener(l); p.addView(b,new LinearLayout.LayoutParams(-1,dp(54))); }
    private void addSmall(LinearLayout p,String label,View.OnClickListener l){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setOnClickListener(l); p.addView(b,new LinearLayout.LayoutParams(0,dp(50),1)); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    static class StreamGobbler extends Thread{
        private final InputStream is; String data="";
        StreamGobbler(InputStream is){this.is=is;}
        public void run(){try{ByteArrayOutputStream bos=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))>0)bos.write(buf,0,n); data=bos.toString();}catch(Throwable ignored){}}
    }
}
