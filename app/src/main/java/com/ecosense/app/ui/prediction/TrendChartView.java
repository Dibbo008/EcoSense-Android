package com.ecosense.app.ui.prediction;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ecosense.app.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Custom chart: smooth history curve with gradient fill, regression forecast with a widening
 * confidence band, optional safe-limit line, and a left-to-right reveal animation.
 */
public class TrendChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint xText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint histLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint histFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM", Locale.getDefault());

    private int primary, warn;

    private double[] hx = new double[0], hy = new double[0], px = new double[0], py = new double[0];
    private double band = 0;
    private double limit = Double.NaN;
    private long t0 = 0;
    private float progress = 1f;
    private ValueAnimator animator;

    // mapping state (set each draw)
    private double minX, maxX, minY, maxY;
    private float cl, cr, ct, cb;

    public TrendChartView(Context context) {
        super(context);
        init();
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        primary = ContextCompat.getColor(getContext(), R.color.eco_primary);
        warn = ContextCompat.getColor(getContext(), R.color.eco_warn);
        int alert = ContextCompat.getColor(getContext(), R.color.eco_alert);
        int sec = ContextCompat.getColor(getContext(), R.color.eco_text_secondary);
        int stroke = ContextCompat.getColor(getContext(), R.color.eco_stroke);
        int card = ContextCompat.getColor(getContext(), R.color.eco_card);

        gridPaint.setColor(stroke);
        gridPaint.setStrokeWidth(dp(1));

        yText.setColor(sec);
        yText.setTextSize(sp(10));
        yText.setTextAlign(Paint.Align.RIGHT);
        xText.setColor(sec);
        xText.setTextSize(sp(10));

        histLine.setColor(primary);
        histLine.setStyle(Paint.Style.STROKE);
        histLine.setStrokeWidth(dp(3));
        histLine.setStrokeCap(Paint.Cap.ROUND);
        histLine.setStrokeJoin(Paint.Join.ROUND);

        histFill.setStyle(Paint.Style.FILL);

        bandFill.setStyle(Paint.Style.FILL);
        bandFill.setColor(withAlpha(warn, 50));

        predLine.setColor(warn);
        predLine.setStyle(Paint.Style.STROKE);
        predLine.setStrokeWidth(dp(3));
        predLine.setStrokeCap(Paint.Cap.ROUND);
        predLine.setPathEffect(new DashPathEffect(new float[]{dp(9), dp(7)}, 0));

        predDot.setColor(warn);
        pointDot.setColor(primary);
        pointRing.setColor(withAlpha(primary, 70));
        pointCore.setColor(card);

        limitLine.setColor(alert);
        limitLine.setStyle(Paint.Style.STROKE);
        limitLine.setStrokeWidth(dp(1.5f));
        limitLine.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(5)}, 0));
        limitText.setColor(alert);
        limitText.setTextSize(sp(10));
        limitText.setTextAlign(Paint.Align.RIGHT);

        nowLine.setColor(withAlpha(sec, 120));
        nowLine.setStyle(Paint.Style.STROKE);
        nowLine.setStrokeWidth(dp(1));
        nowLine.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(4)}, 0));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * @param hx,hy history (x in hours since t0)
     * @param px,py forecast points
     * @param band  forecast uncertainty (std. error); 0 hides the band
     * @param limit safe limit; NaN hides the line
     * @param t0    epoch millis for x = 0 (for time labels)
     */
    public void setData(double[] hx, double[] hy, double[] px, double[] py,
                        double band, double limit, long t0) {
        this.hx = hx;
        this.hy = hy;
        this.px = px;
        this.py = py;
        this.band = band;
        this.limit = limit;
        this.t0 = t0;

        if (animator != null) animator.cancel();
        if (hx.length == 0) {
            progress = 1f;
            invalidate();
            return;
        }
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private float mx(double x) {
        return (float) (cl + (x - minX) / (maxX - minX) * (cr - cl));
    }

    private float my(double y) {
        return (float) (cb - (y - minY) / (maxY - minY) * (cb - ct));
    }

    private double bandAt(int i) {
        return band * (0.5 + 0.5 * (i + 1) / Math.max(1, px.length));
    }

    private String timeLabel(double x, boolean useDate) {
        Date d = new Date(t0 + (long) (x * 3_600_000.0));
        return useDate ? dateFmt.format(d) : timeFmt.format(d);
    }

    private String yLabel(double v, double range) {
        return range > 20 ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.1f", v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        cl = dp(40);
        cr = w - dp(14);
        ct = dp(14);
        cb = h - dp(28);

        if (hx.length == 0) {
            xText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Not enough data yet", w / 2f, h / 2f, xText);
            xText.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // ---- data bounds ----
        minX = hx[0];
        maxX = hx[0];
        double dMin = Double.MAX_VALUE, dMax = -Double.MAX_VALUE;
        for (int i = 0; i < hx.length; i++) {
            minX = Math.min(minX, hx[i]);
            maxX = Math.max(maxX, hx[i]);
            dMin = Math.min(dMin, hy[i]);
            dMax = Math.max(dMax, hy[i]);
        }
        for (int i = 0; i < px.length; i++) {
            maxX = Math.max(maxX, px[i]);
            dMin = Math.min(dMin, py[i] - bandAt(i));
            dMax = Math.max(dMax, py[i] + bandAt(i));
        }
        if (maxX - minX < 1e-9) maxX = minX + 1;
        double span = Math.max(dMax - dMin, 1.0);
        minY = dMin;
        maxY = dMax;
        boolean showLimit = !Double.isNaN(limit)
                && limit >= dMin - 2 * span && limit <= dMax + 2 * span;
        if (showLimit) {
            minY = Math.min(minY, limit);
            maxY = Math.max(maxY, limit);
        }
        double pad = Math.max((maxY - minY) * 0.10, 0.5);
        minY -= pad;
        maxY += pad;
        double range = maxY - minY;

        // ---- grid + y labels ----
        for (int i = 0; i <= 3; i++) {
            float y = cb - i * (cb - ct) / 3f;
            canvas.drawLine(cl, y, cr, y, gridPaint);
            canvas.drawText(yLabel(minY + i * range / 3.0, range), cl - dp(6), y + dp(3.5f), yText);
        }

        // ---- animated reveal ----
        canvas.save();
        canvas.clipRect(0, 0, cl + (w - cl) * progress, h);

        int n = hx.length;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = mx(hx[i]);
            ys[i] = my(hy[i]);
        }
        float lastX = xs[n - 1], lastY = ys[n - 1];

        // limit line
        if (showLimit) {
            float ly = my(limit);
            canvas.drawLine(cl, ly, cr, ly, limitLine);
            canvas.drawText("Limit " + yLabel(limit, range), cr, ly - dp(4), limitText);
        }

        // "now" divider
        if (px.length > 0) canvas.drawLine(lastX, ct, lastX, cb, nowLine);

        // history: gradient fill + smooth line
        if (n >= 2) {
            Path line = new Path();
            line.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) {
                float cx = (xs[i - 1] + xs[i]) / 2f;
                line.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
            }
            Path fill = new Path(line);
            fill.lineTo(xs[n - 1], cb);
            fill.lineTo(xs[0], cb);
            fill.close();
            histFill.setShader(new LinearGradient(0, ct, 0, cb,
                    withAlpha(primary, 95), withAlpha(primary, 0), Shader.TileMode.CLAMP));
            canvas.drawPath(fill, histFill);
            canvas.drawPath(line, histLine);
        }

        // forecast: confidence band + dashed line + dots
        int k = px.length;
        if (k > 0) {
            if (band > 0) {
                Path b = new Path();
                b.moveTo(lastX, lastY);
                for (int i = 0; i < k; i++) b.lineTo(mx(px[i]), my(py[i] + bandAt(i)));
                for (int i = k - 1; i >= 0; i--) b.lineTo(mx(px[i]), my(py[i] - bandAt(i)));
                b.close();
                canvas.drawPath(b, bandFill);
            }
            Path pp = new Path();
            pp.moveTo(lastX, lastY);
            for (int i = 0; i < k; i++) pp.lineTo(mx(px[i]), my(py[i]));
            canvas.drawPath(pp, predLine);
            for (int i = 0; i < k; i++) canvas.drawCircle(mx(px[i]), my(py[i]), dp(3.5f), predDot);
        }

        // latest real point: halo + dot
        canvas.drawCircle(lastX, lastY, dp(9), pointRing);
        canvas.drawCircle(lastX, lastY, dp(5.5f), pointDot);
        canvas.drawCircle(lastX, lastY, dp(2.2f), pointCore);

        canvas.restore();

        // ---- x labels ----
        boolean useDate = (maxX - minX) > 24;
        float ty = h - dp(8);
        xText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(timeLabel(minX, useDate), cl, ty, xText);
        xText.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(timeLabel(maxX, useDate), cr, ty, xText);
        if (k > 0 && lastX - cl > dp(48) && cr - lastX > dp(48)) {
            xText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Now", lastX, ty, xText);
        }
        xText.setTextAlign(Paint.Align.LEFT);
    }
}
