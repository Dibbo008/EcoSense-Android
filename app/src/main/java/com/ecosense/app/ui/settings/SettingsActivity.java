package com.ecosense.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.remote.FirebaseHelper;
import com.ecosense.app.service.MonitoringService;
import com.ecosense.app.ui.auth.LoginActivity;
import com.ecosense.app.utils.PermissionHelper;
import com.ecosense.app.utils.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private TextInputEditText etTemp, etHumidity, etPm25, etPm10, etInterval;
    private MaterialSwitch swNotifications, swDark;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r ->
                    Toast.makeText(this, "Permissions updated.", Toast.LENGTH_SHORT).show());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        etTemp = findViewById(R.id.etTemp);
        etHumidity = findViewById(R.id.etHumidity);
        etPm25 = findViewById(R.id.etPm25);
        etPm10 = findViewById(R.id.etPm10);
        etInterval = findViewById(R.id.etInterval);
        swNotifications = findViewById(R.id.swNotifications);
        swDark = findViewById(R.id.swDark);
        TextView tvAccount = findViewById(R.id.tvAccount);

        Prefs p = Prefs.get(this);
        etTemp.setText(fmt(p.getMaxTemp()));
        etHumidity.setText(fmt(p.getMaxHumidity()));
        etPm25.setText(fmt(p.getMaxPm25()));
        etPm10.setText(fmt(p.getMaxPm10()));
        etInterval.setText(String.valueOf(p.getIntervalMinutes()));
        swNotifications.setChecked(p.notificationsEnabled());
        swDark.setChecked(p.isDark());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        tvAccount.setText(user != null && user.getEmail() != null
                ? "Signed in as " + user.getEmail() : "Guest mode (cloud sync disabled)");

        swNotifications.setOnCheckedChangeListener((b, on) -> Prefs.get(this).setNotificationsEnabled(on));
        swDark.setOnCheckedChangeListener((b, on) -> {
            Prefs.get(this).setDark(on);
            AppCompatDelegate.setDefaultNightMode(on
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnPermissions).setOnClickListener(v -> {
            String[] missing = PermissionHelper.missingRuntimePermissions(this);
            if (missing.length == 0) {
                Toast.makeText(this, "All permissions already granted.", Toast.LENGTH_SHORT).show();
            } else {
                permissionLauncher.launch(missing);
            }
        });
        findViewById(R.id.btnRestore).setOnClickListener(v ->
                FirebaseHelper.downloadAll(this, (ok, msg) ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()));
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        findViewById(R.id.btnSource).setOnClickListener(v -> {
            // Implicit intent: open the data provider's website in any browser
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com")));
        });
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
    }

    private static String fmt(float v) {
        return v == (long) v ? String.format(Locale.US, "%d", (long) v) : String.format(Locale.US, "%.1f", v);
    }

    private static String str(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void save() {
        try {
            float t = Float.parseFloat(str(etTemp));
            float h = Float.parseFloat(str(etHumidity));
            float p25 = Float.parseFloat(str(etPm25));
            float p10 = Float.parseFloat(str(etPm10));
            int interval = Integer.parseInt(str(etInterval));
            if (h <= 0 || h > 100) { etHumidity.setError("1 - 100"); return; }
            if (p25 <= 0) { etPm25.setError("Must be > 0"); return; }
            if (p10 <= 0) { etPm10.setError("Must be > 0"); return; }
            if (interval < 5) { etInterval.setError("Minimum 5 minutes"); return; }
            Prefs.get(this).saveThresholds(t, h, p25, p10, interval);
            Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please fill every field with valid numbers.", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear local history?")
                .setMessage("All locally stored readings will be deleted. Cloud copies are not affected.")
                .setPositiveButton("Delete", (d, w) -> AppDatabase.IO.execute(() -> {
                    AppDatabase.getInstance(getApplicationContext()).envLogDao().clearAll();
                    runOnUiThread(() -> Toast.makeText(this, "History cleared.", Toast.LENGTH_SHORT).show());
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logout() {
        stopService(new Intent(this, MonitoringService.class));
        Prefs prefs = Prefs.get(this);
        prefs.setMonitoring(false);
        prefs.setGuest(false);
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
