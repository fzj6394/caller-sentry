package cn.fzj.callersentry;

import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 数据源与更新（二级页面）：
 * 号段库、号码库、在线查询三块的地址都收在这里，首页不再出现输入框。
 */
public class SourcesActivity extends BasePageActivity {

    private static final int REQ_YELLOW_FILE = 401;

    private TextView segInfo;
    private TextView yellowInfo;
    private TextView spamInfo;
    private TextView onlineState;

    @Override
    protected String pageTitle() {
        return "数据源与更新";
    }

    @Override
    protected String pageSubtitle() {
        return "四块的地址都在这一页，改完点「更新」";
    }

    @Override
    protected void build(LinearLayout content) {
        content.addView(segBlock());
        content.addView(spamBlock());
        content.addView(onlineBlock());
        content.addView(yellowBlock());
        content.addView(optionBlock());
    }

    // ---------- 号段库 ----------

    private LinearLayout segBlock() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "① 号段库（归属地用）"));

        segInfo = Ui.sub(this, t, "");
        segInfo.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        card.addView(segInfo);

        final EditText url = field(Prefs.urlSeg(this));
        card.addView(url);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);

        TextView go = Ui.pill(this, t, "立即更新", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = url.getText().toString().trim();
                Prefs.setUrlSeg(SourcesActivity.this, u);
                segInfo.setText("正在下载…");
                Update.updateSegments(SourcesActivity.this, u, false, new Update.Callback() {
                    @Override
                    public void done(final boolean ok, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                segInfo.setText((ok ? "✅ " : "⚠️ ") + message);
                                refreshInfo();
                            }
                        });
                    }
                });
            }
        });
        btns.addView(go);

        TextView reset = Ui.pill(this, t, "恢复默认", false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.dp(this, 8);
        reset.setLayoutParams(rlp);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                url.setText(Update.DEFAULT_SEG_URL);
                Prefs.setUrlSeg(SourcesActivity.this, Update.DEFAULT_SEG_URL);
            }
        });
        btns.addView(reset);
        card.addView(btns);

        TextView note = Ui.sub(this, t, "整包约 4.3MB，走 jsDelivr，只在点更新或开 App 自动检查时下载。");
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(note);
        return card;
    }

    // ---------- 号码库 ----------

    private LinearLayout spamBlock() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "② 号码库（号段骚扰表）"));

        spamInfo = Ui.sub(this, t, "");
        spamInfo.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        card.addView(spamInfo);

        final EditText url = field(Prefs.urlSpam(this));
        card.addView(url);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);

        TextView go = Ui.pill(this, t, "合并更新", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = url.getText().toString().trim();
                Prefs.setUrlSpam(SourcesActivity.this, u);
                spamInfo.setText("正在下载…");
                Update.updateSpam(SourcesActivity.this, u, new Update.Callback() {
                    @Override
                    public void done(final boolean ok, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                spamInfo.setText((ok ? "✅ " : "⚠️ ") + message);
                                refreshInfo();
                            }
                        });
                    }
                });
            }
        });
        btns.addView(go);

        TextView reset = Ui.pill(this, t, "恢复默认", false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.dp(this, 8);
        reset.setLayoutParams(rlp);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                url.setText(Update.DEFAULT_SPAM_URL);
                Prefs.setUrlSpam(SourcesActivity.this, Update.DEFAULT_SPAM_URL);
            }
        });
        btns.addView(reset);
        card.addView(btns);

        TextView note = Ui.sub(this, t, "默认源是 vlongen/BanHarassment，已自动丢掉固话区号，只并手机号段。");
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(note);
        return card;
    }

    // ---------- 在线查询 ----------

    private LinearLayout onlineBlock() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "③ 在线查询（兜底，可选）"));

        onlineState = Ui.sub(this, t, "");
        onlineState.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        card.addView(onlineState);

        final EditText url = field(Prefs.onlineUrl(this));
        card.addView(url);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);

        final TextView toggle = Ui.pill(this, t, "", Prefs.onlineOn(this));
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.onlineOn(SourcesActivity.this);
                Prefs.setOnlineOn(SourcesActivity.this, on);
                toggle.setText(on ? "已开启" : "已关闭");
                toggle.setBackground(on ? Ui.pill(SourcesActivity.this, t, "", true).getBackground()
                        : Ui.pill(SourcesActivity.this, t, "", false).getBackground());
                refreshInfo();
                if (on) toast("来电时会把号码发给你填的地址查一次");
            }
        });
        toggle.setText(Prefs.onlineOn(this) ? "已开启" : "已关闭");
        btns.addView(toggle);

        TextView save = Ui.pill(this, t, "保存地址", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        save.setLayoutParams(slp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setOnlineUrl(SourcesActivity.this, url.getText().toString().trim());
                toast("已保存");
                refreshInfo();
            }
        });
        btns.addView(save);
        card.addView(btns);

        TextView note = Ui.sub(this, t,
                "腾讯/360 的标记库不对外开放，没有整包下载。有聚合 API 账号就把地址填这里，"
                        + "{num} 会被换成号码；查到的结果存本地，断网也能用。");
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(note);
        return card;
    }


    // ---------- 黄页 ----------

    private LinearLayout yellowBlock() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "④ 黄页（客服号、单位名）"));

        yellowInfo = Ui.sub(this, t, "当前 " + Yellow.size(this) + " 条");
        yellowInfo.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        card.addView(yellowInfo);

        final EditText url = field(Prefs.urlYellow(this));
        card.addView(url);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);

        TextView go = Ui.pill(this, t, "更新", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = url.getText().toString().trim();
                Prefs.setUrlYellow(SourcesActivity.this, u);
                yellowInfo.setText("正在下载…");
                Update.updateYellow(SourcesActivity.this, u, new Update.Callback() {
                    @Override
                    public void done(final boolean ok, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                yellowInfo.setText((ok ? "✅ " : "⚠️ ") + message
                                        + "\n当前 " + Yellow.size(SourcesActivity.this) + " 条");
                            }
                        });
                    }
                });
            }
        });
        btns.addView(go);

        TextView fromFile = Ui.pill(this, t, "从文件导入", false);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.leftMargin = Ui.dp(this, 8);
        fromFile.setLayoutParams(flp);
        fromFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_YELLOW_FILE);
                } catch (Throwable e) {
                    toast("没有文件选择器");
                }
            }
        });
        btns.addView(fromFile);
        card.addView(btns);

        TextView note = Ui.sub(this, t,
                "格式：一行一条「号码|名称|类别」，如 95338|顺丰速运|快递。导入是合并，不会删掉已有的；"
                        + "内置的 79 条在首次启动时已经写进本地数据库。");
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(note);
        return card;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_YELLOW_FILE || resultCode != RESULT_OK || data == null) return;
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            int added = YellowStore.importText(this, new String(out.toByteArray(), "UTF-8"));
            refreshInfo();
            toast(added == 0 ? "没解析出「号码|名称」的行" : "导入 " + added + " 条");
        } catch (Throwable e) {
            toast("读取失败：" + e.getMessage());
        }
    }

    // ---------- 选项 ----------


    private LinearLayout optionBlock() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "其他"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);

        final TextView check = Ui.text(this, t, Prefs.autoCheck(this) ? "☑" : "☐", 15,
                Prefs.autoCheck(this) ? t.accent : t.textMuted);
        row.addView(check);
        row.addView(Ui.text(this, t, "  打开 App 时自动检查号段库更新", 13, t.text));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.autoCheck(SourcesActivity.this);
                Prefs.setAutoCheck(SourcesActivity.this, on);
                check.setText(on ? "☑" : "☐");
                check.setTextColor(on ? t.accent : t.textMuted);
            }
        });
        card.addView(row);
        return card;
    }

    // ---------- 工具 ----------

    private EditText field(String value) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setTextSize(12);
        et.setTextColor(t.text);
        et.setHintTextColor(Ui.withAlpha(t.textMuted, 0x99));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Ui.withAlpha(t.text, t.isDark() ? 0x1F : 0x0C));
        d.setCornerRadius(Ui.dp(this, 12));
        d.setStroke(Math.max(1, Ui.dp(this, 0.8f)), Ui.withAlpha(t.text, 0x14));
        et.setBackground(d);
        et.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        return et;
    }

    private void refreshInfo() {
        if (segInfo != null) {
            PhoneLookup pl = PhoneLookup.get(this);
            segInfo.setText("当前 " + pl.size() + " 条 · "
                    + (pl.fromCache() ? "用的是手机内更新版" : "用的是内置版"));
        }
        if (spamInfo != null) {
            spamInfo.setText("本地共 " + Blacklist.count(this) + " 条规则（含你自己加的）");
        }
        if (yellowInfo != null) {
            yellowInfo.setText("当前 " + Yellow.size(this) + " 条（SQLite 存，内存查）");
        }
        if (onlineState != null) {
            String url = Prefs.onlineUrl(this);
            onlineState.setText(Prefs.onlineOn(this)
                    ? "已开启 · " + (url.length() == 0 ? "但还没填地址" : "地址已填")
                    : "未开启（关闭时完全离线）");
        }
    }

    @Override
    protected void onPageResume() {
        refreshInfo();
    }
}
