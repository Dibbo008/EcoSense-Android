package com.ecosense.app.utils;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.service.AlertReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compares a reading against the user's thresholds. */
public final class AlertChecker {
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

    /** Sends an explicit broadcast to AlertReceiver when any limit is exceeded. */
    public static void dispatch(Context c, EnvLog l) {
        if (!Prefs.get(c).notificationsEnabled()) return;
        List<String> alerts = check(c, l);
        if (alerts.isEmpty()) return;
        Intent i = new Intent(c, AlertReceiver.class);
        i.setAction(AlertReceiver.ACTION_ALERT);
        i.putExtra(AlertReceiver.EXTRA_MESSAGE, TextUtils.join("\n", alerts));
        c.sendBroadcast(i);
    }
}
