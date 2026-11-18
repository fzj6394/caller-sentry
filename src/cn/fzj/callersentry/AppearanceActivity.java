package cn.fzj.callersentry;

import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 外观主题：三套液态玻璃配色，点一下就切 */
public class AppearanceActivity extends BasePageActivity {

    @Override
    protected String pageTitle() {
        return "外观主题";
    }

    @Override
    protected String pageSubtitle() {
        return "三套玻璃配色，立即生效";
    }

    @Override
    protected void build(LinearLayout content) {
        int current = Prefs.theme(this);
        for (final UiTheme tt : UiTheme.all()) {
            content.addView(preview(tt, tt.id == current));
        }

        LinearLayout note = Ui.card(this, t);
        note.addView(Ui.title(this, t, "说明"));
        TextView tv = Ui.sub(this, t,
                "液态玻璃的做法：渐变底色 + 柔光斑 + 半透明卡片 + 1dp 亮边 + 轻微投影。"
                        + "安卓没有 iOS 那种实时毛玻璃采样，这套是静态模拟，观感接近但不吃性能、不耗电。");
        tv.setPadding(0, Ui.dp(this, 6), 0, 0);
        note.addView(tv);
        content.addView(note);
    }

    private View preview(final UiTheme tt, boolean selected) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new Ui.Backdrop(tt));
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(AppearanceActivity.this, 18));
            }
        });

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        LinearLayout glass = new LinearLayout(this);
        glass.setOrientation(LinearLayout.VERTICAL);
        glass.setBackground(Ui.glass(tt, 14, this));
        glass.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        glass.setElevation(Ui.dp(this, 4));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = Ui.text(this, tt, tt.name, 15, tt.text);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleRow.addView(name);

        TextView desc = Ui.text(this, tt, "　" + tt.desc, 11, tt.textMuted);
        desc.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(desc);

        if (selected) {
            TextView on = Ui.text(this, tt, "使用中", 11, tt.accent);
            titleRow.addView(on);
        }
        glass.addView(titleRow);

        // 模拟一条来电提示
        TextView demo = Ui.text(this, tt, "绍兴 · 电信｜黄页 顺丰速运·快递", 11, tt.textMuted);
        demo.setPadding(0, Ui.dp(this, 8), 0, 0);
        glass.addView(demo);

        inner.addView(glass);
        frame.addView(inner);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 14);
        frame.setLayoutParams(lp);

        frame.setClickable(true);
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Prefs.theme(AppearanceActivity.this) == tt.id) return;
                Prefs.setTheme(AppearanceActivity.this, tt.id);
                toast("已切到「" + tt.name + "」");
                finish();
            }
        });
        return frame;
    }
}
