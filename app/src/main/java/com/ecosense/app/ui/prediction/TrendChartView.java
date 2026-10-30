package com.ecosense.app.ui.prediction;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ecosense.app.R;

import java.util.Locale;

/** Lightweight custom chart: history (solid) + regression forecast (dashed). No external libs. */
public class TrendChartView extends View {
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hist = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pred = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint histDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private double[] hx = new double[0], hy = new double[0], px = new double[0], py = new double[0];

    public TrendChartView(Context context) {
        super(context);
        init();
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        int primary = ContextCompat.getColor(getContext(), R.color.eco_primary);
        int warn = ContextCompat.getColor(getContext(), R.color.eco_warn);
        int sec = ContextCompat.getColor(getContext(), R.color.eco_text_secondary);

        axis.setColor(sec);
        axis.setStrokeWidth(dp(1));

        hist.setColor(primary);
        hist.setStyle(Paint.Style.STROKE);
        hist.setStrokeWidth(dp(2.5f));
        hist.setStrokeJoin(Paint.Join.ROUND);

        pred.setColor(warn);
        pred.setStyle(Paint.Style.STROKE);
        pred.setStrokeWidth(dp(2.5f));
        pred.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));

        histDot.setColor(primary);
        predDot.setColor(warn);

        text.setColor(sec);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10,
                getResources().getDisplayMetrics()));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setData(double[] hx, double[] hy, double[] px, double[] py) {
        this.hx = hx;
        this.hy = hy;
        this.px = px;
        this.py = py;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float left = dp(44), right = w - dp(12), top = dp(12), bottom = h - dp(24);

        if (hx.length == 0) {
            canvas.drawText("Not enough data yet", dp(16), h / 2f, text);
            return;
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < hx.length; i++) {
            minX = Math.min(minX, hx[i]); maxX = Math.max(maxX, hx[i]);
            minY = Math.min(minY, hy[i]); maxY = Math.max(maxY, hy[i]);
        }
        for (int i = 0; i < px.length; i++) {
            minX = Math.min(minX, px[i]); maxX = Math.max(maxX, px[i]);
            minY = Math.min(minY, py[i]); maxY = Math.max(maxY, py[i]);
        }
        if (maxX - minX < 1e-9) maxX = minX + 1;
        double padY = (maxY - minY) * 0.1;
        if (padY < 1e-9) padY = 1;
        minY -= padY;
        maxY += padY;

        canvas.drawLine(left, top, left, bottom, axis);
        canvas.drawLine(left, bottom, right, bottom, axis);
        canvas.drawText(String.format(Locale.US, "%.1f", maxY), dp(2), top + dp(8), text);
        canvas.drawText(String.format(Locale.US, "%.1f", minY), dp(2), bottom, text);
        canvas.drawText("past", left, h - dp(6), text);
        canvas.drawText("forecast", right - dp(44), h - dp(6), text);

        Path hp = new Path();
        float lastX = left, lastY = bottom;
        for (int i = 0; i < hx.length; i++) {
            float cx = (float) (left + (hx[i] - minX) / (maxX - minX) * (right - left));
            float cy = (float) (bottom - (hy[i] - minY) / (maxY - minY) * (bottom - top));
            if (i == 0) hp.moveTo(cx, cy); else hp.lineTo(cx, cy);
            canvas.drawCircle(cx, cy, dp(2.5f), histDot);
            lastX = cx;
            lastY = cy;
        }
        canvas.drawPath(hp, hist);

        if (px.length > 0) {
            Path pp = new Path();
            pp.moveTo(lastX, lastY);
            for (int i = 0; i < px.length; i++) {
                float cx = (float) (left + (px[i] - minX) / (maxX - minX) * (right - left));
                float cy = (float) (bottom - (py[i] - minY) / (maxY - minY) * (bottom - top));
                pp.lineTo(cx, cy);
                canvas.drawCircle(cx, cy, dp(3), predDot);
            }
            canvas.drawPath(pp, pred);
        }
    }
}
