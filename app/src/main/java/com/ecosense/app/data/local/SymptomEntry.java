package com.ecosense.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One symptom diary entry, with a snapshot of the air at the moment it was logged. */
@Entity(tableName = "symptom_entries")
public class SymptomEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long timestamp;      // epoch millis
    public int type;            // index into SymptomTypes
    public int severity;        // 1 mild, 2 moderate, 3 severe (0 for "feeling fine")
    @NonNull
    public String note = "";
    public int aqi;             // snapshot (0 if no reading was available)
    public double pm25;
    public double temperature;
    public double humidity;
}
