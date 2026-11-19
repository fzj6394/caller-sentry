package cn.fzj.callersentry;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * 来电时压一条提示（解锁状态用）。
 *
 * 设计：
 * - 一层低透明度深色底，无描边；三行文字居中；
 * - 文字用 GlowView 自己画，色带高饱和、首尾同色循环，任意一帧都带颜色；
 * - 命中号码库只染色带，不整块铺红。
 *
 * 这里必须有一层底色：悬浮条画在别人的界面（来电全屏页）之上，
 * 亮色文字直接压在亮色背景上会糊。锁屏卡片有整屏压暗，那边可以不要底色。
 *
 * addView 必须在有 Looper 的线程上跑，而 onScreenCall 是 binder 线程，
 * 所以统一 post 到主线程。
 */
public final class Overlay {

    private static View current;
    private static GlowView overView;
    private static GlowView titleView;
    private static GlowView subView;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingHide;

    private Overlay() {
    }

    public static boolean canDraw(Context c) {
        return Settings.canDrawOverlays(c);
    }

    private static int dp(Context c, float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
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
        box.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 15));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xA60B1220);
        bg.setCornerRadius(dp(c, 20));
        box.setBackground(bg);
        box.setElevation(dp(c, 10));

        if (overline != null && overline.length() > 0) {
            GlowView over = new GlowView(c);
            over.text(overline).sizeSp(11).colors(palette(warn, 0xCC)).setPhase(0.66f);
            over.letterSpacing(0.26f);
            over.setPadding(0, 0, 0, dp(c, 5));
            box.addView(over);
            overView = over;
        } else {
            overView = null;
        }

        GlowView title = new GlowView(c);
        title.text(line1).sizeSp(19).colors(palette(warn, 0xFF)).setPhase(0f);
        title.bold(true);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(title);
        titleView = title;

        GlowView sub = new GlowView(c);
        sub.text(line2).sizeSp(12.5f).colors(palette(warn, 0xEE)).setPhase(0.45f);
        sub.letterSpacing(0.10f);
        sub.setPadding(0, dp(c, 4), 0, 0);
        sub.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(sub);
        subView = sub;

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
            if (overView != null) overView.startGlow(overView.phase());
            if (titleView != null) titleView.startGlow(titleView.phase());
            if (subView != null) subView.startGlow(subView.phase());
            Trace.add(c, "overlay", "addView 成功：" + line1);
        } catch (Throwable e) {
            Trace.add(c, "overlay", "addView 失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            current = null;
            overView = null;
            titleView = null;
            subView = null;
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

    /** 一条高饱和色带，首尾同色，REPEAT 循环无接缝 */
    private static int[] palette(boolean warn, int alpha) {
        int a = (alpha & 0xFF) << 24;
        if (warn) {
            return new int[]{a | 0xFFE0D2, a | 0xFF7A45, a | 0xFF4E8A,
                    a | 0xFFB324, a | 0xFF7A45, a | 0xFFE0D2};
        }
        return new int[]{a | 0xFFFFFF, a | 0x3FE0FF, a | 0x9B8CFF,
                a | 0xFF7FD0, a | 0x4BFFC4, a | 0xFFFFFF};
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
        if (titleView != null) titleView.stopGlow();
        if (overView != null) overView.stopGlow();
        if (subView != null) subView.stopGlow();
        if (current == null) {
            overView = null;
            titleView = null;
            subView = null;
            return;
        }
        try {
            WindowManager wm = (WindowManager) current.getContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.removeView(current);
        } catch (Throwable ignored) {
        }
        current = null;
        overView = null;
        titleView = null;
        subView = null;
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
                        overView.text(overline);
                    }
                    if (titleView != null && line1 != null) titleView.text(line1);
                    if (subView != null && line2 != null) subView.text(line2);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
