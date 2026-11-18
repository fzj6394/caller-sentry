package cn.fzj.callersentry;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 二级页面的公共骨架：玻璃背景 + 返回栏 + 主题跟随 */
public abstract class BasePageActivity extends Activity {

    protected UiTheme t;
    protected Ui.Page page;

    protected abstract String pageTitle();

    protected abstract String pageSubtitle();

    protected abstract void build(LinearLayout content);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            t = UiTheme.current(this);
            getWindow().setBackgroundDrawable(new Ui.Backdrop(t));
            page = Ui.page(this, t);
            setContentView(page.scroll);
            Ui.header(this, t, page.content, pageTitle(), pageSubtitle(), "返回",
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
            build(page.content);
        } catch (Throwable e) {
            App.save(this, e, pageTitle() + " 页面构建失败");
            fallback(e);
        }
    }

    private void fallback(Throwable e) {
        try {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(36, 90, 36, 36);
            TextView tv = new TextView(this);
            tv.setText("这个页面打不开了：" + e);
            tv.setTextSize(13);
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            box.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            setContentView(box);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (t != null && Prefs.theme(this) != t.id) {
            recreate();
            return;
        }
        onPageResume();
    }

    protected void onPageResume() {
    }

    protected void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
