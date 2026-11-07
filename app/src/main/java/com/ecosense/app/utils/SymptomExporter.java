package com.ecosense.app.utils;

import android.content.Context;

import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.SymptomEntry;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Builds a CSV a doctor can open: every symptom with the air quality at that moment. */
public final class SymptomExporter {
    private SymptomExporter() {}

    public static CsvExporter.Result export(Context context) throws IOException {
        List<SymptomEntry> list = AppDatabase.getInstance(context.getApplicationContext())
                .symptomDao().getAllDesc();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder("date,symptom,severity,note,us_aqi,pm25,temperature_c,humidity_pct\n");
        for (SymptomEntry e : list) {
            int type = SymptomTypes.clamp(e.type);
            sb.append(iso.format(new Date(e.timestamp))).append(',')
                    .append('"').append(SymptomTypes.NAMES[type]).append('"').append(',')
                    .append(SymptomTypes.SEVERITY[Math.max(0, Math.min(3, e.severity))]).append(',')
                    .append('"').append(e.note.replace("\"", "\"\"").replace("\n", " ")).append('"').append(',')
                    .append(e.aqi).append(',')
                    .append(String.format(Locale.US, "%.1f,%.1f,%.0f", e.pm25, e.temperature, e.humidity))
                    .append('\n');
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return CsvExporter.saveText(context, "ecosense_symptoms_" + stamp + ".csv", sb.toString());
    }
}
