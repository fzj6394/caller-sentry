package cn.fzj.callersentry;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 把号码写进通讯录，好让系统通话记录显示出名字。
 *
 * 为什么只能这么做：通话记录里的"名字"是系统拿号码去通讯录查的。
 * 官方的 Directory Provider 通道（CallerIdProvider）三星电话 App 不查，
 * 所以只有"真的成为一个联系人"这一条路能生效。
 *
 * 写入约定：
 * - 落在本地账户（account type/name 都为 null），不会同步到云端；
 * - 额外写一条备注 MARK 作为归属标记，用于统计和一键清除；
 * - 已存在同名号码的联系人直接跳过，不重复写。
 */
public final class Contacts {

    static final String MARK = "来电哨兵写入";

    private Contacts() {
    }

    /** 通-looking 名字：标签或归属地 + 号码后四位，便于在通讯录里区分 */
    public static String makeName(String digits, String area, String tag) {
        String head = tag != null && tag.length() > 0
                ? tag
                : (area != null && area.length() > 0 ? area : "未知");
        String tail = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return head + " " + tail;
    }

    public static boolean exists(Context c, String digits) {
        Cursor cu = null;
        try {
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(digits));
            cu = c.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup._ID}, null, null, null);
            return cu != null && cu.moveToFirst();
        } catch (Throwable t) {
            Trace.add(c, "contacts", "查询失败 " + t.getClass().getSimpleName());
            return true;
        } finally {
            if (cu != null) {
                try {
                    cu.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static boolean write(Context c, String digits, String name) {
        try {
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, digits)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, MARK)
                    .build());
            c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            Trace.add(c, "contacts", "已写入 " + digits + " → " + name);
            return true;
        } catch (Throwable t) {
            Trace.add(c, "contacts", "写入失败 " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    /** 本应用写入的联系人条数 */
    public static int count(Context c) {
        Cursor cu = null;
        try {
            cu = c.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.RAW_CONTACT_ID},
                    ContactsContract.Data.MIMETYPE + "=? AND "
                            + ContactsContract.CommonDataKinds.Note.NOTE + "=?",
                    new String[]{ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, MARK},
                    null);
            return cu == null ? 0 : cu.getCount();
        } catch (Throwable t) {
            return 0;
        } finally {
            if (cu != null) {
                try {
                    cu.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 删掉本应用写入的全部联系人 */
    public static int clear(Context c) {
        Set<Long> ids = new HashSet<Long>();
        Cursor cu = null;
        try {
            cu = c.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.RAW_CONTACT_ID},
                    ContactsContract.Data.MIMETYPE + "=? AND "
                            + ContactsContract.CommonDataKinds.Note.NOTE + "=?",
                    new String[]{ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, MARK},
                    null);
            if (cu != null) {
                while (cu.moveToNext()) ids.add(cu.getLong(0));
            }
        } catch (Throwable t) {
            Trace.add(c, "contacts", "读取失败 " + t.getClass().getSimpleName());
        } finally {
            if (cu != null) {
                try {
                    cu.close();
                } catch (Throwable ignored) {
                }
            }
        }

        int n = 0;
        for (Long id : ids) {
            try {
                Uri raw = Uri.withAppendedPath(ContactsContract.RawContacts.CONTENT_URI,
                        String.valueOf(id));
                n += c.getContentResolver().delete(raw, null, null);
            } catch (Throwable ignored) {
            }
        }
        Trace.add(c, "contacts", "清除 " + n + " 条");
        return n;
    }
}
