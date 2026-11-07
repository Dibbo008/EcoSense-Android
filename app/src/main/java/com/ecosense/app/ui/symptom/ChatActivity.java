package com.ecosense.app.ui.symptom;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ecosense.app.R;
import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.SymptomRepository;
import com.ecosense.app.data.remote.ChatRepository;
import com.ecosense.app.utils.AdvisorEngine;
import com.ecosense.app.utils.CorrelationEngine;
import com.ecosense.app.utils.Prefs;
import com.ecosense.app.utils.SymptomAdvisor;
import com.ecosense.app.utils.SymptomTypes;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Minimal symptom chat. The panel at the bottom walks through: symptoms -> strength -> duration
 * -> notes, then EcoSense replies with what may be happening, why, and what to do.
 * Messages are stored in Firebase Realtime Database (local fallback for guests).
 */
public class ChatActivity extends AppCompatActivity {
    private static final int ST_SYMPTOMS = 0, ST_SEVERITY = 1, ST_DURATION = 2, ST_NOTE = 3, ST_DONE = 4;

    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private TextView tvPrompt, tvMode, tvChatEmpty;
    private android.widget.LinearLayout llQuick;
    private View rowInput;
    private TextInputEditText etMsg;
    private MaterialCheckBox cbDiary;
    private ChatRepository repo;

    private int stage = ST_SYMPTOMS;
    private final TreeSet<Integer> picked = new TreeSet<>();
    private int severity = 1;
    private int duration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        rvChat = findViewById(R.id.rvChat);
        tvPrompt = findViewById(R.id.tvPrompt);
        tvMode = findViewById(R.id.tvMode);
        tvChatEmpty = findViewById(R.id.tvChatEmpty);
        llQuick = findViewById(R.id.llQuick);
        rowInput = findViewById(R.id.rowInput);
        etMsg = findViewById(R.id.etMsg);
        cbDiary = findViewById(R.id.cbDiary);

        adapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);
        rvChat.setAdapter(adapter);

        findViewById(R.id.btnClearChat).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Clear chat history?")
                .setMessage("This removes your saved advice messages.")
                .setPositiveButton("Clear", (d, w) -> repo.clear())
                .setNegativeButton("Cancel", null)
                .show());
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String text = etMsg.getText() == null ? "" : etMsg.getText().toString();
            etMsg.setText("");
            finishCheck(text);
        });

        repo = new ChatRepository(new ChatRepository.Listener() {
            @Override
            public void onAdded(ChatRepository.Message m) {
                if (adapter.add(m) >= 0) {
                    tvChatEmpty.setVisibility(View.GONE);
                    rvChat.scrollToPosition(adapter.size() - 1);
                }
            }

            @Override
            public void onRemoved(String id) {
                adapter.remove(id);
                tvChatEmpty.setVisibility(adapter.size() == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCleared() {
                adapter.clear();
                tvChatEmpty.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        tvMode.setText(repo.isCloud()
                ? "\u2601 Synced with Firebase Realtime Database"
                : "\uD83D\uDCF1 Local chat. Sign in to save it to the cloud.");
        showStage();
    }

    @Override
    protected void onStart() {
        super.onStart();
        adapter.clear();
        tvChatEmpty.setVisibility(View.VISIBLE);
        repo.start();
    }

    @Override
    protected void onStop() {
        repo.stop();
        super.onStop();
    }

    // ------------------------------------------------------------ guided flow

    private void showStage() {
        llQuick.removeAllViews();
        rowInput.setVisibility(View.GONE);
        switch (stage) {
            case ST_SYMPTOMS:
                tvPrompt.setText("What are you feeling? Select all that apply.");
                for (int i = 0; i < SymptomTypes.FINE; i++) {
                    final int type = i;
                    Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chat_choice, llQuick, false);
                    chip.setText(SymptomTypes.EMOJI[i] + " " + SymptomTypes.SHORT[i]);
                    chip.setChecked(picked.contains(type));
                    chip.setOnCheckedChangeListener((b, checked) -> {
                        if (checked) picked.add(type);
                        else picked.remove(type);
                    });
                    llQuick.addView(chip);
                }
                addAction("Next \u203A", () -> {
                    if (picked.isEmpty()) {
                        Toast.makeText(this, "Pick at least one symptom.", Toast.LENGTH_SHORT).show();
                    } else {
                        stage = ST_SEVERITY;
                        showStage();
                    }
                });
                break;
            case ST_SEVERITY:
                tvPrompt.setText("How strong is it overall?");
                addAction("Mild", () -> chooseSeverity(1));
                addAction("Moderate", () -> chooseSeverity(2));
                addAction("Severe", () -> chooseSeverity(3));
                break;
            case ST_DURATION:
                tvPrompt.setText("How long has it been going on?");
                addAction("Just today", () -> chooseDuration(0));
                addAction("2\u20133 days", () -> chooseDuration(1));
                addAction("4+ days", () -> chooseDuration(2));
                break;
            case ST_NOTE:
                tvPrompt.setText("Anything else? When it started, fever, what you were doing\u2026");
                rowInput.setVisibility(View.VISIBLE);
                addAction("Skip", () -> finishCheck(""));
                break;
            default:
                tvPrompt.setText("Want to check something else?");
                addAction("New check", () -> {
                    picked.clear();
                    stage = ST_SYMPTOMS;
                    showStage();
                });
                break;
        }
    }

    private void addAction(String label, Runnable onClick) {
        MaterialButton b = (MaterialButton) getLayoutInflater().inflate(R.layout.item_chat_action, llQuick, false);
        b.setText(label);
        b.setOnClickListener(v -> onClick.run());
        llQuick.addView(b);
    }

    private void chooseSeverity(int s) {
        severity = s;
        stage = ST_DURATION;
        showStage();
    }

    private void chooseDuration(int d) {
        duration = d;
        stage = ST_NOTE;
        showStage();
    }

    private void finishCheck(String rawNote) {
        final String note = rawNote == null ? "" : rawNote.trim();
        final List<Integer> types = new ArrayList<>(picked);
        final int sev = severity;
        final int dur = duration;
        final boolean saveDiary = cbDiary.isChecked();
        final Context app = getApplicationContext();

        llQuick.removeAllViews();
        rowInput.setVisibility(View.GONE);
        tvPrompt.setText("Thinking\u2026");

        // 1) the user's message: the detailed list they chose
        repo.send(true, summary(types, sev, dur, note));

        // 2) analysis off the main thread, then the reply
        AppDatabase.IO.execute(() -> {
            if (saveDiary) {
                for (int t : types) SymptomRepository.log(app, t, sev, note);
            }
            EnvLog env = AppDatabase.getInstance(app).envLogDao().getLatest();
            CorrelationEngine.Report report = SymptomRepository.analyze(app);

            SymptomAdvisor.Input in = new SymptomAdvisor.Input();
            in.symptoms.addAll(types);
            in.severity = sev;
            in.duration = dur;
            in.note = note;
            in.env = env;
            in.profile = AdvisorEngine.clampProfile(Prefs.get(app).getProfile());
            for (CorrelationEngine.Insight i : report.insights) {
                if (types.contains(i.symptom)) in.personal.add(i.sentence());
            }
            for (CorrelationEngine.EarlySignal e : report.earlySignals) {
                if (types.contains(e.symptom)) in.personal.add("(early signal) " + e.sentence());
            }
            final String reply = SymptomAdvisor.advise(in);

            runOnUiThread(() -> {
                if (isDestroyed()) return;
                repo.send(false, reply);
                stage = ST_DONE;
                showStage();
            });
        });
    }

    private static String summary(List<Integer> types, int sev, int dur, String note) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            int t = types.get(i);
            if (i > 0) sb.append(", ");
            sb.append(SymptomTypes.EMOJI[t]).append(' ').append(SymptomTypes.NAMES[t]);
        }
        sb.append("\nStrength: ").append(SymptomTypes.SEVERITY[Math.max(1, Math.min(3, sev))])
                .append(" \u00B7 Duration: ").append(SymptomAdvisor.DURATION[Math.max(0, Math.min(2, dur))]);
        if (!note.isEmpty()) sb.append("\nNote: ").append(note);
        return sb.toString();
    }
}
