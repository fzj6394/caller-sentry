package cn.fzj.probe;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 最小探针：不加载任何布局、资源、第三方库。
 * 它能起来，说明系统装 APK 没问题，问题在正式应用的代码/资源里。
 */
public class ProbeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setPadding(40, 120, 40, 40);
        tv.setText("探针启动成功\n\n"
                + "android " + Build.VERSION.RELEASE + " (api " + Build.VERSION.SDK_INT + ")\n"
                + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + Build.DISPLAY + "\n\n"
                + "把这屏截图发我即可。");
        setContentView(tv);
    }
}
