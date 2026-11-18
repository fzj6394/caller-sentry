package cn.fzj.callersentry;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * 在线查询结果的本地缓存：查过一次就存下来，断网时照样能用。
 * 来电时要查得快，所以内存里放一份 Map，SharedPreferences 只负责落盘。
 * 落盘格式：一行一条「号码|标记|时间戳」，最多 500 条。
 */
public final class MarkCache {

    private static final String KEY = "marks";
    private static final int MAX = 500;

    private static Map<String, String> mem;

    private MarkCache() {
    }

    private static synchronized Map<String, String> map(Context c) {
        if (mem != null) return mem;
        Map<String, String> m = new HashMap<String, String>();
        try {
            String all = Prefs.sp(c).getString(KEY, "");
            if (all != null && all.length() > 0) {
                for (String line : all.split("\n")) {
                    int a = line.indexOf('|');
                    if (a <= 0) continue;
                    int b = line.indexOf('|', a + 1);
                    if (b <= a) continue;
                    m.put(line.substring(0, a), line.substring(a + 1, b));
                }
            }
        } catch (Throwable ignored) {
        }
        mem = m;
        return mem;
    }

    public static String get(Context c, String digits) {
        if (digits == null || digits.length() == 0) return null;
        return map(c).get(digits);
    }

    public static void put(Context c, String digits, String label) {
        if (digits == null || digits.length() == 0) return;
        if (label == null || label.length() == 0) return;
        Map<String, String> m = map(c);
        m.put(digits, label);

        StringBuilder sb = new StringBuilder();
        int kept = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (kept++ >= MAX) break;
            sb.append(e.getKey()).append('|').append(e.getValue().replace('|', ' '))
                    .append('|').append(now).append('\n');
        }
        try {
            Prefs.sp(c).edit().putString(KEY, sb.toString().trim()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static int size(Context c) {
        return map(c).size();
    }

    public static void clear(Context c) {
        map(c).clear();
        try {
            Prefs.sp(c).edit().remove(KEY).apply();
        } catch (Throwable ignored) {
        }
    }
}
