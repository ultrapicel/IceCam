
package com.icecam.dev.hook;

import android.util.Log;
import java.io.*;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IceCamHook implements IXposedHookLoadPackage {
    private static final String TAG="IceCam/Hook";
    private static final String LOG="/data/adb/icecam/logs/hook.log";
    private static final String CONFIG="/data/adb/icecam/config/app_config.json";
    private static final String ACTIVE="/data/adb/icecam/state/active";

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp)throws Throwable{
        log("[LOAD] package="+lp.packageName+" process="+lp.processName+" active="+active()+" cfg="+shortConfig());
        hookCamera2(lp); hookCamera1(lp);
    }

    private void hookCamera2(final XC_LoadPackage.LoadPackageParam lp){
        try{
            final Class<?> cls=XposedHelpers.findClass("android.hardware.camera2.CameraManager",lp.classLoader);
            XposedHelpers.findAndHookMethod(cls,"getCameraIdList",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){log("[Camera2] getCameraIdList before pkg="+lp.packageName+" active="+active()+" mode="+mode());}
                @Override protected void afterHookedMethod(MethodHookParam p){log("[Camera2] getCameraIdList after pkg="+lp.packageName+" result="+safe(p.getResult()));}
            });
            XposedHelpers.findAndHookMethod(cls,"getCameraCharacteristics",String.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){log("[Camera2] getCameraCharacteristics pkg="+lp.packageName+" id="+p.args[0]+" active="+active());}
            });
            for(Method m:cls.getDeclaredMethods()){
                if(!"openCamera".equals(m.getName()))continue;
                XposedBridge.hookMethod(m,new XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam p){
                        Object id=p.args!=null&&p.args.length>0?p.args[0]:"?";
                        String md=mode();
                        log("[Camera2] openCamera pkg="+lp.packageName+" id="+id+" active="+active()+" mode="+md+" mediaPath=/data/adb/icecam/media/source");
                        if(active() && "block-open-test".equals(md)) p.setResult(null);
                    }
                });
            }
            log("[OK] Camera2 hooks installed pkg="+lp.packageName);
        }catch(Throwable t){log("[ERR] Camera2 hook failed pkg="+lp.packageName+" "+t);}
    }

    private void hookCamera1(final XC_LoadPackage.LoadPackageParam lp){
        try{
            Class<?> cam=XposedHelpers.findClass("android.hardware.Camera",lp.classLoader);
            XposedHelpers.findAndHookMethod(cam,"open",int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){log("[Camera1] open pkg="+lp.packageName+" id="+p.args[0]+" active="+active()+" mode="+mode());}
            });
            XposedHelpers.findAndHookMethod(cam,"open",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){log("[Camera1] open default pkg="+lp.packageName+" active="+active()+" mode="+mode());}
            });
            log("[OK] Camera1 hooks installed pkg="+lp.packageName);
        }catch(Throwable t){log("[ERR] Camera1 hook failed pkg="+lp.packageName+" "+t);}
    }

    private static boolean active(){ try { return read(ACTIVE).trim().equals("1"); } catch(Throwable t){ return false; } }
    private static String mode(){String c=readConfig(); if(c.contains("\"mode\":\"block-open-test\""))return"block-open-test"; if(c.contains("\"mode\":\"virtual-stub\""))return"virtual-stub"; return"log-only";}
    private static String shortConfig(){String c=readConfig(); return c.length()>220?c.substring(0,220)+"...":c;}
    private static String readConfig(){return read(CONFIG);}
    private static String read(String path){
        try{
            File f=new File(path); if(!f.exists())return"{}";
            FileInputStream fis=new FileInputStream(f); ByteArrayOutputStream bos=new ByteArrayOutputStream();
            byte[] buf=new byte[4096]; int n; while((n=fis.read(buf))>0)bos.write(buf,0,n); fis.close(); return bos.toString();
        }catch(Throwable t){return"{}";}
    }
    private static String safe(Object o){if(o==null)return"null"; if(o instanceof String[]){String[]a=(String[])o; StringBuilder sb=new StringBuilder("["); for(int i=0;i<a.length;i++){if(i>0)sb.append(","); sb.append(a[i]);} return sb.append("]").toString();} return String.valueOf(o);}
    private static synchronized void log(String s){String line=System.currentTimeMillis()+" "+s; Log.i(TAG,line); try{File f=new File(LOG); File p=f.getParentFile(); if(p!=null)p.mkdirs(); FileWriter fw=new FileWriter(f,true); fw.write(line+"\n"); fw.close();}catch(Throwable ignored){} try{XposedBridge.log("IceCam "+line);}catch(Throwable ignored){}}
}
