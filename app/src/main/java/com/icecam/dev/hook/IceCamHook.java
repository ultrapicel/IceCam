package com.icecam.dev.hook;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.*;

public class IceCamHook implements IXposedHookLoadPackage {
  static final String LOG="/data/adb/icecam/logs/hook.log";
  public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    if (lpparam.packageName.equals("com.icecam.dev")) return;
    log("loaded package="+lpparam.packageName);
    try {
      Class<?> cm = XposedHelpers.findClass("android.hardware.camera2.CameraManager", lpparam.classLoader);
      XposedHelpers.findAndHookMethod(cm, "getCameraIdList", new XC_MethodHook(){ protected void beforeHookedMethod(MethodHookParam p){ log("getCameraIdList app="+lpparam.packageName); }});
      XposedHelpers.findAndHookMethod(cm, "getCameraCharacteristics", String.class, new XC_MethodHook(){ protected void beforeHookedMethod(MethodHookParam p){ log("getCameraCharacteristics app="+lpparam.packageName+" id="+p.args[0]); }});
      XposedHelpers.findAndHookMethod(cm, "openCamera", String.class, android.hardware.camera2.CameraDevice.StateCallback.class, android.os.Handler.class, new XC_MethodHook(){ protected void beforeHookedMethod(MethodHookParam p){ log("openCamera app="+lpparam.packageName+" id="+p.args[0]); }});
    } catch(Throwable t){ log("hook error app="+lpparam.packageName+" err="+t); }
  }
  static void log(String s){ try{ File f=new File(LOG); f.getParentFile().mkdirs(); FileWriter w=new FileWriter(f,true); w.write(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())+" "+s+"\n"); w.close(); }catch(Throwable ignored){} XposedBridge.log("IceCam: "+s); }
}
