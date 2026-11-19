package cn.fzj.callersentry;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 来电时压一条提示。
 *
 * 设计（v2.7.3 起）：
 * - 无描边，只有一层低透明度的深色底，靠留白和字重分级说话；
 * - 三级文字：小字距标签（来电 / 快递 / 疑似骚扰）→ 主行（归属地）→ 带字距的号码；
 * - 全部左对齐，两侧各留 18dp，不再把三行居中堆成一张小标签；
 * - 命中号码库不铺红，只染标签和主行，底仍是深灰玻璃。
 *
 * 这里必须有一层底色：悬浮条画在别人的界面（来电全屏页）之上，
 * 白字直接压在亮色背景上会糊。锁屏卡片有整屏压暗，那边可以不要底色。
 *
 * addView 必须在有 Looper 的线程上跑，而 onScreenCall 是 binder 线程，
 * 所以统一 post 到主线程。
 */
public final class Overlay {

    private static View current;
    private static TextView overView;
    private static TextView titleView;
    private static GlowText glowView;
    private static GlowText subGlow;
    private static TextView subView;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingHide;

    private Overlay() {
    }

    public static boolean canDraw(Context c) {
        return Settings.canDrawOverlays(c);
    }

    private static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static void show(Context ctx, String line1, String line2, boolean warn) {
        show(ctx, null, line1, line2, warn, 7000);
    }

    public static void show(Context ctx, String line1, String line2, boolean warn, int holdMs) {
        show(ctx, null, line1, line2, warn, holdMs);
    }

    /**
     * @param overline 最上面那行小字（来电 / 快递 / 疑似骚扰），null 表示不显示
     * @param holdMs   0 表示不自动消失，由调用方在通话状态变化时收起
     */
    public static void show(final Context ctx, final String overline, final String line1,
                            final String line2, final boolean warn, final int holdMs) {
        if (!canDraw(ctx)) {
            Trace.add(ctx, "overlay", "没有悬浮窗权限，跳过");
            return;
        }
        final Context c = ctx.getApplicationContext();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                addOverlay(c, overline, line1, line2, warn, holdMs);
            }
        });
    }

    private static void addOverlay(Context c, String overline, String line1, String line2,
                                   boolean warn, int holdMs) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Trace.add(c, "overlay", "拿不到 WindowManager");
            return;
        }

        cancelPendingHide();
        hideNow();

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(c, 18), dp(c, 13), dp(c, 18), dp(c, 14));

        // 无描边的一层深色玻璃：透出底下的来电界面，又不至于把字糊掉
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(warn ? 0x8F1C100E : 0x8F0B1220);
        bg.setCornerRadius(dp(c, 20));
        box.setBackground(bg);
        box.setElevation(dp(c, 10));

        if (overline != null && overline.length() > 0) {
            GlowText over = glow(c, overline, 11, 0xB3, warn, 0.66f);
            over.setLetterSpacing(0.26f);
            over.setPadding(0, 0, 0, dp(c, 5));
            box.addView(over);
            overView = over;
        } else {
            overView = null;
        }

        GlowText title = glow(c, line1, 18, 0xFF, warn, 0f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.marquee(true);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(title);
        titleView = title;
        glowView = title;

        GlowText sub = glow(c, line2, 12.5f, 0xE6, warn, 0.42f);
        sub.setLetterSpacing(0.10f);
        sub.setPadding(0, dp(c, 4), 0, 0);
        box.addView(sub);
        subView = sub;
        subGlow = sub;

        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenW - dp(c, 64), dp(c, 300));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // 三星来电卡片占顶部一大块，提示条放它下方，避免被盖住
        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        lp.y = Math.max(dp(c, 120), (int) (screenH * 0.38f));

        try {
            wm.addView(box, lp);
            current = box;
            if (glowView != null) glowView.startGlow(glowView.phase());
            if (subGlow != null) subGlow.startGlow(subGlow.phase());
            Trace.add(c, "overlay", "addView 成功：" + line1);
        } catch (Throwable e) {
            Trace.add(c, "overlay", "addView 失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            current = null;
            overView = null;
            titleView = null;
            subView = null;
            glowView = null;
            return;
        }

        // holdMs <= 0：不主动收，但留 120 秒兜底，避免异常时卡在屏幕上
        final long delay = holdMs > 0 ? holdMs : 120_000L;
        pendingHide = new Runnable() {
            @Override
            public void run() {
                hideNow();
            }
        };
        MAIN.postDelayed(pendingHide, delay);
    }

    /** 一行居中、带流光的文字 */
    private static GlowText glow(Context c, String text, float sp, int alpha,
                                 boolean warn, float phase) {
        GlowText tv = new GlowText(c);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.colors(palette(warn, alpha));
        tv.setPhase(phase);
        tv.setShadowLayer(dp(c, 6), 0, dp(c, 1), 0x59000000);
        return tv;
    }

    /** 一条色带：正常冰蓝/浅紫/薄荷，骚扰走暖红系 */
    private static int[] palette(boolean warn, int alpha) {
        int a = (alpha & 0xFF) << 24;
        if (warn) {
            return new int[]{a | 0xFFCFC2, a | 0xFF8F73, a | 0xFF7FA2, a | 0xFFA96B, a | 0xFFCFC2};
        }
        return new int[]{a | 0xF2FAFF, a | 0x7FD8FF, a | 0xC0A6FF, a | 0x7FF0CE, a | 0xF2FAFF};
    }

    private static void cancelPendingHide() {
        if (pendingHide != null) {
            MAIN.removeCallbacks(pendingHide);
            pendingHide = null;
        }
    }

    public static void hide() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                cancelPendingHide();
                hideNow();
            }
        });
    }

    private static void hideNow() {
        if (current == null) return;
        try {
            WindowManager wm = (WindowManager) current.getContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.removeView(current);
        } catch (Throwable ignored) {
        }
        if (glowView != null) glowView.stopGlow();
        if (subGlow != null) subGlow.stopGlow();
        current = null;
        overView = null;
        titleView = null;
        subView = null;
        glowView = null;
        subGlow = null;
    }

    /** 归属地算出来得晚一点时（冷启动），先把号码显示出来，回头把文字补上。 */
    public static void update(final String line1, final String line2) {
        update(null, line1, line2);
    }

    public static void update(final String overline, final String line1, final String line2) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (overView != null && overline != null && overline.length() > 0) {
                        overView.setText(overline);
                    }
                    if (titleView != null && line1 != null) titleView.setText(line1);
                    if (subView != null && line2 != null) subView.setText(line2);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
