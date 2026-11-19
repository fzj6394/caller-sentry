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
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 锁屏来电卡片。
 *
 * 为什么需要它：TYPE_APPLICATION_OVERLAY 的窗口在锁屏界面上会被系统整体隐藏，
 * addView 依然返回成功，但用户什么都看不到（我们日志里 [overlay] 成功、锁屏却没东西，
 * 就是这个原因）。锁屏上唯一能显示内容的通道是 Activity——带 setShowWhenLocked(true)
 * 的透明页面可以画在锁屏之上。
 *
 * 于是：解锁状态走悬浮条（不改动用户正在看的界面），锁屏状态走这个 Activity。
 * 两者内容、样式一致，用户感知不到区别。
 *
 * 收起的时机与悬浮条相同：接听（OFFHOOK）或挂断（IDLE），另外留 120 秒兜底。
 */
public class CallCardActivity extends Activity {

    private static volatile CallCardActivity alive;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TextView titleView;
    private TextView subView;
    private BroadcastReceiver rx;
    private final Runnable autoClose = new Runnable() {
        @Override
        public void run() {
            finishQuietly();
        }
    };

    /** 从服务里调：启动（或复用）锁屏卡片 */
    public static void show(Context c, String line1, String line2, boolean warn) {
        try {
            Intent i = new Intent(c, CallCardActivity.class)
                    .putExtra("l1", line1)
                    .putExtra("l2", line2)
                    .putExtra("warn", warn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            c.startActivity(i);
            Trace.add(c, "card", "已启动锁屏卡片：" + line1);
        } catch (Throwable e) {
            Trace.add(c, "card", "启动失败 " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 归属地算得晚时补文字 */
    public static void update(final String line1, final String line2) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                CallCardActivity a = alive;
                if (a == null) return;
                try {
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

        // 关键：允许画在锁屏之上；来电时屏幕本来就是亮的，不强制点亮
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(false);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } catch (Throwable ignored) {
        }

        setContentView(buildRoot(getIntent()));
        Trace.add(this, "card", "卡片已显示");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        try {
            if (titleView != null) titleView.setText(intent.getStringExtra("l1"));
            if (subView != null) subView.setText(intent.getStringExtra("l2"));
            boolean warn = intent.getBooleanExtra("warn", false);
            if (titleView != null) titleView.setTextColor(warn ? 0xFFB3322A : 0xFF111B2B);
            MAIN.removeCallbacks(autoClose);
            MAIN.postDelayed(autoClose, 120_000L);
        } catch (Throwable ignored) {
        }
    }

    private View buildRoot(Intent in) {
        String l1 = in == null ? "来电" : in.getStringExtra("l1");
        String l2 = in == null ? "" : in.getStringExtra("l2");
        boolean warn = in != null && in.getBooleanExtra("warn", false);
        if (l1 == null) l1 = "来电";

        FrameLayout root = new FrameLayout(this);
        // 轻微压暗：白色玻璃在亮色锁屏 / 来电界面上才立得住，但不足以遮住系统界面
        root.setBackgroundColor(0x40000000);
        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishQuietly();
            }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(16), dp(22), dp(14));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                warn ? new int[]{0xFAFFF5F3, 0xF3F7E4E0}
                        : new int[]{0xFAFFFFFF, 0xF3F0F4FA});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), warn ? 0x59D9483B : 0x2E0B1220);
        card.setBackground(bg);
        card.setElevation(dp(12));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishQuietly();
            }
        });

        titleView = new TextView(this);
        titleView.setText(l1);
        titleView.setTextColor(warn ? 0xFFB3322A : 0xFF111B2B);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(titleView);

        subView = new TextView(this);
        subView.setText(l2);
        subView.setTextColor(warn ? 0xB3A03A30 : 0x991B2B3A);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subView.setGravity(Gravity.CENTER);
        subView.setMaxLines(1);
        subView.setEllipsize(TextUtils.TruncateAt.END);
        subView.setPadding(0, dp(6), 0, 0);
        card.addView(subView);

        TextView hint = new TextView(this);
        hint.setText("轻点收起");
        hint.setTextColor(0x661B2B3A);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(9), 0, 0);
        card.addView(hint);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(screenW - dp(56), dp(340));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (screenH * 0.26f);
        card.setLayoutParams(lp);

        root.addView(card);
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
