package cn.fzj.callersentry;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 自己画字的流光文本。
 *
 * 为什么不用 TextView：TextView 的 onDraw 会接管 mTextPaint 的颜色，
 * shader 挂上去也经常被覆盖，实测在锁屏 Activity 里根本不显色（一直白字）。
 * 这里直接用一个 Paint 在 Canvas 上 drawText，渐变一定生效。
 *
 * 色带用 REPEAT 循环，且首尾同色（都是白），接缝看不出来；
 * 色带宽度只有控件宽的 0.62，所以文字上始终压着两道颜色，不会出现整块白。
 */
public class GlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private String text = "";
    private int[] colors;
    private LinearGradient shader;
    private float span;
    private float phase;
    private float baseSize;
    private ValueAnimator anim;

    public GlowView(Context c) {
        super(c);
        paint.setTextAlign(Paint.Align.CENTER);
        // 兜底色：万不得已 shader 没挂上，也至少是白字，不会变成黑字看不见
        paint.setColor(0xFFFFFFFF);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public GlowView text(String t) {
        this.text = t == null ? "" : t;
        requestLayout();
        invalidate();
        return this;
    }

    public GlowView sizeSp(float sp) {
        this.baseSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
        paint.setTextSize(baseSize);
        requestLayout();
        invalidate();
        return this;
    }

    public GlowView letterSpacing(float em) {
        try {
            paint.setLetterSpacing(em);
        } catch (Throwable ignored) {
        }
        requestLayout();
        invalidate();
        return this;
    }

    public GlowView bold(boolean on) {
        paint.setTypeface(Typeface.create(on ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        requestLayout();
        invalidate();
        return this;
    }

    public GlowView shadow(float radiusDp, int color) {
        float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusDp,
                getResources().getDisplayMetrics());
        paint.setShadowLayer(Math.max(1f, r), 0f, r * 0.15f, color);
        invalidate();
        return this;
    }

    /** 色带，至少两个，首尾建议同色（循环接缝才不显） */
    public GlowView colors(int... cs) {
        this.colors = cs;
        this.shader = null;
        rebuild(getWidth());
        invalidate();
        return this;
    }

    public GlowView setPhase(float p) {
        phase = p - (float) Math.floor(p);
        return this;
    }

    public float phase() {
        return phase;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // 放不下就等比缩字号，绝不截断：号码少一位比小一号更糟
        int wMode0 = MeasureSpec.getMode(widthSpec);
        int wSize0 = MeasureSpec.getSize(widthSpec);
        if (baseSize > 0) paint.setTextSize(baseSize);
        if (wMode0 != MeasureSpec.UNSPECIFIED) {
            float avail = wSize0 - getPaddingLeft() - getPaddingRight();
            float tw = paint.measureText(text);
            if (avail > 0 && tw > avail) {
                paint.setTextSize(baseSize * avail / tw);
            }
        }
        int w = (int) Math.ceil(paint.measureText(text)) + getPaddingLeft() + getPaddingRight();
        int h = (int) Math.ceil(paint.descent() - paint.ascent())
                + getPaddingTop() + getPaddingBottom();
        int wMode = MeasureSpec.getMode(widthSpec);
        int wSize = MeasureSpec.getSize(widthSpec);
        if (wMode == MeasureSpec.AT_MOST) w = Math.min(w, wSize);
        if (wMode == MeasureSpec.EXACTLY) w = wSize;

        int hMode = MeasureSpec.getMode(heightSpec);
        int hSize = MeasureSpec.getSize(heightSpec);
        if (hMode == MeasureSpec.AT_MOST) h = Math.min(h, hSize);
        if (hMode == MeasureSpec.EXACTLY) h = hSize;

        setMeasuredDimension(Math.max(1, w), Math.max(1, h));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        shader = null;
        rebuild(w);
    }

    private void rebuild(int w) {
        if (w <= 0 || colors == null || colors.length < 2) return;
        // 色带比控件窄：文字上始终有两段颜色；首尾同色，所以 REPEAT 的接缝看不出来
        span = Math.max(1f, w * 0.62f);
        shader = new LinearGradient(0, 0, span, 0, colors, null, Shader.TileMode.REPEAT);
        shader.setLocalMatrix(matrix);
    }

    public void startGlow() {
        startGlow(phase);
    }

    public void startGlow(float p) {
        phase = p - (float) Math.floor(p);
        stopGlow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2400L);
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
        if (shader == null) rebuild(w);
        if (shader == null) return;
        float t = f + phase;
        t = t - (float) Math.floor(t);
        if (span <= 0) span = Math.max(1f, w * 0.62f);
        // REPEAT 的周期就是一个色带宽度，位移 span 正好回到起点，切换无跳变
        matrix.setTranslate(t * span, 0);
        shader.setLocalMatrix(matrix);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (text.length() == 0) return;
        if (shader == null) rebuild(getWidth());
        if (shader == null) return;
        paint.setShader(shader);
        float y = getPaddingTop() - paint.ascent();
        // Align.CENTER：以控件中线为基准，天然居中，不用自己算宽度
        canvas.drawText(text, getWidth() / 2f, y, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopGlow();
        super.onDetachedFromWindow();
    }
}
