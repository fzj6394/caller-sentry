package cn.fzj.callersentry;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 联网更新：只做 GET 下载，不上传任何号码。
 * 号段库整包替换（带 If-Modified-Since，没变就是 304，省流量）；
 * 号码库是文本（每行一个号码或前缀），合并进本地库。
 */
public final class Update {

    public static final String DEFAULT_SEG_URL =
            "https://cdn.jsdelivr.net/gh/xluohome/phonedata@master/phone.dat";

    /** vlongen/BanHarassment：国内骚扰号段数据集，长期更新，JSON 数组 */
    public static final String DEFAULT_SPAM_URL =
            "https://cdn.jsdelivr.net/gh/vlongen/BanHarassment@main/dist/phone.json";

    private static final String UA = "CallerSentry/1.1 (Android)";

    public interface Callback {
        /** 回调在后台线程，界面更新请自己切回主线程 */
        void done(boolean ok, String message);
    }

    private Update() {
    }

    /** 号段库：conditional=true 时带上 If-Modified-Since，用于启动自动检查 */
    public static void updateSegments(final Context ctx, final String url,
                                      final boolean conditional, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    if (url == null || url.trim().length() == 0) {
                        safeDone(cb, false, "先填号段库地址");
                        return;
                    }
                    String lm = conditional ? Prefs.sp(ctx).getString("seg_lm", null) : null;
                    c = open(url.trim(), lm);
                    int code = c.getResponseCode();
                    if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        safeDone(cb, true, "号段库已是最新");
                        return;
                    }
                    if (code != HttpURLConnection.HTTP_OK) {
                        safeDone(cb, false, "服务器返回 " + code);
                        return;
                    }
                    // jsDelivr 这类 CDN 不认 If-Modified-Since，只能靠长度判断是否同一份
                    int remoteLen = c.getContentLength();
                    if (conditional && remoteLen > 0
                            && remoteLen == PhoneLookup.get(ctx).dataLength()) {
                        safeDone(cb, true, "号段库已是最新");
                        return;
                    }
                    byte[] body = readBody(c.getInputStream(), 16 * 1024 * 1024);
                    String newLm = c.getHeaderField("Last-Modified");
                    if (!PhoneLookup.isPlausible(body)) {
                        safeDone(cb, false, "下回来的文件不像号段库，已丢弃");
                        return;
                    }
                    File dir = ctx.getFilesDir();
                    File tmp = new File(dir, PhoneLookup.FILE_NAME + ".tmp");
                    FileOutputStream fo = new FileOutputStream(tmp);
                    try {
                        fo.write(body);
                        fo.getFD().sync();
                    } finally {
                        fo.close();
                    }
                    File dst = new File(dir, PhoneLookup.FILE_NAME);
                    if (!tmp.renameTo(dst)) {
                        dst.delete();
                        if (!tmp.renameTo(dst)) {
                            tmp.delete();
                            safeDone(cb, false, "写入失败，可能是空间不足");
                            return;
                        }
                    }
                    if (newLm != null) {
                        Prefs.sp(ctx).edit().putString("seg_lm", newLm).apply();
                    }
                    PhoneLookup.reload();
                    PhoneLookup.warmup(ctx);
                    int n = PhoneLookup.get(ctx).size();
                    safeDone(cb, true, "号段库已更新：" + n + " 条");
                } catch (Throwable t) {
                    safeDone(cb, false, "更新失败：" + shortMsg(t));
                } finally {
                    if (c != null) {
                        try {
                            c.disconnect();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }, "upd-seg").start();
    }

    /** 号码库：纯文本，一行一个（也认逗号/空格分隔），合并进本地库 */
    public static void updateSpam(final Context ctx, final String url, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    if (url == null || url.trim().length() == 0) {
                        safeDone(cb, false, "先填号码库地址");
                        return;
                    }
                    step(ctx, "下载号码库");
                    c = open(url.trim(), null);
                    int code = c.getResponseCode();
                    step(ctx, "HTTP " + code);
                    if (code != HttpURLConnection.HTTP_OK) {
                        safeDone(cb, false, "服务器返回 " + code);
                        return;
                    }
                    byte[] body = readBody(c.getInputStream(), 4 * 1024 * 1024);
                    step(ctx, "下载完成 " + body.length + " 字节");
                    String text = new String(body, "UTF-8");
                    List<String> rules = parseRules(text);
                    step(ctx, "解析出 " + rules.size() + " 条规则");
                    if (rules.isEmpty()) {
                        safeDone(cb, false, "地址里没解析出号码");
                        return;
                    }
                    int before = Blacklist.count(ctx);
                    Blacklist.addAll(ctx, rules);
                    int after = Blacklist.count(ctx);
                    step(ctx, "合并完成，共 " + after + " 条");
                    safeDone(cb, true, "号码库已合并：" + (after - before) + " 条新增，共 " + after + " 条");
                } catch (Throwable t) {
                    step(ctx, "出错：" + shortMsg(t));
                    safeDone(cb, false, "更新失败：" + shortMsg(t));
                } finally {
                    if (c != null) {
                        try {
                            c.disconnect();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }, "upd-spam").start();
    }


    /** 黄页：文本格式「号码|名称|类别」，合并进本地 SQLite */
    public static void updateYellow(final Context ctx, final String url, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    if (url == null || url.trim().length() == 0) {
                        safeDone(cb, false, "先填黄页地址");
                        return;
                    }
                    step(ctx, "下载黄页");
                    c = open(url.trim(), null);
                    int code = c.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) {
                        safeDone(cb, false, "服务器返回 " + code);
                        return;
                    }
                    byte[] body = readBody(c.getInputStream(), 4 * 1024 * 1024);
                    String text = new String(body, "UTF-8");
                    int n = YellowStore.importText(ctx, text);
                    if (n == 0) {
                        safeDone(cb, false, "没解析出「号码|名称」格式的行");
                        return;
                    }
                    safeDone(cb, true, "黄页已合并：" + n + " 条，现有 " + Yellow.size(ctx) + " 条");
                } catch (Throwable t) {
                    safeDone(cb, false, "更新失败：" + shortMsg(t));
                } finally {
                    if (c != null) {
                        try {
                            c.disconnect();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }, "upd-yellow").start();
    }

    private static HttpURLConnection open(String url, String ifModifiedSince) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        if (ifModifiedSince != null && ifModifiedSince.length() > 0) {
            c.setRequestProperty("If-Modified-Since", ifModifiedSince);
        }
        return c;
    }

    private static byte[] readBody(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 1024);
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > limit) break;
        }
        try {
            in.close();
        } catch (Throwable ignored) {
        }
        return out.toByteArray();
    }

    /**
     * 认三种格式：JSON 数组（BanHarassment 那种）、CSV（首列是号码）、纯文本（一行一个）。
     * 纯数字 3-6 位当成号段前缀（如 133 → 133*），7 位以上当成完整号码；
     * 0 开头的 3-4 位按区号处理，直接跳过，免得把所有固话来电都标成骚扰。
     */
    static List<String> parseRules(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String t = text.trim();
        if (t.startsWith("[") || t.startsWith("{")) {
            // 手写提取引号里的字符串，不依赖 org.json：这活儿只有一句话，少一个依赖少一个坑
            List<String> quoted = extractQuoted(t);
            for (String q : quoted) {
                addRule(out, q);
            }
            if (out.size() > 0) return out;
        }
        String[] lines = t.split("\r?\n");
        for (String line : lines) {
            String s = line == null ? "" : line.trim();
            if (s.length() == 0 || s.startsWith("#")) continue;
            int comma = s.indexOf(',');
            String first = comma > 0 ? s.substring(0, comma) : s;
            if (!addRule(out, first)) {
                for (String tok : s.split("[\t;，； ]+")) {
                    if (addRule(out, tok)) break;
                }
            }
        }
        return out;
    }

    private static boolean addRule(List<String> out, String raw) {
        String rule = toRule(raw);
        if (rule == null) return false;
        if (!out.contains(rule)) out.add(rule);
        return true;
    }

    /** 把 JSON 里所有双引号字符串抠出来，够用了 */
    private static List<String> extractQuoted(String t) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        int n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (c != '"') {
                i++;
                continue;
            }
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < n) {
                char ch = t.charAt(i);
                if (ch == '\\' && i + 1 < n) {
                    sb.append(t.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (ch == '"') {
                    i++;
                    break;
                }
                sb.append(ch);
                i++;
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static void safeDone(Callback cb, boolean ok, String message) {
        if (cb == null) return;
        try {
            cb.done(ok, message);
        } catch (Throwable ignored) {
            // 回调里再抛异常就会把整个进程带走，这里必须兜住
        }
    }

    private static void step(Context c, String s) {
        try {
            Prefs.sp(c).edit().putString("upd_step", s).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 更新过程走到哪一步了，出问题时用来定位 */
    public static String lastStep(Context c) {
        try {
            return Prefs.sp(c).getString("upd_step", "");
        } catch (Throwable t) {
            return "";
        }
    }

    static String toRule(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() == 0 || s.startsWith("#")) return null;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') digits.append(ch);
        }
        String d = digits.toString();
        if (d.length() == 0) return null;
        if (d.startsWith("0086")) d = d.substring(4);
        else if (d.length() == 13 && d.startsWith("86")) d = d.substring(2);
        if (d.length() >= 7) return d;
        // 带星号的明确写法允许 2 位（95*），其余至少 3 位，避免把 CSV 里的计数列当成规则
        if (s.indexOf('*') >= 0 && d.length() >= 2) return d + "*";
        if (d.length() >= 3 && !d.startsWith("0")) return d + "*";
        return null;
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null) m = t.getClass().getSimpleName();
        if (m.length() > 80) m = m.substring(0, 80);
        return m;
    }
}
