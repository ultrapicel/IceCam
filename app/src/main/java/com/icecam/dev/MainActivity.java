
package com.icecam.dev;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_PHOTO = 701;
    private static final int REQ_VIDEO = 702;
    private final String ctl = "/data/adb/icecam/bin/icecamctl";
    private final String ice = "/data/adb/icecam";
    private String tab = "Dashboard", mode = "log-only", cameraMode = "auto", mediaType = "none";
    private boolean replacementActive = false, loop = true, mirror = false;
    private int rotation = 0;
    private float zoom = 1.0f;
    private Uri mediaUri = null;
    private LinearLayout root, tabBar, content;
    private TextView out, status;
    private ImageView imagePreview;
    private VideoView videoPreview;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestRuntimePermissionsOnly();
        renderUi();
    }

    private void requestRuntimePermissionsOnly() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.CAMERA}, 7);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 7);
    }

    private TextView tv(String s, int sp, int style) { TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(226,232,240)); v.setTypeface(null, style); v.setPadding(10,6,10,6); return v; }
    private Button btn(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(30,41,59)); return b; }
    private void addBtn(LinearLayout l, String s, View.OnClickListener c){ Button b=btn(s); b.setOnClickListener(c); l.addView(b, new LinearLayout.LayoutParams(-1,-2)); }
    private void line(String s){ content.addView(tv(s,14,0)); }
    private void show(String s){ out.setText(s); }

    private void renderUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(11,15,20));
        status = tv("IceCam v7-from-scratch", 18, 1); root.addView(status);
        tabBar = new LinearLayout(this); tabBar.setOrientation(LinearLayout.HORIZONTAL); root.addView(tabBar);
        for (String t: new String[]{"Dashboard","Root","Media","Hooks","Logs","Diagnostics"}) { Button b=btn(t); b.setTextSize(11); b.setOnClickListener(v->{tab=t; renderUi();}); tabBar.addView(b, new LinearLayout.LayoutParams(0,-2,1)); }
        ScrollView sv = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(12,6,12,6); sv.addView(content); root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
        out = tv("stdout/stderr/exitCode will appear here", 12, 0); out.setTextColor(Color.rgb(147,197,253)); out.setMovementMethod(new ScrollingMovementMethod()); root.addView(out, new LinearLayout.LayoutParams(-1,220));
        setContentView(root);
        if(tab.equals("Dashboard")) dashboard(); else if(tab.equals("Root")) rootTab(); else if(tab.equals("Media")) mediaTab(); else if(tab.equals("Hooks")) hooksTab(); else if(tab.equals("Logs")) logsTab(); else diagTab();
    }

    private void dashboard(){
        line("Replacement: "+(replacementActive?"ACTIVE":"STOPPED")); line("Hook mode: "+mode); line("Camera profile: "+cameraMode); line("Media type: "+mediaType); line("Media URI: "+String.valueOf(mediaUri));
        addBtn(content,"Start Replacement",v->startReplacement()); addBtn(content,"Stop Replacement",v->stopReplacement()); addBtn(content,"Write Config + Prepare Hooks",v->prepareHooks()); addBtn(content,"Export Debug Bundle",v->exportDebugBundle());
    }
    private void rootTab(){ line("control path: "+ctl); line("module path: "+ice); addBtn(content,"Request Root Check",v->requestRootCheck()); addBtn(content,"Prepare Hook Layer",v->prepareHooks()); addBtn(content,"Clear Logs",v->runCtl("clear-logs")); addBtn(content,"Export Debug Bundle",v->exportDebugBundle()); }
    private void mediaTab(){
        addBtn(content,"Select Photo",v->selectPhoto()); addBtn(content,"Select Video",v->selectVideo()); addBtn(content,"Start Replacement",v->startReplacement()); addBtn(content,"Stop Replacement",v->stopReplacement());
        addBtn(content,"Loop "+(loop?"ON":"OFF"),v->{loop=!loop; renderUi();}); addBtn(content,"Mirror",v->{mirror=!mirror; applyPreviewTransform(); writeAppConfigViaRoot();});
        addBtn(content,"Zoom +",v->{zoom+=0.1f; applyPreviewTransform(); writeAppConfigViaRoot();}); addBtn(content,"Zoom -",v->{zoom=Math.max(0.1f,zoom-0.1f); applyPreviewTransform(); writeAppConfigViaRoot();});
        addBtn(content,"Rotate",v->{rotation=(rotation+90)%360; applyPreviewTransform(); writeAppConfigViaRoot();}); addBtn(content,"Reset",v->{zoom=1;rotation=0;mirror=false;applyPreviewTransform();writeAppConfigViaRoot();});
        line("Selected media URI: "+String.valueOf(mediaUri)); line("Working copy path: /data/adb/icecam/media/source");
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(Color.BLACK); content.addView(frame, new LinearLayout.LayoutParams(-1,520));
        imagePreview = new ImageView(this); imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); videoPreview = new VideoView(this);
        frame.addView(imagePreview, new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER)); frame.addView(videoPreview, new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER)); updatePreview();
    }
    private void hooksTab(){ line("Mode: "+mode); line("Target camera: "+cameraMode); addBtn(content,"Mode: log-only",v->{mode="log-only";writeAppConfigViaRoot();renderUi();}); addBtn(content,"Mode: block-open-test",v->{mode="block-open-test";writeAppConfigViaRoot();renderUi();}); addBtn(content,"Mode: virtual-stub",v->{mode="virtual-stub";writeAppConfigViaRoot();renderUi();}); addBtn(content,"Target: auto",v->{cameraMode="auto";writeAppConfigViaRoot();renderUi();}); addBtn(content,"Target: back",v->{cameraMode="back";writeAppConfigViaRoot();renderUi();}); addBtn(content,"Target: front",v->{cameraMode="front";writeAppConfigViaRoot();renderUi();}); addBtn(content,"Write Config + Prepare Hooks",v->prepareHooks()); addBtn(content,"Show Hook Log",v->runRoot("cat /data/adb/icecam/logs/hook.log 2>/dev/null || true")); }
    private void logsTab(){ addBtn(content,"Show Hook Log",v->runRoot("cat /data/adb/icecam/logs/hook.log 2>/dev/null || true")); addBtn(content,"Show Module Log",v->runRoot("cat /data/adb/icecam/logs/module.log 2>/dev/null || true")); addBtn(content,"Export Debug Bundle",v->exportDebugBundle()); addBtn(content,"Clear Logs",v->runCtl("clear-logs")); }
    private void diagTab(){ line("package: "+getPackageName()); line("device: "+Build.MANUFACTURER+" "+Build.MODEL+" / "+Build.DEVICE); line("sdk: "+Build.VERSION.SDK_INT); line("abi: "+Arrays.toString(Build.SUPPORTED_ABIS)); addBtn(content,"Dump CameraManager",v->dumpCameras()); }

    private void selectPhoto(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); startActivityForResult(i, REQ_PHOTO); }
    private void selectVideo(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*"); startActivityForResult(i, REQ_VIDEO); }
    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(c==RESULT_OK&&d!=null){ mediaUri=d.getData(); getContentResolver().takePersistableUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); mediaType=(r==REQ_VIDEO)?"video":"photo"; renderUi(); } }
    private void updatePreview(){ if(mediaUri==null) return; if(mediaType.equals("video")){ imagePreview.setVisibility(View.GONE); videoPreview.setVisibility(View.VISIBLE); videoPreview.setVideoURI(mediaUri); videoPreview.setOnPreparedListener(mp->{mp.setLooping(loop); videoPreview.start();}); } else { videoPreview.setVisibility(View.GONE); imagePreview.setVisibility(View.VISIBLE); imagePreview.setImageURI(mediaUri); } applyPreviewTransform(); }
    private void applyPreviewTransform(){ View v=mediaType.equals("video")?videoPreview:imagePreview; if(v==null)return; v.setScaleX((mirror?-1:1)*zoom); v.setScaleY(zoom); v.setRotation(rotation); }

    private void requestRootCheck(){ runCtl("status"); }
    private void prepareHooks(){ writeAppConfigViaRoot(); runCtl("prepare-hooks"); }
    private void startReplacement(){ if(mediaUri==null){show("Select photo/video first");return;} copyMediaToRoot(); replacementActive=true; writeAppConfigViaRoot(); runCtl("start"); }
    private void stopReplacement(){ replacementActive=false; writeAppConfigViaRoot(); runCtl("stop"); }
    private void exportDebugBundle(){ runCtl("logs"); }
    private void runCtl(String arg){ runRoot(ctl+" "+arg); }

    private String json(){ String uri=(mediaUri==null?"":mediaUri.toString()).replace("\\","\\\\").replace("\"","\\\""); return "{\n"+
            "  \"version\": \"7.0-from-scratch\",\n"+
            "  \"active\": "+replacementActive+",\n"+
            "  \"mode\": \""+mode+"\",\n"+
            "  \"cameraMode\": \""+cameraMode+"\",\n"+
            "  \"mediaType\": \""+mediaType+"\",\n"+
            "  \"mediaUri\": \""+uri+"\",\n"+
            "  \"mediaPath\": \"/data/adb/icecam/media/source\",\n"+
            "  \"loop\": "+loop+", \"mirror\": "+mirror+", \"zoom\": "+zoom+", \"rotation\": "+rotation+"\n}"; }
    private void writeAppConfigViaRoot(){ String b64=android.util.Base64.encodeToString(json().getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP); runRoot("mkdir -p /data/adb/icecam/config && echo '"+b64+"' | base64 -d > /data/adb/icecam/config/app_config.json && chmod 666 /data/adb/icecam/config/app_config.json"); }
    private void copyMediaToRoot(){ try{ File tmp=new File(getCacheDir(),"icecam_source"); try(InputStream in=getContentResolver().openInputStream(mediaUri); OutputStream os=new FileOutputStream(tmp)){ byte[] buf=new byte[1024*128]; int n; while((n=in.read(buf))>0) os.write(buf,0,n);} runRoot("mkdir -p /data/adb/icecam/media && cp '"+tmp.getAbsolutePath()+"' /data/adb/icecam/media/source && chmod 666 /data/adb/icecam/media/source && ls -l /data/adb/icecam/media/source"); }catch(Exception e){ show("copyMediaToRoot error: "+e); } }
    private void runRoot(String command){ new Thread(()->{ StringBuilder sb=new StringBuilder(); int exit=-1; try{ Process p=Runtime.getRuntime().exec(new String[]{"su","-c",command}); String so=read(p.getInputStream()), se=read(p.getErrorStream()); exit=p.waitFor(); sb.append("$ su -c ").append(command).append("\n\nstdout:\n").append(so).append("\n\nstderr:\n").append(se).append("\nexitCode=").append(exit); }catch(Exception e){ sb.append("runRoot error: ").append(e); } runOnUiThread(()->out.setText(sb.toString())); }).start(); }
    private String read(InputStream is)throws IOException{ ByteArrayOutputStream bo=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n; while((n=is.read(b))!=-1)bo.write(b,0,n); return bo.toString(); }
    private void dumpCameras(){ try{ CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE); StringBuilder sb=new StringBuilder(); for(String id:cm.getCameraIdList()){ CameraCharacteristics cc=cm.getCameraCharacteristics(id); sb.append("id=").append(id).append('\n'); sb.append(" facing=").append(cc.get(CameraCharacteristics.LENS_FACING)).append('\n'); sb.append(" orientation=").append(cc.get(CameraCharacteristics.SENSOR_ORIENTATION)).append('\n'); sb.append(" hardwareLevel=").append(cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).append('\n'); sb.append(" focalLength=").append(Arrays.toString(cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))).append('\n'); sb.append(" fps=").append(Arrays.toString(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))).append("\n\n"); } out.setText(sb.toString()); }catch(Exception e){ out.setText("Camera dump error: "+e); } }
}
