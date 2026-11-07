package com.ecosense.app.ui.dashboard;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.ForecastCache;
import com.ecosense.app.data.local.HourPoint;
import com.ecosense.app.data.local.SymptomRepository;
import com.ecosense.app.data.remote.EnvRepository;
import com.ecosense.app.data.remote.ForecastRepository;
import com.ecosense.app.service.MonitoringService;
import com.ecosense.app.ui.auth.LoginActivity;
import com.ecosense.app.ui.plan.PlanActivity;
import com.ecosense.app.ui.symptom.SymptomActivity;
import com.ecosense.app.utils.AdvisorEngine;
import com.ecosense.app.utils.AlertChecker;
import com.ecosense.app.utils.CorrelationEngine;
import com.ecosense.app.utils.EcoTips;
import com.ecosense.app.utils.LogFormatter;
import com.ecosense.app.utils.NavHelper;
import com.ecosense.app.utils.PermissionHelper;
import com.ecosense.app.utils.Prefs;
import com.ecosense.app.utils.SpikeDetector;
import com.ecosense.app.utils.SymptomTypes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {
    private static final String STATE_UPDATED = "state_updated";

    private TextView tvGreeting, tvName, tvLocation, tvUpdated, tvTemp, tvHumidity, tvPm25, tvPm10,
            tvAqi, tvAqiLabel, tvTip, tvStatus, tvAlert, tvRecentEmpty;
    private LinearLayout llRecent, llPlanChips;
    private TextView tvPlanHeadline, tvPlanBest, tvSymStatus;
    private AqiGaugeView aqiGauge;
    private LinearProgressIndicator pbSymptom;
    private ProgressBar progress;
    private MaterialSwitch swMonitor;
    private EnvLog latest;
    private boolean receiverRegistered;
    private boolean permissionsPrompted;
    private final SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    /** Dynamic receiver: refreshes the UI whenever the service stores a new reading. */
    private final BroadcastReceiver readingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadLatest();
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!PermissionHelper.hasLocation(this)) {
                    Toast.makeText(this, "Location denied: using the default location.",
                            Toast.LENGTH_LONG).show();
                }
                refresh();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvGreeting = findViewById(R.id.tvGreeting);
        tvName = findViewById(R.id.tvName);
        tvLocation = findViewById(R.id.tvLocation);
        tvUpdated = findViewById(R.id.tvUpdated);
        tvTemp = findViewById(R.id.tvTemp);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvPm25 = findViewById(R.id.tvPm25);
        tvPm10 = findViewById(R.id.tvPm10);
        tvAqi = findViewById(R.id.tvAqi);
        tvAqiLabel = findViewById(R.id.tvAqiLabel);
        tvTip = findViewById(R.id.tvTip);
        tvStatus = findViewById(R.id.tvStatus);
        tvAlert = findViewById(R.id.tvAlert);
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty);
        llRecent = findViewById(R.id.llRecent);
        llPlanChips = findViewById(R.id.llPlanChips);
        tvPlanHeadline = findViewById(R.id.tvPlanHeadline);
        tvPlanBest = findViewById(R.id.tvPlanBest);
        tvSymStatus = findViewById(R.id.tvSymStatus);
        pbSymptom = findViewById(R.id.pbSymptom);
        aqiGauge = findViewById(R.id.aqiGauge);
        progress = findViewById(R.id.progress);
        swMonitor = findViewById(R.id.swMonitor);

        if (savedInstanceState != null) {
            tvUpdated.setText(savedInstanceState.getString(STATE_UPDATED, tvUpdated.getText().toString()));
        }
        tvTip.setText(EcoTips.today());
        updateHeader();

        tvLocation.setOnClickListener(v -> openLocationInMaps());

        swMonitor.setChecked(Prefs.get(this).isMonitoring());
        updateMonitorLabel();
        swMonitor.setOnCheckedChangeListener((b, checked) -> toggleService(checked));

        findViewById(R.id.btnRefresh).setOnClickListener(v -> refresh());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareReport());
        findViewById(R.id.cardPlan).setOnClickListener(v ->
                startActivity(new Intent(this, PlanActivity.class)));
        findViewById(R.id.cardSymptom).setOnClickListener(v ->
                startActivity(new Intent(this, SymptomActivity.class)));
        // One-tap quick log (mild); details can be added in the Symptom screen
        final int[][] quick = {{R.id.qlFine, SymptomTypes.FINE}, {R.id.qlHeadache, 0},
                {R.id.qlCough, 1}, {R.id.qlBreath, 3}, {R.id.qlFatigue, 4}};
        for (int[] q : quick) {
            findViewById(q[0]).setOnClickListener(v -> quickLog(q[1]));
        }
        NavHelper.setup(this, NavHelper.HOME);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Auth guard: signed-in users or guests only
        if (FirebaseAuth.getInstance().getCurrentUser() == null && !Prefs.get(this).isGuest()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, readingReceiver,
                    new IntentFilter(MonitoringService.ACTION_NEW_READING),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeader();
        swMonitor.setChecked(Prefs.get(this).isMonitoring());
        loadLatest();
        updateSymptomCard();
        promptForPermissions();
    }

    @Override
    protected void onPause() {
        // Persist lightweight UI state before leaving the foreground
        Prefs.get(this).setLastViewed(System.currentTimeMillis());
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(readingReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_UPDATED, tvUpdated.getText().toString());
    }

    // ---------------------------------------------------------------- header

    private String displayName() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null && u.getEmail() != null) return u.getEmail().split("@")[0];
        return "Eco Explorer";
    }

    private void updateHeader() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good Morning \u2600\uFE0F"
                : hour < 17 ? "Good Afternoon \uD83C\uDF24\uFE0F" : "Good Evening \uD83C\uDF19";
        tvGreeting.setText(greeting);
        tvName.setText(displayName());
        tvLocation.setText(Prefs.get(this).getLocationLabel());
    }

    private void promptForPermissions() {
        if (permissionsPrompted) return;
        final String[] missing = PermissionHelper.missingRuntimePermissions(this);
        if (missing.length == 0) return;
        permissionsPrompted = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permissions")
                .setMessage("EcoSense uses your location to fetch local air-quality data and "
                        + "notifications to warn you about unsafe conditions.")
                .setPositiveButton("Continue", (d, w) -> permissionLauncher.launch(missing))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void toggleService(boolean on) {
        Prefs.get(this).setMonitoring(on);
        Intent svc = new Intent(this, MonitoringService.class);
        if (on) {
            ContextCompat.startForegroundService(this, svc);
        } else {
            stopService(svc);
        }
        updateMonitorLabel();
    }

    private void updateMonitorLabel() {
        tvStatus.setText(swMonitor.isChecked() ? "Background monitoring: ON" : "Background monitoring: OFF");
    }

    // ------------------------------------------------------------------ data

    private void loadLatest() {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            EnvLog l = AppDatabase.getInstance(app).envLogDao().getLatest();
            List<EnvLog> recent = AppDatabase.getInstance(app).envLogDao().getRecent(15);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (l != null) showLog(l);
                showRecent(recent);
            });
        });
    }

    private void refresh() {
        progress.setVisibility(View.VISIBLE);
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                EnvLog l = EnvRepository.fetchAndStore(app);
                AlertChecker.dispatch(app, l);
                List<EnvLog> recent = AppDatabase.getInstance(app).envLogDao().getRecent(15);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    showLog(l);
                    showRecent(recent);
                });
                // Warm the hourly forecast cache so "Today's Plan" has a best-time window
                ForecastRepository.load(app, false);
                runOnUiThread(() -> {
                    if (!isDestroyed()) renderPlan();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not fetch data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showLog(EnvLog l) {
        latest = l;
        updateHeader();
        Prefs p = Prefs.get(this);
        tvTemp.setText(LogFormatter.temp(l.temperature));
        tvHumidity.setText(LogFormatter.hum(l.humidity));
        tvPm25.setText(String.format(Locale.US, "%.1f", l.pm25));
        tvPm10.setText(String.format(Locale.US, "%.1f", l.pm10));
        tvAqi.setText("US AQI " + l.aqi);
        aqiGauge.setValue(l.aqi);
        tvAqiLabel.setText(LogFormatter.aqiLabel(l.aqi));
        tvUpdated.setText("Updated " + LogFormatter.date(l.timestamp));
        tint(tvHumidity, l.humidity > p.getMaxHumidity());
        tint(tvPm25, l.pm25 > p.getMaxPm25());
        tint(tvPm10, l.pm10 > p.getMaxPm10());
        tint(tvTemp, l.temperature > p.getMaxTemp());
    }

    /** White text on the sky background; amber when a limit is exceeded. */
    private void tint(TextView tv, boolean unsafe) {
        tv.setTextColor(unsafe ? ContextCompat.getColor(this, R.color.sky_warn)
                : ContextCompat.getColor(this, android.R.color.white));
    }

    /** Glass card: newest reading first. */
    private void showRecent(List<EnvLog> list) {
        llRecent.removeAllViews();
        tvRecentEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        updateAlertChip(list);
        renderPlan();
        if (list == null) return;
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            EnvLog l = list.get(i);
            View v = getLayoutInflater().inflate(R.layout.item_recent, llRecent, false);
            ((TextView) v.findViewById(R.id.tvRTime)).setText(i == 0 ? "Now" : hourFmt.format(new Date(l.timestamp)));
            ((TextView) v.findViewById(R.id.tvREmoji)).setText(emojiFor(l.aqi));
            ((TextView) v.findViewById(R.id.tvRTemp)).setText(String.format(Locale.US, "%.0f\u00B0", l.temperature));
            llRecent.addView(v);
        }
    }

    /** One-tap diary entry from the dashboard. */
    private void quickLog(int type) {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            SymptomRepository.log(app, type, type == SymptomTypes.FINE ? 0 : 1, "");
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Toast.makeText(this, "Logged " + SymptomTypes.EMOJI[type] + " " + SymptomTypes.NAMES[type]
                        + (type == SymptomTypes.FINE ? "" : " (mild). Open Symptom Link to add details."),
                        Toast.LENGTH_SHORT).show();
                updateSymptomCard();
            });
        });
    }

    /** Symptom Link card: today's personal risk, or progress toward unlocking patterns. */
    private void updateSymptomCard() {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            CorrelationEngine.Report r = SymptomRepository.analyze(app);
            EnvLog latestLog = AppDatabase.getInstance(app).envLogDao().getLatest();
            ForecastCache.Blob cache = ForecastCache.read(app);
            List<String> risks = CorrelationEngine.todayRisks(r.insights, latestLog,
                    cache != null ? cache.points : null, System.currentTimeMillis());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (!risks.isEmpty()) {
                    tvSymStatus.setText("\u26A0 " + risks.get(0));
                    pbSymptom.setVisibility(View.GONE);
                } else if (r.daysToUnlock > 0) {
                    tvSymStatus.setText(String.format(Locale.US,
                            "Log %d more day%s to unlock your personal patterns (%d/%d).",
                            r.daysToUnlock, r.daysToUnlock == 1 ? "" : "s",
                            r.observedDays, CorrelationEngine.MIN_OBSERVED_DAYS));
                    pbSymptom.setMax(CorrelationEngine.MIN_OBSERVED_DAYS);
                    pbSymptom.setProgressCompat(r.observedDays, true);
                    pbSymptom.setVisibility(View.VISIBLE);
                } else if (!r.insights.isEmpty()) {
                    tvSymStatus.setText(r.insights.size() + " personal pattern"
                            + (r.insights.size() == 1 ? "" : "s") + " found. Today looks fine for you.");
                    pbSymptom.setVisibility(View.GONE);
                } else {
                    tvSymStatus.setText("No clear triggers yet. Keep logging to improve accuracy.");
                    pbSymptom.setVisibility(View.GONE);
                }
            });
        });
    }

    /** Spike warning wins over the plain "above limits" warning. */
    private void updateAlertChip(List<EnvLog> list) {
        if (list == null || list.isEmpty()) {
            tvAlert.setVisibility(View.GONE);
            return;
        }
        String spike = SpikeDetector.detect(list, Prefs.get(this).getProfile());
        if (spike != null) {
            tvAlert.setText("\uD83D\uDCC8 " + spike);
            tvAlert.setVisibility(View.VISIBLE);
        } else if (AlertChecker.isUnsafe(this, list.get(0))) {
            tvAlert.setText("\u26A0 Above your safe limits");
            tvAlert.setVisibility(View.VISIBLE);
        } else {
            tvAlert.setVisibility(View.GONE);
        }
    }

    /** "Today's Plan" card: headline, best window (from cached forecast) and activity chips. */
    private void renderPlan() {
        llPlanChips.removeAllViews();
        if (latest == null) {
            tvPlanHeadline.setText("Tap refresh to build your plan");
            tvPlanBest.setText("");
            return;
        }
        ForecastCache.Blob cache = ForecastCache.read(this);
        List<HourPoint> pts = cache != null ? cache.points : null;
        AdvisorEngine.Plan plan = AdvisorEngine.build(Prefs.get(this).getProfile(), latest, pts,
                System.currentTimeMillis());
        tvPlanHeadline.setText(plan.headline);
        tvPlanBest.setText(plan.best != null
                ? "\u2B50 Best time outside: " + AdvisorEngine.formatWindow(plan.best)
                : "\u2B50 Open the plan to load the hourly forecast");

        float d = getResources().getDisplayMetrics().density;
        for (AdvisorEngine.Advice a : plan.advice) {
            int color = ContextCompat.getColor(this, a.level == AdvisorEngine.OK ? R.color.eco_safe
                    : a.level == AdvisorEngine.CAUTION ? R.color.eco_warn : R.color.eco_alert);
            TextView chip = new TextView(this);
            chip.setText(a.emoji + " " + a.shortLabel);
            chip.setTextSize(12);
            chip.setTextColor(ContextCompat.getColor(this, R.color.eco_text));
            chip.setPadding(Math.round(12 * d), Math.round(6 * d), Math.round(12 * d), Math.round(6 * d));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(50 * d);
            bg.setColor((color & 0x00FFFFFF) | 0x33000000);
            bg.setStroke(Math.round(1.5f * d), color);
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(8 * d));
            llPlanChips.addView(chip, lp);
        }
    }

    private static String emojiFor(int aqi) {
        if (aqi <= 50) return "\u2600\uFE0F";
        if (aqi <= 100) return "\u26C5";
        if (aqi <= 150) return "\uD83C\uDF25\uFE0F";
        return "\uD83C\uDF2B\uFE0F";
    }

    /** Implicit intent: opens the data location in a maps app / browser. */
    private void openLocationInMaps() {
        Prefs p = Prefs.get(this);
        if (!p.hasLocation()) {
            Toast.makeText(this, "Tap refresh to detect your location first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse(String.format(Locale.US, "https://www.google.com/maps?q=%f,%f",
                p.getLocationLat(), p.getLocationLon()));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app available to open maps.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Implicit intent: hands a text report to any app that can share text. */
    private void shareReport() {
        if (latest == null) {
            Toast.makeText(this, "No reading yet. Tap refresh first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String body = String.format(Locale.US,
                "EcoSense report (%s)\nLocation: %s\nTemperature: %.1f\u00B0C\nHumidity: %.0f%%\nPM2.5: %.1f\nPM10: %.1f\nAQI: %d (%s)",
                LogFormatter.date(latest.timestamp), Prefs.get(this).getLocationDisplay(),
                latest.temperature, latest.humidity,
                latest.pm25, latest.pm10, latest.aqi, LogFormatter.aqiLabel(latest.aqi));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(send, "Share report"));
    }
}
