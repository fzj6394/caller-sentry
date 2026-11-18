import cn.fzj.callersentry.NumUtil;
import cn.fzj.callersentry.PhoneLookup;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 纯 JVM 自测：不依赖手机，验证号段查表与号码描述逻辑。
 * 编译时需要 android.jar 在 classpath（PhoneLookup 有 Context 重载），运行时不需要。
 */
public class TestLookup {

    private static int pass = 0;
    private static int fail = 0;
    private static PhoneLookup PL;

    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(args.length > 0 ? args[0] : "assets/phone.dat"));
        PhoneLookup pl = new PhoneLookup(data);
        PL = pl;
        System.out.println("号段库记录数 = " + pl.size());
        check("size", pl.size() == 497191);

        expect(pl, "13800138000", "北京", "移动");
        expect(pl, "18957509123", "绍兴", "电信");
        expect(pl, "13000010000", "常州", "联通");
        expect(pl, "17000000000", "北京", "电信");
        expect(pl, "19200000000", "佛山", "广电");
        expect(pl, "13490000000", "北京", null);
        expect(pl, "11010101010", null, null);   // 不存在的号段

        // 近似（号段库里只有同段更小的记录）
        PhoneLookup.Result approx = pl.lookupMobile("16210350000");
        System.out.println("16210350000 -> " + (approx == null ? "null" : approx.text()));
        check("approx-not-null", approx != null);

        // 描述逻辑
        desc("4001234567", "企业服务热线（营销/骚扰高发）");
        desc("95588", "企业客服短号（95 开头）");
        desc("01088888888", "固话 · 北京");
        desc("+85261234567", "国际来电 · 中国香港");
        desc("+8613800138000", "北京 · 移动");

        // 号码清洗
        check("clean-86", "13800138000".equals(NumUtil.clean("+86 138-0013-8000")));
        check("clean-0086", "13800138000".equals(NumUtil.clean("008613800138000")));

        System.out.println("\n用例结果：pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }

    private static void expect(PhoneLookup pl, String num, String city, String isp) {
        String d = NumUtil.clean(num);
        PhoneLookup.Result r = pl.lookupMobile(d);
        String gotCity = r == null ? null : r.city;
        String gotIsp = r == null ? null : r.isp;
        boolean ok = (city == null ? gotCity == null : city.equals(gotCity))
                && (isp == null || (gotIsp != null && gotIsp.contains(isp)));
        System.out.println((ok ? "OK   " : "FAIL ") + num + " -> "
                + (r == null ? "未收录" : r.text()));
        if (ok) pass++;
        else fail++;
    }

    private static void desc(String num, String mustContain) {
        String d = NumUtil.clean(num);
        PhoneLookup.Result r = null;
        if (d.length() >= 7 && !NumUtil.looksInternational(num)) {
            r = PL.lookupMobile(d);
        }
        String text = NumUtil.describe(num, d, r);
        boolean ok = text.contains(mustContain);
        System.out.println((ok ? "OK   " : "FAIL ") + num + " => " + text);
        if (ok) pass++;
        else fail++;
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + name);
        if (ok) pass++;
        else fail++;
    }
}
