package cn.fzj.callersentry;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.telephony.TelephonyManager;

/**
 * 兼容模式：不依赖「来电显示和骚扰拦截」角色，靠通话状态广播拿号码。
 *
 * 用在 One UI 这类把来电先交给自家拨号器的系统上——拦截服务拿不到来电，
 * 但 PHONE_STATE 广播里的号码是给得出的（实测三星 SM-S9360 可以）。
 *
 * 代价：必须有常驻通知（前台服务硬性要求），且只能提示，不能拒接/静音。
 */
public class Watcher extends Service {

    private static final String CHANNEL = "watcher";
    private static final int NOTICE_ID = 2001;

    private BroadcastReceiver rx;

    private static volatile boolean running;

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, Watcher.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
            Prefs.sp(c).edit().putBoolean("compat_on", true).apply();
            Trace.add(c, "watcher", "请求启动");
        } catch (Throwable t) {
            Trace.add(c, "watcher", "启动失败 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** 服务是否真的在跑：覆盖安装、被系统回收后这个会是 false */
    public static boolean isRunning() {
        return running;
    }

    public static void stop(Context c) {
        try {
            Prefs.sp(c).edit().putBoolean("compat_on", false).apply();
            c.stopService(new Intent(c, Watcher.class));
            Trace.add(c, "watcher", "已停止");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        Trace.add(this, "watcher", "服务创建");
        startForegroundNotice();

        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent in) {
                onCallState(in);
            }
        };
        try {
            registerReceiver(rx, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
            Trace.add(this, "watcher", "已注册通话状态监听");
        } catch (Throwable t) {
            Trace.add(this, "watcher", "注册失败 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        if (rx != null) {
            try {
                unregisterReceiver(rx);
            } catch (Throwable ignored) {
            }
            rx = null;
        }
        running = false;
        Trace.add(this, "watcher", "服务销毁");
        super.onDestroy();
    }

    private void startForegroundNotice() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    NotificationChannel ch = new NotificationChannel(CHANNEL, "兼容模式运行中",
                            NotificationManager.IMPORTANCE_MIN);
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
            } catch (Throwable ignored) {
            }
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("来电哨兵 · 兼容模式")
                .setContentText("来电时提示归属地")
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTICE_ID, n);
            }
            Trace.add(this, "watcher", "前台通知已挂");
        } catch (Throwable t) {
            Trace.add(this, "watcher", "startForeground 失败 " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
        }
    }

    private void onCallState(Intent in) {
        try {
            String state = in.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                Trace.add(this, "watcher", "状态=" + state);
                // 接听（OFFHOOK）或挂断/未接（IDLE）都收起提示条
                if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)
                        || TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    Overlay.hide();
                }
                return;
            }
            String raw = in.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (raw == null) raw = in.getStringExtra("incoming_number");
            final String digits = NumUtil.clean(raw);
            Trace.add(this, "watcher", "响铃 号码=" + (digits.length() == 0 ? "空" : digits));
            if (digits.length() == 0) return;
            note();

            final String mark = label(digits);
            PhoneLookup lib = null;
            try {
                lib = PhoneLookup.peek();
            } catch (Throwable ignored) {
            }

            String area = "";
            if (lib != null && digits.length() >= 7) {
                try {
                    area = NumUtil.describe(raw, digits, lib.lookupMobile(digits));
                } catch (Throwable ignored) {
                }
            }
            show(digits, area, mark);

            if (lib == null) {
                // 冷启动：库还没好，先显示号码，加载完再补归属地
                final String rawRef = raw;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            PhoneLookup loaded = PhoneLookup.get(Watcher.this);
                            String a = NumUtil.describe(rawRef, digits, loaded.lookupMobile(digits));
                            if (a != null && a.length() > 0) {
                                String[] l = lines(digits, a, mark);
                                Overlay.update(l[0], l[1]);
                                Trace.add(Watcher.this, "watcher", "归属地补齐 " + a);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }, "watcher-area").start();
            }
        } catch (Throwable t) {
            Trace.add(this, "watcher", "处理失败 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** 兼容模式自己的计数：和拦截服务的 svc_* 分开，避免混为一谈 */
    private void note() {
        try {
            long now = System.currentTimeMillis();
            int n = Prefs.sp(this).getInt("w_count", 0) + 1;
            Prefs.sp(this).edit()
                    .putLong("w_last", now)
                    .putInt("w_count", n)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private String label(String digits) {
        try {
            // 用户自己标的优先级最高：他接过、知道是谁
            String tag = Tags.get(this, digits);
            if (tag != null) return tag;

            if (Blacklist.matchExact(this, digits) != null) return Tags.SPAM;
            String y = Yellow.label(this, digits);
            if (y != null) return y;
            if (Blacklist.match(this, digits) != null) return Tags.SPAM;
            String cache = MarkCache.get(this, digits);
            if (cache != null) return Tags.SPAM;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String[] lines(String digits, String area, String mark) {
        String line1;
        String line2 = digits;
        if (mark != null) {
            line1 = mark;
            if (area != null && area.length() > 0 && digits.length() >= 7) {
                line2 = area + "　" + digits;
            }
        } else if (area != null && area.length() > 0) {
            line1 = area;
        } else {
            line1 = "来电";
        }
        return new String[]{line1, line2};
    }

    private void show(String digits, String area, String mark) {
        String[] l = lines(digits, area, mark);
        boolean ovOn = Prefs.overlayOn(this);
        boolean ovOk = Overlay.canDraw(this);
        boolean ntOn = Prefs.notifyOn(this);
        Trace.add(this, "watcher", "显示 " + l[0] + " / " + l[1]
                + " · 悬浮窗=" + (ovOn && ovOk) + " 通知=" + ntOn);
        // holdMs = 0：不按时间收，等接听/挂断的状态变化再收
        if (ovOn && ovOk) Overlay.show(this, l[0], l[1], Tags.warn(mark), 0);
        if (ntOn) Notify.show(this, l[0], l[1]);
        try {
            LogStore.add(this, digits, area, mark == null ? "兼容模式·提示" : "兼容模式·" + mark);
        } catch (Throwable ignored) {
        }
    }
}
