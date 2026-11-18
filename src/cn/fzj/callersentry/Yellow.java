package cn.fzj.callersentry;

import android.content.Context;

/**
 * 黄页查询入口（薄壳）。
 * 实际存储已经搬到 YellowStore：SQLite 持久化 + 内存热缓存，
 * 这样既支持大数据量导入和热更新，又保证来电瞬间是内存级查询。
 */
public final class Yellow {

    private Yellow() {
    }

    public static void warmup(Context c) {
        YellowStore.ensure(c);
    }

    public static int size(Context c) {
        return YellowStore.size(c);
    }

    /** 号码 → 「顺丰速运·快递」，没收录返回 null */
    public static String label(Context c, String digits) {
        return YellowStore.label(c, digits);
    }
}
