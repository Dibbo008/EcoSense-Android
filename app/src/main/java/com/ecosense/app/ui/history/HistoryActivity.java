package com.ecosense.app.ui.history;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.remote.FirebaseHelper;
import com.ecosense.app.utils.AlertChecker;
import com.ecosense.app.utils.CsvExporter;
import com.ecosense.app.utils.LogFormatter;
import com.ecosense.app.utils.PermissionHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private static final int MODE_LIST = 0, MODE_CARDS = 1, MODE_GRID = 2;

    private View root;
    private ListView listView;
    private RecyclerView recycler;
    private TextView tvEmpty;
    private EditText etSearch;
    private Spinner spFilter;
    private LogListAdapter listAdapter;
    private LogRecyclerAdapter recyclerAdapter;
    private final List<EnvLog> all = new ArrayList<>();

    private final ActivityResultLauncher<String> storageLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) exportCsv();
                else Toast.makeText(this, "Storage permission is required to export on this Android version.",
                        Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        root = findViewById(R.id.root);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        listView = findViewById(R.id.listView);
        recycler = findViewById(R.id.recycler);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        spFilter = findViewById(R.id.spFilter);

        listAdapter = new LogListAdapter(this);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((p, v, pos, id) -> {
            EnvLog l = listAdapter.getItem(pos);
            if (l != null) showDetails(l);
        });

        recyclerAdapter = new LogRecyclerAdapter(this, this::showDetails);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setAdapter(recyclerAdapter);

        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"All readings", "Unsafe only", "Safe only"});
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilter.setAdapter(filterAdapter);
        spFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleMode);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnModeList) setMode(MODE_LIST);
            else if (checkedId == R.id.btnModeCards) setMode(MODE_CARDS);
            else setMode(MODE_GRID);
        });
        toggle.check(R.id.btnModeList);

        findViewById(R.id.btnExport).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btnSync).setOnClickListener(v ->
                FirebaseHelper.uploadUnsynced(this, (ok, msg) ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()));

        // Room LiveData: UI updates automatically when new logs arrive
        AppDatabase.getInstance(this).envLogDao().observeAll().observe(this, logs -> {
            all.clear();
            if (logs != null) all.addAll(logs);
            applyFilter();
        });
    }

    private void setMode(int mode) {
        boolean list = mode == MODE_LIST;
        listView.setVisibility(list ? View.VISIBLE : View.GONE);
        recycler.setVisibility(list ? View.GONE : View.VISIBLE);
        if (!list) {
            boolean grid = mode == MODE_GRID;
            recyclerAdapter.setGrid(grid);
            recycler.setLayoutManager(grid ? new GridLayoutManager(this, 2) : new LinearLayoutManager(this));
        }
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        int f = spFilter.getSelectedItemPosition();
        List<EnvLog> out = new ArrayList<>();
        for (EnvLog l : all) {
            boolean unsafe = AlertChecker.isUnsafe(this, l);
            if (f == 1 && !unsafe) continue;
            if (f == 2 && unsafe) continue;
            if (!q.isEmpty() && !LogFormatter.searchText(l).contains(q)) continue;
            out.add(l);
        }
        listAdapter.clear();
        listAdapter.addAll(out);
        recyclerAdapter.submitList(out);
        tvEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDetails(EnvLog l) {
        List<String> alerts = AlertChecker.check(this, l);
        String msg = String.format(Locale.US,
                "Temperature: %.1f\u00B0C\nHumidity: %.0f%%\nPM2.5: %.1f \u00B5g/m\u00B3\nPM10: %.1f \u00B5g/m\u00B3\nAQI: %d (%s)\nSource: %s\nCloud synced: %s",
                l.temperature, l.humidity, l.pm25, l.pm10, l.aqi, LogFormatter.aqiLabel(l.aqi),
                l.source, l.synced ? "yes" : "no");
        if (!alerts.isEmpty()) msg += "\n\nWarnings:\n" + android.text.TextUtils.join("\n", alerts);
        new MaterialAlertDialogBuilder(this)
                .setTitle(LogFormatter.date(l.timestamp))
                .setMessage(msg)
                .setPositiveButton("Close", null)
                .show();
    }

    private void exportCsv() {
        if (Build.VERSION.SDK_INT <= 28 && !PermissionHelper.hasLegacyStorage(this)) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        AppDatabase.IO.execute(() -> {
            try {
                CsvExporter.Result r = CsvExporter.export(getApplicationContext());
                runOnUiThread(() -> onExported(r));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void onExported(CsvExporter.Result r) {
        Snackbar sb = Snackbar.make(root, "Saved to " + r.displayPath, Snackbar.LENGTH_LONG);
        if (r.uri != null) sb.setAction("Share", v -> shareCsv(r.uri));
        sb.show();
    }

    /** Implicit intent: share the exported CSV with any capable app. */
    private void shareCsv(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share CSV"));
    }
}
