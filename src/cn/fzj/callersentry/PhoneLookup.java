package cn.fzj.callersentry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 离线号段库：优先读手机里的更新版 files/phone.dat，没有就用 APK 内置的 assets/phone.dat。
 * 文件结构（xluohome/phonedata）：前 4 字节版本号，第 5-8 字节为索引区起始偏移（小端 int）；
 * 索引区每条 9 字节：前7位号段(int,小端) + 记录偏移(int,小端) + 卡类型(byte)；
 * 记录区为 UTF-8 字符串「省|市|邮编|区号」，以 \0 结尾。索引按号段升序。
 */
public final class PhoneLookup {

    public static final String FILE_NAME = "phone.dat";

    public static final class Result {
        public final String province;
        public final String city;
        public final String isp;
        public final boolean approximate;

        Result(String province, String city, String isp, boolean approximate) {
            this.province = province;
            this.city = city;
            this.isp = isp;
            this.approximate = approximate;
        }

        /** 「北京 · 移动」这种给用户看的短文本 */
        public String text() {
            StringBuilder sb = new StringBuilder();
            if (city != null && city.length() > 0) {
                sb.append(city);
            } else if (province != null) {
                sb.append(province);
            }
            if (isp != null && isp.length() > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(isp);
            }
            if (approximate) sb.append("（约）");
            return sb.toString();
        }
    }

    private static volatile PhoneLookup instance;
    private static volatile boolean warming;

    private byte[] data;
    private int indexStart;
    private int count;
    private boolean fromCache;

    /**
     * 来电路径专用：**不等待**。已加载就返回，没加载就返回 null（由调用方 fail-open），
     * 同时顺手触发后台加载。这样冷启动时来电不会被 4.3MB 的解压拖住。
     */
    public static PhoneLookup peek() {
        return instance;
    }

    /** 可以等的地方（设置页、查号）用这个：没有就同步加载 */
    public static PhoneLookup get(android.content.Context ctx) {
        PhoneLookup p = instance;
        if (p != null) return p;
        synchronized (PhoneLookup.class) {
            if (instance == null) {
                instance = new PhoneLookup(ctx);
            }
            return instance;
        }
    }

    /** 后台预热，重复调用无害 */
    public static void warmup(final android.content.Context ctx) {
        if (instance != null || warming) return;
        warming = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (PhoneLookup.class) {
                        if (instance == null) {
                            instance = new PhoneLookup(ctx.getApplicationContext());
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    warming = false;
                }
            }
        }, "phone-warmup").start();
    }

    public static void reload() {
        synchronized (PhoneLookup.class) {
            instance = null;
        }
    }

    /** 直接从字节加载，便于在电脑上跑单元测试 */
    public PhoneLookup(byte[] raw) {
        init(raw, false);
    }

    private PhoneLookup(android.content.Context ctx) {
        byte[] raw = null;
        boolean cached = false;
        try {
            File f = new File(ctx.getFilesDir(), FILE_NAME);
            if (f.isFile() && f.length() > 500000) {
                raw = readFile(f);
                if (isPlausible(raw)) {
                    cached = true;
                } else {
                    raw = null;
                }
            }
        } catch (Throwable ignored) {
        }
        if (raw == null) {
            raw = loadAsset(ctx);
        }
        init(raw, cached);
    }

    private void init(byte[] raw, boolean cached) {
        this.data = raw == null ? new byte[0] : raw;
        this.indexStart = data.length > 8 ? readInt(4) : 0;
        this.count = indexStart > 0 && indexStart < data.length ? (data.length - indexStart) / 9 : 0;
        this.fromCache = cached && count > 0;
    }

    /** 大概是合法的库：够大、索引偏移合理、条数在合理范围 */
    public static boolean isPlausible(byte[] raw) {
        if (raw == null || raw.length < 500000) return false;
        int idx = (raw[4] & 0xFF) | ((raw[5] & 0xFF) << 8) | ((raw[6] & 0xFF) << 16) | ((raw[7] & 0xFF) << 24);
        if (idx < 8 || idx >= raw.length) return false;
        int n = (raw.length - idx) / 9;
        return n > 100000 && n < 2000000;
    }

    private static byte[] loadAsset(android.content.Context ctx) {
        try {
            InputStream in = ctx.getAssets().open(FILE_NAME);
            return readAll(in, 16 * 1024 * 1024);
        } catch (Exception ignored) {
            // 数据缺失时退化为「查不到」，不影响 App 其它功能
            return new byte[0];
        }
    }

    private static byte[] readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            return readAll(in, 16 * 1024 * 1024);
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static byte[] readAll(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4 * 1024 * 1024);
        byte[] tmp = new byte[64 * 1024];
        int n;
        while ((n = in.read(tmp)) > 0) {
            out.write(tmp, 0, n);
            if (out.size() > limit) break;
        }
        try {
            in.close();
        } catch (Throwable ignored) {
        }
        return out.toByteArray();
    }

    public boolean isReady() {
        return count > 0;
    }

    public int size() {
        return count;
    }

    /** 当前库的字节数：和服务器上的 Content-Length 比，判断要不要重新下载 */
    public int dataLength() {
        return data.length;
    }

    /** true = 用的是手机里更新过的库；false = 用的 APK 内置库 */
    public boolean fromCache() {
        return fromCache;
    }

    /**
     * 传入已经清洗过的数字串（手机号 11 位）。
     * 精确命中直接用；未命中时若同号段（前3位）有更小的记录，就用它并标记为「约」。
     */
    public Result lookupMobile(String digits) {
        if (count == 0 || digits == null || digits.length() < 7) return null;
        int p7;
        try {
            p7 = Integer.parseInt(digits.substring(0, 7));
        } catch (Exception e) {
            return null;
        }
        int exact = findExact(p7);
        if (exact >= 0) {
            return readResult(exact, false);
        }
        int floor = findFloor(p7);
        if (floor >= 0) {
            int key = readInt(indexStart + floor * 9);
            if (key / 10000 == p7 / 10000) {
                return readResult(floor, true);
            }
        }
        return null;
    }

    private int findExact(int key) {
        int lo = 0, hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int k = readInt(indexStart + mid * 9);
            if (k == key) return mid;
            if (k < key) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }

    /** 最后一个 key 严格小于目标的下标 */
    private int findFloor(int key) {
        int lo = 0, hi = count - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int k = readInt(indexStart + mid * 9);
            if (k < key) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private Result readResult(int i, boolean approximate) {
        int base = indexStart + i * 9;
        int offset = readInt(base + 4);
        int type = data[base + 8] & 0xFF;
        String rec = readString(offset);
        String[] parts = rec.split("\\|");
        String province = parts.length > 0 ? parts[0] : "";
        String city = parts.length > 1 ? parts[1] : "";
        return new Result(province, city, ispName(type), approximate);
    }

    private static String ispName(int type) {
        switch (type) {
            case 1: return "移动";
            case 2: return "联通";
            case 3: return "电信";
            case 4: return "电信·虚拟号段";
            case 5: return "联通·虚拟号段";
            case 6: return "其他运营商";
            case 7: return "广电";
            default: return "";
        }
    }

    private String readString(int offset) {
        if (offset < 0 || offset >= data.length) return "";
        int end = offset;
        while (end < data.length && data[end] != 0) end++;
        try {
            return new String(data, offset, end - offset, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private int readInt(int off) {
        if (off < 0 || off + 4 > data.length) return -1;
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }
}
