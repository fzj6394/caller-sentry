package cn.fzj.callersentry;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 锁屏来电提示。
 *
 * 为什么需要它：TYPE_APPLICATION_OVERLAY 的窗口在锁屏界面上会被系统整体隐藏，
 * addView 依然返回成功，但用户什么都看不到。锁屏上唯一能显示内容的通道是 Activity——
 * 带 setShowWhenLocked(true) 的透明页面可以画在锁屏之上。
 *
 * 设计（v2.7.3 起）：不画卡片、不画描边。锁屏本身已经有整屏压暗，
 * 再叠一个白块反而笨重；这里只用三级文字直接浮在壁纸上，
 * 靠 22sp 主行 + 拉开字距的小标签 + 阴影来立住。
 *
 * 收起：接听（OFFHOOK）、挂断（IDLE）、轻点、或 120 秒兜底。
 */
public class CallCardActivity extends Activity {

    private static volatile CallCardActivity alive;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TextView overView;
    private TextView titleView;
    private TextView subView;
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
                        a.overView.setText(overline);
                    }
                    if (a.titleView != null && line1 != null) a.titleView.setText(line1);
                    if (a.subView != null && line2 != null) a.subView.setText(line2);
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
            String over = intent.getStringExtra("over");
            if (overView != null && over != null && over.length() > 0) overView.setText(over);
            if (titleView != null) {
                titleView.setText(intent.getStringExtra("l1"));
                titleView.setTextColor(intent.getBooleanExtra("warn", false)
                        ? 0xFFFFB4A6 : 0xFFFFFFFF);
            }
            if (subView != null) subView.setText(intent.getStringExtra("l2"));
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
        // 顶部略压暗、往下渐隐：白字在任何壁纸上都有底，又不至于把锁屏压黑
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0x4D000000, 0x00000000});
        root.setBackground(scrim);
        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishQuietly();
            }
        });

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.START);
        block.setClickable(true);
        block.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishQuietly();
            }
        });

        if (over != null && over.length() > 0) {
            overView = new TextView(this);
            overView.setText(over);
            overView.setTextColor(warn ? 0xCCFFB4A6 : 0x99FFFFFF);
            overView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            overView.setLetterSpacing(0.30f);
            overView.setGravity(Gravity.START);
            overView.setMaxLines(1);
            overView.setEllipsize(TextUtils.TruncateAt.END);
            overView.setShadowLayer(dp(8), 0, dp(1), 0x80000000);
            block.addView(overView);
        }

        titleView = new TextView(this);
        titleView.setText(l1);
        titleView.setTextColor(warn ? 0xFFFFB4A6 : 0xFFFFFFFF);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setGravity(Gravity.START);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setShadowLayer(dp(10), 0, dp(1), 0x8C000000);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(5);
        titleView.setLayoutParams(tlp);
        block.addView(titleView);

        subView = new TextView(this);
        subView.setText(l2);
        subView.setTextColor(0xB3FFFFFF);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subView.setGravity(Gravity.START);
        subView.setLetterSpacing(0.12f);
        subView.setMaxLines(1);
        subView.setEllipsize(TextUtils.TruncateAt.END);
        subView.setShadowLayer(dp(8), 0, dp(1), 0x80000000);
        subView.setPadding(0, dp(6), 0, 0);
        block.addView(subView);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.min(screenW - dp(52), dp(400)), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (screenH * 0.26f);
        block.setLayoutParams(lp);

        root.addView(block);
        return root;
    }

    @Override
    protected void onStart() {
        super.onStart();
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

    @Override
    protected void onStop() {
        if (rx != null) {
            try {
                unregisterReceiver(rx);
            } catch (Throwable ignored) {
            }
            rx = null;
        }
        MAIN.removeCallbacks(autoClose);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (alive == this) alive = null;
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
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
