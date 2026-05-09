
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
    private static final int PICK_MEDIA = 200;
    private final String ctl = "/data/adb/icecam/bin/icecamctl";
    private LinearLayout main;
    private TextView status, output;
    private ImageView preview;
    private String tab = "Dashboard", mode = "log-only", cameraMode = "auto";
    private boolean mirror = false;
    private float scale = 1f, rotation = 0f;
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
        root.addView(tv("v5.1 dev · system camera replacement", 16, MUTED));
        status = card("Status: ready");
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
        b.setText(name);
        b.setTextSize(13);
        b.setAllCaps(false);
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
        main.addView(info("Mode", mode));
        main.addView(info("Camera profile", cameraMode));
        main.addView(info("Media", mediaUri == null ? "none" : mediaUri.toString()));
        main.addView(info("Transform", "mirror=" + mirror + " scale=" + scale + " rotation=" + rotation));
        addButton(main, "Write Config + Prepare Hooks", v -> { writeAppConfig(); runCtl("prepare-hooks"); });
        addButton(main, "Export Debug Bundle", v -> runCtl("logs"));
    }

    private void drawRoot() {
        main.addView(section("Root & Module"));
        addButton(main, "Request Root Check", v -> runCtl("status"));
        addButton(main, "Prepare Hook Layer", v -> { writeAppConfig(); runCtl("prepare-hooks"); });
        addButton(main, "Clear Logs", v -> runCtl("clear-logs"));
        main.addView(info("Control path", ctl));
        main.addView(info("Module path", "/data/adb/icecam"));
    }

    private void drawMedia() {
        main.addView(section("Media Source"));
        preview = new ImageView(this);
        preview.setBackgroundColor(0xff020408);
        preview.setMinimumHeight(dp(260));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (mediaUri != null) { try { preview.setImageURI(mediaUri); } catch(Throwable ignored){} applyTransform(); }
        main.addView(preview, new LinearLayout.LayoutParams(-1, dp(300)));
        addButton(main, "Select Photo / Video", v -> pickMedia());
        LinearLayout row1=row(); addSmall(row1,"Mirror",v->{mirror=!mirror;applyTransform();writeAppConfig();}); addSmall(row1,"Zoom +",v->{scale+=0.1f;applyTransform();writeAppConfig();}); addSmall(row1,"Zoom -",v->{scale=Math.max(0.2f,scale-0.1f);applyTransform();writeAppConfig();}); main.addView(row1);
        LinearLayout row2=row(); addSmall(row2,"Rotate",v->{rotation+=90f;applyTransform();writeAppConfig();}); addSmall(row2,"Reset",v->{mirror=false;scale=1f;rotation=0f;applyTransform();writeAppConfig();}); main.addView(row2);
        main.addView(info("Selected media", mediaUri == null ? "none" : mediaUri.toString()));
    }

    private void drawHooks() {
        main.addView(section("Hook Layer"));
        main.addView(info("Current mode", mode));
        LinearLayout modes=row(); addSmall(modes,"Log",v->{mode="log-only";writeAppConfig();render();}); addSmall(modes,"Block",v->{mode="block-open-test";writeAppConfig();render();}); addSmall(modes,"Stub",v->{mode="virtual-stub";writeAppConfig();render();}); main.addView(modes);
        main.addView(info("Camera target", cameraMode));
        LinearLayout cams=row(); addSmall(cams,"Auto",v->{cameraMode="auto";writeAppConfig();render();}); addSmall(cams,"Back",v->{cameraMode="back";writeAppConfig();render();}); addSmall(cams,"Front",v->{cameraMode="front";writeAppConfig();render();}); main.addView(cams);
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
        try {
            CameraManager cm=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
            for(String id:cm.getCameraIdList()){
                CameraCharacteristics cc=cm.getCameraCharacteristics(id);
                main.addView(info("Camera "+id, "facing="+cc.get(CameraCharacteristics.LENS_FACING)+" orientation="+cc.get(CameraCharacteristics.SENSOR_ORIENTATION)+" hwLevel="+cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));
            }
        } catch(Throwable t){ main.addView(info("Camera dump failed", String.valueOf(t))); }
        addButton(main, "Refresh Diagnostics", v -> { tab="Diagnostics"; render(); });
    }

    private TextView section(String s){ TextView t=tv(s,24,TXT); t.setPadding(0,dp(18),0,dp(8)); return t; }
    private TextView info(String k,String v){ TextView t=card(k+"\n"+v); t.setTextSize(16); return t; }
    private TextView tv(String s,int sp,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(0,dp(5),0,dp(5)); return t; }
    private TextView card(String s){ TextView t=tv(s,16,TXT); t.setPadding(dp(14),dp(12),dp(14),dp(12)); t.setBackgroundColor(CARD); return t; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,dp(4),0,dp(4)); return l; }
    private void addButton(LinearLayout p,String label,View.OnClickListener l){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setOnClickListener(l); p.addView(b,new LinearLayout.LayoutParams(-1,dp(54))); }
    private void addSmall(LinearLayout p,String label,View.OnClickListener l){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setOnClickListener(l); p.addView(b,new LinearLayout.LayoutParams(0,dp(50),1)); }

    private void pickMedia(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}); startActivityForResult(i,PICK_MEDIA); }
    @Override protected void onActivityResult(int r,int c,Intent data){ super.onActivityResult(r,c,data); if(r==PICK_MEDIA&&c==RESULT_OK&&data!=null){ mediaUri=data.getData(); try{getContentResolver().takePersistableUriPermission(mediaUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){} writeAppConfig(); status.setText("Media selected:\n"+mediaUri); tab="Media"; render(); } }
    private void applyTransform(){ if(preview==null)return; preview.setScaleX(scale*(mirror?-1f:1f)); preview.setScaleY(scale); preview.setRotation(rotation); }
    private void writeAppConfig(){ String media=mediaUri==null?"":mediaUri.toString(); String json="{\"enabled\":true,\"version\":\"0.5.1-dev\",\"mode\":\""+mode+"\",\"cameraMode\":\""+cameraMode+"\",\"mediaUri\":\""+esc(media)+"\",\"mirror\":"+mirror+",\"scale\":"+scale+",\"rotation\":"+rotation+"}"; String encoded=Base64.getEncoder().encodeToString(json.getBytes()); execRoot("mkdir -p /data/adb/icecam/config && echo "+encoded+" | base64 -d > /data/adb/icecam/config/app_config.json && chmod 666 /data/adb/icecam/config/app_config.json"); }
    private String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
    private void runCtl(String arg){ new Thread(()->{String result=execRoot(ctl+" "+arg); runOnUiThread(()->{status.setText(result); if(output!=null)output.setText(result);});}).start(); }
    private void showFile(String path){ new Thread(()->{String result=execRoot("cat "+path); runOnUiThread(()->{status.setText("Loaded: "+path); if(output!=null)output.setText(result); else {tab="Logs"; render(); if(output!=null)output.setText(result);}});}).start(); }
    private String execRoot(String cmd){ StringBuilder out=new StringBuilder(); int code=-999; try{ java.lang.Process p=Runtime.getRuntime().exec(new String[]{"su","-c",cmd}); StreamGobbler so=new StreamGobbler(p.getInputStream()); StreamGobbler se=new StreamGobbler(p.getErrorStream()); so.start(); se.start(); code=p.waitFor(); so.join(); se.join(); out.append(so.data); if(se.data.length()>0)out.append("\nstderr:\n").append(se.data);}catch(Throwable e){out.append(e.toString());} out.append("\nexit=").append(code); return out.toString(); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    static class StreamGobbler extends Thread { private final InputStream is; String data=""; StreamGobbler(InputStream is){this.is=is;} public void run(){ try{ByteArrayOutputStream bos=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))>0)bos.write(buf,0,n); data=bos.toString();}catch(Throwable ignored){} } }
}
