package cn.fzj.callersentry;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启：只负责把兼容模式的前台服务拉起来 */
public class Boot extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        try {
            boolean on = Prefs.sp(c).getBoolean("compat_on", false);
            Trace.add(c, "boot", "收到 " + (intent == null ? "?" : intent.getAction())
                    + " compat_on=" + on);
            if (on) Watcher.start(c);
        } catch (Throwable t) {
            Trace.add(c, "boot", "启动失败 " + t.getClass().getSimpleName());
        }
    }
}
