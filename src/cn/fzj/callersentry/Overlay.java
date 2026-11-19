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
 * 样式：液态玻璃——半透明白、细描边、柔和渐变，和系统来电卡片同色系。
 * 命中号码库时不再整块铺红，只把描边和文字染成暖红，警示但不刺眼。
 *
 * 显示时长：
 * - 兼容模式传 holdMs = 0，表示"不自动消失"，接听/挂断时调用方 hide()；
 * - 其它入口（预览、拦截服务）传 7000，7 秒后自己收。
 *
 * 注意：addView 必须在有 Looper 的线程上跑，而 onScreenCall 是 binder 线程，
 * 所以这里统一 post 到主线程。
 */
public final class Overlay {

    private static View current;
    private static TextView titleView;
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
        show(ctx, line1, line2, warn, 7000);
    }

    /** @param holdMs 0 表示不自动消失，由调用方在通话状态变化时收起 */
    public static void show(final Context ctx, final String line1, final String line2,
                            final boolean warn, final int holdMs) {
        if (!canDraw(ctx)) {
            Trace.add(ctx, "overlay", "没有悬浮窗权限，跳过");
            return;
        }
        final Context c = ctx.getApplicationContext();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                addOverlay(c, line1, line2, warn, holdMs);
            }
        });
    }

    private static void addOverlay(Context c, String line1, String line2, boolean warn, int holdMs) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Trace.add(c, "overlay", "拿不到 WindowManager");
            return;
        }

        cancelPendingHide();
        hideNow();

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));

        // 液态玻璃：上偏白、下略带冷灰，半透明透出底下的来电卡片
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                warn ? new int[]{0xF7FFF5F3, 0xEFF7E4E0}
                        : new int[]{0xF7FFFFFF, 0xEFF0F4FA});
        bg.setCornerRadius(dp(c, 20));
        bg.setStroke(dp(c, 1), warn ? 0x59D9483B : 0x2E0B1220);
        box.setBackground(bg);
        box.setElevation(dp(c, 10));

        TextView t1 = new TextView(c);
        t1.setText(line1);
        t1.setTextColor(warn ? 0xFFB3322A : 0xFF111B2B);
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setGravity(Gravity.CENTER);
        t1.setMaxLines(1);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(t1);

        TextView t2 = new TextView(c);
        t2.setText(line2);
        t2.setTextColor(warn ? 0xB3A03A30 : 0x991B2B3A);
        t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        t2.setGravity(Gravity.CENTER);
        t2.setMaxLines(1);
        t2.setEllipsize(TextUtils.TruncateAt.END);
        t2.setPadding(0, dp(c, 5), 0, 0);
        box.addView(t2);

        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenW - dp(c, 72), dp(c, 320));

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
            titleView = t1;
            subView = t2;
            Trace.add(c, "overlay", "addView 成功：" + line1);
        } catch (Throwable e) {
            Trace.add(c, "overlay", "addView 失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
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
        current = null;
        titleView = null;
        subView = null;
    }

    /** 归属地算出来得晚一点时（冷启动），先把号码显示出来，回头把文字补上。 */
    public static void update(final String line1, final String line2) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (titleView != null && line1 != null) titleView.setText(line1);
                    if (subView != null && line2 != null) subView.setText(line2);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
