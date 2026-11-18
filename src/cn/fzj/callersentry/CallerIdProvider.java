package cn.fzj.callersentry;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;

/**
 * 给系统拨号器用的"虚拟号码目录"（Directory Provider）。
 *
 * 通话记录里的名字是系统从通讯录取的，第三方 App 不能直接写 CallLog，
 * 官方唯一的通道就是这个：拨号器查号码时顺带问我们一句"你认识这个号吗"，
 * 我们把黄页 / 号码库 / 归属地拼成的名字返回去，通话界面和通话记录就能显示。
 *
 * 注意：AOSP 拨号器和 Google 拨号器会用；三星原生的"电话"查不查，
 * 取决于三星自己的实现，不保证。
 */
public class CallerIdProvider extends ContentProvider {

    public static final String AUTHORITY = "cn.fzj.callersentry.directory";

    private static final int DIRECTORIES = 1;
    private static final int PHONE_LOOKUP = 2;

    private static final String[] DIRECTORY_PROJECTION = {
            ContactsContract.Directory._ID,
            ContactsContract.Directory.ACCOUNT_NAME,
            ContactsContract.Directory.ACCOUNT_TYPE,
            ContactsContract.Directory.DISPLAY_NAME,
            ContactsContract.Directory.TYPE_RESOURCE_ID,
            ContactsContract.Directory.EXPORT_SUPPORT,
            ContactsContract.Directory.SHORTCUT_SUPPORT,
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String path = uri.getPath();
        if (path == null) return null;

        if (path.startsWith("/phone_lookup")) {
            return lookup(uri, projection);
        }
        if (path.startsWith("/directories") || path.length() == 0 || "/".equals(path)) {
            return directories(projection);
        }
        return null;
    }

    /** 目录自身的登记信息，系统装包后会自动来问一次 */
    private Cursor directories(String[] projection) {
        String[] cols = projection != null ? projection : DIRECTORY_PROJECTION;
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        String label = "来电哨兵";
        for (int i = 0; i < cols.length; i++) {
            String col = cols[i];
            if (ContactsContract.Directory.ACCOUNT_NAME.equals(col)) row[i] = label;
            else if (ContactsContract.Directory.ACCOUNT_TYPE.equals(col)) row[i] = AUTHORITY;
            else if (ContactsContract.Directory.DISPLAY_NAME.equals(col)) row[i] = label;
            else if (ContactsContract.Directory.TYPE_RESOURCE_ID.equals(col)) row[i] = 0;
            else if (ContactsContract.Directory.EXPORT_SUPPORT.equals(col))
                row[i] = ContactsContract.Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY;
            else if (ContactsContract.Directory.SHORTCUT_SUPPORT.equals(col))
                row[i] = ContactsContract.Directory.SHORTCUT_SUPPORT_NONE;
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    /** 拨号器拿具体号码来问 */
    private Cursor lookup(Uri uri, String[] projection) {
        String[] cols = projection != null ? projection : new String[]{
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER,
        };
        MatrixCursor c = new MatrixCursor(cols);

        String number;
        try {
            number = uri.getLastPathSegment();
        } catch (Throwable t) {
            return c;
        }
        if (number == null) return c;

        String label = labelFor(number);
        if (label == null || label.length() == 0) return c;

        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            String col = cols[i];
            if (ContactsContract.PhoneLookup._ID.equals(col)) row[i] = -1;
            else if (ContactsContract.PhoneLookup.DISPLAY_NAME.equals(col)) row[i] = label;
            else if (ContactsContract.PhoneLookup.NUMBER.equals(col)) row[i] = number;
            else if (ContactsContract.PhoneLookup.LABEL.equals(col)) row[i] = "";
            else if (ContactsContract.PhoneLookup.TYPE.equals(col)) row[i] = 0;
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    /** 黄页 → 自己的号码库 → 在线缓存 → 归属地，谁先命中用谁 */
    private String labelFor(String rawNumber) {
        try {
            String digits = NumUtil.clean(rawNumber);
            if (digits.length() == 0) return null;

            String exact = Blacklist.matchExact(getContext(), digits);
            if (exact != null) return "号码库 · 已标记";

            String yellow = Yellow.label(getContext(), digits);
            if (yellow != null) return yellow;

            String rule = Blacklist.match(getContext(), digits);
            if (rule != null) return "号码库 · 已标记";

            String cached = MarkCache.get(getContext(), digits);
            if (cached != null) return cached;

            if (digits.length() >= 7 && !NumUtil.looksInternational(rawNumber)) {
                cn.fzj.callersentry.PhoneLookup.Result r =
                        cn.fzj.callersentry.PhoneLookup.get(getContext()).lookupMobile(digits);
                if (r != null) return r.text();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
