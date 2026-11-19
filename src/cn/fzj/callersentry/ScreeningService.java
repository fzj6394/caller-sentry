package cn.fzj.callersentry;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

/**
 * 来电识别与拦截的核心。
 *
 * 时延上的三条硬规矩（v2.3）：
 * 1. 本地判定全部走内存（黄页 Map、号码库、设置快照），不碰磁盘、不联网；
 * 2. **判定完立刻 respondToCall**，系统才推来电界面，慢活儿一律放后面；
 * 3. 归属地库还没加载完（冷启动）就不等它：先显示号码，加载完再补文字。
 */
public class ScreeningService extends CallScreeningService {

    private static final int MODE_TIP = 0;
    private static final int MODE_SILENCE = 1;
    private static final int MODE_REJECT = 2;

    /** 系统第一次绑定（或强杀后重新绑定）本服务时走这里，用来区分「没绑定」和「绑定了没调用」 */
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            long now = System.currentTimeMillis();
            int n = Prefs.sp(this).getInt("bind_count", 0) + 1;
            Prefs.sp(this).edit()
                    .putLong("bind_last", now)
                    .putInt("bind_count", n)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onScreenCall(Call.Details details) {
        final long t0 = System.nanoTime();
        boolean responded = false;
        // 先记账：哪怕后面抛异常，也能证明系统确实把电话交过来了
        touch(details);
        try {
            Uri h = details.getHandle();
            Trace.add(this, "call", "进入 dir=" + details.getCallDirection()
                    + " handle=" + (h == null ? "null" : h.getSchemeSpecificPart()));
        } catch (Throwable ignored) {
        }
        try {
            if (details.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
                respondToCall(details, new CallResponse.Builder().build());
                return;
            }

            Uri handle = details.getHandle();
            String raw = handle == null ? null : handle.getSchemeSpecificPart();
            String digits = NumUtil.clean(raw);
            if (digits.length() == 0) {
                respondToCall(details, new CallResponse.Builder().build());
                return;
            }

            // ---------- 1. 本地判定：纯内存 ----------
            String exactHit = Blacklist.matchExact(this, digits);
            String yellow = Yellow.label(this, digits);
            String mark = null;
            boolean spam = false;

            if (exactHit != null) {
                mark = Tags.SPAM;
                spam = true;
            } else if (yellow != null) {
                mark = yellow;
            } else {
                String ruleHit = Blacklist.match(this, digits);
                if (ruleHit != null) {
                    mark = Tags.SPAM;
                    spam = true;
                } else {
                    mark = MarkCache.get(this, digits);
                    spam = mark != null;
                }
            }

            int mode = Prefs.blockMode(this);
            boolean repeat = isRepeatSoon(digits);
            remember(digits);
            final long tLocal = System.nanoTime();

            // ---------- 2. 立刻 respond ----------
            CallResponse.Builder b = new CallResponse.Builder();
            boolean rejected = false;
            boolean silenced = false;
            if (spam && !repeat && mode == MODE_REJECT) {
                b.setDisallowCall(true)
                        .setRejectCall(true)
                        .setSilenceCall(false)
                        .setSkipCallLog(false)
                        .setSkipNotification(true);
                rejected = true;
            } else if (spam && !repeat && mode == MODE_SILENCE) {
                b.setDisallowCall(false)
                        .setSilenceCall(true)
                        .setSkipCallLog(false);
                silenced = true;
            }
            respondToCall(details, b.build());
            responded = true;
            final long tResp = System.nanoTime();
            Trace.add(this, "respond", ms(t0, tResp) + "ms rejected=" + rejected
                    + " silenced=" + silenced);

            // ---------- 3. 归属地：库没加载完就不等 ----------
            PhoneLookup lib = PhoneLookup.peek();
            String area = "";
            boolean areaPending = false;
            if (lib != null) {
                area = describe(raw, digits, lib);
            } else {
                areaPending = true;
                PhoneLookup.warmup(this);
            }
            final long tArea = System.nanoTime();

            if (rejected) {
                LogStore.add(this, digits, area, "已拒接·" + mark);
                Timing.record(this, digits, ms(t0, tLocal), ms(tLocal, tResp),
                        ms(tResp, tArea), ms(t0, tArea));
                return;
            }
            if (silenced) {
                LogStore.add(this, digits, area, "静音放行·" + mark);
                show(digits, area, mark, true);
                Timing.record(this, digits, ms(t0, tLocal), ms(tLocal, tResp),
                        ms(tResp, tArea), ms(t0, tArea));
                return;
            }

            if (spam) {
                LogStore.add(this, digits, area, (repeat ? "重复来电放行·" : "提示·") + mark);
            } else {
                LogStore.add(this, digits, area, "提示");
            }
            show(digits, area, mark, spam);
            Timing.record(this, digits, ms(t0, tLocal), ms(tLocal, tResp),
                    ms(tResp, tArea), ms(t0, tArea));

            // ---------- 4. 冷启动时库还没好：等它好了把文字补上 ----------
            if (areaPending) {
                fillAreaLater(digits, mark, spam);
            }

            // ---------- 5. 在线兜底：异步，只写缓存 ----------
            if (mark == null && Prefs.onlineOn(this)) {
                onlineAsync(digits, area);
            }
        } catch (Throwable t) {
            Trace.add(this, "error", t.getClass().getSimpleName() + ": " + t.getMessage());
            if (!responded) {
                try {
                    respondToCall(details, new CallResponse.Builder().build());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String describe(String raw, String digits, PhoneLookup lib) {
        try {
            PhoneLookup.Result r = null;
            if (digits.length() >= 7 && !NumUtil.looksInternational(raw)) {
                r = lib.lookupMobile(digits);
            }
            return NumUtil.describe(raw, digits, r);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 冷启动期：号段库加载完之后，把归属地补进已显示的悬浮条 */
    private void fillAreaLater(final String digits, final String mark, final boolean spam) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PhoneLookup lib = PhoneLookup.get(ScreeningService.this);
                    String area = describe(null, digits, lib);
                    if (area == null || area.length() == 0) return;
                    String[] lines = lines(digits, area, mark);
                    Overlay.update(lines[0], lines[1], lines[2]);
                    LogStore.add(ScreeningService.this, digits, area, "归属地补齐");
                } catch (Throwable ignored) {
                }
            }
        }, "area-fill").start();
    }

    /** 本地没命中时的兜底查询：查到就存本地，下次断网也能用 */
    private void onlineAsync(final String digits, final String area) {
        final String url = Prefs.onlineUrl(this);
        if (url == null || url.trim().length() == 0) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String online = OnlineLookup.lookup(digits, url, 2500);
                    if (online != null) {
                        MarkCache.put(ScreeningService.this, digits, online);
                        LogStore.add(ScreeningService.this, digits, area, "在线查到 " + online);
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "online-lookup").start();
    }

    /** 返回 {顶部小字, 主行, 号码行}；小字可能为 null（没有标签时不占一行） */
    private static String[] lines(String digits, String area, String mark) {
        String num = NumUtil.pretty(digits);
        String over = null;
        String line1;
        if (area != null && area.length() > 0) {
            over = mark;
            line1 = area;
        } else if (mark != null) {
            line1 = mark;
        } else {
            line1 = "来电";
        }
        return new String[]{over, line1, num};
    }

    private void show(String digits, String area, String mark, boolean warn) {
        String[] l = lines(digits, area, mark);
        boolean ovOn = Prefs.overlayOn(this);
        boolean ovOk = Overlay.canDraw(this);
        boolean ntOn = Prefs.notifyOn(this);
        boolean ntOk = Notify.allowed(this);
        Trace.add(this, "show", "overlayOn=" + ovOn + " 权限=" + ovOk
                + " notifyOn=" + ntOn + " 通知允许=" + ntOk
                + " 内容=" + l[0] + " / " + l[1] + " / " + l[2]);
        if (ovOn && ovOk) {
            Overlay.show(this, l[0], l[1], l[2], warn, 7000);
        }
        if (ntOn) {
            Notify.show(this, l[1], l[2], warn);
        }
    }

    /** 同一号码在设定时间内的再次来电，认为是要紧事，正常响铃 */
    private boolean isRepeatSoon(String digits) {
        String lastNum = Prefs.sp(this).getString("last_num", "");
        long lastTs = Prefs.sp(this).getLong("last_ts", 0L);
        if (!digits.equals(lastNum)) return false;
        long gap = System.currentTimeMillis() - lastTs;
        return gap > 0 && gap < Prefs.repeatMinutes(this) * 60_000L;
    }

    /** 每进一次 onScreenCall 就记一笔，不论方向、不论成败 */
    private void touch(Call.Details details) {
        try {
            long now = System.currentTimeMillis();
            // 存归一化后的方向：0 来电 / 1 拨出 / -1 未知。
            // 注意 Call.Details 与 CallLog 的常量不一样，前者 INCOMING = 0、OUTGOING = 1。
            int raw = details.getCallDirection();
            int dir = raw == Call.Details.DIRECTION_INCOMING ? 0
                    : raw == Call.Details.DIRECTION_OUTGOING ? 1 : -1;

            int total = Prefs.sp(this).getInt("svc_count", 0) + 1;
            android.content.SharedPreferences.Editor e = Prefs.sp(this).edit()
                    .putLong("svc_last", now)
                    .putInt("svc_count", total)
                    .putInt("svc_dir", dir);
            // onCreate 的记账是异步落盘的，极端情况下会被系统回收吞掉，这里补一次
            if (Prefs.sp(this).getInt("bind_count", 0) == 0) {
                e.putInt("bind_count", 1).putLong("bind_last", now);
            }
            if (dir == 0) {
                e.putInt("svc_in", Prefs.sp(this).getInt("svc_in", 0) + 1);
            }
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    private void remember(String digits) {
        Prefs.sp(this).edit()
                .putString("last_num", digits)
                .putLong("last_ts", System.currentTimeMillis())
                .apply();
    }

    private static long ms(long from, long to) {
        return (to - from) / 1_000_000L;
    }

}
