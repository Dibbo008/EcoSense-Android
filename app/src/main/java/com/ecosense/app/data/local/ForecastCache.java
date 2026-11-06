package com.ecosense.app.data.local;

import android.content.Context;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/** Keeps the last downloaded hourly forecast so the plan still works offline. */
public final class ForecastCache {
    private static final String FILE = "forecast_cache";
    private static final String KEY = "blob";
    private static final long MAX_AGE_MS = 12L * 3600_000L;

    public static class Blob {
        public long fetchedAt;
        public double lat;
        public double lon;
        public List<HourPoint> points = new ArrayList<>();
    }

    private ForecastCache() {}

    public static void save(Context c, Blob blob) {
        c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY, new Gson().toJson(blob)).apply();
    }

    /** Returns null when nothing is cached, the cache is corrupt, or it is older than 12 hours. */
    public static Blob read(Context c) {
        String json = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY, null);
        if (json == null) return null;
        try {
            Blob b = new Gson().fromJson(json, Blob.class);
            if (b == null || b.points == null || b.points.isEmpty()) return null;
            if (System.currentTimeMillis() - b.fetchedAt > MAX_AGE_MS) return null;
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
