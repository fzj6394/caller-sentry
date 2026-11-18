package cn.fzj.callersentry;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

/**
 * 在线兜底查询：本地库没命中时才用。
 * 地址模板里用 {num} 占位，例如 https://your-server/lookup?phone={num}
 * 返回 JSON（会自动找 tag/label/name/mark 这类字段）或一段纯文本都认。
 * 拿不到就返回 null，来电照常放行（fail open，绝不因为网络问题拦掉你的电话）。
 */
public final class OnlineLookup {

    private OnlineLookup() {
    }

    public static String lookup(String digits, String template, int readTimeoutMs) {
        if (digits == null || digits.length() < 5) return null;
        if (template == null || template.trim().length() == 0) return null;
        String url = template.trim();
        if (url.contains("{num}")) {
            url = url.replace("{num}", digits);
        } else if (!url.endsWith(digits)) {
            url = url + digits;
        }

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(Math.max(1000, readTimeoutMs));
            c.setRequestProperty("User-Agent", "CallerSentry/1.2 (Android)");
            c.setRequestProperty("Accept", "application/json,text/plain,*/*");
            if (c.getResponseCode() != 200) return null;
            String body = readBody(c.getInputStream(), 64 * 1024);
            return parseLabel(body);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static final String[] KEYS = {
            "tag", "label", "mark", "name", "title", "category", "type",
            "platform_b_name", "platform_c_name", "platform_d_name", "desc"
    };

    static String parseLabel(String body) {
        if (body == null) return null;
        String s = body.trim();
        if (s.length() == 0) return null;

        if (s.startsWith("{") || s.startsWith("[")) {
            try {
                String found = findInJson(new JSONObject(s.startsWith("[") ? "{\"list\":" + s + "}" : s), 0);
                return clean(found);
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (s.indexOf('<') >= 0) return null;  // HTML 不解析，避免脆弱的网页抓取
        return clean(s);
    }

    private static String findInJson(JSONObject o, int depth) {
        if (o == null || depth > 4) return null;
        for (String k : KEYS) {
            String v = o.optString(k, null);
            if (v != null && v.length() > 0 && v.length() <= 30) return v;
        }
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v instanceof JSONObject) {
                String r = findInJson((JSONObject) v, depth + 1);
                if (r != null) return r;
            } else if (v instanceof org.json.JSONArray) {
                org.json.JSONArray arr = (org.json.JSONArray) v;
                for (int i = 0; i < Math.min(arr.length(), 5); i++) {
                    Object e = arr.opt(i);
                    if (e instanceof JSONObject) {
                        String r = findInJson((JSONObject) e, depth + 1);
                        if (r != null) return r;
                    }
                }
            }
        }
        return null;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replace('\n', ' ');
        if (t.length() == 0) return null;
        if (t.length() > 30) t = t.substring(0, 30);
        String lower = t.toLowerCase();
        if (lower.equals("null") || lower.equals("none") || lower.equals("false")
                || lower.equals("0") || lower.equals("未标记") || lower.equals("无标记")) {
            return null;
        }
        return t;
    }

    private static String readBody(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > limit) break;
        }
        try {
            in.close();
        } catch (Throwable ignored) {
        }
        return new String(out.toByteArray(), "UTF-8");
    }
}
