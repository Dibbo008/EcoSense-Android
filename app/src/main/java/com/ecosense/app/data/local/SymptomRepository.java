package com.ecosense.app.data.local;

import android.content.Context;

import com.ecosense.app.utils.CorrelationEngine;

import java.util.ArrayList;
import java.util.List;

/** Blocking helpers around the diary. Background thread only. */
public final class SymptomRepository {
    private static final long DAY = 24L * 3_600_000L;

    private SymptomRepository() {}

    /** Saves a symptom with a snapshot of the latest reading. */
    public static SymptomEntry log(Context context, int type, int severity, String note) {
        Context app = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(app);
        SymptomEntry e = new SymptomEntry();
        e.timestamp = System.currentTimeMillis();
        e.type = type;
        e.severity = severity;
        e.note = note == null ? "" : note.trim();
        EnvLog latest = db.envLogDao().getLatest();
        if (latest != null) {
            e.aqi = latest.aqi;
            e.pm25 = latest.pm25;
            e.temperature = latest.temperature;
            e.humidity = latest.humidity;
        }
        e.id = db.symptomDao().insert(e);
        return e;
    }

    public static CorrelationEngine.Report analyze(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        List<SymptomEntry> entries = db.symptomDao().getAllAsc();
        if (entries.isEmpty()) return CorrelationEngine.analyze(entries, new ArrayList<EnvLog>());
        List<EnvLog> readings = db.envLogDao().since(entries.get(0).timestamp - DAY);
        return CorrelationEngine.analyze(entries, readings);
    }

    /** Warnings for today based on the user's own trigger levels. */
    public static List<String> risksToday(Context context, EnvLog now, List<HourPoint> forecast) {
        CorrelationEngine.Report r = analyze(context);
        return CorrelationEngine.todayRisks(r.insights, now, forecast, System.currentTimeMillis());
    }
}
