package com.ecosense.app.utils;

import com.ecosense.app.data.local.EnvLog;

import java.util.List;
import java.util.Locale;

/**
 * Detects sudden changes instead of fixed limits: compares the newest reading with the
 * average of readings from 20-120 minutes earlier. Sensitive profiles trigger earlier.
 */
public final class SpikeDetector {
    private static final long MIN_AGE = 20L * 60_000L;
    private static final long MAX_AGE = 120L * 60_000L;

    private SpikeDetector() {}

    /** @param recentDesc newest first. @return message, or null when nothing unusual. */
    public static String detect(List<EnvLog> recentDesc, int profile) {
        if (recentDesc == null || recentDesc.size() < 2) return null;
        EnvLog now = recentDesc.get(0);

        double sum25 = 0, sum10 = 0, sumT = 0;
        int n = 0;
        for (int i = 1; i < recentDesc.size(); i++) {
            EnvLog r = recentDesc.get(i);
            long age = now.timestamp - r.timestamp;
            if (age < MIN_AGE) continue;
            if (age > MAX_AGE) break; // list is newest-first, so everything after is older
            sum25 += r.pm25;
            sum10 += r.pm10;
            sumT += r.temperature;
            n++;
        }
        if (n == 0) return null;
        double base25 = sum25 / n, base10 = sum10 / n, baseT = sumT / n;

        double f = AdvisorEngine.factor(profile);
        double ratio = 1 + 0.8 / f;       // normal 1.8x, sensitive ~1.57x
        double minDelta25 = 15 / f;       // ug/m3
        double minDelta10 = 30 / f;

        if (now.pm25 - base25 >= minDelta25 && now.pm25 >= base25 * ratio) {
            return String.format(Locale.US, "PM2.5 jumped %.1fx in the last hour or so (%.0f \u2192 %.0f \u00B5g/m\u00B3).",
                    now.pm25 / Math.max(base25, 1), base25, now.pm25);
        }
        if (now.pm10 - base10 >= minDelta10 && now.pm10 >= base10 * ratio) {
            return String.format(Locale.US, "PM10 jumped %.1fx in the last hour or so (%.0f \u2192 %.0f \u00B5g/m\u00B3).",
                    now.pm10 / Math.max(base10, 1), base10, now.pm10);
        }
        double dT = now.temperature - baseT;
        if (Math.abs(dT) >= 6) {
            return String.format(Locale.US, "Temperature %s %.0f\u00B0C quickly (%.0f \u2192 %.0f\u00B0C).",
                    dT > 0 ? "rose" : "dropped", Math.abs(dT), baseT, now.temperature);
        }
        return null;
    }
}
