package cn.fzj.callersentry;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 首页：只留最常用的事——授权状态、三档开关、查号、四个入口 */
public class MainActivity extends Activity {

    private static final int REQ_ROLE = 101;
    private static final int REQ_NOTIFY = 103;

    private UiTheme t;
    private TextView statusTitle;
    private TextView statusSub;
    private TextView result;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable e) {
            App.save(this, e, "onCreate 失败");
            showCrash(e);
        }
    }

    private void buildUi() {
        t = UiTheme.current(this);
        Ui.Page page = Ui.page(this, t);
        getWindow().setBackgroundDrawable(new Ui.Backdrop(t));
        setContentView(page.scroll);

        headerBlock(page.content);
        statusBlock(page.content);
        modeBlock(page.content);
        queryBlock(page.content);
        entriesBlock(page.content);
        footerBlock(page.content);

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
            } catch (Throwable ignored) {
            }
        }

        final String autoUrl = Prefs.autoCheck(this) ? Prefs.urlSeg(this) : "";
        if (autoUrl != null && autoUrl.length() > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Update.updateSegments(MainActivity.this, autoUrl, true,
                                new Update.Callback() {
                                    @Override
                                    public void done(boolean ok, String message) {
                                        refreshStatus();
                                    }
                                });
                    } catch (Throwable ignored) {
                    }
                }
            }, 5000);
        }

        showPendingCrashIfAny();
    }

    // ---------- 顶部 ----------

    private void headerBlock(LinearLayout parent) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(MainActivity.this, 13));
            }
        });
        bar.addView(icon);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 12), 0, 0, 0);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = Ui.text(this, t, "来电哨兵", 21, t.text);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(name);
        TextView ver = Ui.text(this, t, "v" + App.VERSION + " · 本地识别 · 号码不出手机", 11, t.textMuted);
        ver.setPadding(0, Ui.dp(this, 3), 0, 0);
        col.addView(ver);
        bar.addView(col);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 16);
        parent.addView(bar, lp);
    }

    // ---------- 状态 ----------

    private void statusBlock(LinearLayout parent) {
        LinearLayout card = Ui.card(this, t);

        statusTitle = Ui.title(this, t, "");
        statusTitle.setTextSize(16);
        card.addView(statusTitle);

        statusSub = Ui.sub(this, t, "");
        statusSub.setPadding(0, Ui.dp(this, 5), 0, 0);
        card.addView(statusSub);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, Ui.dp(this, 12), 0, 0);

        TextView auth = Ui.pill(this, t, "授予来电识别权限", true);
        auth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestRole();
            }
        });
        buttons.addView(auth);

        TextView overlay = Ui.pill(this, t, "悬浮窗", false);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.leftMargin = Ui.dp(this, 8);
        overlay.setLayoutParams(olp);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        });
        buttons.addView(overlay);

        TextView keep = Ui.pill(this, t, "后台保活", false);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.leftMargin = Ui.dp(this, 8);
        keep.setLayoutParams(klp);
        keep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestBatteryWhitelist();
            }
        });
        buttons.addView(keep);

        card.addView(buttons);

        // 第二行：兼容模式（不依赖拦截角色）
        LinearLayout buttons2 = new LinearLayout(this);
        buttons2.setOrientation(LinearLayout.HORIZONTAL);
        buttons2.setPadding(0, Ui.dp(this, 8), 0, 0);

        final boolean compatNow = Prefs.sp(this).getBoolean("compat_on", false);
        final TextView compat = Ui.pill(this, t,
                compatNow ? "兼容模式：开" : "兼容模式：关", compatNow);
        // 覆盖安装或系统回收后服务会停掉，开关却还显示"开"。
        // 每次进前台补一次启动，避免用户以为开着其实没跑。
        if (compatNow && !Watcher.isRunning()) {
            Watcher.start(this);
        }
        compat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = Prefs.sp(MainActivity.this).getBoolean("compat_on", false);
                if (on) {
                    Watcher.stop(MainActivity.this);
                    compat.setText("兼容模式：关");
                    toast("兼容模式已关闭");
                } else {
                    Watcher.start(MainActivity.this);
                    compat.setText("兼容模式：开");
                    toast("已开启：状态栏会有一条常驻通知，来电时提示归属地");
                }
            }
        });
        buttons2.addView(compat);

        TextView compatHint = Ui.sub(this, t, "拦截服务被系统挡住时用这个");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.leftMargin = Ui.dp(this, 10);
        compatHint.setLayoutParams(hl);
        buttons2.addView(compatHint);

        card.addView(buttons2);
        parent.addView(card);
    }

    // ---------- 三档 ----------

    private void modeBlock(LinearLayout parent) {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "命中号码库时"));

        TextView hint = Ui.sub(this, t, "「我的号码库」或号段规则命中的来电，按这里处理");
        hint.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 12));
        card.addView(hint);

        LinearLayout seg = Ui.segmented(this, t,
                new String[]{"仅提示", "静音放行", "直接拒接"},
                Prefs.blockMode(this), new Ui.OnPick() {
                    @Override
                    public void pick(int index) {
                        Prefs.setBlockMode(MainActivity.this, index);
                        if (index == 2) toast("命中后会直接挂断，建议先观察几天");
                    }
                });
        card.addView(seg);

        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setOrientation(LinearLayout.HORIZONTAL);
        repeatRow.setGravity(Gravity.CENTER_VERTICAL);
        repeatRow.setPadding(0, Ui.dp(this, 12), 0, 0);

        final TextView check = Ui.text(this, t,
                Prefs.repeatMinutes(this) > 0 ? "☑" : "☐", 15,
                Prefs.repeatMinutes(this) > 0 ? t.accent : t.textMuted);
        repeatRow.addView(check);

        TextView label = Ui.text(this, t, "  同一号码 5 分钟内再打，正常响铃（防误拦）", 12, t.textMuted);
        repeatRow.addView(label);
        repeatRow.setClickable(true);
        repeatRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = Prefs.repeatMinutes(MainActivity.this) == 0;
                Prefs.setRepeatMinutes(MainActivity.this, on ? 5 : 0);
                check.setText(on ? "☑" : "☐");
                check.setTextColor(on ? t.accent : t.textMuted);
            }
        });
        card.addView(repeatRow);

        parent.addView(card);
    }

    // ---------- 查号 ----------

    private void queryBlock(LinearLayout parent) {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "查号"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);

        input = new EditText(this);
        input.setHint("手机号 / 座机 / 95338");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setTextSize(15);
        input.setTextColor(t.text);
        input.setHintTextColor(Ui.withAlpha(t.textMuted, 0x99));
        input.setBackground(fieldBg());
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);

        TextView go = Ui.pill(this, t, "查询", true);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = Ui.dp(this, 8);
        go.setLayoutParams(glp);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doQuery();
            }
        });
        row.addView(go);
        card.addView(row);

        result = Ui.text(this, t, "", 13, t.text);
        result.setPadding(0, Ui.dp(this, 12), 0, 0);
        card.addView(result);

        parent.addView(card);
    }

    private android.graphics.drawable.Drawable fieldBg() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Ui.withAlpha(t.text, t.isDark() ? 0x1F : 0x0C));
        d.setCornerRadius(Ui.dp(this, 12));
        d.setStroke(Math.max(1, Ui.dp(this, 0.8f)), Ui.withAlpha(t.text, 0x14));
        return d;
    }

    // ---------- 入口 ----------

    private void entriesBlock(LinearLayout parent) {
        LinearLayout card = Ui.card(this, t);

        card.addView(Ui.row(this, t, "我的号码库",
                Blacklist.count(this) + " 条规则 · 完整号码或 95* 前缀",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, RulesActivity.class));
                    }
                }));
        card.addView(Ui.divider(this, t));

        card.addView(Ui.row(this, t, "数据源与更新",
                "号段库 / 号码库 / 在线查询",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, SourcesActivity.class));
                    }
                }));
        card.addView(Ui.divider(this, t));

        card.addView(Ui.row(this, t, "外观主题",
                t.name + " · 液态玻璃，共 3 套",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, AppearanceActivity.class));
                    }
                }));
        card.addView(Ui.divider(this, t));

        card.addView(Ui.row(this, t, "拦截记录与诊断",
                "最近的来电、上次崩溃",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, RecordsActivity.class));
                    }
                }));

        parent.addView(card);
    }

    private void footerBlock(LinearLayout parent) {
        TextView foot = Ui.text(this, t,
                "归属地靠号段推断，只精确到市；黄页为公开客服号；个人骚扰号只能靠在线查询或自己标记。",
                11, Ui.withAlpha(t.textMuted, 0xCC));
        foot.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), 0);
        parent.addView(foot);
    }

    // ---------- 逻辑 ----------

    protected void refreshStatus() {
        if (statusTitle == null) return;
        boolean held = roleHeld();
        statusTitle.setText(held ? "✅ 来电识别已开启" : "⚠️ 来电识别未开启");
        statusTitle.setTextColor(held ? t.text : t.danger);

        StringBuilder sb = new StringBuilder();
        sb.append("号段库 ").append(PhoneLookup.get(this).size()).append(" 条");
        sb.append(" · 黄页 ").append(Yellow.size(this)).append(" 条");
        if (!Overlay.canDraw(this)) sb.append("\n悬浮窗未授权，来电提示只能走通知栏");
        if (!batteryFree(this)) sb.append("\n电池策略可能杀掉后台，点「后台保活」放行");
        if (!held) sb.append("\n不授权的话，来电根本不会经过本 App");
        statusSub.setText(sb.toString());
    }

    private void doQuery() {
        String raw = input.getText().toString().trim();
        String digits = NumUtil.clean(raw);
        if (digits.length() == 0) {
            result.setText("先输个号码");
            return;
        }
        PhoneLookup.Result r = null;
        if (digits.length() >= 7 && !NumUtil.looksInternational(raw)) {
            r = PhoneLookup.get(this).lookupMobile(digits);
        }
        String area = NumUtil.describe(raw, digits, r);
        String yellow = Yellow.label(this, digits);
        String hit = Blacklist.match(this, digits);
        String cached = MarkCache.get(this, digits);

        StringBuilder sb = new StringBuilder();
        sb.append("归属地　").append(area);
        if (yellow != null) sb.append("\n黄页　").append(yellow).append("（正规号，不拦截）");
        if (hit != null) sb.append("\n号码库　命中 ").append(hit);
        if (cached != null) sb.append("\n在线查过　").append(cached);
        result.setText(sb.toString());
    }

    private static boolean batteryFree(Context c) {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean roleHeld() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                return rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                        && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private void requestRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                        toast("已经授权过了");
                        return;
                    }
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            startActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"));
            toast("在默认应用里找「来电显示和骚扰拦截」");
        } catch (Throwable e) {
            toast("请到 电话 App → 设置 → 来电显示和骚扰拦截 里指定");
        }
    }

    /** 三星的强杀主要来自电池策略，把 App 加进"不受限制"比任何保活代码都管用 */
    private void requestBatteryWhitelist() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                android.os.PowerManager pm =
                        (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    toast("已经在白名单里，不需要再设置");
                    return;
                }
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            toast("在「电池」里选「不受限制」");
        } catch (Throwable e) {
            toast("设置 → 应用 → 来电哨兵 → 电池 → 不受限制");
        }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable e) {
            toast("设置 → 应用 → 特殊权限 → 显示在其他应用上层");
        }
    }

    private void showPendingCrashIfAny() {
        final String text = App.lastCrash(this);
        if (text.length() == 0) return;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("上次崩溃记录")
                            .setMessage("上一步：" + Update.lastStep(MainActivity.this)
                                    + "\n\n" + (text.length() > 3500 ? text.substring(0, 3500) : text))
                            .setPositiveButton("分享", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    shareText(text);
                                }
                            })
                            .setNeutralButton("清除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    Prefs.sp(MainActivity.this).edit().remove("last_crash").apply();
                                }
                            })
                            .setNegativeButton("关掉", null)
                            .show();
                } catch (Throwable ignored) {
                }
            }
        }, 700);
    }

    private void shareText(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, "发送到…"));
        } catch (Throwable ignored) {
        }
    }

    /** 启动就炸时用纯代码画一个能发出错误的界面 */
    private void showCrash(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        final String text = "启动失败\n" + App.deviceLine() + "\n\n" + sw.toString();
        try {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundColor(0xFFFFFFFF);
            box.setPadding(36, 90, 36, 36);

            TextView title = new TextView(this);
            title.setText("启动出错了。点下面按钮把这段发给我。");
            title.setTextSize(15);
            box.addView(title);

            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(10);
            tv.setTextIsSelectable(true);
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            box.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            Button share = new Button(this);
            share.setText("分享错误信息");
            share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shareText(text);
                }
            });
            box.addView(share);
            setContentView(box);
        } catch (Throwable ignored) {
            Toast.makeText(this, "启动失败：" + e, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (t != null && Prefs.theme(this) != t.id) {
            recreate();
            return;
        }
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            refreshStatus();
            if (roleHeld()) toast("授权成功，下次来电就会显示归属地");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
