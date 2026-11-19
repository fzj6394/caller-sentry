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

    /** 锁屏才挂全屏意图：解锁状态下挂它，部分 ROM 会直接把 Activity 顶上来 */
    private static boolean keyguardLocked(Context c) {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager)
                    c.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Android 14 起全屏意图需要单独授权，没授权就只能退化成横幅 */
    public static boolean canFullScreen(NotificationManager nm) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) return nm.canUseFullScreenIntent();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void show(Context c, String title, String text) {
        show(c, title, text, false);
    }

    /**
     * @param warn 命中号码库时为 true，锁屏卡片的配色要跟着变
     *
     * 顺带挂一条全屏意图：屏幕锁着时系统会把来电卡片直接顶到锁屏之上，
     * 这是在"后台启动 Activity 被限制"时唯一的兜底通道。
     */
    public static void show(Context c, String title, String text, boolean warn) {
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

            Intent card = new Intent(c, CallCardActivity.class)
                    .putExtra("l1", title)
                    .putExtra("l2", text)
                    .putExtra("warn", warn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            PendingIntent fsi = PendingIntent.getActivity(c, 1, card,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            boolean locked = keyguardLocked(c);
            Notification.Builder b = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);
            if (locked) b.setFullScreenIntent(fsi, true);
            Notification n = b.build();
            nm.notify(1001, n);
            Trace.add(c, "notify", "已发出：" + title + " / " + text
                    + (locked ? (canFullScreen(nm) ? "（锁屏·含全屏意图）" : "（锁屏·全屏意图未授权）") : ""));
        } catch (Throwable e) {
            Trace.add(c, "notify", "发送失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }
}
