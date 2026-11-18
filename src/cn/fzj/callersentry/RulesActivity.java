package cn.fzj.callersentry;

import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** 我的号码库：整号拉黑 + 号段前缀，支持导入导出 */
public class RulesActivity extends BasePageActivity {

    private static final int REQ_IMPORT = 201;

    private LinearLayout listBox;
    private TextView countText;
    private EditText input;

    @Override
    protected String pageTitle() {
        return "我的号码库";
    }

    @Override
    protected String pageSubtitle() {
        return "命中后按首页选的三档处理";
    }

    @Override
    protected void build(LinearLayout content) {
        // 添加
        LinearLayout add = Ui.card(this, t);
        add.addView(Ui.title(this, t, "添加规则"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);

        input = new EditText(this);
        input.setHint("13800138000 或 95*");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextSize(14);
        input.setTextColor(t.text);
        input.setHintTextColor(Ui.withAlpha(t.textMuted, 0x99));
        input.setBackground(fieldBg());
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);

        TextView addBtn = Ui.pill(this, t, "加入", true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Ui.dp(this, 8);
        addBtn.setLayoutParams(blp);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Blacklist.add(RulesActivity.this, input.getText().toString())) {
                    input.setText("");
                    renderRules();
                } else {
                    toast("格式不对：至少 3 位数字，前缀写成 95*");
                }
            }
        });
        row.addView(addBtn);
        add.addView(row);

        TextView hint = Ui.sub(this, t, "完整号码全等匹配；带 * 的按前缀匹配。");
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        add.addView(hint);
        content.addView(add);

        // 列表
        LinearLayout card = Ui.card(this, t);
        countText = Ui.title(this, t, "规则");
        card.addView(countText);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(listBox);
        content.addView(card);

        // 工具
        LinearLayout tools = Ui.card(this, t);
        tools.addView(Ui.title(this, t, "导入导出"));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);

        TextView imp = Ui.pill(this, t, "从文件导入", false);
        imp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doImport();
            }
        });
        btns.addView(imp);

        TextView exp = Ui.pill(this, t, "分享导出", false);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.leftMargin = Ui.dp(this, 8);
        exp.setLayoutParams(elp);
        exp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doExport();
            }
        });
        btns.addView(exp);

        TextView clr = Ui.pill(this, t, "清空", false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dp(this, 8);
        clr.setLayoutParams(clp);
        clr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Blacklist.clear(RulesActivity.this);
                renderRules();
                toast("已清空");
            }
        });
        btns.addView(clr);
        tools.addView(btns);
        content.addView(tools);

        renderRules();
    }

    private android.graphics.drawable.Drawable fieldBg() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Ui.withAlpha(t.text, t.isDark() ? 0x1F : 0x0C));
        d.setCornerRadius(Ui.dp(this, 12));
        d.setStroke(Math.max(1, Ui.dp(this, 0.8f)), Ui.withAlpha(t.text, 0x14));
        return d;
    }

    private void renderRules() {
        if (listBox == null) return;
        listBox.removeAllViews();
        List<String> rules = Blacklist.load(this);
        countText.setText("规则（" + rules.size() + " 条）");

        if (rules.isEmpty()) {
            TextView empty = Ui.sub(this, t, "还没有规则。可以把 95*、400*、170* 这类先加进来。");
            empty.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
            listBox.addView(empty);
            return;
        }

        int shown = 0;
        for (final String rule : rules) {
            if (shown++ >= 150) {
                TextView more = Ui.sub(this, t, "…还有 " + (rules.size() - 150) + " 条未显示");
                more.setPadding(0, Ui.dp(this, 8), 0, 0);
                listBox.addView(more);
                break;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));

            TextView tv = Ui.text(this, t, rule, 14, t.text);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            TextView del = Ui.text(this, t, "移除", 12, t.danger);
            del.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4));
            del.setClickable(true);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Blacklist.remove(RulesActivity.this, rule);
                    renderRules();
                }
            });
            row.addView(del);

            listBox.addView(row);
            listBox.addView(Ui.divider(this, t));
        }
        // 去掉最后一个分隔线留白不影响观感，就不折腾了
    }

    private void doImport() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        } catch (Throwable e) {
            toast("这台机器没有文件选择器");
        }
    }

    private void doExport() {
        List<String> rules = Blacklist.load(this);
        if (rules.isEmpty()) {
            toast("号码库是空的");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String r : rules) sb.append(r).append('\n');
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        try {
            startActivity(Intent.createChooser(i, "导出到…"));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String text = new String(out.toByteArray(), "UTF-8");
            int before = Blacklist.count(this);
            List<String> parsed = Update.parseRules(text);
            Blacklist.addAll(this, parsed);
            renderRules();
            toast("解析 " + parsed.size() + " 条，去重后共 " + Blacklist.count(this) + " 条（原 " + before + " 条）");
        } catch (Throwable e) {
            toast("读取失败：" + e.getMessage());
        }
    }
}
