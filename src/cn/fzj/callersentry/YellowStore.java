package cn.fzj.callersentry;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄页存储：SQLite 负责持久化、增量导入和大数据量，内存 Map 负责来电瞬间的查询。
 *
 * 为什么不全用 SQLite 查：单次 SQLite 查询在手机上约几十到几百微秒，
 * 而 HashMap 是 0.05 微秒级。来电只有几毫秒预算，所以热路径必须走内存。
 * 数据来源优先级：手机内数据库（可热更新）→ 内置 assets/yellow.txt（首次导入）。
 *
 * 表：yellow(num TEXT PRIMARY KEY, name TEXT, cat TEXT)
 * num 是主键即索引，支持精确查找；前缀查找走内存遍历（几万条也是微秒级）。
 */
public final class YellowStore {

    private static final String DB_NAME = "yellow.db";
    private static final String ASSET = "yellow.txt";
    private static final int VERSION = 1;
    /** 内置黄页的版本号：每次扩充 assets/yellow.txt 就 +1，老用户升级时会自动补导 */
    private static final int SEED_VERSION = 2;

    private static Helper helper;
    private static volatile Map<String, String> mem;
    private static volatile boolean loaded;
    private static volatile int count;

    private YellowStore() {
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context c) {
            super(c, DB_NAME, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS yellow ("
                    + "num TEXT PRIMARY KEY, name TEXT NOT NULL, cat TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // 结构没变过，保留数据
        }
    }

    private static synchronized Helper helper(Context c) {
        if (helper == null) {
            helper = new Helper(c.getApplicationContext());
        }
        return helper;
    }

    /** 首次调用会建表、导内置数据、把全表读进内存；之后零 IO */
    public static void ensure(Context c) {
        if (loaded) return;
        synchronized (YellowStore.class) {
            if (loaded) return;
            try {
                SQLiteDatabase db = helper(c).getWritableDatabase();
                int seed = Prefs.sp(c).getInt("yellow_seed", 0);
                if (count(db) == 0 || seed < SEED_VERSION) {
                    // 表空 → 首次导入；表非空但种子版本旧 → 补导新增条目（INSERT OR REPLACE，不会删已有）
                    importAsset(c, db);
                    Prefs.sp(c).edit().putInt("yellow_seed", SEED_VERSION).apply();
                }
                Map<String, String> m = loadAll(db);
                mem = m;
                count = m.size();
                loaded = true;
            } catch (Throwable t) {
                if (mem == null) mem = new HashMap<String, String>();
                loaded = true;
            }
        }
    }

    public static int size(Context c) {
        ensure(c);
        return count;
    }

    /** 「顺丰速运·快递」；精确 → 前 6 位 → 前 5 位（带分机也能认） */
    public static String label(Context c, String digits) {
        if (digits == null || digits.length() < 3) return null;
        ensure(c);
        Map<String, String> m = mem;
        if (m == null || m.isEmpty()) return null;

        String v = m.get(digits);
        if (v != null) return v;
        for (int len = 6; len >= 5; len--) {
            if (digits.length() > len) {
                v = m.get(digits.substring(0, len));
                if (v != null) return v;
            }
        }
        return null;
    }

    // ---------- 导入 ----------

    /** 把文本解析成「号码|名称|类别」行；也认逗号分隔 */
    public static List<String[]> parse(String text) {
        List<String[]> out = new ArrayList<String[]>();
        if (text == null) return out;
        for (String line : text.split("\r?\n")) {
            String s = line == null ? "" : line.trim();
            if (s.length() == 0 || s.startsWith("#")) continue;
            String[] p;
            if (s.indexOf('|') > 0) {
                p = s.split("\\|");
            } else if (s.indexOf(',') > 0) {
                p = s.split(",");
            } else {
                continue;
            }
            String num = p[0].trim().replaceAll("[^0-9]", "");
            if (num.length() < 3) continue;
            String name = p.length > 1 ? p[1].trim() : "";
            if (name.length() == 0) continue;
            String cat = p.length > 2 ? p[2].trim() : "";
            out.add(new String[]{num, name, cat});
        }
        return out;
    }

    /** 合并导入；返回新增/更新的条数 */
    public static int importText(Context c, String text) {
        List<String[]> rows = parse(text);
        if (rows.isEmpty()) return 0;
        ensure(c);
        SQLiteDatabase db = null;
        try {
            db = helper(c).getWritableDatabase();
            db.beginTransaction();
            for (String[] r : rows) {
                db.execSQL("INSERT OR REPLACE INTO yellow(num,name,cat) VALUES(?,?,?)",
                        new Object[]{r[0], r[1], r[2]});
            }
            db.setTransactionSuccessful();
        } catch (Throwable t) {
            return 0;
        } finally {
            if (db != null) {
                try {
                    db.endTransaction();
                } catch (Throwable ignored) {
                }
            }
        }
        // 重建内存视图
        loaded = false;
        ensure(c);
        return rows.size();
    }

    private static int importAsset(Context c, SQLiteDatabase db) {
        try {
            InputStream in = c.getAssets().open(ASSET);
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            List<String[]> rows = parse(new String(out.toByteArray(), "UTF-8"));
            if (rows.isEmpty()) return 0;
            db.beginTransaction();
            try {
                for (String[] r : rows) {
                    db.execSQL("INSERT OR REPLACE INTO yellow(num,name,cat) VALUES(?,?,?)",
                            new Object[]{r[0], r[1], r[2]});
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return rows.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int count(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM yellow", null);
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable t) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    private static Map<String, String> loadAll(SQLiteDatabase db) {
        Map<String, String> m = new HashMap<String, String>();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT num,name,cat FROM yellow", null);
            while (c.moveToNext()) {
                String num = c.getString(0);
                String name = c.getString(1);
                String cat = c.getString(2);
                if (num == null || name == null) continue;
                m.put(num, (cat != null && cat.length() > 0) ? name + "·" + cat : name);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return Collections.unmodifiableMap(m);
    }
}
