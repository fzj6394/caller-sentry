package cn.fzj.callersentry;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 手工搭"液态玻璃"界面的小工具箱：没有 Material 库，全靠 Drawable 和 LinearLayout */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    /** 背景：底色线性渐变 + 三个柔光斑，模拟 iOS 那种极光玻璃 */
    public static class Backdrop extends Drawable {
        private final UiTheme t;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Backdrop(UiTheme t) {
            this.t = t;
        }

        @Override
        public void draw(Canvas cv) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w == 0 || h == 0) return;

            p.setShader(new LinearGradient(0, 0, w * 0.4f, h,
                    t.bgTop, t.bgBottom, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);

            blob(cv, w * 0.12f, h * 0.06f, w * 0.62f, withAlpha(t.accent, t.isDark() ? 0x33 : 0x1F));
            blob(cv, w * 0.92f, h * 0.22f, w * 0.55f, withAlpha(0xFF5EC8FF, t.isDark() ? 0x26 : 0x22));
            blob(cv, w * 0.55f, h * 0.88f, w * 0.70f, withAlpha(0xFFB7D4FF, t.isDark() ? 0x1A : 0x18));
        }

        private void blob(Canvas cv, float cx, float cy, float r, int color) {
            p.setShader(new RadialGradient(cx, cy, r, color, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, getBounds().width(), getBounds().height(), p);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 玻璃卡片底：上半亮、下半略透，1dp 亮边 */
    public static GradientDrawable glass(UiTheme t, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{t.glassTop, t.glassBottom});
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(Math.max(1, dp(c, 0.8f)), t.stroke);
        return d;
    }

    /** 卡片容器：玻璃底 + 内边距 + 轻微投影 */
    public static LinearLayout card(Context c, UiTheme t) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(glass(t, 18, c));
        box.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        box.setElevation(dp(c, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        box.setLayoutParams(lp);
        return box;
    }

    public static TextView text(Context c, UiTheme t, String s, float sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(c, 2), 1f);
        return tv;
    }

    public static TextView title(Context c, UiTheme t, String s) {
        TextView tv = text(c, t, s, 15, t.text);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    public static TextView sub(Context c, UiTheme t, String s) {
        return text(c, t, s, 12, t.textMuted);
    }

    public static View divider(Context c, UiTheme t) {
        View v = new View(c);
        v.setBackgroundColor(withAlpha(t.text, t.isDark() ? 0x1A : 0x12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    /** 胶囊按钮 */
    public static TextView pill(Context c, UiTheme t, String label, boolean primary) {
        TextView tv = text(c, t, label, 13, primary ? t.accentText : t.text);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                primary
                        ? new int[]{lighten(t.accent, 0.12f), t.accent}
                        : new int[]{withAlpha(t.text, t.isDark() ? 0x1F : 0x0E),
                        withAlpha(t.text, t.isDark() ? 0x14 : 0x08)});
        d.setCornerRadius(dp(c, 22));
        if (!primary) d.setStroke(Math.max(1, dp(c, 0.8f)), t.stroke);
        tv.setBackground(d);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    /** 分段控件（三选一） */
    public static LinearLayout segmented(Context c, UiTheme t, String[] items,
                                         int selected, final OnPick onPick) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(withAlpha(t.text, t.isDark() ? 0x22 : 0x0D));
        bg.setCornerRadius(dp(c, 16));
        bar.setBackground(bg);
        bar.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));

        final TextView[] pills = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView tv = text(c, t, items[i], 13, t.textMuted);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(c, 9), 0, dp(c, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(lp);
            tv.setClickable(true);
            pills[i] = tv;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int k = 0; k < pills.length; k++) {
                        stylePill(pills[k], t, k == idx);
                    }
                    onPick.pick(idx);
                }
            });
            bar.addView(tv);
        }
        for (int i = 0; i < items.length; i++) stylePill(pills[i], t, i == selected);
        return bar;
    }

    private static void stylePill(TextView tv, UiTheme t, boolean on) {
        tv.setTextColor(on ? t.accentText : t.textMuted);
        if (on) {
            GradientDrawable d = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{lighten(t.accent, 0.16f), t.accent});
            d.setCornerRadius(dp(tv.getContext(), 13));
            tv.setBackground(d);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setElevation(dp(tv.getContext(), 3));
        } else {
            tv.setBackground(null);
            tv.setTypeface(Typeface.DEFAULT);
            tv.setElevation(0);
        }
    }

    public interface OnPick {
        void pick(int index);
    }

    /** 一行条目：标题 + 副标题 + 右侧箭头 */
    public static LinearLayout row(Context c, UiTheme t, String title, String subText,
                                   final Runnable onClick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 2), dp(c, 11), dp(c, 2), dp(c, 11));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(clp);

        TextView tv = text(c, t, title, 14, t.text);
        col.addView(tv);
        if (subText != null && subText.length() > 0) {
            TextView sv = text(c, t, subText, 11, t.textMuted);
            sv.setPadding(0, dp(c, 3), 0, 0);
            col.addView(sv);
        }
        row.addView(col);

        TextView arrow = text(c, t, "›", 20, withAlpha(t.text, 0x66));
        row.addView(arrow);

        if (onClick != null) {
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClick.run();
                }
            });
        }
        return row;
    }

    /** 页面骨架：玻璃背景 + 可滚动内容 */
    public static final class Page {
        public final ScrollView scroll;
        public final LinearLayout content;

        Page(ScrollView s, LinearLayout l) {
            this.scroll = s;
            this.content = l;
        }
    }

    public static Page page(Context c, UiTheme t) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setBackground(new Backdrop(t));
        sv.setClipToPadding(false);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 28));
        sv.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Page(sv, content);
    }

    /** 顶部：大标题 + 副标题（可带返回） */
    public static void header(Context c, UiTheme t, LinearLayout parent,
                              String titleText, String subText, String backLabel,
                              final Runnable onBack) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        if (onBack != null) {
            TextView back = text(c, t, "‹ " + (backLabel == null ? "返回" : backLabel), 15, t.accent);
            back.setPadding(0, dp(c, 4), dp(c, 14), dp(c, 4));
            back.setClickable(true);
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBack.run();
                }
            });
            bar.addView(back);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView big = text(c, t, titleText, 22, t.text);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(big);
        if (subText != null && subText.length() > 0) {
            TextView s = text(c, t, subText, 12, t.textMuted);
            s.setPadding(0, dp(c, 3), 0, 0);
            col.addView(s);
        }
        bar.addView(col);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 16);
        parent.addView(bar, lp);
    }

    public static int lighten(int color, float f) {
        int r = Math.min(255, (int) (Color.red(color) + (255 - Color.red(color)) * f));
        int g = Math.min(255, (int) (Color.green(color) + (255 - Color.green(color)) * f));
        int b = Math.min(255, (int) (Color.blue(color) + (255 - Color.blue(color)) * f));
        return Color.argb(Color.alpha(color), r, g, b);
    }

    /** 圆角图标底 */
    public static GradientDrawable iconBg(UiTheme t, Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(t.accent, 0.25f), t.accent});
        d.setCornerRadius(dp(c, 14));
        return d;
    }
}
