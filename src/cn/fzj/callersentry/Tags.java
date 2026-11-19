package cn.fzj.callersentry;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户自己给号码打的标签。
 *
 * 场景：接完一通电话才知道"这是快递/外卖/推销"，事后标一下，
 * 下次这个号码再打进来，提示条上直接显示"快递"，不用再猜。
 *
 * 存在 SharedPreferences 里，格式一行一条：号码|标签。
 * 上限 500 条，超出后丢掉最早写入的（LinkedHashMap 保持插入顺序）。
 */
public final class Tags {

    private static final String KEY = "tags";
    private static final int MAX = 500;

    /** 可选的标签，顺序即弹窗顺序；最后一项是删除 */
    public static final String[] CHOICES = {
            "快递", "外卖", "广告推销", "骚扰诈骗", "正常", "清除标记"
    };

    public static final String SPAM = "疑似骚扰电话";

    private Tags() {
    }

    private static Map<String, String> load(Context c) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        String s;
        try {
            s = Prefs.sp(c).getString(KEY, "");
        } catch (Throwable t) {
            return m;
        }
        if (s == null || s.length() == 0) return m;
        for (String line : s.split("\n")) {
            if (line.length() < 3) continue;
            int i = line.indexOf('|');
            if (i <= 0) continue;
            m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }

    private static void save(Context c, Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (n++ >= MAX) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append('|').append(e.getValue());
        }
        Prefs.sp(c).edit().putString(KEY, sb.toString()).apply();
    }

    public static String get(Context c, String num) {
        if (num == null || num.length() == 0) return null;
        String v = load(c).get(num);
        return (v == null || v.length() == 0) ? null : v;
    }

    public static void put(Context c, String num, String label) {
        if (num == null || num.length() == 0) return;
        Map<String, String> m = load(c);
        m.remove(num);
        m.put(num, label);
        save(c, m);
    }

    public static void remove(Context c, String num) {
        Map<String, String> m = load(c);
        if (m.remove(num) != null) save(c, m);
    }

    /** 全部标签，供批量操作遍历 */
    public static Map<String, String> entries(Context c) {
        return load(c);
    }

    public static boolean hasAny(Context c) {
        return !load(c).isEmpty();
    }

    /** 这个标签要不要按"警示"样式显示 */
    public static boolean warn(String label) {
        if (label == null) return false;
        return "广告推销".equals(label) || "骚扰诈骗".equals(label) || SPAM.equals(label);
    }
}
