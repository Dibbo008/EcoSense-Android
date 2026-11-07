package com.ecosense.app.ui.dashboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/** 270-degree ring gauge for the US AQI (0-300), colored by category, animated on change. */
public class AqiGaugeView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int aqi = -1;           // -1 = no data
    private float shown = 0f;       // animated 0..1 fraction
    private ValueAnimator animator;

    public AqiGaugeView(Context context) {
        super(context);
        init();
    }

    public AqiGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x55FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        number.setColor(Color.WHITE);
        number.setTextAlign(Paint.Align.CENTER);
        number.setFakeBoldText(true);
        number.setTextSize(sp(26));
        label.setColor(0xCCFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(sp(11));
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setValue(int newAqi) {
        float target = Math.min(1f, Math.max(0f, newAqi / 300f));
        aqi = newAqi;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(800);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private static int colorFor(int aqi) {
        if (aqi <= 50) return 0xFF69F0AE;   // good
        if (aqi <= 100) return 0xFFFFEE58;  // moderate
        if (aqi <= 150) return 0xFFFFA726;  // unhealthy for sensitive
        if (aqi <= 200) return 0xFFFF5252;  // unhealthy
        return 0xFFE040FB;                  // very unhealthy / hazardous
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float stroke = dp(9);
        float pad = stroke / 2f + dp(2);
        float left = (getWidth() - size) / 2f + pad;
        float top = (getHeight() - size) / 2f + pad;
        oval.set(left, top, left + size - 2 * pad, top + size - 2 * pad);
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);

        canvas.drawArc(oval, 135f, 270f, false, track);
        if (aqi >= 0) {
            arc.setColor(colorFor(aqi));
            canvas.drawArc(oval, 135f, 270f * shown, false, arc);
        }
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        canvas.drawText(aqi >= 0 ? String.valueOf(aqi) : "--", cx, cy + dp(4), number);
        canvas.drawText("US AQI", cx, cy + dp(22), label);
    }
}
