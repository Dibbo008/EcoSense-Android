package com.ecosense.app.ui.plan;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.HourPoint;
import com.ecosense.app.data.remote.ForecastRepository;
import com.ecosense.app.ui.settings.SettingsActivity;
import com.ecosense.app.utils.AdvisorEngine;
import com.ecosense.app.utils.LogFormatter;
import com.ecosense.app.utils.NavHelper;
import com.ecosense.app.utils.Prefs;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** "Today's Plan": best time to go outside, 24-hour strip, and activity advice for your health profile. */
public class PlanActivity extends AppCompatActivity {
    private TextView tvHeadline, tvSub, tvProfileChip, tvBestTime, tvBestSub, tvWorst, tvNote;
    private LinearLayout llHours, llAdvice;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        NavHelper.setup(this, NavHelper.NONE);

        tvHeadline = findViewById(R.id.tvPHeadline);
        tvSub = findViewById(R.id.tvPSub);
        tvProfileChip = findViewById(R.id.tvPProfile);
        tvBestTime = findViewById(R.id.tvBestTime);
        tvBestSub = findViewById(R.id.tvBestSub);
        tvWorst = findViewById(R.id.tvWorst);
        tvNote = findViewById(R.id.tvPNote);
        llHours = findViewById(R.id.llHours);
        llAdvice = findViewById(R.id.llAdvice);
        progress = findViewById(R.id.progress);

        tvProfileChip.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            EnvLog latest = AppDatabase.getInstance(app).envLogDao().getLatest();
            ForecastRepository.Result r = ForecastRepository.load(app, false);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                progress.setVisibility(View.GONE);
                render(latest, r);
            });
        });
    }

    private void render(EnvLog latest, ForecastRepository.Result r) {
        Prefs prefs = Prefs.get(this);
        int profile = AdvisorEngine.clampProfile(prefs.getProfile());
        long now = System.currentTimeMillis();
        List<HourPoint> pts = r.points;

        tvProfileChip.setText("\uD83E\uDE7A Profile: " + AdvisorEngine.PROFILE_NAMES[profile] + "  \u203A");

        EnvLog current = latest;
        if (current == null && pts != null && !pts.isEmpty()) current = fromForecast(pts, now);
        if (current == null) {
            tvHeadline.setText("No data yet");
            tvSub.setText("Open the dashboard and tap refresh, then come back.");
            tvNote.setText(r.error != null ? "Forecast unavailable: " + r.error : "");
            llHours.removeAllViews();
            llAdvice.removeAllViews();
            tvBestTime.setText("--");
            tvBestSub.setText("");
            tvWorst.setText("");
            return;
        }

        AdvisorEngine.Plan plan = AdvisorEngine.build(profile, current, pts, now);

        tvHeadline.setText(plan.headline);
        tvSub.setText(String.format(Locale.US, "AQI %d (%s) \u00B7 %.0f\u00B0C \u00B7 %s",
                current.aqi, LogFormatter.aqiLabel(current.aqi), current.temperature,
                prefs.getLocationLabel()));

        // ---- best / worst window
        if (plan.best != null) {
            tvBestTime.setText(AdvisorEngine.formatWindow(plan.best));
            tvBestSub.setText(String.format(Locale.US, "Expected AQI about %.0f (%s) for you.",
                    plan.best.avgAqi, levelWord(plan.best.level)));
        } else {
            tvBestTime.setText("Not available");
            tvBestSub.setText("Connect to the internet once to load the hourly forecast.");
        }
        tvWorst.setText(plan.worst != null
                ? "\u26A0 Avoid " + AdvisorEngine.formatWindow(plan.worst)
                + String.format(Locale.US, " (AQI about %.0f)", plan.worst.avgAqi)
                : (plan.best != null ? "No clearly bad period expected in the next 24 hours." : ""));

        // ---- 24 hour strip
        llHours.removeAllViews();
        if (pts != null) {
            int shown = 0;
            for (HourPoint p : pts) {
                if (p.time < now - 30 * 60_000L) continue;
                if (shown >= 24) break;
                llHours.addView(hourView(p, profile, shown == 0, plan.best));
                shown++;
            }
        }

        // ---- advice rows
        llAdvice.removeAllViews();
        for (AdvisorEngine.Advice a : plan.advice) {
            View row = getLayoutInflater().inflate(R.layout.item_advice, llAdvice, false);
            ((TextView) row.findViewById(R.id.tvAEmoji)).setText(a.emoji);
            ((TextView) row.findViewById(R.id.tvATitle)).setText(a.title);
            ((TextView) row.findViewById(R.id.tvADetail)).setText(a.detail);
            TextView st = row.findViewById(R.id.tvAStatus);
            int color = levelColor(a.level);
            st.setText(a.status);
            st.setTextColor(color);
            st.setBackground(pill(withAlpha(color, 0x26), 50));
            llAdvice.addView(row);
        }

        // ---- footer note
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String note;
        if (r.error != null && r.fromCache) {
            note = "Offline: showing the forecast saved at " + f.format(new Date(r.fetchedAt)) + ".";
        } else if (r.error != null) {
            note = "Forecast unavailable (" + r.error + "). Showing advice for the latest reading only.";
        } else {
            note = "Forecast updated " + f.format(new Date(r.fetchedAt)) + " \u00B7 data from Open-Meteo.";
        }
        tvNote.setText(note + " General guidance only, not medical advice.");
    }

    private View hourView(HourPoint p, int profile, boolean first, AdvisorEngine.Window best) {
        View v = getLayoutInflater().inflate(R.layout.item_hour, llHours, false);
        TextView aqi = v.findViewById(R.id.tvHAqi);
        TextView time = v.findViewById(R.id.tvHTime);
        View bar = v.findViewById(R.id.vBar);

        int level = AdvisorEngine.hourLevel(profile, p);
        double score = AdvisorEngine.hourScore(profile, p);
        float density = getResources().getDisplayMetrics().density;
        float hDp = 20 + (float) Math.min(1.0, score / 200.0) * 80; // 20..100 dp
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
        lp.height = Math.round(hDp * density);
        bar.setLayoutParams(lp);

        int color = levelColor(level);
        GradientDrawable d = pill(color, 8);
        boolean inBest = best != null && p.time >= best.start && p.time < best.end;
        if (inBest) d.setStroke(Math.round(2 * density), ContextCompat.getColor(this, R.color.eco_text));
        bar.setBackground(d);

        aqi.setText(String.valueOf(p.aqi));
        time.setText(first ? "Now" : new SimpleDateFormat("ha", Locale.getDefault()).format(new Date(p.time)));
        if (inBest) time.setText("\u2B50 " + time.getText());
        return v;
    }

    private static EnvLog fromForecast(List<HourPoint> pts, long now) {
        HourPoint nearest = pts.get(0);
        for (HourPoint p : pts) {
            if (Math.abs(p.time - now) < Math.abs(nearest.time - now)) nearest = p;
        }
        EnvLog l = new EnvLog();
        l.timestamp = nearest.time;
        l.temperature = nearest.temp;
        l.humidity = nearest.humidity;
        l.pm25 = nearest.pm25;
        l.pm10 = nearest.pm10;
        l.aqi = nearest.aqi;
        return l;
    }

    private int levelColor(int level) {
        return ContextCompat.getColor(this, level == AdvisorEngine.OK ? R.color.eco_safe
                : level == AdvisorEngine.CAUTION ? R.color.eco_warn : R.color.eco_alert);
    }

    private static String levelWord(int level) {
        return level == AdvisorEngine.OK ? "good" : level == AdvisorEngine.CAUTION ? "fair" : "poor";
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return d;
    }
}
