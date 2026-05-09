package com.icecam.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class Shell {
    private Shell() {}

    public static Result run(String[] cmd, long timeoutMs) {
        Result result = new Result();
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                result.timeout = true;
                p.destroyForcibly();
                result.exitCode = -999;
            } else {
                result.exitCode = p.exitValue();
            }
            result.stdout = readAll(p.getInputStream());
            result.stderr = readAll(p.getErrorStream());
        } catch (Throwable t) {
            result.exitCode = -998;
            result.stderr = t.getClass().getSimpleName() + ": " + t.getMessage();
            IceLog.e("Shell", "Command failed", t);
        }
        return result;
    }

    private static String readAll(java.io.InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    public static String oneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "<empty>";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static final class Result {
        public int exitCode = -1;
        public boolean timeout = false;
        public String stdout = "";
        public String stderr = "";
    }
}
