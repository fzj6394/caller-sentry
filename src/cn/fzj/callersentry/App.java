package cn.fzj.callersentry;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

    static final String VERSION = "2.5";
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
                } catch (Throwable ignored) {
                }
                if (prev != null) {
                    prev.uncaughtException(t, e);
                }
            }
        });

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
                    ensureChannel(App.this);
                } catch (Throwable ignored) {
                }
            }
        }, "warmup").start();
    }

    /** 通知通道只建一次，别每次来电都 createNotificationChannel */
    static void ensureChannel(Context c) {
        try {
            NotificationManager nm = (NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(ScreeningService.CHANNEL) != null) return;
            NotificationChannel ch = new NotificationChannel(ScreeningService.CHANNEL,
                    "来电归属地", NotificationManager.IMPORTANCE_HIGH);
            ch.setShowBadge(false);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
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
