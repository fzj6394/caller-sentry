package cn.fzj.callersentry;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** 通知栏提示：来电时与悬浮条并行发一条，悬浮条被系统拦时它就是唯一可见的 */
public final class Notify {

    public static final String CHANNEL = "caller_id";

    private Notify() {
    }

    /** 通道只建一次，别每次来电都 create */
    public static void ensureChannel(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL) != null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL, "来电归属地",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setShowBadge(false);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
        }
    }

    /** 通知权限是否可用（Android 13+ 需要 POST_NOTIFICATIONS） */
    public static boolean allowed(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void show(Context c, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Trace.add(c, "notify", "拿不到 NotificationManager");
                return;
            }
            ensureChannel(c);

            Intent open = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .build();
            nm.notify(1001, n);
            Trace.add(c, "notify", "已发出：" + title + " / " + text);
        } catch (Throwable e) {
            Trace.add(c, "notify", "发送失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }
}
