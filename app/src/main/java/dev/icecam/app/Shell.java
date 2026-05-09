package dev.icecam.app;
import java.io.*;import java.util.concurrent.*;
public final class Shell{
 public static String run(String cmd){StringBuilder out=new StringBuilder();try{Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String l;while((l=r.readLine())!=null)out.append(l).append('\n'); boolean ok=p.waitFor(8,TimeUnit.SECONDS);out.append("exit=").append(ok?p.exitValue():"timeout").append('\n');}catch(Throwable t){out.append("ERR ").append(t).append('\n');}return out.toString();}
}
