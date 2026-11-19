package cn.fzj.callersentry;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 开关与设置。
 *
 * 来电路径上读设置要快（每次读 SharedPreferences 都要过一遍 XML 缓存和加锁），
 * 所以这里维护一份内存快照：写的时候同时更新磁盘和内存，读的时候直接读内存。
 * 首次调用 ensure() 读一次盘，之后就零 IO。
 */
public final class Prefs {

    private static final String FILE = "sentry";

    private static volatile boolean loaded;

    private static volatile boolean notify = true;
    private static volatile boolean overlay = true;
    private static volatile boolean online = false;
    private static volatile boolean trace = false;
    private static volatile boolean autoCheck = true;
    private static volatile int blockMode = 0;
    private static volatile int repeatMinutes = 5;
    private static volatile int theme = 0;
    private static volatile String urlSeg;
    private static volatile String urlSpam;
    private static volatile String urlOnline = "";
    private static volatile String urlYellow = "";

    private Prefs() {
    }

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 读一次盘填快照；App 启动时预热，之后来电读设置不再碰磁盘 */
    public static void ensure(Context c) {
        if (loaded) return;
        synchronized (Prefs.class) {
            if (loaded) return;
            SharedPreferences p = sp(c);
            notify = p.getBoolean("notify", true);
            overlay = p.getBoolean("overlay", true);
            online = p.getBoolean("online_on", false);
            trace = p.getBoolean("trace_on", false);
            autoCheck = p.getBoolean("auto_check", true);
            blockMode = p.getInt("block_mode", 0);
            repeatMinutes = p.getInt("repeat_min", 5);
            theme = p.getInt("theme", 0);
            urlSeg = p.getString("url_seg", Update.DEFAULT_SEG_URL);
            urlSpam = p.getString("url_spam", Update.DEFAULT_SPAM_URL);
            urlOnline = p.getString("online_url", "");
            urlYellow = p.getString("url_yellow", "");
            loaded = true;
        }
    }

    /** 外部直接改过 SharedPreferences（比如导入配置）时调一下 */
    public static void invalidate() {
        loaded = false;
    }

    public static boolean notifyOn(Context c) {
        ensure(c);
        return notify;
    }

    public static void setNotifyOn(Context c, boolean v) {
        notify = v;
        sp(c).edit().putBoolean("notify", v).apply();
    }

    /** 运行日志开关：默认关，排查问题时再打开 */
    public static boolean traceOn(Context c) {
        ensure(c);
        return trace;
    }

    public static void setTraceOn(Context c, boolean v) {
        trace = v;
        sp(c).edit().putBoolean("trace_on", v).apply();
    }

    public static boolean overlayOn(Context c) {
        ensure(c);
        return overlay;
    }

    public static void setOverlayOn(Context c, boolean v) {
        overlay = v;
        sp(c).edit().putBoolean("overlay", v).apply();
    }

    /** 0=仅提示 1=静音放行 2=直接拒接 */
    public static int blockMode(Context c) {
        ensure(c);
        return blockMode;
    }

    public static void setBlockMode(Context c, int v) {
        blockMode = v;
        sp(c).edit().putInt("block_mode", v).apply();
    }

    /** 同一号码多少分钟内再次来电就正常响铃 */
    public static int repeatMinutes(Context c) {
        ensure(c);
        return repeatMinutes;
    }

    public static void setRepeatMinutes(Context c, int v) {
        repeatMinutes = v;
        sp(c).edit().putInt("repeat_min", v).apply();
    }

    public static boolean onlineOn(Context c) {
        ensure(c);
        return online;
    }

    public static void setOnlineOn(Context c, boolean v) {
        online = v;
        sp(c).edit().putBoolean("online_on", v).apply();
    }

    /** 在线兜底查询地址，{num} 会被替换成号码 */
    public static String onlineUrl(Context c) {
        ensure(c);
        return urlOnline == null ? "" : urlOnline;
    }

    public static void setOnlineUrl(Context c, String v) {
        urlOnline = v == null ? "" : v;
        sp(c).edit().putString("online_url", urlOnline).apply();
    }

    public static String urlSeg(Context c) {
        ensure(c);
        return urlSeg == null ? Update.DEFAULT_SEG_URL : urlSeg;
    }

    public static void setUrlSeg(Context c, String v) {
        urlSeg = v;
        sp(c).edit().putString("url_seg", v).apply();
    }

    public static String urlSpam(Context c) {
        ensure(c);
        return urlSpam == null ? Update.DEFAULT_SPAM_URL : urlSpam;
    }

    public static void setUrlSpam(Context c, String v) {
        urlSpam = v;
        sp(c).edit().putString("url_spam", v).apply();
    }

    /** 黄页源地址（自己维护的 num|名称|类别 文本） */
    public static String urlYellow(Context c) {
        ensure(c);
        return urlYellow == null ? "" : urlYellow;
    }

    public static void setUrlYellow(Context c, String v) {
        urlYellow = v == null ? "" : v;
        sp(c).edit().putString("url_yellow", urlYellow).apply();
    }

    public static boolean autoCheck(Context c) {
        ensure(c);
        return autoCheck;
    }

    public static void setAutoCheck(Context c, boolean v) {
        autoCheck = v;
        sp(c).edit().putBoolean("auto_check", v).apply();
    }

    /** 0 冰川蓝 1 晨雾白 2 暗夜灰 */
    public static int theme(Context c) {
        ensure(c);
        return theme;
    }

    public static void setTheme(Context c, int v) {
        theme = v;
        sp(c).edit().putInt("theme", v).apply();
    }
}
