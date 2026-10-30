package com.ecosense.app.ui.prediction;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.utils.LogFormatter;
import com.ecosense.app.utils.Prefs;
import com.ecosense.app.utils.RegressionCalculator;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PredictionActivity extends AppCompatActivity {
    private static final String[] METRICS = {
            "Temperature (\u00B0C)", "Humidity (%)", "PM2.5 (\u00B5g/m\u00B3)", "PM10 (\u00B5g/m\u00B3)"};
    private static final int FORECAST_STEPS = 6;

    private Spinner spMetric;
    private TrendChartView chart;
    private TextView tvEquation, tvTrend, tvForecast;
    private List<EnvLog> logs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        spMetric = findViewById(R.id.spMetric);
        chart = findViewById(R.id.chart);
        tvEquation = findViewById(R.id.tvEquation);
        tvTrend = findViewById(R.id.tvTrend);
        tvForecast = findViewById(R.id.tvForecast);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, METRICS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMetric.setAdapter(adapter);
        spMetric.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                compute();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

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

    private double value(EnvLog l, int metric) {
        switch (metric) {
            case 0: return l.temperature;
            case 1: return l.humidity;
            case 2: return l.pm25;
            default: return l.pm10;
        }
    }

    private double limit(int metric) {
        Prefs p = Prefs.get(this);
        switch (metric) {
            case 0: return p.getMaxTemp();
            case 1: return p.getMaxHumidity();
            case 2: return p.getMaxPm25();
            default: return p.getMaxPm10();
        }
    }

    private void compute() {
        int metric = spMetric.getSelectedItemPosition();
        int n = logs.size();
        if (n < 3) {
            chart.setData(new double[0], new double[0], new double[0], new double[0]);
            tvEquation.setText("Need at least 3 readings to predict. Refresh the dashboard a few times "
                    + "or enable background monitoring.");
            tvTrend.setText("");
            tvForecast.setText("");
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

        double step = (x[n - 1] - x[0]) / (n - 1);
        if (step <= 0) step = 0.25;

        double[] px = new double[FORECAST_STEPS];
        double[] py = new double[FORECAST_STEPS];
        StringBuilder sb = new StringBuilder("Next forecasts:\n");
        boolean exceeds = false;
        double lim = limit(metric);
        for (int k = 0; k < FORECAST_STEPS; k++) {
            px[k] = x[n - 1] + step * (k + 1);
            py[k] = Math.max(0, rc.predict(px[k]));
            long ts = t0 + (long) (px[k] * 3_600_000.0);
            sb.append(String.format(Locale.US, "\u2022 %s \u2192 %.2f\n", LogFormatter.date(ts), py[k]));
            if (py[k] > lim) exceeds = true;
        }
        if (exceeds) {
            sb.append(String.format(Locale.US, "\n\u26A0 Forecast exceeds your safe limit (%.0f).", lim));
        }

        chart.setData(x, y, px, py);
        tvEquation.setText(String.format(Locale.US,
                "Model: y = %.4f\u00B7x + %.3f   (x = hours, %d points)\nR\u00B2 = %.3f",
                rc.getSlope(), rc.getIntercept(), n, rc.getRSquared()));
        double eps = Math.max(0.001, 0.002 * Math.abs(rc.getIntercept()));
        tvTrend.setText(String.format(Locale.US, "Trend: %s (%+.3f per hour)",
                rc.trend(eps), rc.getSlope()));
        tvForecast.setText(sb.toString().trim());
    }
}
