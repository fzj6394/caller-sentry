package cn.fzj.callersentry;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 运行日志：记录来电链路上每一步的真实结果。
 *
 * 悬浮窗和通知的代码都写了 catch(Throwable ignored)，正常使用时不会有人察觉，
 * 但出问题时就完全看不到线索。这里把每一步（进入服务、判定、respond、
 * 悬浮窗权限、addView 是否成功、通知是否发出、异常原文）都记下来。
 *
 * 和 LogStore 一样走单线程队列，来电路径上只入队，不落盘。
 */
public final class Trace {

    private static final String KEY = "trace";
    private static final int MAX = 120;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private Trace() {
    }

    public static void add(final Context c, final String tag, final String msg) {
        if (c == null) return;
        final Context app;
        try {
            app = c.getApplicationContext();
        } catch (Throwable t) {
            return;
        }
        if (app == null) return;
        try {
            IO.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        write(app, tag, msg);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            try {
                write(app, tag, msg);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void write(Context c, String tag, String msg) {
        String time = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(new Date());
        String line = time + " [" + tag + "] " + (msg == null ? "" : msg);
        SharedPreferences sp = Prefs.sp(c);
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

    /** 拼成一段可以整份发出去的文本 */
    public static String dump(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("CallerSentry v").append(App.VERSION)
                .append(" · Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (api ").append(android.os.Build.VERSION.SDK_INT).append(")")
                .append(" · ").append(android.os.Build.MODEL).append('\n');

        boolean held = false;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = (android.app.role.RoleManager)
                        c.getSystemService(Context.ROLE_SERVICE);
                held = rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING);
            } catch (Throwable ignored) {
            }
        }
        sb.append("拦截角色 ").append(held ? "有" : "无")
                .append(" · 悬浮窗 ").append(Settings.canDrawOverlays(c) ? "有" : "无")
                .append(" · 通知 ").append(Notify.allowed(c) ? "允许" : "关闭")
                .append('\n');
        sb.append("---------- 日志 ----------\n");
        for (String line : all(c)) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static void clear(Context c) {
        Prefs.sp(c).edit().remove(KEY).apply();
    }
}
