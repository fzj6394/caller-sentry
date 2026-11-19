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
    private TextView overlayStatus;
    private TextView serviceInfo;

    @Override
    protected String pageTitle() {
        return "记录与诊断";
    }

    @Override
    protected String pageSubtitle() {
        return "来电记录 · 处理耗时 · 诊断";
    }

    @Override
    protected void build(LinearLayout content) {
        content.addView(serviceCard());
        content.addView(recordsCard());
        content.addView(timingCard());
        content.addView(traceCard());
        content.addView(advancedCard());

        advancedBox = new LinearLayout(this);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.setVisibility(View.GONE);
        advancedBox.addView(watchCard());
        advancedBox.addView(previewCard());
        advancedBox.addView(probeCard());
        advancedBox.addView(crashCard());
        advancedBox.addView(directoryCard());
        content.addView(advancedBox);
    }

    /** 记录卡：号码 + 时间/归属地 + 标签，右侧「标记」 */
    private LinearLayout recordsCard() {
        LinearLayout card = Ui.card(this, t);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);

        logTitle = Ui.title(this, t, "记录");
        logTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(logTitle);

        TextView tagBtn = Ui.link(this, t, "选号码标记", new Runnable() {
            @Override
            public void run() {
                pickNumber();
            }
        });
        head.addView(tagBtn);

        TextView clear = Ui.link(this, t, "清空", new Runnable() {
            @Override
            public void run() {
                LogStore.clear(RecordsActivity.this);
                renderLogs();
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dp(this, 14);
        clear.setLayoutParams(clp);
        head.addView(clear);

        card.addView(head);

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(logBox);
        return card;
    }

    /** 高级诊断入口：默认收起，避免整页都是调试信息 */
    private LinearLayout advancedCard() {
        LinearLayout card = Ui.card(this, t);
        LinearLayout row = Ui.item(this, t, "高级诊断", "拦截服务 / 自测 / 探测 ›", new Runnable() {
            @Override
            public void run() {
                toggleAdvanced();
            }
        });
        advancedValue = (TextView) row.getChildAt(1);
        card.addView(row);
        return card;
    }

    private void toggleAdvanced() {
        if (advancedBox == null) return;
        boolean show = advancedBox.getVisibility() != View.VISIBLE;
        advancedBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (advancedValue != null) {
            advancedValue.setText(show ? "收起" : "拦截服务 / 自测 / 探测 ›");
        }
    }

    /** 悬浮条预览：不用等真来电，点一下就能看到 7 秒提示 */
    private LinearLayout previewCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "悬浮条预览"));

        overlayStatus = Ui.sub(this, t, "");
        overlayStatus.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(overlayStatus);

        final TextView open = Ui.pill(this, t, "去开启悬浮窗", false);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        olp.topMargin = Ui.dp(this, 10);
        open.setLayoutParams(olp);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName())));
                } catch (Throwable e) {
                    toast("设置 → 应用 → 特殊权限 → 显示在其他应用上层");
                }
            }
        });
        card.addView(open);

        TextView hint = Ui.sub(this, t,
                "点按钮，屏幕上方会出现 7 秒提示，样式与真实来电一致；"
                        + "真来电时会一直显示到接听或挂断。\n"
                        + "锁屏来电走另一套：系统会隐藏悬浮层，改用能覆盖锁屏的卡片。");
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
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
                preview("疑似骚扰电话", "绍兴 · 电信　138 0013 8000", true);
            }
        });
        btns.addView(spam);

        TextView note = Ui.pill(this, t, "预览通知", false);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = Ui.dp(this, 8);
        note.setLayoutParams(nlp);
        note.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Notify.allowed(RecordsActivity.this)) {
                    toast("通知没开：设置 → 应用 → 来电哨兵 → 通知");
                    return;
                }
                Notify.show(RecordsActivity.this, "绍兴 · 电信", "0575 8888 8888");
                toast("下拉通知栏看看");
            }
        });
        btns.addView(note);
        card.addView(btns);

        LinearLayout btns2 = new LinearLayout(this);
        btns2.setOrientation(LinearLayout.HORIZONTAL);
        btns2.setPadding(0, Ui.dp(this, 8), 0, 0);

        TextView lock = Ui.pill(this, t, "锁屏卡片", false);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CallCardActivity.show(RecordsActivity.this,
                        "哈尔滨 · 移动", "158 4661 0296", false);
                toast("锁屏后再来一次，就能看到它在锁屏上的样子");
            }
        });
        btns2.addView(lock);
        card.addView(btns2);

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

    /** 顶部摘要：兼容模式是否在跑、最近一通什么时候 */
    private LinearLayout serviceCard() {
        LinearLayout card = Ui.card(this, t);

        LinearLayout line = Ui.statusLine(this, t, "", 0xFF2FA84F);
        statusDot = line.getChildAt(0);
        statusTitle = (TextView) line.getChildAt(1);
        card.addView(line);

        serviceInfo = Ui.sub(this, t, "");
        serviceInfo.setPadding(0, Ui.dp(this, 6), 0, 0);
        serviceInfo.setLineSpacing(Ui.dp(this, 4), 1f);
        card.addView(serviceInfo);

        return card;
    }

    /** 系统侧探测：这个放高级里，平时不用看 */
    private LinearLayout probeCard() {
        LinearLayout card = Ui.card(this, t);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = Ui.title(this, t, "系统侧探测");
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);
        head.addView(Ui.link(this, t, "清零计数", new Runnable() {
            @Override
            public void run() {
                Prefs.sp(RecordsActivity.this).edit()
                        .remove("svc_count").remove("svc_in").remove("svc_dir")
                        .remove("svc_last").remove("bind_count").remove("bind_last")
                        .remove("w_count").remove("w_last")
                        .apply();
                refreshServiceInfo();
                toast("计数已清零");
            }
        }));
        card.addView(head);

        TextView probe = Ui.sub(this, t, screeningProbe());
        probe.setPadding(0, Ui.dp(this, 8), 0, 0);
        probe.setTextColor(t.textMuted);
        card.addView(probe);

        TextView hint = Ui.sub(this, t, "拦截服务在本机不可用，数字为 0 属正常。");
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(hint);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(android.view.Gravity.CENTER_VERTICAL);
        links.setPadding(0, Ui.dp(this, 4), 0, 0);

        links.addView(Ui.link(this, t, "重新授予角色", new Runnable() {
            @Override
            public void run() {
                askRole();
            }
        }));

        TextView batt = Ui.link(this, t, "应用设置·电池", new Runnable() {
            @Override
            public void run() {
                try {
                    startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + getPackageName())));
                    toast("把「电池」改成「不受限制」");
                } catch (Throwable e) {
                    toast("设置 → 应用 → 来电哨兵 → 电池 → 不受限制");
                }
            }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = Ui.dp(this, 18);
        batt.setLayoutParams(ilp);
        links.addView(batt);

        card.addView(links);
        return card;
    }

    /**
     * 系统到底认不认这个服务：问 PackageManager 要所有声明的拦截服务。
     * 我们不在这个列表里，就说明系统根本不会来绑我们，跟代码无关。
     */
    private String screeningProbe() {
        StringBuilder sb = new StringBuilder();
        try {
            android.content.Intent i = new android.content.Intent("android.telecom.CallScreeningService");
            java.util.List<android.content.pm.ResolveInfo> list = getPackageManager()
                    .queryIntentServices(i, 0);
            int n = list == null ? 0 : list.size();
            sb.append("系统可发现的拦截服务：").append(n).append(" 个");
            boolean hasUs = false;
            if (list != null) {
                for (android.content.pm.ResolveInfo ri : list) {
                    if (ri.serviceInfo == null) continue;
                    boolean us = getPackageName().equals(ri.serviceInfo.packageName);
                    if (us) hasUs = true;
                    sb.append("\n· ");
                    if (us) {
                        sb.append("✅ 本应用（").append(ri.serviceInfo.name).append("）");
                    } else {
                        sb.append(ri.serviceInfo.packageName);
                    }
                }
            }
            if (!hasUs) {
                sb.append("\n❌ 列表里没有本应用 → 系统不会来绑定，这属于安装/组件问题");
            }
            android.content.ComponentName cn = new android.content.ComponentName(
                    this, ScreeningService.class);
            int st = getPackageManager().getComponentEnabledSetting(cn);
            sb.append("\n本应用服务组件：")
                    .append(st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            ? "❌ 被禁用"
                            : "✅ 启用");
        } catch (Throwable e) {
            sb.append("探测失败：").append(e);
        }
        return sb.toString();
    }

    // ============ 备用通道自测：通话状态广播（不依赖拦截角色）============

    private TextView watchInfo;
    private android.content.BroadcastReceiver watchRx;
    private long watchUntil;
    private final android.os.Handler watchHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable watchTick = new Runnable() {
        @Override
        public void run() {
            renderWatch();
            if (watchUntil > 0 && System.currentTimeMillis() < watchUntil) {
                watchHandler.postDelayed(this, 1000);
            } else {
                stopWatch(false);
            }
        }
    };

    private LinearLayout watchCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "备用通道自测"));

        int done = Prefs.sp(this).getInt("svc_count", 0);
        TextView desc = Ui.sub(this, t, done > 0
                ? "拦截服务已经能收到来电（累计 " + done + " 次），本项可以忽略。"
                : "拦截角色被三星挡住时，还有一条路：监听系统通话状态广播。"
                        + "它不需要拦截角色，但能不能拿到号码要看系统给不给。"
                        + "\n点按钮，然后用另一部电话打进来一次，本页保持在前台就行。");
        desc.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(desc);

        watchInfo = Ui.sub(this, t, "");
        watchInfo.setPadding(0, Ui.dp(this, 10), 0, 0);
        watchInfo.setLineSpacing(Ui.dp(this, 4), 1f);
        card.addView(watchInfo);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 12);
        btns.setLayoutParams(blp);

        TextView start = Ui.pill(this, t, "开始监听 60 秒", true);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPhonePerms();
            }
        });
        btns.addView(start);

        TextView stop = Ui.pill(this, t, "停止", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        stop.setLayoutParams(slp);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopWatch(true);
            }
        });
        btns.addView(stop);
        card.addView(btns);
        return card;
    }

    private void askPhonePerms() {
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        String[] all = new String[]{"android.permission.READ_PHONE_STATE",
                "android.permission.READ_CALL_LOG"};
        for (String p : all) {
            try {
                if (checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    need.add(p);
                }
            } catch (Throwable ignored) {
            }
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 302);
            return;
        }
        startWatch();
    }

    private void startWatch() {
        if (watchRx != null) return;
        Prefs.sp(this).edit().putInt("watch_count", 0)
                .putString("watch_state", "").putString("watch_num", "").apply();
        watchRx = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context c, Intent in) {
                try {
                    String st = in.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE);
                    String num = in.getStringExtra(
                            android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER);
                    int n = Prefs.sp(RecordsActivity.this).getInt("watch_count", 0) + 1;
                    Prefs.sp(RecordsActivity.this).edit()
                            .putInt("watch_count", n)
                            .putString("watch_state", st == null ? "?" : st)
                            .putString("watch_num", num == null ? "" : num)
                            .apply();
                    renderWatch();
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            registerReceiver(watchRx, new android.content.IntentFilter(
                    android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED));
        } catch (Throwable e) {
            watchRx = null;
            toast("注册失败：" + e);
            return;
        }
        watchUntil = System.currentTimeMillis() + 60_000L;
        watchHandler.post(watchTick);
    }

    private void stopWatch(boolean say) {
        watchUntil = 0;
        watchHandler.removeCallbacks(watchTick);
        if (watchRx != null) {
            try {
                unregisterReceiver(watchRx);
            } catch (Throwable ignored) {
            }
            watchRx = null;
        }
        renderWatch();
        if (say) toast("已停止监听");
    }

    private void renderWatch() {
        if (watchInfo == null) return;
        int n = Prefs.sp(this).getInt("watch_count", 0);
        String st = Prefs.sp(this).getString("watch_state", "");
        String num = Prefs.sp(this).getString("watch_num", "");

        StringBuilder sb = new StringBuilder();
        if (watchRx != null) {
            long left = Math.max(0, (watchUntil - System.currentTimeMillis()) / 1000);
            sb.append("监听中… 剩余 ").append(left).append(" 秒");
        } else {
            sb.append("未监听");
        }
        sb.append("\n收到事件：").append(n).append(" 个");
        if (n > 0) {
            sb.append("\n状态：").append(st.length() == 0 ? "?" : st);
            sb.append("\n号码：").append(num.length() == 0 ? "❌ 空（系统没给）" : "✅ " + num);
        }
        watchInfo.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        stopWatch(false);
        super.onDestroy();
    }

    private void refreshServiceInfo() {
        if (serviceInfo == null) return;
        boolean running = Watcher.isRunning();
        long wLast = Prefs.sp(this).getLong("w_last", 0L);
        int wCount = Prefs.sp(this).getInt("w_count", 0);
        int svcTotal = Prefs.sp(this).getInt("svc_count", 0);

        if (statusTitle != null) {
            statusTitle.setText(running ? "兼容模式运行中" : "兼容模式未运行");
            statusTitle.setTextColor(running ? t.text : t.danger);
        }
        paintDot(statusDot, running ? 0xFF2FA84F : 0xFFD9483B);

        StringBuilder sb = new StringBuilder();
        sb.append(wLast == 0
                ? "还没有接到来电"
                : "最近 " + clock(wLast) + "（" + ago(wLast) + "）· 累计 " + wCount + " 通");
        if (!running) sb.append("\n打开首页的兼容模式开关后，来电时会显示归属地");
        sb.append("\n悬浮窗 ").append(Overlay.canDraw(this) ? "可用" : "未授权")
                .append(" · 通知 ").append(Notify.allowed(this) ? "允许" : "关闭");

        serviceInfo.setText(sb.toString());
        serviceInfo.setTextColor(running ? t.textMuted : t.danger);
    }

    private void paintDot(View v, int color) {
        if (v == null) return;
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
    }

    private static String ago(long ts) {
        long d = System.currentTimeMillis() - ts;
        if (d < 0) d = 0;
        if (d < 60000L) return (d / 1000) + " 秒前";
        if (d < 3600000L) return (d / 60000L) + " 分钟前";
        if (d < 86400000L) return (d / 3600000L) + " 小时前";
        return (d / 86400000L) + " 天前";
    }

    private static String clock(long ts) {
        try {
            return new java.text.SimpleDateFormat("MM-dd HH:mm",
                    java.util.Locale.CHINA).format(new java.util.Date(ts));
        } catch (Throwable e) {
            return "";
        }
    }

    /** 后台受限 = 三星把本应用冻结了，服务自然起不来 */
    private boolean bgRestricted() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.app.ActivityManager am = (android.app.ActivityManager)
                        getSystemService(ACTIVITY_SERVICE);
                return am != null && am.isBackgroundRestricted();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean roleHeld() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = (android.app.role.RoleManager)
                        getSystemService(ROLE_SERVICE);
                return rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                        && rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private void askRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = (android.app.role.RoleManager)
                        getSystemService(ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                    if (rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                        // 角色在手但系统不调用：把角色先切走再切回来，能重置系统的绑定缓存
                        try {
                            startActivity(new Intent(
                                    android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                            toast("把「来电显示和骚扰拦截」先改成别的，再改回来电哨兵");
                        } catch (Throwable e) {
                            toast("已经授权过了，问题可能在电池/休眠策略");
                        }
                        return;
                    }
                    startActivityForResult(rm.createRequestRoleIntent(
                            android.app.role.RoleManager.ROLE_CALL_SCREENING), 401);
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

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 401) refreshServiceInfo();
    }

    /** 来电耗时：判定 / respond / 归属地 / 合计，用来验证优化有没有生效 */
    private LinearLayout timingCard() {
        LinearLayout card = Ui.card(this, t);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = Ui.title(this, t, "来电耗时");
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);

        head.addView(Ui.link(this, t, "清空", new Runnable() {
            @Override
            public void run() {
                Timing.clear(RecordsActivity.this);
                recreate();
            }
        }));
        card.addView(head);

        java.util.List<String> rows = Timing.all(this);
        if (rows.isEmpty()) {
            TextView empty = Ui.sub(this, t, "还没有数据，接一通电话再看。");
            empty.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
            card.addView(empty);
            return card;
        }

        TextView hint = Ui.sub(this, t, "判定 = 查号码库；respond = 让来电界面出现；归属地 = 查号段库");
        hint.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 6));
        card.addView(hint);

        int n = Math.min(rows.size(), 6);
        for (int i = 0; i < n; i++) {
            TextView tv = Ui.text(this, t, rows.get(i), 11, t.textMuted);
            tv.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            card.addView(tv);
            if (i < n - 1) card.addView(thinDivider());
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
        if (code == 302) {
            boolean ok = true;
            for (int g : grant) if (g != android.content.pm.PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) {
                startWatch();
            } else {
                toast("两个权限都给了才能测：电话 + 通话记录");
            }
        }
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

    private TextView traceInfo;
    private View statusDot;
    private TextView statusTitle;
    private LinearLayout advancedBox;
    private TextView advancedValue;

    /** 运行日志：来电链路上每一步的真实结果，出问题时直接发这段 */
    private LinearLayout traceCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "运行日志"));

        LinearLayout toggle = Ui.item(this, t, "记录日志",
                Prefs.traceOn(this) ? "开" : "关", new Runnable() {
                    @Override
                    public void run() {
                        boolean on = !Prefs.traceOn(RecordsActivity.this);
                        Prefs.setTraceOn(RecordsActivity.this, on);
                        if (!on) Trace.clear(RecordsActivity.this);
                        toast(on ? "已开启：之后的来电会逐条记录" : "已关闭，不再写入");
                        recreate();
                    }
                });
        card.addView(toggle);

        TextView hint = Ui.sub(this, t, Prefs.traceOn(this)
                ? "记的是进入服务、判定、respond、悬浮窗结果、通知是否发出，以及被吞掉的异常原文。"
                : "平时关掉不写盘。来电不提示、或想让我看问题时再打开。");
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        card.addView(hint);

        traceInfo = Ui.text(this, t, "", 11, t.text);
        traceInfo.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(traceInfo);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 12);
        btns.setLayoutParams(blp);

        TextView share = Ui.pill(this, t, "分享日志", true);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, Trace.dump(RecordsActivity.this));
                try {
                    startActivity(Intent.createChooser(i, "发送到…"));
                } catch (Throwable ignored) {
                }
            }
        });
        btns.addView(share);

        TextView refresh = Ui.pill(this, t, "刷新", false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.dp(this, 8);
        refresh.setLayoutParams(rlp);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renderTrace();
                toast("已刷新");
            }
        });
        btns.addView(refresh);

        TextView probe = Ui.pill(this, t, "写入自检", false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = Ui.dp(this, 8);
        probe.setLayoutParams(plp);
        probe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Trace.add(RecordsActivity.this, "test", "写入自检 ping");
                watchHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        renderTrace();
                        int n = Trace.all(RecordsActivity.this).size();
                        toast(n > 0 ? "日志可用，当前 " + n + " 条" : "日志写入失败：读回 0 条");
                    }
                }, 400);
            }
        });
        btns.addView(probe);

        TextView clear = Ui.pill(this, t, "清空", false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dp(this, 8);
        clear.setLayoutParams(clp);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Trace.clear(RecordsActivity.this);
                renderTrace();
            }
        });
        btns.addView(clear);
        card.addView(btns);
        return card;
    }

    private void renderTrace() {
        if (traceInfo == null) return;
        java.util.List<String> rows = Trace.all(this);
        if (rows.isEmpty()) {
            traceInfo.setText("还没有日志。\n点「写入自检」可验证日志是否可用；"
                    + "如果自检有记录、而接电话后没有新增，说明来电没进到本应用。");
            return;
        }
        int n = Math.min(rows.size(), 40);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(rows.get(i));
        }
        traceInfo.setText(sb.toString());
    }

    private void renderLogs() {
        if (logBox == null) return;
        logBox.removeAllViews();
        List<String> logs = LogStore.all(this);
        logTitle.setText("记录（" + logs.size() + "）");

        if (logs.isEmpty()) {
            TextView empty = Ui.sub(this, t, "还没有记录。接一通电话，这里就会出现号码和归属地。");
            empty.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 8));
            logBox.addView(empty);
            return;
        }

        int n = Math.min(logs.size(), 30);
        for (int i = 0; i < n; i++) {
            final String line = logs.get(i);
            String[] p = line.split("\\s{2,}");
            String time = p.length > 0 ? p[0] : "";
            String area = p.length > 2 ? p[2] : "";
            final String num = numberFrom(line);
            String tag = num.length() >= 3 ? Tags.get(this, num) : null;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvNum = Ui.text(this, t, num.length() == 0 ? line : num, 13.5f, t.text);
            col.addView(tvNum);

            String hm = time.length() > 6 ? time.substring(6) : time;
            TextView tvSub = Ui.text(this, t, hm + " · " + area, 11, t.textMuted);
            tvSub.setPadding(0, Ui.dp(this, 2), 0, 0);
            col.addView(tvSub);
            row.addView(col);

            if (tag != null) row.addView(badge(tag));

            if (num.length() >= 3) {
                TextView mark = Ui.link(this, t, "标记", new Runnable() {
                    @Override
                    public void run() {
                        askTag(num);
                    }
                });
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                mlp.leftMargin = Ui.dp(this, 10);
                mark.setLayoutParams(mlp);
                row.addView(mark);
            }

            logBox.addView(row);
            if (i < n - 1) logBox.addView(thinDivider());
        }

        if (logs.size() > n) {
            TextView more = Ui.link(this, t, "还有 " + (logs.size() - n) + " 条旧记录", null);
            logBox.addView(more);
        }
    }

    /** 小标签：绿=记号、红=可疑、灰=正常 */
    private TextView badge(String text) {
        boolean warn = Tags.warn(text);
        boolean plain = "正常".equals(text);
        int fg = warn ? 0xFFB3322A : plain ? 0xFF6B7684 : 0xFF3B6D11;
        int bg = warn ? 0x14B3322A : plain ? 0x146B7684 : 0x143B6D11;

        TextView tv = Ui.text(this, t, Tags.SPAM.equals(text) ? "疑似骚扰" : text, 11, fg);
        tv.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(Ui.dp(this, 7));
        tv.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View thinDivider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.withAlpha(t.text, t.isDark() ? 0x14 : 0x0D));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.7f))));
        return v;
    }

    /** 记录行格式：MM-dd HH:mm  号码  归属地  [处理结果]，取出第 2 段 */
    private static String numberFrom(String line) {
        try {
            String[] parts = line.split("\\s{2,}");
            if (parts.length >= 2) {
                String d = NumUtil.clean(parts[1]);
                if (d.length() >= 3) return d;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 从通话记录里挑号码，挑完直接选标签，不用手打 */
    private void pickNumber() {
        boolean granted = true;
        try {
            granted = checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
        }
        if (!granted) {
            toast("需要「通话记录」权限才能列出号码");
            requestPermissions(new String[]{"android.permission.READ_CALL_LOG"}, 301);
            return;
        }

        final java.util.List<String> nums = new java.util.ArrayList<String>();
        final java.util.List<String> items = new java.util.ArrayList<String>();
        try {
            android.database.Cursor cu = getContentResolver().query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    new String[]{android.provider.CallLog.Calls.NUMBER,
                            android.provider.CallLog.Calls.CACHED_NAME},
                    null, null,
                    android.provider.CallLog.Calls.DATE + " DESC");
            if (cu != null) {
                java.util.HashSet<String> seen = new java.util.HashSet<String>();
                while (cu.moveToNext() && nums.size() < 20) {
                    String d = NumUtil.clean(cu.getString(0));
                    if (d.length() < 3 || !seen.add(d)) continue;
                    String name = cu.getString(1);
                    String tag = Tags.get(this, d);
                    StringBuilder sb = new StringBuilder(d);
                    if (name != null && name.length() > 0) sb.append("（").append(name).append("）");
                    if (tag != null) sb.append("　已标 · ").append(tag);
                    nums.add(d);
                    items.add(sb.toString());
                }
                cu.close();
            }
        } catch (Throwable ignored) {
        }

        if (nums.isEmpty()) {
            toast("通话记录里没有号码，可以手动输入");
            askNumberThenTag();
            return;
        }

        items.add(0, "手动输入号码…");
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择要标记的号码")
                .setItems(items.toArray(new String[0]),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                if (which == 0) {
                                    askNumberThenTag();
                                } else {
                                    askTag(nums.get(which - 1));
                                }
                            }
                        })
                .show();
    }

    private void askNumberThenTag() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入完整号码，如 17303642680");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        new android.app.AlertDialog.Builder(this)
                .setTitle("标记号码")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("下一步", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String num = NumUtil.clean(input.getText().toString());
                        if (num.length() < 3) {
                            toast("号码不完整");
                            return;
                        }
                        askTag(num);
                    }
                })
                .show();
    }

    /** 给号码打标签；再点一次可以改或清除 */
    private void askTag(final String num) {
        final String current = Tags.get(this, num);
        String title = num + (current == null ? "" : "　现在：" + current);
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(Tags.CHOICES, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String label = Tags.CHOICES[which];
                        if ("清除标记".equals(label)) {
                            Tags.remove(RecordsActivity.this, num);
                            toast("已清除标记");
                        } else {
                            Tags.put(RecordsActivity.this, num, label);
                            toast("已标记「" + label + "」，下次来电直接显示");
                        }
                        renderLogs();
                    }
                })
                .show();
    }

    @Override
    protected void onPageResume() {
        renderLogs();
        refreshServiceInfo();
        renderWatch();
        // 留在日志里一条"我来过"，用来区分"没调用"和"日志没写进去"
        Trace.add(this, "ui", "打开诊断页");
        watchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                renderTrace();
            }
        }, 350);
        if (overlayStatus != null) {
            boolean ov = Overlay.canDraw(this);
            boolean nt = Notify.allowed(this);
            overlayStatus.setText("悬浮窗：" + (ov ? "✅ 已授予" : "❌ 未授予")
                    + "　通知：" + (nt ? "✅ 已允许" : "❌ 被关闭"));
            overlayStatus.setTextColor(ov && nt ? t.textMuted : t.danger);
        }
    }
}
