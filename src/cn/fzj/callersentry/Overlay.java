package cn.fzj.callersentry;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 来电时在通话界面上方压一条提示。
 * 通话界面是全屏的，通知栏在部分三星机型上会被盖住，所以悬浮条更可靠。
 * 注意：addView 必须在有 Looper 的线程上跑，而 onScreenCall 是 binder 线程，
 * 所以这里统一 post 到主线程。
 */
public final class Overlay {

    private static View current;
    private static TextView titleView;
    private static TextView subView;

    private Overlay() {
    }

    public static boolean canDraw(Context c) {
        return Settings.canDrawOverlays(c);
    }

    private static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static void show(final Context ctx, final String line1, final String line2,
                            final boolean warn) {
        if (!canDraw(ctx)) {
            Trace.add(ctx, "overlay", "没有悬浮窗权限，跳过");
            return;
        }
        final Context c = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                addOverlay(c, line1, line2, warn);
            }
        });
    }

    private static void addOverlay(Context c, String line1, String line2, boolean warn) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Trace.add(c, "overlay", "拿不到 WindowManager");
            return;
        }

        hideNow();

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(warn ? 0xF2C0392B : 0xE6182432);
        bg.setCornerRadius(dp(c, 14));
        box.setBackground(bg);

        final TextView t1 = new TextView(c);
        t1.setText(line1);
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(17);
        t1.setGravity(Gravity.CENTER);
        box.addView(t1);

        {
            TextView t2 = new TextView(c);
            t2.setText(line2);
            t2.setTextColor(0xCCFFFFFF);
            t2.setTextSize(12);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, dp(c, 4), 0, 0);
            box.addView(t2);
            subView = t2;
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // 原来固定 120dp，正好落在三星来电卡片（顶部那块圆角卡片）的范围内，
        // 被卡片遮住后看起来像"没显示"。改到屏幕 38% 高度处，避开卡片。
        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        lp.y = Math.max(dp(c, 120), (int) (screenH * 0.38f));

        try {
            wm.addView(box, lp);
            current = box;
            titleView = t1;
            Trace.add(c, "overlay", "addView 成功：" + line1);
        } catch (Throwable e) {
            Trace.add(c, "overlay", "addView 失败 " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                hide();
            }
        }, 7000);
    }

    public static void hide() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
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

    /**
     * 归属地算出来得晚一点时（冷启动），先把号码显示出来，回头把文字补上。
     * 这样用户看到的是"提示已出现、内容在补齐"，而不是干等。
     */
    public static void update(final String line1, final String line2) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
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
