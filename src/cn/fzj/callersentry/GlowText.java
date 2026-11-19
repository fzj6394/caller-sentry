package cn.fzj.callersentry;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

/**
 * 会流光的文字。
 *
 * 两个效果叠在一起：
 * 1. 渐变沿文字横向缓慢扫过（Shader + Matrix 位移），颜色在冰蓝、浅紫、薄荷之间过渡；
 * 2. 文本超出控件宽度时自动跑马灯，短文本不滚动（没超出就没有动画）。
 *
 * 只在来电提示存活的那几秒里动，收起后动画随之停止，不留后台。
 */
public class GlowText extends TextView {

    private LinearGradient gradient;
    private final Matrix matrix = new Matrix();
    private ValueAnimator anim;
    private int[] colors;
    private boolean marquee;

    public GlowText(Context c) {
        super(c);
        setSingleLine(true);
        setHorizontallyScrolling(true);
        setEllipsize(TextUtils.TruncateAt.MARQUEE);
        setMarqueeRepeatLimit(-1);
    }

    /** 传一组颜色，沿文字横向循环扫过；至少两个 */
    public GlowText colors(int... cs) {
        this.colors = cs;
        return this;
    }

    /** 开跑马灯：文本真的超宽才滚，短文本静止 */
    public GlowText marquee(boolean on) {
        this.marquee = on;
        if (on) setSelected(true);
        return this;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        gradient = null;
        build(w);
    }

    private void build(int w) {
        if (w <= 0 || colors == null || colors.length < 2) return;
        // 渐变宽度取两倍控件宽，扫过去时不会看到硬边
        gradient = new LinearGradient(0, 0, w * 2f, 0, colors, null, Shader.TileMode.CLAMP);
        gradient.setLocalMatrix(matrix);
        getPaint().setShader(gradient);
    }

    public void startGlow() {
        if (colors == null) return;
        stopGlow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2600L);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                apply(((Float) a.getAnimatedValue()).floatValue());
            }
        });
        anim.start();
        apply(0f);
    }

    public void stopGlow() {
        if (anim != null) {
            try {
                anim.cancel();
            } catch (Throwable ignored) {
            }
            anim = null;
        }
    }

    private void apply(float f) {
        int w = getWidth();
        if (w <= 0) return;
        if (gradient == null) build(w);
        if (gradient == null) return;
        // 从完全移出左侧扫到完全移出右侧
        matrix.setTranslate(f * w * 3f - w * 2f, 0);
        gradient.setLocalMatrix(matrix);
        getPaint().setShader(gradient);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (marquee) setSelected(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopGlow();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (gradient != null) getPaint().setShader(gradient);
        super.onDraw(canvas);
    }
}
