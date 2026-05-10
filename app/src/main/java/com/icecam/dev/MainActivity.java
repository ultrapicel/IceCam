package com.icecam.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
    private static final String APP_VERSION = "v9.4-renderer-sandbox";
    private final String ctl = "/data/adb/icecam/bin/icecamctl";
    private final String ice = "/data/adb/icecam";
    private String tab = "Dashboard", mode = "log-only", cameraMode = "auto", compatibilityMode = "strict-real", mediaType = "none";
    private boolean replacementActive = false, loop = true, mirror = false;
    private int diagStep = 1;
    private int rotation = 0;
    private float zoom = 1.0f;
    private Uri mediaUri = null;
    private boolean startupRootRequested = false;
    private LinearLayout root, tabBar, content;
    private TextView out, status;
    private ImageView imagePreview;
    private VideoView videoPreview;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestRuntimePermissionsOnly();
        loadSettings();
        renderUi();
        requestRootOnStartup();
    }


    private void requestRootOnStartup() {
        if (startupRootRequested) return;
        startupRootRequested = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            runRoot(ctl + " status");
        }, 700);
    }

    private android.content.SharedPreferences prefs(){ return getSharedPreferences("icecam_settings", MODE_PRIVATE); }
    private void loadSettings(){
        android.content.SharedPreferences p=prefs();
        tab=p.getString("tab", tab);
        mode=p.getString("mode", mode);
        cameraMode=p.getString("cameraMode", cameraMode);
        compatibilityMode=p.getString("compatibilityMode", compatibilityMode);
        mediaType=p.getString("mediaType", mediaType);
        replacementActive=p.getBoolean("replacementActive", replacementActive);
        loop=p.getBoolean("loop", loop);
        mirror=p.getBoolean("mirror", mirror);
        rotation=p.getInt("rotation", rotation);
        zoom=p.getFloat("zoom", zoom);
        diagStep=p.getInt("diagStep", diagStep);
        String u=p.getString("mediaUri", "");
        if(u.length()>0) mediaUri=Uri.parse(u);
    }
    private void saveSettings(){
        prefs().edit()
                .putString("tab", tab)
                .putString("mode", mode)
                .putString("cameraMode", cameraMode)
                .putString("compatibilityMode", compatibilityMode)
                .putString("mediaType", mediaType)
                .putBoolean("replacementActive", replacementActive)
                .putBoolean("loop", loop)
                .putBoolean("mirror", mirror)
                .putInt("rotation", rotation)
                .putFloat("zoom", zoom)
                .putInt("diagStep", diagStep)
                .putString("mediaUri", mediaUri==null?"":mediaUri.toString())
                .apply();
    }

    private void requestRuntimePermissionsOnly() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.CAMERA}, 7);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 7);
    }

    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private android.graphics.drawable.GradientDrawable round(int color, float radius){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.argb(48,255,255,255));
        return g;
    }
    private android.graphics.drawable.Drawable glass(int color, float radius){
        android.graphics.drawable.GradientDrawable base=new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(128,255,255,255), color, Color.argb(32,255,255,255)});
        base.setCornerRadius(dp(radius));
        base.setStroke(dp(1), Color.argb(56,255,255,255));
        return base;
    }
    private android.graphics.drawable.GradientDrawable appBg(){
        return new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(4,8,16), Color.rgb(11,28,44), Color.rgb(7,13,22), Color.rgb(2,5,10)});
    }
    private TextView tv(String s, int sp, int style) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(232,238,247));
        v.setTypeface(null, style); v.setPadding(dp(10),dp(5),dp(10),dp(5)); return v;
    }
    private TextView muted(String s){ TextView v=tv(s,12,0); v.setTextColor(Color.rgb(168,184,205)); return v; }
    private TextView chip(String s, boolean selected){
        TextView v=tv(s,12,selected?1:0); v.setGravity(Gravity.CENTER); v.setSingleLine(false);
        v.setTextColor(selected?Color.WHITE:Color.rgb(198,213,232));
        v.setBackground(glass(selected?Color.argb(124,70,132,220):Color.argb(42,255,255,255), 18));
        v.setPadding(dp(14),dp(8),dp(14),dp(8)); if (Build.VERSION.SDK_INT >= 21) v.setElevation(dp(selected?4:1)); return v;
    }
    private Button btn(String s, boolean primary) {
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(50)); b.setPadding(dp(14),0,dp(14),0);
        b.setBackground(glass(primary?Color.argb(168,56,124,222):Color.argb(74,255,255,255), 22));
        if (Build.VERSION.SDK_INT >= 21) { b.setElevation(dp(primary?8:5)); b.setTranslationZ(dp(primary?3:1)); b.setStateListAnimator(null); }
        return b;
    }
    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(13),dp(13),dp(13),dp(13));
        c.setBackground(glass(Color.argb(48,255,255,255), 26)); if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(3));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(10),dp(7),dp(10),dp(7)); content.addView(c,lp); return c;
    }
    private void section(LinearLayout l, String title, String subtitle){
        l.addView(tv(title,16,1)); if(subtitle!=null && subtitle.length()>0) l.addView(muted(subtitle));
    }
    private void addBtn(LinearLayout l, String s, boolean primary, View.OnClickListener c){
        Button b=btn(s, primary); b.setOnClickListener(c); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52)); lp.setMargins(0,dp(5),0,dp(5)); l.addView(b, lp);
    }
    private void pillRow(LinearLayout l, String label, String value){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0,dp(3),0,dp(3));
        TextView a=muted(label); TextView b=tv(value,13,1); b.setGravity(Gravity.RIGHT); r.addView(a,new LinearLayout.LayoutParams(0,-2,1)); r.addView(b,new LinearLayout.LayoutParams(0,-2,1)); l.addView(r);
    }
    private void show(String s){ out.setText(s); }

    private void renderUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackground(appBg());
        status = tv("IceCam " + APP_VERSION, 20, 1); status.setPadding(dp(14),dp(12),dp(14),dp(6)); root.addView(status);
        TextView hint = muted("Development build · noise-filtered surface mapping · renderer prep · no frame injection yet"); hint.setPadding(dp(14),0,dp(14),dp(6)); root.addView(hint);

        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); tabBar = new LinearLayout(this); tabBar.setOrientation(LinearLayout.HORIZONTAL); tabBar.setPadding(dp(8),dp(4),dp(8),dp(6)); hsv.addView(tabBar); root.addView(hsv);
        for (String t: new String[]{"Dashboard","Root","Media","Hooks","Logs","Diagnostics"}) { final String ft=t; TextView b=chip(t, tab.equals(t)); b.setOnClickListener(v->{tab=ft; saveSettings(); renderUi();}); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(t.equals("Diagnostics")?112:92),dp(44)); lp.setMargins(dp(3),0,dp(3),0); tabBar.addView(b, lp); }

        ScrollView sv = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(6),0,dp(6),dp(6)); sv.addView(content); root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
        out = tv("Command output: stdout / stderr / exitCode", 12, 0); out.setTextColor(Color.rgb(160,210,255)); out.setMovementMethod(new ScrollingMovementMethod()); out.setBackground(round(Color.argb(48,0,0,0), 20)); LinearLayout.LayoutParams outLp=new LinearLayout.LayoutParams(-1,dp(128)); outLp.setMargins(dp(10),dp(4),dp(10),dp(10)); root.addView(out, outLp);
        setContentView(root);
        if(tab.equals("Dashboard")) dashboard(); else if(tab.equals("Root")) rootTab(); else if(tab.equals("Media")) mediaTab(); else if(tab.equals("Hooks")) hooksTab(); else if(tab.equals("Logs")) logsTab(); else diagTab();
    }

    private void dashboard(){
        LinearLayout s=card(); section(s,"Status", "Current local UI state. Root state is checked by Step 1.");
        pillRow(s,"Next diagnostic step", String.valueOf(diagStep));
        pillRow(s,"Replacement", replacementActive?"ACTIVE":"STOPPED"); pillRow(s,"Hook mode",mode); pillRow(s,"Camera target",cameraMode); pillRow(s,"Media",mediaType); pillRow(s,"Selected",mediaUri==null?"none":"yes");

        LinearLayout w=card(); section(w,"Diagnostic flow", "Press in order. Step 4 clears stale logcat, then Step 5 must open target camera apps before export.");
        addBtn(w,"1 · Root Check", diagStep==1, v->{diagStep=2; saveSettings(); requestRootCheck(); renderUi();});
        addBtn(w,"2 · Prepare Hook Layer", diagStep==2, v->{diagStep=3; saveSettings(); prepareHooks(); renderUi();});
        addBtn(w,"3A · Select Photo", diagStep==3, v->selectPhoto());
        addBtn(w,"3B · Select Video", diagStep==3, v->selectVideo());
        addBtn(w,"4 · Start Replacement", diagStep==4, v->{diagStep=5; saveSettings(); startReplacement(); renderUi();});
        addBtn(w,"5 · Open target camera manually", diagStep==5, v->{diagStep=6; saveSettings(); show("Open Camera / Telegram / Chrome camera screen now. Keep each camera open for 5–10 seconds. Then return and press Step 6 Export Lite Debug Bundle."); renderUi();});
        addBtn(w,"6 · Export Lite Debug Bundle", diagStep==6, v->{diagStep=1; saveSettings(); exportDebugBundle(); renderUi();});
        addBtn(w,"Stop Replacement", false, v->stopReplacement());

        LinearLayout n=card(); section(n,"Expected v7 hook markers", null);
        n.addView(muted("Required markers: [LOAD] → [HOOKED] → getCameraIdList → getCameraCharacteristics → openCamera"));
        n.addView(muted("v9.4 required markers: target [LOAD], renderer init/tick, openCamera, createCaptureSession, CaptureRequest target/surface ownership traces"));
    }
    private void rootTab(){
        LinearLayout c=card(); section(c,"Root / module paths", "Root is requested once on app start in dev builds. Manual tools stay here."); c.addView(muted("control path: "+ctl)); c.addView(muted("module path: "+ice));
        LinearLayout a=card(); section(a,"Root tools", "Main diagnostic actions are only on Dashboard to avoid duplicate flow buttons.");
        addBtn(a,"Clear Logs", false, v->runCtl("clear-logs"));
        addBtn(a,"Clean Old Dev Data", false, v->runCtl("reset-dev-data"));
        addBtn(a,"Reset UI Settings", false, v->{prefs().edit().clear().apply(); mediaUri=null; mediaType="none"; replacementActive=false; loop=true; mirror=false; zoom=1.0f; rotation=0; diagStep=1; tab="Dashboard"; show("UI settings reset"); renderUi();});
    }
    private void mediaTab(){
        LinearLayout info=card(); section(info,"Media preview", "Select media only through Dashboard step 3 during diagnostics.");
        info.addView(muted("Selected media URI: "+String.valueOf(mediaUri))); info.addView(muted("Working copy path: /data/adb/icecam/media/source"));
        LinearLayout b=card(); section(b,"Preview controls", "Affects preview/config only; frame injection is not enabled yet.");
        addBtn(b,"Loop "+(loop?"ON":"OFF"),false,v->{loop=!loop; saveSettings(); writeAppConfigViaRoot(); renderUi();}); addBtn(b,"Mirror",false,v->{mirror=!mirror; applyPreviewTransform(); saveSettings(); writeAppConfigViaRoot();});
        addBtn(b,"Zoom +",false,v->{zoom+=0.1f; applyPreviewTransform(); saveSettings(); writeAppConfigViaRoot();}); addBtn(b,"Zoom -",false,v->{zoom=Math.max(0.1f,zoom-0.1f); applyPreviewTransform(); saveSettings(); writeAppConfigViaRoot();});
        addBtn(b,"Rotate",false,v->{rotation=(rotation+90)%360; applyPreviewTransform(); saveSettings(); writeAppConfigViaRoot();}); addBtn(b,"Reset Transform",false,v->{zoom=1;rotation=0;mirror=false;applyPreviewTransform();saveSettings();writeAppConfigViaRoot();});
        FrameLayout frame = new FrameLayout(this); frame.setBackground(round(Color.argb(82,0,0,0), 22)); LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,dp(270)); flp.setMargins(dp(10),dp(8),dp(10),dp(8)); content.addView(frame, flp);
        imagePreview = new ImageView(this); imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); videoPreview = new VideoView(this);
        frame.addView(imagePreview, new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER)); frame.addView(videoPreview, new FrameLayout.LayoutParams(-1,-1, Gravity.CENTER)); updatePreview();
    }
    private void hooksTab(){
        LinearLayout c=card(); section(c,"Hook configuration", "v9.4 adds passive renderer sandbox: renderer thread, placeholder producer and owned SurfaceTexture telemetry. Camera frames are not replaced yet."); pillRow(c,"Mode",mode); pillRow(c,"Target",cameraMode); pillRow(c,"Compatibility",compatibilityMode);
        LinearLayout a=card(); section(a,"Mode", null); addBtn(a,"log-only", mode.equals("log-only"),v->{mode="log-only";saveSettings();writeAppConfigViaRoot();renderUi();}); addBtn(a,"block-open-test", mode.equals("block-open-test"),v->{mode="block-open-test";saveSettings();writeAppConfigViaRoot();renderUi();}); addBtn(a,"virtual-stub", mode.equals("virtual-stub"),v->{mode="virtual-stub";saveSettings();writeAppConfigViaRoot();renderUi();});
        LinearLayout t=card(); section(t,"Target camera", null); addBtn(t,"auto", cameraMode.equals("auto"),v->{cameraMode="auto";saveSettings();writeAppConfigViaRoot();renderUi();}); addBtn(t,"back", cameraMode.equals("back"),v->{cameraMode="back";saveSettings();writeAppConfigViaRoot();renderUi();}); addBtn(t,"front", cameraMode.equals("front"),v->{cameraMode="front";saveSettings();writeAppConfigViaRoot();renderUi();});
        LinearLayout m=card(); section(m,"Compatibility profile mode", "strict-real is default; compatibility/experimental are passive flags in v8.");
        addBtn(m,"strict-real", compatibilityMode.equals("strict-real"),v->{compatibilityMode="strict-real";saveSettings();writeAppConfigViaRoot();renderUi();});
        addBtn(m,"compatibility", compatibilityMode.equals("compatibility"),v->{compatibilityMode="compatibility";saveSettings();writeAppConfigViaRoot();renderUi();});
        addBtn(m,"experimental", compatibilityMode.equals("experimental"),v->{compatibilityMode="experimental";saveSettings();writeAppConfigViaRoot();renderUi();});
    }
    private void logsTab(){
        LinearLayout a=card(); section(a,"Logs", "Lite export is Step 6. Full export is only for heavy crash/debug cases.");
        addBtn(a,"Export Full Debug Bundle",false,v->runCtl("full-logs"));
        addBtn(a,"Show Hook Log",true,v->runRoot("cat /data/adb/icecam/logs/hook.log 2>/dev/null || true"));
        addBtn(a,"Show Module Log",false,v->runRoot("cat /data/adb/icecam/logs/module.log 2>/dev/null || true"));
        addBtn(a,"Show Filtered Logcat",false,v->runRoot("logcat -d -v threadtime | grep -E 'IceCam|LSPosed|Xposed|CameraManager|Camera2|CameraService|CameraProvider|ProfileCache' | tail -n 320"));
        addBtn(a,"Show Profile Cache",false,v->runRoot("cat /data/adb/icecam/cache/camera_profiles.json 2>/dev/null || echo no-profile-cache-yet"));
        addBtn(a,"Show Renderer Events",false,v->runRoot("cat /data/adb/icecam/cache/renderer_events.jsonl 2>/dev/null || echo no-renderer-events-yet"));
    }
    private void diagTab(){
        LinearLayout c=card(); section(c,"Device", null); c.addView(muted("package: "+getPackageName())); c.addView(muted("device: "+Build.MANUFACTURER+" "+Build.MODEL+" / "+Build.DEVICE)); c.addView(muted("sdk: "+Build.VERSION.SDK_INT)); c.addView(muted("abi: "+Arrays.toString(Build.SUPPORTED_ABIS)));
        LinearLayout a=card(); section(a,"Camera diagnostics", null); addBtn(a,"Dump CameraManager",true,v->dumpCameras()); addBtn(a,"Read Profile Cache",false,v->runRoot("cat /data/adb/icecam/cache/camera_profiles.json 2>/dev/null || true"));
    }

    private void selectPhoto(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); startActivityForResult(i, REQ_PHOTO); }
    private void selectVideo(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*"); startActivityForResult(i, REQ_VIDEO); }
    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(c==RESULT_OK&&d!=null){ mediaUri=d.getData(); try{getContentResolver().takePersistableUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){} mediaType=(r==REQ_VIDEO)?"video":"photo"; diagStep=4; tab="Dashboard"; saveSettings(); renderUi(); } }
    private void updatePreview(){ if(mediaUri==null) return; if(mediaType.equals("video")){ imagePreview.setVisibility(View.GONE); videoPreview.setVisibility(View.VISIBLE); videoPreview.setVideoURI(mediaUri); videoPreview.setOnPreparedListener(mp->{mp.setLooping(loop); videoPreview.start();}); } else { videoPreview.setVisibility(View.GONE); imagePreview.setVisibility(View.VISIBLE); imagePreview.setImageURI(mediaUri); } applyPreviewTransform(); }
    private void applyPreviewTransform(){ View v=mediaType.equals("video")?videoPreview:imagePreview; if(v==null)return; v.setScaleX((mirror?-1:1)*zoom); v.setScaleY(zoom); v.setRotation(rotation); }

    private void requestRootCheck(){ runCtl("status"); }
    private void prepareHooks(){ writeAppConfigViaRoot(); runCtl("prepare-hooks"); }
    private void startReplacement(){ if(mediaUri==null){show("Step 3 required: select photo/video first");return;} copyMediaToRoot(); replacementActive=true; saveSettings(); writeAppConfigViaRoot(); runCtl("start"); }
    private void stopReplacement(){ replacementActive=false; saveSettings(); writeAppConfigViaRoot(); runCtl("stop"); }
    private void exportDebugBundle(){ runCtl("lite-logs"); }
    private void runCtl(String arg){ runRoot(ctl+" "+arg); }

    private String json(){ String uri=(mediaUri==null?"":mediaUri.toString()).replace("\\","\\\\").replace("\"","\\\""); return "{\n"+
            "  \"version\": \""+APP_VERSION+"\",\n"+
            "  \"active\": "+replacementActive+",\n"+
            "  \"mode\": \""+mode+"\",\n"+
            "  \"cameraMode\": \""+cameraMode+"\",\n"+
            "  \"compatibilityMode\": \""+compatibilityMode+"\",\n"+
            "  \"mediaType\": \""+mediaType+"\",\n"+
            "  \"mediaUri\": \""+uri+"\",\n"+
            "  \"mediaPath\": \"/data/adb/icecam/media/source\",\n"+
            "  \"mediaMetaPath\": \"/data/adb/icecam/media/source.meta.json\",\n"+
            "  \"pipelineStage\": \"renderer-sandbox-passive\",\n"+
            "  \"loop\": "+loop+", \"mirror\": "+mirror+", \"zoom\": "+zoom+", \"rotation\": "+rotation+"\n}"; }
    private void writeAppConfigViaRoot(){ String b64=android.util.Base64.encodeToString(json().getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP); runRoot("mkdir -p /data/adb/icecam/config && echo '"+b64+"' | base64 -d > /data/adb/icecam/config/app_config.json && chmod 666 /data/adb/icecam/config/app_config.json"); }
    private void copyMediaToRoot(){ try{ File tmp=new File(getCacheDir(),"icecam_source"); long bytes=0; try(InputStream in=getContentResolver().openInputStream(mediaUri); OutputStream os=new FileOutputStream(tmp)){ byte[] buf=new byte[1024*128]; int n; while((n=in.read(buf))>0){ os.write(buf,0,n); bytes+=n; }} String meta=mediaMetaJson(bytes); String b64=android.util.Base64.encodeToString(meta.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP); runRoot("mkdir -p /data/adb/icecam/media && cp '"+tmp.getAbsolutePath()+"' /data/adb/icecam/media/source && echo '"+b64+"' | base64 -d > /data/adb/icecam/media/source.meta.json && chmod 666 /data/adb/icecam/media/source /data/adb/icecam/media/source.meta.json && ls -l /data/adb/icecam/media/source /data/adb/icecam/media/source.meta.json"); }catch(Exception e){ show("copyMediaToRoot error: "+e); } }
    private String mediaMetaJson(long bytes){ String uri=(mediaUri==null?"":mediaUri.toString()).replace("\\","\\\\").replace("\"","\\\""); return "{\n"+
            "  \"version\": \""+APP_VERSION+"\",\n"+
            "  \"mediaType\": \""+mediaType+"\",\n"+
            "  \"sourceUri\": \""+uri+"\",\n"+
            "  \"bytes\": "+bytes+",\n"+
            "  \"loop\": "+loop+", \"mirror\": "+mirror+", \"zoom\": "+zoom+", \"rotation\": "+rotation+",\n"+
            "  \"fitMode\": \"fill-center-crop\",\n"+
            "  \"workingPath\": \"/data/adb/icecam/media/source\"\n}"; }
    private void runRoot(String command){ new Thread(()->{ StringBuilder sb=new StringBuilder(); int exit=-1; try{ Process p=Runtime.getRuntime().exec(new String[]{"su","-c",command}); String so=read(p.getInputStream()), se=read(p.getErrorStream()); exit=p.waitFor(); sb.append("$ su -c ").append(command).append("\n\nstdout:\n").append(so).append("\n\nstderr:\n").append(se).append("\nexitCode=").append(exit); }catch(Exception e){ sb.append("runRoot error: ").append(e); } runOnUiThread(()->out.setText(sb.toString())); }).start(); }
    private String read(InputStream is)throws IOException{ ByteArrayOutputStream bo=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n; while((n=is.read(b))!=-1)bo.write(b,0,n); return bo.toString(); }
    private void dumpCameras(){ try{ CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE); StringBuilder sb=new StringBuilder(); for(String id:cm.getCameraIdList()){ CameraCharacteristics cc=cm.getCameraCharacteristics(id); sb.append("id=").append(id).append('\n'); sb.append(" facing=").append(cc.get(CameraCharacteristics.LENS_FACING)).append('\n'); sb.append(" orientation=").append(cc.get(CameraCharacteristics.SENSOR_ORIENTATION)).append('\n'); sb.append(" hardwareLevel=").append(cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)).append('\n'); sb.append(" focalLength=").append(Arrays.toString(cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))).append('\n'); sb.append(" fps=").append(Arrays.toString(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))).append("\n\n"); } out.setText(sb.toString()); }catch(Exception e){ out.setText("Camera dump error: "+e); } }
}
