package com.ecosense.app.utils;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.service.AlertReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compares readings against the user's limits and detects sudden spikes. */
public final class AlertChecker {
    private static final long SPIKE_COOLDOWN_MS = 3L * 3600_000L;

    private AlertChecker() {}

    public static boolean isUnsafe(Context c, EnvLog l) {
        Prefs p = Prefs.get(c);
        return l.temperature > p.getMaxTemp() || l.humidity > p.getMaxHumidity()
                || l.pm25 > p.getMaxPm25() || l.pm10 > p.getMaxPm10();
    }

    public static List<String> check(Context c, EnvLog l) {
        Prefs p = Prefs.get(c);
        List<String> out = new ArrayList<>();
        if (l.temperature > p.getMaxTemp()) {
            out.add(String.format(Locale.US, "Temperature %.1f\u00B0C is above %.0f\u00B0C", l.temperature, p.getMaxTemp()));
        }
        if (l.humidity > p.getMaxHumidity()) {
            out.add(String.format(Locale.US, "Humidity %.0f%% is above %.0f%%", l.humidity, p.getMaxHumidity()));
        }
        if (l.pm25 > p.getMaxPm25()) {
            out.add(String.format(Locale.US, "PM2.5 %.1f is above %.0f \u00B5g/m\u00B3", l.pm25, p.getMaxPm25()));
        }
        if (l.pm10 > p.getMaxPm10()) {
            out.add(String.format(Locale.US, "PM10 %.1f is above %.0f \u00B5g/m\u00B3", l.pm10, p.getMaxPm10()));
        }
        return out;
    }

    /**
     * Sends notifications for exceeded limits and for sudden spikes.
     * Does database work, so call it from a background thread only.
     */
    public static void dispatch(Context c, EnvLog l) {
        Prefs p = Prefs.get(c);
        if (!p.notificationsEnabled()) return;

        List<String> alerts = check(c, l);
        if (!alerts.isEmpty()) {
            send(c, "EcoSense alert: unsafe conditions", TextUtils.join("\n", alerts),
                    NotificationHelper.ID_ALERT);
        }

        try {
            List<EnvLog> recent = AppDatabase.getInstance(c).envLogDao().getRecent(15);
            String spike = SpikeDetector.detect(recent, p.getProfile());
            long now = System.currentTimeMillis();
            if (spike != null && now - p.getLastSpikeAt() > SPIKE_COOLDOWN_MS) {
                p.setLastSpikeAt(now);
                send(c, "Sudden change detected", spike, NotificationHelper.ID_SPIKE);
            }
        } catch (Exception ignored) {
            // Spike detection is best-effort and must never break the main flow
        }
    }

    private static void send(Context c, String title, String message, int id) {
        Intent i = new Intent(c, AlertReceiver.class);
        i.setAction(AlertReceiver.ACTION_ALERT);
        i.putExtra(AlertReceiver.EXTRA_MESSAGE, message);
        i.putExtra(AlertReceiver.EXTRA_TITLE, title);
        i.putExtra(AlertReceiver.EXTRA_ID, id);
        c.sendBroadcast(i);
    }
}
