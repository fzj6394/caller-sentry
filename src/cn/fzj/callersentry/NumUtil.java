package cn.fzj.callersentry;

import java.util.HashMap;
import java.util.Map;

/** 号码清洗、国际区号、特殊号段提示 */
public final class NumUtil {

    private static final Map<String, String> INTL = new HashMap<String, String>();

    static {
        INTL.put("852", "中国香港");
        INTL.put("853", "中国澳门");
        INTL.put("886", "中国台湾");
        INTL.put("1", "美国/加拿大");
        INTL.put("7", "俄罗斯");
        INTL.put("20", "埃及");
        INTL.put("27", "南非");
        INTL.put("31", "荷兰");
        INTL.put("33", "法国");
        INTL.put("34", "西班牙");
        INTL.put("39", "意大利");
        INTL.put("41", "瑞士");
        INTL.put("44", "英国");
        INTL.put("45", "丹麦");
        INTL.put("46", "瑞典");
        INTL.put("47", "挪威");
        INTL.put("48", "波兰");
        INTL.put("49", "德国");
        INTL.put("52", "墨西哥");
        INTL.put("55", "巴西");
        INTL.put("60", "马来西亚");
        INTL.put("61", "澳大利亚");
        INTL.put("62", "印度尼西亚");
        INTL.put("63", "菲律宾");
        INTL.put("64", "新西兰");
        INTL.put("65", "新加坡");
        INTL.put("66", "泰国");
        INTL.put("81", "日本");
        INTL.put("82", "韩国");
        INTL.put("84", "越南");
        INTL.put("90", "土耳其");
        INTL.put("91", "印度");
        INTL.put("92", "巴基斯坦");
        INTL.put("95", "缅甸");
        INTL.put("98", "伊朗");
        INTL.put("855", "柬埔寨");
        INTL.put("856", "老挝");
        INTL.put("966", "沙特");
        INTL.put("971", "阿联酋");
        INTL.put("972", "以色列");
    }

    private static final Map<String, String> AREA = new HashMap<String, String>();

    static {
        AREA.put("010", "北京");
        AREA.put("020", "广州");
        AREA.put("021", "上海");
        AREA.put("022", "天津");
        AREA.put("023", "重庆");
        AREA.put("024", "沈阳");
        AREA.put("025", "南京");
        AREA.put("027", "武汉");
        AREA.put("028", "成都");
        AREA.put("029", "西安");
        AREA.put("0311", "石家庄");
        AREA.put("0351", "太原");
        AREA.put("0411", "大连");
        AREA.put("0431", "长春");
        AREA.put("0451", "哈尔滨");
        AREA.put("0512", "苏州");
        AREA.put("0531", "济南");
        AREA.put("0532", "青岛");
        AREA.put("0551", "合肥");
        AREA.put("0571", "杭州");
        AREA.put("0574", "宁波");
        AREA.put("0591", "福州");
        AREA.put("0592", "厦门");
        AREA.put("0731", "长沙");
        AREA.put("0755", "深圳");
        AREA.put("0757", "佛山");
        AREA.put("0769", "东莞");
        AREA.put("0771", "南宁");
        AREA.put("0791", "南昌");
        AREA.put("0851", "贵阳");
        AREA.put("0871", "昆明");
        AREA.put("0898", "海南");
        AREA.put("0371", "郑州");
    }

    /** 区号：01x/02x 是 3 位，其余 4 位 */
    private static String areaCode(String digits) {
        if (digits.startsWith("01") || digits.startsWith("02")) {
            return digits.substring(0, Math.min(3, digits.length()));
        }
        return digits.substring(0, Math.min(4, digits.length()));
    }

    private NumUtil() {
    }

    /** 只保留数字，并把 0086 / 86 前缀去掉，方便比较 */
    public static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        String d = sb.toString();
        if (d.startsWith("0086")) return d.substring(4);
        if (d.length() == 15 && d.startsWith("0086")) return d.substring(4);
        if (d.length() == 13 && d.startsWith("86")) return d.substring(2);
        return d;
    }

    /**
     * 给眼睛看的号码：3-4-4 / 4-4-4 分组。
     * 屏幕上连续 11 位数字很难一眼读出来，分组后好认得多。
     */
    public static String pretty(String digits) {
        if (digits == null || digits.length() == 0) return "";
        int n = digits.length();
        if (n == 11 && digits.charAt(0) == '1') {
            return digits.substring(0, 3) + " " + digits.substring(3, 7) + " " + digits.substring(7);
        }
        if (n == 10 && (digits.startsWith("400") || digits.startsWith("800"))
                && isAllDigits(digits)) {
            return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
        }
        if (n == 12 && digits.charAt(0) == '0') {
            int ac = (digits.startsWith("01") || digits.startsWith("02")) ? 3 : 4;
            String rest = digits.substring(ac);
            StringBuilder sb = new StringBuilder(digits.substring(0, ac));
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && i % 4 == 0) sb.append(' ');
                sb.append(rest.charAt(i));
            }
            return sb.toString();
        }
        if (n <= 6) return digits;
        StringBuilder sb = new StringBuilder(n + 4);
        for (int i = 0; i < n; i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** 带 + 或 00 开头，且不是中国大陆号 */
    public static boolean looksInternational(String raw) {
        if (raw == null) return false;
        String t = raw.trim();
        if (t.startsWith("+")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c >= '0' && c <= '9') sb.append(c);
            }
            String d = sb.toString();
            // +86 138... / +86138... 都是大陆号
            return !(d.startsWith("86") && d.length() >= 13);
        }
        if (t.startsWith("00")) return !t.startsWith("0086");
        return false;
    }

    private static String intlRegion(String digitsClean) {
        for (int len = 3; len >= 1; len--) {
            if (digitsClean.length() >= len) {
                String code = digitsClean.substring(0, len);
                String name = INTL.get(code);
                if (name != null) return name;
            }
        }
        return null;
    }

    /** 给用户看的一句话：归属地 + 号码性质 */
    public static String describe(String raw, String digits, PhoneLookup.Result r) {
        if (digits == null || digits.length() == 0) return "号码隐藏或无号码";

        if (looksInternational(raw)) {
            String d = digits;
            if (d.startsWith("00")) d = d.substring(2);
            String region = intlRegion(d);
            return region == null ? "国际来电" : "国际来电 · " + region;
        }
        if (digits.length() == 11 && digits.charAt(0) == '1') {
            if (r != null) return r.text();
            return "大陆手机号 · 号段库里没有（可能是新号段）";
        }
        if (digits.startsWith("400") || digits.startsWith("800")) {
            return "企业服务热线（营销/骚扰高发）";
        }
        if (digits.startsWith("95") && digits.length() <= 6) {
            return "企业客服短号（95 开头）";
        }
        if (digits.startsWith("0") && digits.length() >= 9) {
            String code = areaCode(digits);
            String city = AREA.get(code);
            if (city != null) return "固话 · " + city + " · " + code;
            return "固话 · 区号 " + code;
        }
        if (digits.length() < 7) {
            return "短号/特服号";
        }
        return "无法识别";
    }
}
