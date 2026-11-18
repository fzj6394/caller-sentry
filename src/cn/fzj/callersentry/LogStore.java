package cn.fzj.callersentry;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 最近来电记录。
 * 写入走单线程队列：来电路径上只入队不落盘，避免在 respond 之后
 * 还要同步做一次 SharedPreferences 的整份序列化。
 */
public final class LogStore {

    private static final String KEY = "logs";
    private static final int MAX = 80;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private LogStore() {
    }

    public static void add(final Context c, final String number, final String area, final String action) {
        final Context app = c.getApplicationContext();
        try {
            IO.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        write(app, number, area, action);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            try {
                write(app, number, area, action);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void write(Context c, String number, String area, String action) {
        SharedPreferences sp = Prefs.sp(c);
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        String line = time + "  " + number + "  " + area + "  [" + action + "]";
        String old = sp.getString(KEY, "");
        StringBuilder sb = new StringBuilder(line);
        if (old.length() > 0) {
            String[] lines = old.split("\n");
            int keep = Math.min(lines.length, MAX - 1);
            for (int i = 0; i < keep; i++) {
                sb.append('\n').append(lines[i]);
            }
        }
        sp.edit().putString(KEY, sb.toString()).apply();
    }

    public static List<String> all(Context c) {
        List<String> list = new ArrayList<String>();
        String s = Prefs.sp(c).getString(KEY, "");
        if (s.length() > 0) {
            for (String line : s.split("\n")) {
                if (line.trim().length() > 0) list.add(line);
            }
        }
        return list;
    }

    public static void clear(Context c) {
        Prefs.sp(c).edit().remove(KEY).apply();
    }
}
