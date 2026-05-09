package dev.icecam.hook;
import android.util.Log;import java.io.*;import java.lang.reflect.*;
public class IceCamHookEntry implements de.robv.android.xposed.IXposedHookLoadPackage{
 static final String TAG="IceCam/Hook";
 public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam)throws Throwable{
  if(lpparam.packageName.equals("dev.icecam.app"))return;
  log("loadPackage="+lpparam.packageName+" process="+lpparam.processName);
  hookCameraManager(lpparam);
 }
 static void hookCameraManager(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lp){try{
  Class<?> cm=Class.forName("android.hardware.camera2.CameraManager",false,lp.classLoader);
  de.robv.android.xposed.XposedHelpers.findAndHookMethod(cm,"getCameraIdList",new de.robv.android.xposed.XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){log("getCameraIdList caller result="+java.util.Arrays.toString((String[])p.getResult()));}});
  de.robv.android.xposed.XposedHelpers.findAndHookMethod(cm,"getCameraCharacteristics",String.class,new de.robv.android.xposed.XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){log("getCameraCharacteristics id="+p.args[0]);}});
  de.robv.android.xposed.XposedHelpers.findAndHookMethod(cm,"openCamera",String.class,android.hardware.camera2.CameraDevice.StateCallback.class,android.os.Handler.class,new de.robv.android.xposed.XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){log("openCamera id="+p.args[0]+" callback="+p.args[1]);}});
 }catch(Throwable t){log("hookCameraManager failed "+t);}}
 static synchronized void log(String m){Log.i(TAG,m);try{File dir=new File("/data/adb/icecam/logs");dir.mkdirs();PrintWriter p=new PrintWriter(new FileWriter(new File(dir,"hook.log"),true));p.println(System.currentTimeMillis()+" "+m);p.close();}catch(Throwable ignored){}}
}
