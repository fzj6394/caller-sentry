package cn.fzj.callersentry;

import java.nio.file.Files;
import java.nio.file.Paths;

/** 纯 JVM 计时：号段库加载与查询到底花多少时间，给优化方向提供依据 */
public class Bench {

    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(args[0]));
        System.out.println("phone.dat 字节数 = " + data.length);

        // 1. 构造（索引解析）
        long t0 = System.nanoTime();
        PhoneLookup pl = null;
        for (int i = 0; i < 20; i++) {
            pl = new PhoneLookup(data);
        }
        long t1 = System.nanoTime();
        System.out.printf("构造 20 次平均 = %.2f ms（条数 %d）%n",
                (t1 - t0) / 20.0 / 1e6, pl.size());

        // 2. 单次查询（含二分）
        String[] nums = {"13800138000", "18957509123", "13000010000",
                "17000000000", "19200000000", "19900001234"};
        for (int round = 0; round < 3; round++) {
            long s = System.nanoTime();
            int hit = 0;
            for (int i = 0; i < 60000; i++) {
                if (pl.lookupMobile(nums[i % nums.length]) != null) hit++;
            }
            long e = System.nanoTime();
            System.out.printf("6 万次查询 = %.1f ms（命中 %d）→ 单次 %.3f µs%n",
                    (e - s) / 1e6, hit, (e - s) / 60000.0 / 1e3);
        }

        // 3. 不存在的号段（最坏路径：精确未命中 + 回退）
        long s2 = System.nanoTime();
        for (int i = 0; i < 60000; i++) {
            pl.lookupMobile("11010101010");
        }
        long e2 = System.nanoTime();
        System.out.printf("6 万次未命中 = %.1f ms → 单次 %.3f µs%n",
                (e2 - s2) / 1e6, (e2 - s2) / 60000.0 / 1e3);
    }
}
