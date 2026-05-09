package com.icecam.dev;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.hardware.camera2.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    TextView status, diag;
    static final String CTL = "/data/adb/icecam/bin/icecamctl";
    int bg = 0xff05080c, card = 0xff101820, accent = 0xff5d6f9f;
    @Override public void onCreate(Bundle b){ super.onCreate(b); if(Build.VERSION.SDK_INT>=23) requestPermissions(new String[]{Manifest.permission.CAMERA},5); buildUi(); dumpCameras(); }
    TextView tv(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextColor(0xffedf3ff); v.setTextSize(sp); v.setPadding(18,12,18,12); return v; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(0xffffffff); b.setTextSize(16); b.setAllCaps(false); return b; }
    void buildUi(){ ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,28,18,28); root.setBackgroundColor(bg); sv.addView(root); root.addView(tv("IceCam",42)); root.addView(tv("System camera replacement · v3.1 dev hook layer",20)); status=tv("Ready. Root ctl path: "+CTL,18); status.setBackgroundColor(card); root.addView(status); Button r=btn("Request Root Check"); root.addView(r); r.setOnClickListener(v->runCtl("status")); Button p=btn("Prepare Hook Layer"); root.addView(p); p.setOnClickListener(v->runCtl("prepare-hooks")); Button e=btn("Export Debug Bundle"); root.addView(e); e.setOnClickListener(v->runCtl("logs")); diag=tv("Diagnostics pending",17); diag.setBackgroundColor(card); root.addView(diag); setContentView(sv); }
    void runCtl(String arg){ new Thread(()->{ String out=exec("su -c '"+CTL+" "+arg+"'"); runOnUiThread(()->status.setText(out)); }).start(); }
    String exec(String cmd){ StringBuilder sb=new StringBuilder(); try{ Process p=Runtime.getRuntime().exec(new String[]{"sh","-c",cmd}); BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream())); BufferedReader er=new BufferedReader(new InputStreamReader(p.getErrorStream())); String l; while((l=br.readLine())!=null) sb.append(l).append('\n'); while((l=er.readLine())!=null) sb.append(l).append('\n'); int code=p.waitFor(); sb.append("exit=").append(code); }catch(Exception e){ sb.append(e); } return sb.toString(); }
    void dumpCameras(){ try{ CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE); StringBuilder sb=new StringBuilder("IceCam v3.1 dev ready\n"); for(String id: cm.getCameraIdList()){ CameraCharacteristics c=cm.getCameraCharacteristics(id); Integer f=c.get(CameraCharacteristics.LENS_FACING); Integer o=c.get(CameraCharacteristics.SENSOR_ORIENTATION); Integer hw=c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL); sb.append("Camera ").append(id).append(" facing=").append(f).append(" orientation=").append(o).append(" hwLevel=").append(hw).append('\n'); } diag.setText(sb.toString()); }catch(Exception e){ diag.setText("Camera dump error: "+e); } }
}
