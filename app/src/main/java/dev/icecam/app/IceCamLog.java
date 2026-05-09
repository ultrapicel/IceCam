package dev.icecam.app;
import android.util.Log;
import java.io.*;import java.text.*;import java.util.*;
public final class IceCamLog{
 public static final String TAG="IceCam/App"; static final File DIR=new File("/data/data/dev.icecam.app/files/logs");
 public static void i(String tag,String msg){Log.i(tag,msg); write(tag,"I",msg,null);} public static void e(String tag,String msg,Throwable t){Log.e(tag,msg,t); write(tag,"E",msg,t);} 
 static synchronized void write(String tag,String level,String msg,Throwable t){try{DIR.mkdirs();File f=new File(DIR,"app.log");PrintWriter p=new PrintWriter(new FileWriter(f,true));p.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())+" "+level+" "+tag+": "+msg); if(t!=null)t.printStackTrace(p);p.close();}catch(Throwable ignored){}}
}
