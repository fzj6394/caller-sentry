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
 * 数据源与更新（二级页面）。
 *
 * 排版规矩：每块一行标题+右侧状态，一个输入框，一排文字链接，最后一行小字说明。
 * 不再用"一段说明 + 一排胶囊按钮"的写法——那是调试面板的写法。
 */
public class SourcesActivity extends BasePageActivity {

    private static final int REQ_YELLOW_FILE = 401;

    private TextView segValue;
    private TextView spamValue;
    private TextView onlineValue;
    private TextView onlineToggle;
    private TextView autoValue;
    private TextView yellowValue;

    @Override
    protected String pageTitle() {
        return "数据源与更新";
    }

    @Override
    protected String pageSubtitle() {
        return "改完地址点下面的链接即可";
    }

    @Override
    protected void build(LinearLayout content) {
        content.addView(segBlock());
        content.addView(spamBlock());
        content.addView(yellowBlock());
        content.addView(onlineBlock());
        content.addView(optionBlock());
    }

    // ---------- 号段库 ----------

    private LinearLayout segBlock() {
        LinearLayout card = Ui.card(this, t);
        segValue = head(card, "号段库", "");

        final EditText url = field(Prefs.urlSeg(this));
        card.addView(url);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);
        links.setPadding(0, Ui.dp(this, 6), 0, 0);

        links.addView(Ui.link(this, t, "立即更新", new Runnable() {
            @Override
            public void run() {
                String u = url.getText().toString().trim();
                Prefs.setUrlSeg(SourcesActivity.this, u);
                segValue.setText("下载中…");
                Update.updateSegments(SourcesActivity.this, u, false, new Update.Callback() {
                    @Override
                    public void done(final boolean ok, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshInfo();
                                toast((ok ? "" : "⚠️ ") + message);
                            }
                        });
                    }
                });
            }
        }));

        links.addView(link2("恢复默认", new Runnable() {
            @Override
            public void run() {
                url.setText(Update.DEFAULT_SEG_URL);
                Prefs.setUrlSeg(SourcesActivity.this, Update.DEFAULT_SEG_URL);
                toast("已恢复默认地址");
            }
        }));
        card.addView(links);

        card.addView(note("整包 4.3 MB，只在点更新或开启自动检查时下载。"));
        return card;
    }

    // ---------- 号码库 ----------

    private LinearLayout spamBlock() {
        LinearLayout card = Ui.card(this, t);
        spamValue = head(card, "号码库", "");

        final EditText url = field(Prefs.urlSpam(this));
        card.addView(url);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);
        links.setPadding(0, Ui.dp(this, 6), 0, 0);

        links.addView(Ui.link(this, t, "合并更新", new Runnable() {
            @Override
            public void run() {
                String u = url.getText().toString().trim();
                Prefs.setUrlSpam(SourcesActivity.this, u);
                spamValue.setText("下载中…");
                Update.updateSpam(SourcesActivity.this, u, new Update.Callback() {
                    @Override
                    public void done(final boolean ok, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshInfo();
                                toast((ok ? "" : "⚠️ ") + message);
                            }
                        });
                    }
                });
            }
        }));

        links.addView(link2("恢复默认", new Runnable() {
            @Override
            public void run() {
                url.setText(Update.DEFAULT_SPAM_URL);
                Prefs.setUrlSpam(SourcesActivity.this, Update.DEFAULT_SPAM_URL);
                toast("已恢复默认地址");
            }
        }));
        card.addView(links);

        card.addView(note("默认源 vlongen/BanHarassment，只并手机号段。"));
        return card;
    }

    // ---------- 黄页 ----------

    private LinearLayout yellowBlock() {
        LinearLayout card = Ui.card(this, t);
        yellowValue = head(card, "黄页", "");

        final EditText url = field(Prefs.urlYellow(this));
        url.setHint("未设置（用内置那批）");
        card.addView(url);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);
        links.setPadding(0, Ui.dp(this, 6), 0, 0);

        links.addView(Ui.link(this, t, "更新", new Runnable() {
            @Override
            public void run() {
                String u = url.getText().toString().trim();
                if (u.length() == 0) {
                    toast("没填地址。可以直接用「从文件导入」");
                    return;
                }
                Prefs.setUrlYellow(SourcesActivity.this, u);
                yellowValue.setText("下载中…");
                Update.updateYellow(SourcesActivity.this, u, new Update.Callback() {
                    @Override
                    public void done(final boolean ok, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                refreshInfo();
                                toast((ok ? "" : "⚠️ ") + message);
                            }
                        });
                    }
                });
            }
        }));

        links.addView(link2("从文件导入", new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_YELLOW_FILE);
                } catch (Throwable e) {
                    toast("没有文件选择器");
                }
            }
        }));
        card.addView(links);

        card.addView(note("一行一条「号码|名称」，如 95338|顺丰速运。导入是合并，不会删已有的。"));
        return card;
    }

    // ---------- 在线查询 ----------

    private LinearLayout onlineBlock() {
        LinearLayout card = Ui.card(this, t);
        onlineValue = head(card, "在线查询", "");

        final EditText url = field(Prefs.onlineUrl(this));
        url.setHint("https://…/{num}");
        card.addView(url);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);
        links.setPadding(0, Ui.dp(this, 6), 0, 0);

        final TextView toggle = Ui.link(this, t, "开启", null);
        onlineToggle = toggle;
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.onlineOn(SourcesActivity.this);
                Prefs.setOnlineOn(SourcesActivity.this, on);
                refreshInfo();
                if (on) toast("来电时会把号码发给你填的地址查一次");
            }
        });
        links.addView(toggle);

        links.addView(link2("保存地址", new Runnable() {
            @Override
            public void run() {
                Prefs.setOnlineUrl(SourcesActivity.this, url.getText().toString().trim());
                refreshInfo();
                toast("已保存");
            }
        }));
        card.addView(links);

        card.addView(note("腾讯/360 的标记库不对外开放，只能自己接一个 API。{num} 会替换成号码。"));
        return card;
    }

    // ---------- 其他 ----------

    private LinearLayout optionBlock() {
        LinearLayout card = Ui.card(this, t);
        LinearLayout row = Ui.item(this, t, "启动时自动检查号段库", "", new Runnable() {
            @Override
            public void run() {
                boolean on = !Prefs.autoCheck(SourcesActivity.this);
                Prefs.setAutoCheck(SourcesActivity.this, on);
                refreshInfo();
            }
        });
        autoValue = (TextView) row.getChildAt(1);
        card.addView(row);
        card.addView(note("关掉之后，只在上面点「立即更新」时才联网。"));
        return card;
    }

    // ---------- 工具 ----------

    /** 标题行：左边标题，右边状态值 */
    private TextView head(LinearLayout card, String title, String tag) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tv = Ui.title(this, t, title);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        TextView state = Ui.text(this, t, "", 11.5f, t.textMuted);
        row.addView(state);
        card.addView(row);
        return state;
    }

    private TextView link2(String label, final Runnable action) {
        TextView tv = Ui.link(this, t, label, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 18);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView note(String text) {
        TextView tv = Ui.text(this, t, text, 11, Ui.withAlpha(t.textMuted, 0xE6));
        tv.setPadding(0, Ui.dp(this, 6), 0, 0);
        return tv;
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

    private EditText field(String value) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setTextSize(11.5f);
        et.setTextColor(t.text);
        et.setHintTextColor(Ui.withAlpha(t.textMuted, 0x99));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Ui.withAlpha(t.text, t.isDark() ? 0x1F : 0x0A));
        d.setCornerRadius(Ui.dp(this, 11));
        d.setStroke(Math.max(1, Ui.dp(this, 0.8f)), Ui.withAlpha(t.text, 0x12));
        et.setBackground(d);
        et.setPadding(Ui.dp(this, 11), Ui.dp(this, 9), Ui.dp(this, 11), Ui.dp(this, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        et.setLayoutParams(lp);
        return et;
    }

    private void refreshInfo() {
        if (segValue != null) {
            PhoneLookup pl = PhoneLookup.get(this);
            segValue.setText(String.format(java.util.Locale.CHINA, "%,d", pl.size()) + " 条 · "
                    + (pl.fromCache() ? "已更新" : "内置"));
        }
        if (spamValue != null) {
            spamValue.setText(Blacklist.count(this) + " 条规则");
        }
        if (yellowValue != null) {
            yellowValue.setText(Yellow.size(this) + " 条");
        }
        if (onlineValue != null) {
            boolean on = Prefs.onlineOn(this);
            boolean has = Prefs.onlineUrl(this).length() > 0;
            onlineValue.setText(on ? (has ? "已开启" : "没填地址") : "未开启");
        }
        if (onlineToggle != null) {
            onlineToggle.setText(Prefs.onlineOn(this) ? "关闭" : "开启");
        }
        if (autoValue != null) {
            autoValue.setText(Prefs.autoCheck(this) ? "开" : "关");
        }
    }

    @Override
    protected void onPageResume() {
        refreshInfo();
    }
}
