
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

    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private android.graphics.drawable.GradientDrawable bg(int color, float radius){ android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), Color.argb(42,255,255,255)); return g; }
    private android.graphics.drawable.GradientDrawable appBg(){ android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(5,10,18), Color.rgb(10,27,42), Color.rgb(3,7,13)}); return g; }
    private TextView tv(String s, int sp, int style) { TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(232,238,247)); v.setTypeface(null, style); v.setPadding(dp(10),dp(5),dp(10),dp(5)); return v; }
    private TextView chip(String s, boolean selected){ TextView v=tv(s,12,selected?1:0); v.setGravity(Gravity.CENTER); v.setSingleLine(false); v.setTextColor(selected?Color.WHITE:Color.rgb(198,213,232)); v.setBackground(bg(selected?Color.argb(120,70,130,210):Color.argb(44,255,255,255), 18)); v.setPadding(dp(14),dp(8),dp(14),dp(8)); return v; }
    private Button btn(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setMinHeight(dp(46)); b.setPadding(dp(12),0,dp(12),0); b.setBackground(bg(Color.argb(72,255,255,255), 18)); return b; }
    private LinearLayout card(){ LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(12),dp(12),dp(12),dp(12)); c.setBackground(bg(Color.argb(54,255,255,255), 24)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(10),dp(8),dp(10),dp(8)); content.addView(c,lp); return c; }
    private void addBtn(LinearLayout l, String s, View.OnClickListener c){ Button b=btn(s); b.setOnClickListener(c); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50)); lp.setMargins(0,dp(5),0,dp(5)); l.addView(b, lp); }
    private void line(String s){ content.addView(tv(s,14,0)); }
    private void show(String s){ out.setText(s); }

    private void renderUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackground(appBg());
        status = tv("IceCam v7.1 · glass UI", 20, 1); status.setPadding(dp(14),dp(12),dp(14),dp(8)); root.addView(status);
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); tabBar = new LinearLayout(this); tabBar.setOrientation(LinearLayout.HORIZONTAL); tabBar.setPadding(dp(8),dp(4),dp(8),dp(6)); hsv.addView(tabBar); root.addView(hsv);
        for (String t: new String[]{"Dashboard","Root","Media","Hooks","Logs","Diagnostics"}) { TextView b=chip(t, tab.equals(t)); b.setOnClickListener(v->{tab=t; renderUi();}); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(t.equals("Diagnostics")?112:92),dp(44)); lp.setMargins(dp(3),0,dp(3),0); tabBar.addView(b, lp); }
        ScrollView sv = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(6),0,dp(6),dp(6)); sv.addView(content); root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
        out = tv("stdout/stderr/exitCode will appear here", 12, 0); out.setTextColor(Color.rgb(155,205,255)); out.setMovementMethod(new ScrollingMovementMethod()); out.setBackground(bg(Color.argb(42,0,0,0), 20)); LinearLayout.LayoutParams outLp=new LinearLayout.LayoutParams(-1,dp(132)); outLp.setMargins(dp(10),dp(4),dp(10),dp(10)); root.addView(out, outLp);
        setContentView(root);
        if(tab.equals("Dashboard")) dashboard(); else if(tab.equals("Root")) rootTab(); else if(tab.equals("Media")) mediaTab(); else if(tab.equals("Hooks")) hooksTab(); else if(tab.equals("Logs")) logsTab(); else diagTab();
    }

    private void dashboard(){
        LinearLayout c=card(); c.addView(tv("Replacement: "+(replacementActive?"ACTIVE":"STOPPED"),16,1)); c.addView(tv("Hook mode: "+mode,14,0)); c.addView(tv("Camera profile: "+cameraMode,14,0)); c.addView(tv("Media type: "+mediaType,14,0)); c.addView(tv("Media URI: "+String.valueOf(mediaUri),12,0));
        LinearLayout a=card(); addBtn(a,"Start Replacement",v->startReplacement()); addBtn(a,"Stop Replacement",v->stopReplacement()); addBtn(a,"Write Config + Prepare Hooks",v->prepareHooks()); addBtn(a,"Export Debug Bundle",v->exportDebugBundle());
    }
    private void rootTab(){ LinearLayout c=card(); c.addView(tv("control path: "+ctl,13,0)); c.addView(tv("module path: "+ice,13,0)); LinearLayout a=card(); addBtn(a,"Request Root Check",v->requestRootCheck()); addBtn(a,"Prepare Hook Layer",v->prepareHooks()); addBtn(a,"Clear Logs",v->runCtl("clear-logs")); addBtn(a,"Export Debug Bundle",v->exportDebugBundle()); }
    private void mediaTab(){
        LinearLayout a=card(); addBtn(a,"Select Photo",v->selectPhoto()); addBtn(a,"Select Video",v->selectVideo()); addBtn(a,"Start Replacement",v->startReplacement()); addBtn(a,"Stop Replacement",v->stopReplacement());
        LinearLayout b=card(); addBtn(b,"Loop "+(loop?"ON":"OFF"),v->{loop=!loop; renderUi();}); addBtn(b,"Mirror",v->{mirror=!mirror; applyPreviewTransform(); writeAppConfigViaRoot();});
        addBtn(b,"Zoom +",v->{zoom+=0.1f; applyPreviewTransform(); writeAppConfigViaRoot();}); addBtn(b,"Zoom -",v->{zoom=Math.max(0.1f,zoom-0.1f); applyPreviewTransform(); writeAppConfigViaRoot();});
        addBtn(b,"Rotate",v->{rotation=(rotation+90)%360; applyPreviewTransform(); writeAppConfigViaRoot();}); addBtn(b,"Reset",v->{zoom=1;rotation=0;mirror=false;applyPreviewTransform();writeAppConfigViaRoot();});
        LinearLayout info=card(); info.addView(tv("Selected media URI: "+String.valueOf(mediaUri),12,0)); info.addView(tv("Working copy path: /data/adb/icecam/media/source",12,0));
        FrameLayout frame = new FrameLayout(this); frame.setBackground(bg(Color.argb(82,0,0,0), 22)); LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,dp(260)); flp.setMargins(dp(10),dp(8),dp(10),dp(8)); content.addView(frame, flp);
        imagePreview = new ImageView(this); imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); videoPreview = new VideoView(this);
        frame.addView(imagePreview, new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER)); frame.addView(videoPreview, new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER)); updatePreview();
    }
    private void hooksTab(){ LinearLayout c=card(); c.addView(tv("Mode: "+mode,15,1)); c.addView(tv("Target camera: "+cameraMode,15,0)); LinearLayout a=card(); addBtn(a,"Mode: log-only",v->{mode="log-only";writeAppConfigViaRoot();renderUi();}); addBtn(a,"Mode: block-open-test",v->{mode="block-open-test";writeAppConfigViaRoot();renderUi();}); addBtn(a,"Mode: virtual-stub",v->{mode="virtual-stub";writeAppConfigViaRoot();renderUi();}); addBtn(a,"Target: auto",v->{cameraMode="auto";writeAppConfigViaRoot();renderUi();}); addBtn(a,"Target: back",v->{cameraMode="back";writeAppConfigViaRoot();renderUi();}); addBtn(a,"Target: front",v->{cameraMode="front";writeAppConfigViaRoot();renderUi();}); addBtn(a,"Write Config + Prepare Hooks",v->prepareHooks()); addBtn(a,"Show Hook Log",v->runRoot("cat /data/adb/icecam/logs/hook.log 2>/dev/null || true")); }
    private void logsTab(){ LinearLayout a=card(); addBtn(a,"Show Hook Log",v->runRoot("cat /data/adb/icecam/logs/hook.log 2>/dev/null || true")); addBtn(a,"Show Module Log",v->runRoot("cat /data/adb/icecam/logs/module.log 2>/dev/null || true")); addBtn(a,"Export Debug Bundle",v->exportDebugBundle()); addBtn(a,"Clear Logs",v->runCtl("clear-logs")); }
    private void diagTab(){ LinearLayout c=card(); c.addView(tv("package: "+getPackageName(),14,0)); c.addView(tv("device: "+Build.MANUFACTURER+" "+Build.MODEL+" / "+Build.DEVICE,14,0)); c.addView(tv("sdk: "+Build.VERSION.SDK_INT,14,0)); c.addView(tv("abi: "+Arrays.toString(Build.SUPPORTED_ABIS),13,0)); LinearLayout a=card(); addBtn(a,"Dump CameraManager",v->dumpCameras()); }

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
