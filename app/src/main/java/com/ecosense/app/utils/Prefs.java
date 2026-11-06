package com.ecosense.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Typed wrapper around SharedPreferences (thresholds, toggles, UI state). */
public final class Prefs {
    private static final String FILE = "ecosense_prefs";

    public static final float DEF_TEMP = 35f;
    public static final float DEF_HUMIDITY = 80f;
    public static final float DEF_PM25 = 35f;
    public static final float DEF_PM10 = 100f;
    public static final int DEF_INTERVAL_MIN = 15;

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Prefs get(Context c) {
        return new Prefs(c);
    }

    public float getMaxTemp() { return sp.getFloat("max_temp", DEF_TEMP); }
    public float getMaxHumidity() { return sp.getFloat("max_humidity", DEF_HUMIDITY); }
    public float getMaxPm25() { return sp.getFloat("max_pm25", DEF_PM25); }
    public float getMaxPm10() { return sp.getFloat("max_pm10", DEF_PM10); }
    public int getIntervalMinutes() { return sp.getInt("interval_min", DEF_INTERVAL_MIN); }

    public void saveThresholds(float temp, float hum, float pm25, float pm10, int intervalMin) {
        sp.edit().putFloat("max_temp", temp).putFloat("max_humidity", hum)
                .putFloat("max_pm25", pm25).putFloat("max_pm10", pm10)
                .putInt("interval_min", intervalMin).apply();
    }

    public boolean notificationsEnabled() { return sp.getBoolean("notifications", true); }
    public void setNotificationsEnabled(boolean v) { sp.edit().putBoolean("notifications", v).apply(); }

    public boolean isDark() { return sp.getBoolean("dark", false); }
    public void setDark(boolean v) { sp.edit().putBoolean("dark", v).apply(); }

    public boolean isMonitoring() { return sp.getBoolean("monitoring", false); }
    public void setMonitoring(boolean v) { sp.edit().putBoolean("monitoring", v).apply(); }

    public boolean isGuest() { return sp.getBoolean("guest", false); }
    public void setGuest(boolean v) { sp.edit().putBoolean("guest", v).apply(); }

    public long getLastViewed() { return sp.getLong("last_viewed", 0L); }
    public void setLastViewed(long ts) { sp.edit().putLong("last_viewed", ts).apply(); }

    // ---- Health profile (AdvisorEngine.PROFILE_NAMES index) ----
    public int getProfile() { return sp.getInt("profile", 0); }
    public void setProfile(int p) { sp.edit().putInt("profile", p).apply(); }

    // ---- Spike alert de-duplication ----
    public long getLastSpikeAt() { return sp.getLong("last_spike", 0L); }
    public void setLastSpikeAt(long ts) { sp.edit().putLong("last_spike", ts).apply(); }

    // ---- Last known location used for the data (shown on dashboard / prediction) ----
    public void saveLocation(double lat, double lon, String name, boolean isDefault) {
        sp.edit().putFloat("loc_lat", (float) lat).putFloat("loc_lon", (float) lon)
                .putString("loc_name", name).putBoolean("loc_default", isDefault).apply();
    }

    public boolean hasLocation() { return !getLocationName().isEmpty(); }
    public String getLocationName() { return sp.getString("loc_name", ""); }
    public boolean isLocationDefault() { return sp.getBoolean("loc_default", true); }
    public float getLocationLat() { return sp.getFloat("loc_lat", 0f); }
    public float getLocationLon() { return sp.getFloat("loc_lon", 0f); }

    /** Text for the UI, e.g. "Khulna, Bangladesh" or "Khulna, Bangladesh (default)". */
    public String getLocationDisplay() {
        if (!hasLocation()) return "Unknown";
        return getLocationName() + (isLocationDefault() ? " (default)" : "");
    }

    /** Label with pin icon for headers. */
    public String getLocationLabel() {
        if (!hasLocation()) return "\uD83D\uDCCD Location not detected yet";
        return "\uD83D\uDCCD " + getLocationName()
                + (isLocationDefault() ? " \u00B7 default location" : "");
    }
}
