package cn.fzj.callersentry;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 兜底：任何线程未捕获的异常都写到手机里，同时留一份到 SharedPreferences，
 * 界面下次打开能直接读到，方便定位闪退。
 */
public class App extends Application {

    static final String VERSION = "2.7.3";
    static final String CRASH_FILE = "crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    save(getApplicationContext(), e, "未捕获异常 @ " + t.getName());
                    Trace.add(getApplicationContext(), "crash",
                            e.getClass().getSimpleName() + ": " + e.getMessage()
                                    + " @ " + t.getName());
                } catch (Throwable ignored) {
                }
                if (prev != null) {
                    prev.uncaughtException(t, e);
                }
            }
        });

        migrateLanUrls();

        Trace.add(this, "app", "进程启动 v" + VERSION + " / " + Build.MODEL
                + " / api " + Build.VERSION.SDK_INT);

        // 预热：设置快照、通知通道、号段库（4.3MB 解压）、黄页、在线缓存。
        // 全放后台，来电时才不会现加载。
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Prefs.ensure(App.this);
                    PhoneLookup.warmup(App.this);
                    Yellow.size(App.this);
                    MarkCache.size(App.this);
                    Notify.ensureChannel(App.this);
                } catch (Throwable ignored) {
                }
            }
        }, "warmup").start();
    }


    /**
     * 早期调试时数据源地址被填成了局域网地址（192.168.x.x / :8765），
     * 换个网络就失效，还会让人以为"默认就是这个"。装新版时清一次。
     */
    private void migrateLanUrls() {
        try {
            android.content.SharedPreferences sp = Prefs.sp(this);
            if (sp.getBoolean("mig_lan", false)) return;
            android.content.SharedPreferences.Editor e = sp.edit();
            String[] keys = {"url_yellow", "url_seg", "url_spam", "online_url"};
            for (String k : keys) {
                String v = sp.getString(k, "");
                if (v != null && (v.contains("192.168.") || v.contains("127.0.0.1")
                        || v.contains("10.0.2.2") || v.contains(":8765"))) {
                    e.remove(k);
                }
            }
            e.putBoolean("mig_lan", true).apply();
            Prefs.invalidate();
        } catch (Throwable ignored) {
        }
    }

    static String deviceLine() {
        return "android " + Build.VERSION.RELEASE + " api " + Build.VERSION.SDK_INT
                + " / " + Build.MANUFACTURER + " " + Build.MODEL
                + " / CallerSentry v" + VERSION;
    }

    static void save(Context c, Throwable e, String tag) {
        StringWriter sw = new StringWriter();
        sw.write("==== " + tag + " ====\n");
        sw.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()) + "\n");
        sw.write(deviceLine() + "\n\n");
        e.printStackTrace(new PrintWriter(sw));
        String text = sw.toString();

        try {
            Prefs.sp(c).edit().putString("last_crash", text).apply();
        } catch (Throwable ignored) {
        }
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            File f = new File(dir, CRASH_FILE);
            FileOutputStream fo = new FileOutputStream(f, true);
            try {
                fo.write(text.getBytes("UTF-8"));
            } finally {
                fo.close();
            }
        } catch (Throwable ignored) {
        }
    }

    static String lastCrash(Context c) {
        try {
            return Prefs.sp(c).getString("last_crash", "");
        } catch (Throwable t) {
            return "";
        }
    }
}
