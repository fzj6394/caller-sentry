package cn.fzj.callersentry;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** 纯 JVM 验证号码库解析逻辑（就是"更新号码库"按钮背后那段） */
public class TestParse {

    public static void main(String[] args) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        List<String> rules = Update.parseRules(json);
        System.out.println("BanHarassment JSON -> " + rules.size() + " 条规则");
        System.out.println(rules);
        if (rules.size() != 42) {
            System.out.println("FAIL：期望 42 条");
            System.exit(1);
        }

        String csv = "phone_number,reports,distinct_reporters\n"
                + "+8613800138000,12,5\n"
                + "0755-12345678,9,3\n"
                + "# 注释行\n"
                + "95588,4,2\n";
        List<String> csvRules = Update.parseRules(csv);
        System.out.println("CSV -> " + csvRules);
        if (!csvRules.contains("13800138000")) {
            System.out.println("FAIL：CSV 首列没解析对");
            System.exit(1);
        }

        List<String> plain = Update.parseRules("95*\n400*\n170*\n");
        System.out.println("纯文本 -> " + plain);

        // 黄页解析（YellowStore.parse 是纯逻辑，不碰 Context）
        java.util.List<String[]> rows = YellowStore.parse(
                "95338|顺丰速运|快递\n# 注释\n12345,政务服务,政务\n坏行没有分隔符\n");
        System.out.println("黄页解析 -> " + rows.size() + " 行");
        for (String[] r : rows) {
            System.out.println("  " + r[0] + " / " + r[1] + " / " + (r.length > 2 ? r[2] : ""));
        }
        if (rows.size() != 2) {
            System.out.println("FAIL：期望 2 行");
            System.exit(1);
        }

        System.out.println("\n全部通过");
    }
}
