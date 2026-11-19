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

        content.addView(serviceCard());
        content.addView(watchCard());
        content.addView(previewCard());
        content.addView(crashCard());
        content.addView(timingCard());
        content.addView(traceCard());
        content.addView(directoryCard());
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
                "点按钮，屏幕上方会出现 7 秒提示，位置和样式与真实来电一致。"
                        + "真来电时也只有这 7 秒，屏幕朝下或放兜里就看不到。");
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
                preview("我的号码库", "绍兴 · 电信　138 0013 8000", true);
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

    /** 服务状态：判断系统的拦截服务到底有没有被调用过 */
    private LinearLayout serviceCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "服务状态"));

        serviceInfo = Ui.sub(this, t, "");
        serviceInfo.setPadding(0, Ui.dp(this, 6), 0, 0);
        serviceInfo.setLineSpacing(Ui.dp(this, 4), 1f);
        card.addView(serviceInfo);

        TextView hint = Ui.sub(this, t,
                "测试必须用另一部电话打进来：从本机拨出去的电话，系统根本不会交给拦截服务，"
                        + "计数器永远是 0。\n"
                        + "「服务绑定」有记录、「最近调用」为空 → 系统绑定了但没把电话交过来；"
                        + "两行都是 0 → 服务被冻结或角色被收回。");
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(hint);

        TextView probe = Ui.sub(this, t, screeningProbe());
        probe.setPadding(0, Ui.dp(this, 12), 0, 0);
        probe.setTextColor(t.textMuted);
        card.addView(probe);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 12);
        btns.setLayoutParams(blp);

        TextView fix = Ui.pill(this, t, "重新授予拦截角色", true);
        fix.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askRole();
            }
        });
        btns.addView(fix);

        TextView batt = Ui.pill(this, t, "应用设置·电池", false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = Ui.dp(this, 8);
        batt.setLayoutParams(ilp);
        batt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + getPackageName())));
                    toast("把「电池」改成「不受限制」，再点「强制停止」下一次系统才会重新拉起服务");
                } catch (Throwable e) {
                    toast("设置 → 应用 → 来电哨兵 → 电池 → 不受限制");
                }
            }
        });
        btns.addView(batt);

        TextView zero = Ui.pill(this, t, "清零计数", false);
        LinearLayout.LayoutParams zlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        zlp.leftMargin = Ui.dp(this, 8);
        zero.setLayoutParams(zlp);
        zero.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.sp(RecordsActivity.this).edit()
                        .remove("svc_count").remove("svc_in").remove("svc_dir")
                        .remove("svc_last").remove("bind_count").remove("bind_last")
                        .apply();
                refreshServiceInfo();
                toast("计数已清零，接一通电话再看");
            }
        });
        btns.addView(zero);
        card.addView(btns);
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
        long bind = Prefs.sp(this).getLong("bind_last", 0L);
        int bindN = Prefs.sp(this).getInt("bind_count", 0);
        long last = Prefs.sp(this).getLong("svc_last", 0L);
        int total = Prefs.sp(this).getInt("svc_count", 0);
        int in = Prefs.sp(this).getInt("svc_in", 0);
        int dir = Prefs.sp(this).getInt("svc_dir", 0);
        boolean held = roleHeld();

        StringBuilder sb = new StringBuilder();
        sb.append("拦截角色：").append(held ? "✅ 已授予" : "❌ 未授予，来电不会被处理");

        sb.append("\n后台限制：").append(bgRestricted()
                ? "❌ 受限，三星会冻结本应用" : "✅ 不受限");

        sb.append("\n服务绑定：").append(bind == 0
                ? "❌ 系统从未绑定过"
                : "✅ 共 " + bindN + " 次，最近 " + ago(bind));

        sb.append("\n最近调用：").append(last == 0
                ? "❌ 从未被调用" : "✅ " + clock(last) + "（" + ago(last) + "）");

        if (last != 0) {
            sb.append("　").append(dir == 0 ? "来电" : dir == 1 ? "拨出" : "未知方向");
        }

        sb.append("\n累计调用：").append(total).append(" 次");
        if (total > 0) sb.append("（其中来电 ").append(in).append("）");

        serviceInfo.setText(sb.toString());
        serviceInfo.setTextColor(held && total > 0 ? t.textMuted : (held ? t.text : t.danger));
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

    /** 运行日志：来电链路上每一步的真实结果，出问题时直接发这段 */
    private LinearLayout traceCard() {
        LinearLayout card = Ui.card(this, t);
        card.addView(Ui.title(this, t, "运行日志"));

        TextView hint = Ui.sub(this, t,
                "记录了进入服务、判定、respond、悬浮窗 addView 结果、通知是否发出，"
                        + "以及被吞掉的异常原文。接完电话点「分享日志」发我即可。");
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
            traceInfo.setText("还没有日志。接一通电话后再看。");
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
        refreshServiceInfo();
        renderWatch();
        renderTrace();
        if (overlayStatus != null) {
            boolean ov = Overlay.canDraw(this);
            boolean nt = Notify.allowed(this);
            overlayStatus.setText("悬浮窗：" + (ov ? "✅ 已授予" : "❌ 未授予")
                    + "　通知：" + (nt ? "✅ 已允许" : "❌ 被关闭"));
            overlayStatus.setTextColor(ov && nt ? t.textMuted : t.danger);
        }
    }
}
