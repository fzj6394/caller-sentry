package cn.fzj.callersentry;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自己的号码库（黑名单）。
 * 规则两种写法：
 *   完整号码  13800138000   —— 全等匹配
 *   前缀带星  95*  400*  170*  —— 前缀匹配
 * 号码比较前都会先清洗（去空格/横线/去掉 86 前缀）。
 */
public final class Blacklist {

    private static final String KEY = "rules";

    private Blacklist() {
    }

    public static String normalizeRule(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        String t = raw.trim();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '*' ) sb.append('*');
            else if (c >= '0' && c <= '9') sb.append(c);
        }
        String s = sb.toString();
        // 星号只保留在结尾
        s = s.replace("*", "");
        if (t.indexOf('*') >= 0 && s.length() > 0) s = s + "*";
        if (s.endsWith("*") && s.length() == 1) return "";
        String digitsPart = s.endsWith("*") ? s.substring(0, s.length() - 1) : s;
        if (digitsPart.startsWith("0086")) digitsPart = digitsPart.substring(4);
        else if (digitsPart.length() == 13 && digitsPart.startsWith("86")) digitsPart = digitsPart.substring(2);
        if (digitsPart.length() < 3) return "";
        return s.endsWith("*") ? digitsPart + "*" : digitsPart;
    }

    public static List<String> load(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        Set<String> set = sp.getStringSet(KEY, null);
        List<String> list = new ArrayList<String>();
        if (set != null) list.addAll(set);
        Collections.sort(list);
        return list;
    }

    public static int count(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        Set<String> set = sp.getStringSet(KEY, null);
        return set == null ? 0 : set.size();
    }

    public static boolean add(Context c, String raw) {
        String rule = normalizeRule(raw);
        if (rule.length() == 0) return false;
        SharedPreferences sp = Prefs.sp(c);
        Set<String> set = new HashSet<String>(load(c));
        set.add(rule);
        sp.edit().putStringSet(KEY, set).apply();
        return true;
    }

    public static void addAll(Context c, List<String> rawList) {
        SharedPreferences sp = Prefs.sp(c);
        Set<String> set = new HashSet<String>(load(c));
        for (String r : rawList) {
            String rule = normalizeRule(r);
            if (rule.length() > 0) set.add(rule);
        }
        sp.edit().putStringSet(KEY, set).apply();
    }

    public static void remove(Context c, String rule) {
        SharedPreferences sp = Prefs.sp(c);
        Set<String> set = new HashSet<String>(load(c));
        set.remove(rule);
        sp.edit().putStringSet(KEY, set).apply();
    }

    public static void clear(Context c) {
        Prefs.sp(c).edit().remove(KEY).apply();
    }

    /** 只比完整号码（忽略 95* 这类前缀规则），用于"用户亲手拉黑的号优先于黄页" */
    public static String matchExact(Context c, String digits) {
        if (digits == null || digits.length() == 0) return null;
        for (String r : load(c)) {
            if (!r.endsWith("*") && r.equals(digits)) return r;
        }
        return null;
    }

    /** 返回命中的规则，没命中返回 null */
    public static String match(Context c, String digits) {
        if (digits == null || digits.length() == 0) return null;
        List<String> rules = load(c);
        String alt = digits;
        if (alt.startsWith("0") && alt.length() > 1) {
            // 座机也可能被写成 0755xxxx 或 +86755xxxx，两种都比一遍
        }
        for (String r : rules) {
            if (r.endsWith("*")) {
                String p = r.substring(0, r.length() - 1);
                if (p.length() > 0 && digits.startsWith(p)) return r;
            } else if (r.equals(digits) || r.equals(alt)) {
                return r;
            }
        }
        return null;
    }
}
