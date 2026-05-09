
package com.icecam.dev.hook;

import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IceCamHook implements IXposedHookLoadPackage {
    private static final String TAG="IceCam/Hook";
    private static final String LOG="/data/adb/icecam/logs/hook.log";
    private static final String CONFIG="/data/adb/icecam/config/app_config.json";
    private static final String ACTIVE="/data/adb/icecam/state/active";
    private static final String MEDIA="/data/adb/icecam/media/source";

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        log("[LOAD] package="+lp.packageName+" process="+lp.processName+" active="+active()+" mode="+mode()+" mediaPath="+MEDIA);
        hookCamera2(lp); hookCamera1(lp);
    }
    private void hookCamera2(XC_LoadPackage.LoadPackageParam lp){
        try{
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lp.classLoader, "getCameraIdList", new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){ log("[Camera2] getCameraIdList before package="+lp.packageName+" active="+active()+" mode="+mode()+" mediaPath="+MEDIA); }
                protected void afterHookedMethod(MethodHookParam p){ log("[Camera2] getCameraIdList after result="+safe(p.getResult())); }
            });
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lp.classLoader, "getCameraCharacteristics", String.class, new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){ log("[Camera2] getCameraCharacteristics id="+p.args[0]+" active="+active()+" mode="+mode()+" config="+readSmall(CONFIG)); }
            });
            hookOpen(lp, "android.hardware.camera2.CameraDevice$StateCallback", "android.os.Handler");
            hookOpen(lp, "java.util.concurrent.Executor", "android.hardware.camera2.CameraDevice$StateCallback");
        }catch(Throwable t){ log("[ERR] hookCamera2 "+t); XposedBridge.log(t); }
    }
    private void hookOpen(XC_LoadPackage.LoadPackageParam lp, String a, String b){
        try{
            Class<?> ca=XposedHelpers.findClass(a, lp.classLoader); Class<?> cb=XposedHelpers.findClass(b, lp.classLoader);
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lp.classLoader, "openCamera", String.class, ca, cb, new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){ log("[Camera2] openCamera id="+p.args[0]+" overload="+a+","+b+" active="+active()+" mode="+mode()+" mediaPath="+MEDIA); }
            });
        }catch(Throwable t){ log("[WARN] openCamera overload not hooked "+a+","+b+" err="+t); }
    }
    private void hookCamera1(XC_LoadPackage.LoadPackageParam lp){
        try{ XposedHelpers.findAndHookMethod("android.hardware.Camera", lp.classLoader, "open", new XC_MethodHook(){ protected void beforeHookedMethod(MethodHookParam p){ log("[Camera1] open id=default active="+active()+" mode="+mode()); }}); }catch(Throwable t){ log("[WARN] Camera.open() "+t); }
        try{ XposedHelpers.findAndHookMethod("android.hardware.Camera", lp.classLoader, "open", int.class, new XC_MethodHook(){ protected void beforeHookedMethod(MethodHookParam p){ log("[Camera1] open id="+p.args[0]+" active="+active()+" mode="+mode()); }}); }catch(Throwable t){ log("[WARN] Camera.open(int) "+t); }
    }
    private static boolean active(){ return "1".equals(readSmall(ACTIVE).trim()); }
    private static String mode(){ String c=readSmall(CONFIG); int i=c.indexOf("\"mode\""); if(i<0)return "unknown"; int q=c.indexOf('"', c.indexOf(':',i)+1); int e=c.indexOf('"', q+1); return (q>=0&&e>q)?c.substring(q+1,e):"unknown"; }
    private static String readSmall(String path){ try{ File f=new File(path); if(!f.exists())return ""; FileInputStream in=new FileInputStream(f); byte[] b=new byte[(int)Math.min(f.length(),8192)]; int n=in.read(b); in.close(); return n>0?new String(b,0,n):""; }catch(Throwable t){ return ""; } }
    private static String safe(Object o){ if(o==null)return "null"; if(o instanceof String[]) return java.util.Arrays.toString((String[])o); return String.valueOf(o); }
    private static void log(String msg){ String line=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date())+" "+msg; Log.i(TAG,line); try{ File f=new File(LOG); File dir=f.getParentFile(); if(dir!=null)dir.mkdirs(); FileWriter w=new FileWriter(f,true); w.write(line+"\n"); w.close(); }catch(Throwable ignored){} }
}
