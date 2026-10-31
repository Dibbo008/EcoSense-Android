package com.ecosense.app.ui.dashboard;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.ecosense.app.data.remote.EnvRepository;
import com.ecosense.app.service.MonitoringService;
import com.ecosense.app.ui.auth.LoginActivity;
import com.ecosense.app.ui.history.HistoryActivity;
import com.ecosense.app.ui.prediction.PredictionActivity;
import com.ecosense.app.utils.AlertChecker;
import com.ecosense.app.utils.EcoTips;
import com.ecosense.app.utils.LogFormatter;
import com.ecosense.app.utils.NavHelper;
import com.ecosense.app.utils.PermissionHelper;
import com.ecosense.app.utils.Prefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
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
    private LinearLayout llRecent;
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
        findViewById(R.id.cardPredict).setOnClickListener(v ->
                startActivity(new Intent(this, PredictionActivity.class)));
        findViewById(R.id.cardHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
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
            List<EnvLog> recent = AppDatabase.getInstance(app).envLogDao().getRecent(5);
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
                List<EnvLog> recent = AppDatabase.getInstance(app).envLogDao().getRecent(5);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    showLog(l);
                    showRecent(recent);
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
        tvAqi.setText(String.valueOf(l.aqi));
        tvAqiLabel.setText(LogFormatter.aqiLabel(l.aqi));
        tvUpdated.setText("Updated " + LogFormatter.date(l.timestamp));
        tint(tvHumidity, l.humidity > p.getMaxHumidity());
        tint(tvPm25, l.pm25 > p.getMaxPm25());
        tint(tvPm10, l.pm10 > p.getMaxPm10());
        tint(tvTemp, l.temperature > p.getMaxTemp());
        tvAlert.setVisibility(AlertChecker.isUnsafe(this, l) ? View.VISIBLE : View.GONE);
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
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            EnvLog l = list.get(i);
            View v = getLayoutInflater().inflate(R.layout.item_recent, llRecent, false);
            ((TextView) v.findViewById(R.id.tvRTime)).setText(i == 0 ? "Now" : hourFmt.format(new Date(l.timestamp)));
            ((TextView) v.findViewById(R.id.tvREmoji)).setText(emojiFor(l.aqi));
            ((TextView) v.findViewById(R.id.tvRTemp)).setText(String.format(Locale.US, "%.0f\u00B0", l.temperature));
            llRecent.addView(v);
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
