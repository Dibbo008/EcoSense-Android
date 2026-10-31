package com.ecosense.app.ui.prediction;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.utils.NavHelper;
import com.ecosense.app.utils.Prefs;
import com.ecosense.app.utils.RegressionCalculator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PredictionActivity extends AppCompatActivity {
    private static final String[] NAMES = {"Temperature", "Humidity", "PM2.5", "PM10"};
    private static final String[] EMOJI = {"\uD83C\uDF21\uFE0F", "\uD83D\uDCA7", "\uD83D\uDE37", "\uD83C\uDF2B\uFE0F"};
    private static final String[] UNITS = {"\u00B0C", "%", "\u00B5g/m\u00B3", "\u00B5g/m\u00B3"};
    private static final int FORECAST_STEPS = 6;

    private TrendChartView chart;
    private TextView tvMetricName, tvLocation, tvCurrent, tvUnit, tvTrendChip, tvNext;
    private TextView tvFit, tvFitLabel, tvSlope, tvPoints, tvInsight;
    private LinearLayout llForecast;

    private List<EnvLog> logs = new ArrayList<>();
    private int metric = 0;
    private final SimpleDateFormat rowFmt = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        NavHelper.setup(this, NavHelper.PREDICT);
        chart = findViewById(R.id.chart);
        tvMetricName = findViewById(R.id.tvMetricName);
        tvLocation = findViewById(R.id.tvLocation);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvUnit = findViewById(R.id.tvUnit);
        tvTrendChip = findViewById(R.id.tvTrendChip);
        tvNext = findViewById(R.id.tvNext);
        tvFit = findViewById(R.id.tvFit);
        tvFitLabel = findViewById(R.id.tvFitLabel);
        tvSlope = findViewById(R.id.tvSlope);
        tvPoints = findViewById(R.id.tvPoints);
        tvInsight = findViewById(R.id.tvInsight);
        llForecast = findViewById(R.id.llForecast);

        ChipGroup chipGroup = findViewById(R.id.chipGroup);
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            metric = id == R.id.chipTemp ? 0 : id == R.id.chipHum ? 1 : id == R.id.chipPm25 ? 2 : 3;
            compute();
        });
        chipGroup.check(R.id.chipTemp);

        AppDatabase.IO.execute(() -> {
            List<EnvLog> recent = AppDatabase.getInstance(getApplicationContext()).envLogDao().getRecent(60);
            Collections.reverse(recent); // oldest -> newest
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                logs = recent;
                compute();
            });
        });
    }

    private double value(EnvLog l, int m) {
        switch (m) {
            case 0: return l.temperature;
            case 1: return l.humidity;
            case 2: return l.pm25;
            default: return l.pm10;
        }
    }

    private double limit(int m) {
        Prefs p = Prefs.get(this);
        switch (m) {
            case 0: return p.getMaxTemp();
            case 1: return p.getMaxHumidity();
            case 2: return p.getMaxPm25();
            default: return p.getMaxPm10();
        }
    }

    private String fmt(int m, double v) {
        return m == 1 ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.1f", v);
    }

    private void compute() {
        tvLocation.setText(Prefs.get(this).getLocationLabel());
        tvMetricName.setText(EMOJI[metric] + "  " + NAMES[metric]);
        tvUnit.setText(UNITS[metric]);
        llForecast.removeAllViews();

        int n = logs.size();
        if (n < 3) {
            chart.setData(new double[0], new double[0], new double[0], new double[0], 0, Double.NaN, 0);
            tvCurrent.setText("--");
            tvTrendChip.setText("Not enough data");
            tvNext.setText("Need at least 3 readings to forecast.");
            tvFit.setText("--");
            tvFitLabel.setText("");
            tvSlope.setText("--");
            tvPoints.setText(String.valueOf(n));
            tvInsight.setText("Refresh the dashboard a few times, or turn on background monitoring, "
                    + "to collect data for predictions.");
            return;
        }

        long t0 = logs.get(0).timestamp;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = (logs.get(i).timestamp - t0) / 3_600_000.0; // hours since first reading
            y[i] = value(logs.get(i), metric);
        }

        RegressionCalculator rc = new RegressionCalculator();
        rc.fit(x, y);
        double sigma = rc.standardError(x, y);

        double step = (x[n - 1] - x[0]) / (n - 1);
        if (step <= 0) step = 0.25;
        double upper = metric == 1 ? 100 : Double.MAX_VALUE; // humidity can't exceed 100%

        double[] px = new double[FORECAST_STEPS];
        double[] py = new double[FORECAST_STEPS];
        double lim = limit(metric);
        double scaleMax = lim;
        for (int k = 0; k < FORECAST_STEPS; k++) {
            px[k] = x[n - 1] + step * (k + 1);
            py[k] = Math.min(upper, Math.max(0, rc.predict(px[k])));
            scaleMax = Math.max(scaleMax, py[k]);
        }
        scaleMax = Math.max(scaleMax * 1.1, 1e-6);

        chart.setData(x, y, px, py, sigma, lim, t0);

        // ---- hero ----
        double now = y[n - 1];
        double slope = rc.getSlope();
        double eps = Math.max(0.001, 0.002 * Math.abs(rc.getIntercept()));
        String arrow = slope > eps ? "\u2197 " : slope < -eps ? "\u2198 " : "\u2192 ";
        tvCurrent.setText(fmt(metric, now));
        tvTrendChip.setText(String.format(Locale.US, "%s%s  %+.2f /h", arrow, rc.trend(eps), slope));
        long firstTs = t0 + (long) (px[0] * 3_600_000.0);
        tvNext.setText("Next forecast (" + rowFmt.format(new Date(firstTs)) + "): "
                + fmt(metric, py[0]) + " " + UNITS[metric]);

        // ---- stats ----
        double r2 = rc.getRSquared();
        tvFit.setText(Math.round(r2 * 100) + "%");
        tvFitLabel.setText(r2 >= 0.7 ? "Strong fit" : r2 >= 0.4 ? "Moderate fit" : "Weak fit");
        tvSlope.setText(String.format(Locale.US, "%+.2f", slope));
        tvPoints.setText(String.valueOf(n));

        // ---- insight in plain language ----
        StringBuilder in = new StringBuilder();
        String dir = slope > eps ? "rising" : slope < -eps ? "falling" : "steady";
        in.append(NAMES[metric]).append(" is ").append(dir);
        if (!dir.equals("steady")) {
            in.append(String.format(Locale.US, " by about %.2f %s per hour", Math.abs(slope), UNITS[metric]));
        }
        in.append(". ");
        double fitNow = rc.predict(x[n - 1]);
        if (fitNow > lim) {
            in.append(String.format(Locale.US, "It is currently above your safe limit (%.0f). ", lim));
            if (slope < -eps) {
                double hrs = (fitNow - lim) / -slope;
                in.append(hrs <= 72 ? String.format(Locale.US, "At this pace it could drop below the limit in ~%.1f h. ", hrs)
                        : "It is not expected to drop below the limit soon. ");
            }
        } else if (slope > eps) {
            double hrs = (lim - fitNow) / slope;
            in.append(hrs <= 72 ? String.format(Locale.US, "At this pace it may cross your limit (%.0f) in ~%.1f h. ", lim, hrs)
                    : String.format(Locale.US, "It is not expected to reach your limit (%.0f) within 3 days. ", lim));
        } else {
            in.append(String.format(Locale.US, "It stays within your limit (%.0f). ", lim));
        }
        if (n < 8) in.append("Only a few readings so far, so treat this as a rough estimate.");
        else if (r2 < 0.4) in.append("The data is noisy, so the trend is not very reliable.");
        tvInsight.setText(in.toString().trim());

        // ---- forecast timeline rows ----
        for (int k = 0; k < FORECAST_STEPS; k++) {
            View row = getLayoutInflater().inflate(R.layout.item_forecast, llForecast, false);
            TextView tvTime = row.findViewById(R.id.tvFTime);
            TextView tvVal = row.findViewById(R.id.tvFVal);
            LinearProgressIndicator bar = row.findViewById(R.id.pbF);
            boolean over = py[k] > lim;
            tvTime.setText(rowFmt.format(new Date(t0 + (long) (px[k] * 3_600_000.0))));
            tvVal.setText((over ? "\u26A0 " : "") + fmt(metric, py[k]));
            bar.setIndicatorColor(ContextCompat.getColor(this, over ? R.color.eco_alert : R.color.eco_primary));
            bar.setProgressCompat((int) Math.round(py[k] / scaleMax * 100), true);
            llForecast.addView(row);
        }
    }
}
