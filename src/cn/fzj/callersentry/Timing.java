package cn.fzj.callersentry;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 来电耗时记录：把一通电话的四个阶段耗时留下来，
 * 在「拦截记录与诊断」里直接看，不用电脑、不用 adb。
 * 格式：时间 号码 本地判定x ms · respond y ms · 归属地 z ms · 合计 n ms
 */
public final class Timing {

    private static final String KEY = "timings";
    private static final int MAX = 10;

    private Timing() {
    }

    public static void record(Context c, String number, long localMs, long respondMs,
                              long areaMs, long totalMs) {
        try {
            SharedPreferences sp = Prefs.sp(c);
            String time = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
            String line = time + "  " + number
                    + "  判定 " + localMs + "ms · respond " + respondMs
                    + "ms · 归属地 " + areaMs + "ms · 合计 " + totalMs + "ms";
            String old = sp.getString(KEY, "");
            StringBuilder sb = new StringBuilder(line);
            if (old.length() > 0) {
                String[] lines = old.split("\n");
                int keep = Math.min(lines.length, MAX - 1);
                for (int i = 0; i < keep; i++) sb.append('\n').append(lines[i]);
            }
            sp.edit().putString(KEY, sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static List<String> all(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            String s = Prefs.sp(c).getString(KEY, "");
            if (s.length() > 0) {
                for (String line : s.split("\n")) {
                    if (line.trim().length() > 0) out.add(line);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void clear(Context c) {
        try {
            Prefs.sp(c).edit().remove(KEY).apply();
        } catch (Throwable ignored) {
        }
    }
}
