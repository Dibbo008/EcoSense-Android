package com.ecosense.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One environmental reading stored for offline use. */
@Entity(tableName = "env_logs")
public class EnvLog {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long timestamp;       // epoch millis
    public double temperature;   // Celsius
    public double humidity;      // percent
    public double pm25;          // ug/m3
    public double pm10;          // ug/m3
    public int aqi;              // US AQI
    public boolean synced;       // uploaded to Firestore?
    @NonNull
    public String source = "Open-Meteo";
}
