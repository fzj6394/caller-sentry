package cn.fzj.callersentry;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** 拦截记录与诊断 */
public class RecordsActivity extends BasePageActivity {

    private LinearLayout logBox;
    private TextView logTitle;

    @Override
    protected String pageTitle() {
        return "拦截记录与诊断";
    }

    @Override
    protected String pageSubtitle() {
        return "最近 80 条来电处理结果";
    }

    @Override
    protected void build(LinearLayout content) {
        // 记录
        LinearLayout card = Ui.card(this, t);
        logTitle = Ui.title(this, t, "记录");
        card.addView(logTitle);

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(logBox);

        TextView clear = Ui.pill(this, t, "清空记录", false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        clear.setLayoutParams(clp);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogStore.clear(RecordsActivity.this);
                renderLogs();
            }
        });
        card.addView(clear);
        content.addView(card);

        content.addView(previewCard());
        content.addView(crashCard());
        content.addView(timingCard());
        content.addView(directoryCard());
    }


    /** 悬浮条预览：不用等真来电，点一下就能看到 7 秒提示 */
    private LinearLayout previewCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "悬浮条预览"));

        TextView hint = Ui.sub(this, t,
                "点一下，屏幕上方会出现 7 秒提示，位置和样式与真实来电一致。"
                        + "需要悬浮窗权限，没给的话只会走通知栏。");
        hint.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(hint);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Ui.dp(this, 12), 0, 0);

        TextView normal = Ui.pill(this, t, "普通来电", true);
        normal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preview("绍兴 · 电信", "0575 8888 8888", false);
            }
        });
        btns.addView(normal);

        TextView spam = Ui.pill(this, t, "命中号码库", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        spam.setLayoutParams(slp);
        spam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preview("我的号码库", "绍兴 · 电信　138 0013 8000", true);
            }
        });
        btns.addView(spam);
        card.addView(btns);
        return card;
    }

    private void preview(String line1, String line2, boolean warn) {
        if (!Overlay.canDraw(this)) {
            toast("先在首页点「悬浮窗」把权限给了");
            return;
        }
        Overlay.show(this, line1, line2, warn);
        toast("看屏幕上方，7 秒后自动消失");
    }


    /** 悬浮条预览：不用等真来电，点一下就能看到 7 秒提示 */
/** 来电耗时：判定 / respond / 归属地 / 合计，用来验证优化有没有生效 */
    private LinearLayout timingCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "来电耗时（最近 10 通）"));

        TextView hint = Ui.sub(this, t,
                "「判定」是查黄页和号码库的耗时，「respond」是让来电界面出来的耗时，"
                        + "「归属地」是算归属地的耗时。冷启动第一通通常最长。");
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        card.addView(hint);

        java.util.List<String> rows = Timing.all(this);
        if (rows.isEmpty()) {
            TextView empty = Ui.sub(this, t, "还没有数据，接一通电话再看。");
            card.addView(empty);
        } else {
            for (String row : rows) {
                TextView tv = Ui.text(this, t, row, 11, t.text);
                tv.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
                card.addView(tv);
            }
            TextView clear = Ui.pill(this, t, "清空", false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = Ui.dp(this, 10);
            clear.setLayoutParams(clp);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Timing.clear(RecordsActivity.this);
                    recreate();
                }
            });
            card.addView(clear);
        }
        return card;
    }

    /** 通话记录里的名字：靠 Directory Provider，拨号器来问我们 */
    private LinearLayout directoryCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "通话记录显示归属地"));

        boolean granted = true;
        try {
            granted = checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
        }

        TextView tv = Ui.sub(this, t, granted
                ? "已授予「通话记录」权限。原理：拨号器查号码时会来问本 App，"
                + "我们把黄页/归属地拼成名字返回。AOSP 和 Google 拨号器会查；"
                + "三星原生「电话」查不查由三星决定，可能不显示。"
                : "需要「通话记录」权限，系统才允许拨号器来问本 App 号码是谁。"
                + "不给也不影响来电提示，只是通话记录里仍是光秃秃的号码。");
        tv.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(tv);

        if (!granted) {
            TextView ask = Ui.pill(this, t, "授予通话记录权限", true);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.dp(this, 12);
            ask.setLayoutParams(alp);
            ask.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        requestPermissions(new String[]{"android.permission.READ_CALL_LOG"}, 301);
                    } catch (Throwable ignored) {
                    }
                }
            });
            card.addView(ask);
        }
        return card;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] grant) {
        super.onRequestPermissionsResult(code, perms, grant);
        if (code == 301) recreate();
    }

    private LinearLayout crashCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "诊断"));

        final String crash = App.lastCrash(this);
        TextView tv = Ui.sub(this, t, crash.length() == 0
                ? "没有崩溃记录。号段库 " + PhoneLookup.get(this).size() + " 条，黄页 "
                + Yellow.size(this) + " 条。"
                : "上次崩溃已记录（" + firstLine(crash) + "）");
        tv.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(tv);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);

        if (crash.length() > 0) {
            TextView share = Ui.pill(this, t, "分享崩溃记录", true);
            share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, "上一步：" + Update.lastStep(RecordsActivity.this)
                            + "\n\n" + crash);
                    try {
                        startActivity(Intent.createChooser(i, "发送到…"));
                    } catch (Throwable ignored) {
                    }
                }
            });
            btns.addView(share);

            TextView del = Ui.pill(this, t, "清除", false);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = Ui.dp(this, 8);
            del.setLayoutParams(dlp);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.sp(RecordsActivity.this).edit().remove("last_crash").apply();
                    recreate();
                }
            });
            btns.addView(del);
        }
        if (btns.getChildCount() > 0) card.addView(btns);
        return card;
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        String head = i > 0 ? s.substring(0, i) : s;
        return head.length() > 40 ? head.substring(0, 40) + "…" : head;
    }

    private void renderLogs() {
        if (logBox == null) return;
        logBox.removeAllViews();
        List<String> logs = LogStore.all(this);
        logTitle.setText("记录（" + logs.size() + " 条）");
        if (logs.isEmpty()) {
            TextView empty = Ui.sub(this, t, "还没有记录。接一通电话后这里会出现处理结果。");
            empty.setPadding(0, Ui.dp(this, 6), 0, 0);
            logBox.addView(empty);
            return;
        }
        int n = Math.min(logs.size(), 40);
        for (int i = 0; i < n; i++) {
            TextView tv = Ui.text(this, t, logs.get(i), 12, t.text);
            tv.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            logBox.addView(tv);
            if (i < n - 1) logBox.addView(Ui.divider(this, t));
        }
        if (logs.size() > n) {
            TextView more = Ui.sub(this, t, "…还有 " + (logs.size() - n) + " 条");
            more.setPadding(0, Ui.dp(this, 8), 0, 0);
            logBox.addView(more);
        }
    }

    @Override
    protected void onPageResume() {
        renderLogs();
    }
}
