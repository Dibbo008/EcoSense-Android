package com.ecosense.app.ui.symptom;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.ForecastCache;
import com.ecosense.app.data.local.SymptomEntry;
import com.ecosense.app.data.local.SymptomRepository;
import com.ecosense.app.utils.CorrelationEngine;
import com.ecosense.app.utils.CsvExporter;
import com.ecosense.app.utils.LogFormatter;
import com.ecosense.app.utils.NavHelper;
import com.ecosense.app.utils.PermissionHelper;
import com.ecosense.app.utils.SymptomExporter;
import com.ecosense.app.utils.SymptomTypes;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

/** Symptom diary + personal pattern finder (CorrelationEngine). */
public class SymptomActivity extends AppCompatActivity {
    private static final int MAX_SHOWN = 10;
    private static final int[] SYMPTOM_CHIPS = {R.id.chipS0, R.id.chipS1, R.id.chipS2, R.id.chipS3,
            R.id.chipS4, R.id.chipS5, R.id.chipS6, R.id.chipS7};

    private View root, llSeverity, cardRisk;
    private ChipGroup chipSymptom, chipSeverity;
    private TextInputEditText etNote;
    private MaterialButton btnLog;
    private TextView tvUnlock, tvRisks, tvInsightNote, tvEntriesEmpty;
    private LinearProgressIndicator pbUnlock;
    private LinearLayout llInsights, llEntries;

    private final ActivityResultLauncher<String> storageLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) exportReport();
                else Toast.makeText(this, "Storage permission is required to export on this Android version.",
                        Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_symptom);

        root = findViewById(R.id.root);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        NavHelper.setup(this, NavHelper.SYMPTOM);

        chipSymptom = findViewById(R.id.chipSymptom);
        chipSeverity = findViewById(R.id.chipSeverity);
        llSeverity = findViewById(R.id.llSeverity);
        etNote = findViewById(R.id.etNote);
        btnLog = findViewById(R.id.btnLog);
        tvUnlock = findViewById(R.id.tvUnlock);
        pbUnlock = findViewById(R.id.pbUnlock);
        cardRisk = findViewById(R.id.cardRisk);
        tvRisks = findViewById(R.id.tvRisks);
        llInsights = findViewById(R.id.llInsights);
        tvInsightNote = findViewById(R.id.tvInsightNote);
        llEntries = findViewById(R.id.llEntries);
        tvEntriesEmpty = findViewById(R.id.tvEntriesEmpty);

        pbUnlock.setMax(CorrelationEngine.MIN_OBSERVED_DAYS);
        chipSeverity.check(R.id.chipSev1);
        // "Feeling fine" has no severity
        chipSymptom.setOnCheckedStateChangeListener((group, ids) ->
                llSeverity.setVisibility(selectedType() == SymptomTypes.FINE ? View.GONE : View.VISIBLE));

        btnLog.setOnClickListener(v -> save());
        findViewById(R.id.btnExportSymptoms).setOnClickListener(v -> exportReport());

        // Any change in the diary refreshes the list and the analysis
        AppDatabase.getInstance(this).symptomDao().observeAll().observe(this, list -> {
            renderEntries(list);
            refreshInsights();
        });
    }

    private int selectedType() {
        int id = chipSymptom.getCheckedChipId();
        for (int i = 0; i < SYMPTOM_CHIPS.length; i++) {
            if (SYMPTOM_CHIPS[i] == id) return i;
        }
        return -1;
    }

    private int selectedSeverity() {
        int id = chipSeverity.getCheckedChipId();
        return id == R.id.chipSev3 ? 3 : id == R.id.chipSev2 ? 2 : 1;
    }

    private void save() {
        final int type = selectedType();
        if (type < 0) {
            Toast.makeText(this, "Pick how you feel first.", Toast.LENGTH_SHORT).show();
            return;
        }
        final int severity = type == SymptomTypes.FINE ? 0 : selectedSeverity();
        final String note = etNote.getText() == null ? "" : etNote.getText().toString();
        final Context app = getApplicationContext();
        btnLog.setEnabled(false);
        AppDatabase.IO.execute(() -> {
            SymptomRepository.log(app, type, severity, note);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                btnLog.setEnabled(true);
                chipSymptom.clearCheck();
                etNote.setText("");
                Toast.makeText(this, "Saved \u2713", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ------------------------------------------------------------------ entries

    private void renderEntries(List<SymptomEntry> list) {
        llEntries.removeAllViews();
        boolean empty = list == null || list.isEmpty();
        tvEntriesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        for (int i = 0; i < Math.min(MAX_SHOWN, list.size()); i++) {
            final SymptomEntry e = list.get(i);
            int type = SymptomTypes.clamp(e.type);
            View row = getLayoutInflater().inflate(R.layout.item_symptom, llEntries, false);
            ((TextView) row.findViewById(R.id.tvSEmoji)).setText(SymptomTypes.EMOJI[type]);
            String title = SymptomTypes.NAMES[type];
            if (e.severity > 0) title += " \u00B7 " + SymptomTypes.SEVERITY[Math.min(3, e.severity)];
            ((TextView) row.findViewById(R.id.tvSTitle)).setText(title);
            StringBuilder meta = new StringBuilder(LogFormatter.date(e.timestamp));
            if (e.aqi > 0) meta.append(" \u00B7 AQI ").append(e.aqi);
            if (!e.note.isEmpty()) meta.append(" \u00B7 ").append(e.note);
            ((TextView) row.findViewById(R.id.tvSMeta)).setText(meta.toString());
            row.findViewById(R.id.ivSDelete).setOnClickListener(v -> confirmDelete(e));
            llEntries.addView(row);
        }
    }

    private void confirmDelete(SymptomEntry e) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this entry?")
                .setMessage(SymptomTypes.NAMES[SymptomTypes.clamp(e.type)] + " \u00B7 " + LogFormatter.date(e.timestamp))
                .setPositiveButton("Delete", (d, w) -> AppDatabase.IO.execute(() ->
                        AppDatabase.getInstance(getApplicationContext()).symptomDao().deleteById(e.id)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- insights

    private void refreshInsights() {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            CorrelationEngine.Report report = SymptomRepository.analyze(app);
            EnvLog latest = AppDatabase.getInstance(app).envLogDao().getLatest();
            ForecastCache.Blob cache = ForecastCache.read(app);
            List<String> risks = CorrelationEngine.todayRisks(report.insights, latest,
                    cache != null ? cache.points : null, System.currentTimeMillis());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                renderInsights(report, risks);
            });
        });
    }

    private void renderInsights(CorrelationEngine.Report report, List<String> risks) {
        int min = CorrelationEngine.MIN_OBSERVED_DAYS;
        pbUnlock.setProgressCompat(Math.min(min, report.observedDays), true);
        if (report.daysToUnlock > 0) {
            tvUnlock.setText(String.format(Locale.US,
                    "Patterns unlock after %d logged days \u00B7 %d / %d so far", min, report.observedDays, min));
        } else {
            tvUnlock.setText(String.format(Locale.US,
                    "Pattern analysis active \u00B7 %d days analysed", report.observedDays));
        }

        cardRisk.setVisibility(risks.isEmpty() ? View.GONE : View.VISIBLE);
        tvRisks.setText(android.text.TextUtils.join("\n\n", risks));

        llInsights.removeAllViews();
        for (CorrelationEngine.Insight in : report.insights) {
            View row = getLayoutInflater().inflate(R.layout.item_insight, llInsights, false);
            ((TextView) row.findViewById(R.id.tvIEmoji)).setText(SymptomTypes.EMOJI[in.symptom]);
            ((TextView) row.findViewById(R.id.tvIText)).setText(in.sentence());
            TextView conf = row.findViewById(R.id.tvIConf);
            conf.setText(in.confidence + " pattern");
            llInsights.addView(row);
        }

        if (report.daysToUnlock > 0) {
            tvInsightNote.setText(String.format(Locale.US,
                    "Log how you feel for %d more day%s (\"Feeling fine\" counts too) and keep EcoSense collecting air data. "
                            + "A day only counts when it has at least 2 air readings.",
                    report.daysToUnlock, report.daysToUnlock == 1 ? "" : "s"));
        } else if (report.insights.isEmpty()) {
            tvInsightNote.setText("No clear link between your symptoms and the air or weather yet. "
                    + "That can be good news, or you may need more days of data.");
        } else {
            tvInsightNote.setText("Patterns are found by comparing your symptom days with high and low exposure days.");
        }
    }

    // ------------------------------------------------------------------ export

    private void exportReport() {
        if (Build.VERSION.SDK_INT <= 28 && !PermissionHelper.hasLegacyStorage(this)) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                CsvExporter.Result r = SymptomExporter.export(app);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    Snackbar sb = Snackbar.make(root, "Saved to " + r.displayPath, Snackbar.LENGTH_LONG);
                    if (r.uri != null) sb.setAction("Share", v -> share(r.uri));
                    sb.show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Implicit intent: send the report to any app (email, drive, ...). */
    private void share(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share symptom report"));
    }
}
