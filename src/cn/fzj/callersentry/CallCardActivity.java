package cn.fzj.callersentry;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 锁屏来电提示。
 *
 * 为什么需要它：TYPE_APPLICATION_OVERLAY 的窗口在锁屏界面上会被系统整体隐藏，
 * addView 依然返回成功，但用户什么都看不到。锁屏上唯一能显示内容的通道是 Activity——
 * 带 setShowWhenLocked(true) 的透明页面可以画在锁屏之上。
 *
 * 设计（v2.7.7 起）：
 * - 不画卡片、不画描边，三行文字居中；
 * - 文字用 GlowView 自己画（不走 TextView，避免 shader 被内部重置成白字），
 *   色带高饱和、首尾同色循环，任意一帧都带颜色；
 * - 整个文字块在屏幕上左右往返移动。
 *
 * 收起：接听（OFFHOOK）、挂断（IDLE）、轻点、或 120 秒兜底。
 */
public class CallCardActivity extends Activity {

    private static volatile CallCardActivity alive;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GlowView overView;
    private GlowView titleView;
    private GlowView subView;
    private ObjectAnimator travel;
    private View block;
    private BroadcastReceiver rx;
    private final Runnable autoClose = new Runnable() {
        @Override
        public void run() {
            finishQuietly();
        }
    };

    /** 从服务里调：启动（或复用）锁屏提示 */
    public static void show(Context c, String line1, String line2, boolean warn) {
        show(c, null, line1, line2, warn);
    }

    /**
     * @param overline 上面那行小字（来电 / 快递 / 疑似骚扰），null 表示不显示
     */
    public static void show(Context c, String overline, String line1, String line2, boolean warn) {
        try {
            Intent i = new Intent(c, CallCardActivity.class)
                    .putExtra("over", overline)
                    .putExtra("l1", line1)
                    .putExtra("l2", line2)
                    .putExtra("warn", warn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            c.startActivity(i);
            Trace.add(c, "card", "已启动锁屏提示：" + line1);
        } catch (Throwable e) {
            Trace.add(c, "card", "启动失败 " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 归属地算得晚时补文字 */
    public static void update(final String overline, final String line1, final String line2) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                CallCardActivity a = alive;
                if (a == null) return;
                try {
                    if (a.overView != null && overline != null && overline.length() > 0) {
                        a.overView.text(overline);
                    }
                    if (a.titleView != null && line1 != null) a.titleView.text(line1);
                    if (a.subView != null && line2 != null) a.subView.text(line2);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public static void dismiss() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                CallCardActivity a = alive;
                if (a != null) a.finishQuietly();
            }
        });
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        alive = this;

        try {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(false);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        } catch (Throwable ignored) {
        }

        setContentView(buildRoot(getIntent()));
        Trace.add(this, "card", "锁屏提示已显示");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        try {
            boolean warn = intent.getBooleanExtra("warn", false);
            String over = intent.getStringExtra("over");
            if (overView != null && over != null && over.length() > 0) {
                overView.text(over).colors(palette(warn, 0xCC));
            }
            if (titleView != null) {
                titleView.text(intent.getStringExtra("l1")).colors(palette(warn, 0xFF));
            }
            if (subView != null) {
                subView.text(intent.getStringExtra("l2")).colors(palette(warn, 0xEE));
            }
            MAIN.removeCallbacks(autoClose);
            MAIN.postDelayed(autoClose, 120_000L);
        } catch (Throwable ignored) {
        }
    }

    private View buildRoot(Intent in) {
        String over = in == null ? null : in.getStringExtra("over");
        String l1 = in == null ? "来电" : in.getStringExtra("l1");
        String l2 = in == null ? "" : in.getStringExtra("l2");
        boolean warn = in != null && in.getBooleanExtra("warn", false);
        if (l1 == null) l1 = "来电";
        if (l2 == null) l2 = "";

        FrameLayout root = new FrameLayout(this);
        // 顶部略压暗、往下渐隐：亮色文字在任何壁纸上都有底，又不至于把锁屏压黑
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x4D000000, 0x00000000});
        root.setBackground(scrim);
        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishQuietly();
            }
        });

        final int screenH = getResources().getDisplayMetrics().heightPixels;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishQuietly();
            }
        });

        if (over != null && over.length() > 0) {
            overView = glow(over, 11, 0xCC, warn, 0.66f);
            overView.letterSpacing(0.30f);
            overView.setPadding(0, 0, 0, dp(7));
            box.addView(overView);
        }

        titleView = glow(l1, 23, 0xFF, warn, 0f);
        titleView.bold(true);
        box.addView(titleView);

        subView = glow(l2, 13.5f, 0xEE, warn, 0.45f);
        subView.letterSpacing(0.12f);
        subView.setPadding(0, dp(7), 0, 0);
        box.addView(subView);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (screenH * 0.26f);
        box.setLayoutParams(lp);

        root.addView(box);
        block = box;
        return root;
    }

    private GlowView glow(String text, float sp, int alpha, boolean warn, float phase) {
        GlowView v = new GlowView(this);
        v.text(text).sizeSp(sp).colors(palette(warn, alpha)).setPhase(phase);
        return v;
    }

    /**
     * 一条高饱和色带，首尾同色 —— REPEAT 循环时接缝看不出来。
     * 正常来电走冰蓝/紫/玫红/薄荷，命中号码库走橙红/玫红/琥珀。
     */
    private int[] palette(boolean warn, int alpha) {
        int a = (alpha & 0xFF) << 24;
        if (warn) {
            return new int[]{a | 0xFFE0D2, a | 0xFF7A45, a | 0xFF4E8A,
                    a | 0xFFB324, a | 0xFF7A45, a | 0xFFE0D2};
        }
        return new int[]{a | 0xFFFFFF, a | 0x3FE0FF, a | 0x9B8CFF,
                a | 0xFF7FD0, a | 0x4BFFC4, a | 0xFFFFFF};
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (overView != null) overView.startGlow(overView.phase());
        if (titleView != null) titleView.startGlow(titleView.phase());
        if (subView != null) subView.startGlow(subView.phase());
        startTravel();

        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent in) {
                String state = in == null ? null
                        : in.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)
                        || TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    finishQuietly();
                }
            }
        };
        try {
            registerReceiver(rx, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
        } catch (Throwable ignored) {
            rx = null;
        }
        MAIN.removeCallbacks(autoClose);
        MAIN.postDelayed(autoClose, 120_000L);
    }

    /**
     * 整个文字块左右往返跑：从左侧留白走到右侧留白，缓停后折返。
     * 块本身比屏幕还宽时不跑（没有余量，硬跑只会抖）。
     */
    private void startTravel() {
        if (block == null) return;
        stopTravel();
        block.post(new Runnable() {
            @Override
            public void run() {
                if (block == null || isFinishing()) return;
                int screenW = getResources().getDisplayMetrics().widthPixels;
                int bw = block.getWidth();
                float range = (screenW - bw) / 2f - dp(20);
                if (range < dp(8)) {
                    Trace.add(CallCardActivity.this, "card", "块太宽，不跑马 range=" + (int) range);
                    return;
                }
                travel = ObjectAnimator.ofFloat(block, "translationX", -range, range);
                travel.setDuration(5200L);
                travel.setRepeatCount(ValueAnimator.INFINITE);
                travel.setRepeatMode(ValueAnimator.REVERSE);
                travel.setInterpolator(new AccelerateDecelerateInterpolator());
                travel.start();
            }
        });
    }

    private void stopTravel() {
        if (travel != null) {
            try {
                travel.cancel();
            } catch (Throwable ignored) {
            }
            travel = null;
        }
        if (block != null) block.setTranslationX(0f);
    }

    @Override
    protected void onStop() {
        if (rx != null) {
            try {
                unregisterReceiver(rx);
            } catch (Throwable ignored) {
            }
            rx = null;
        }
        if (overView != null) overView.stopGlow();
        if (titleView != null) titleView.stopGlow();
        if (subView != null) subView.stopGlow();
        stopTravel();
        MAIN.removeCallbacks(autoClose);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (alive == this) alive = null;
        stopTravel();
        MAIN.removeCallbacks(autoClose);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finishQuietly();
    }

    private void finishQuietly() {
        try {
            finishAndRemoveTask();
        } catch (Throwable ignored) {
            try {
                finish();
            } catch (Throwable ignored2) {
            }
        }
    }

    private int dp(float v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
