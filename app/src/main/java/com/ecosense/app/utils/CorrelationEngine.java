package com.ecosense.app.utils;

import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.HourPoint;
import com.ecosense.app.data.local.SymptomEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds personal triggers: which air/weather conditions go with a user's symptoms.
 *
 * Method (all pure Java, no libraries):
 *  1. Group diary entries and sensor readings by calendar day. A day is "observed" when the user
 *     logged anything that day (a "Feeling fine" entry counts) and at least 2 readings exist.
 *  2. For each symptom and each factor, split observed days at the factor's median into high/low.
 *  3. Compare symptom rates (risk ratio), measure Pearson correlation with severity, and test the
 *     split with a one-sided Fisher exact test so tiny samples do not produce fake "patterns".
 *  4. Keep only the best factor per symptom (limits false positives from testing many factors).
 *
 * This finds correlation, not causation, and the UI says so.
 */
public final class CorrelationEngine {
    public static final int MIN_OBSERVED_DAYS = 7;
    public static final int MIN_SYMPTOM_DAYS = 3;
    private static final double MIN_RISK_RATIO = 1.5;
    private static final double MAX_P = 0.20;
    private static final double MIN_R = 0.20;

    public static final int F_PM25 = 0, F_PM10 = 1, F_TEMP = 2, F_HUM = 3, F_AQI = 4;
    public static final String[] FACTOR_NAMES = {"PM2.5", "PM10", "Temperature", "Humidity", "Air quality (AQI)"};
    public static final String[] FACTOR_UNITS = {" \u00B5g/m\u00B3", " \u00B5g/m\u00B3", "\u00B0C", "%", ""};

    public static final class Insight {
        public int symptom, factor;
        public double threshold;       // "high" means value >= threshold
        public double riskRatio, pValue, r;
        public int symptomDaysHigh, nHigh, symptomDaysLow, nLow;
        public String confidence;      // Strong / Likely / Possible

        public String sentence() {
            return String.format(Locale.US,
                    "%s is %.1f\u00D7 more likely on days when %s is %s%s (%d of %d such days, versus %d of %d other days).",
                    SymptomTypes.NAMES[symptom], riskRatio, FACTOR_NAMES[factor],
                    fmt(factor, threshold), FACTOR_UNITS[factor] + " or higher",
                    symptomDaysHigh, nHigh, symptomDaysLow, nLow);
        }
    }

    public static final class Report {
        public int observedDays;
        public int entryCount;
        public int daysToUnlock;       // >0 while there is not enough data
        public final List<Insight> insights = new ArrayList<>();
    }

    private static final class Day {
        final double[] sum = new double[5];
        double maxTemp = -1000;
        int readings;
        final int[] severity = new int[SymptomTypes.COUNT];
        boolean observed;
        double[] exposure;
    }

    private CorrelationEngine() {}

    // -------------------------------------------------------------------- analysis

    public static Report analyze(List<SymptomEntry> entries, List<EnvLog> readings) {
        Report report = new Report();
        if (entries == null || entries.isEmpty()) {
            report.daysToUnlock = MIN_OBSERVED_DAYS;
            return report;
        }
        report.entryCount = entries.size();

        Map<Integer, Day> days = new HashMap<>();
        for (SymptomEntry e : entries) {
            Day d = days.get(dayKey(e.timestamp));
            if (d == null) {
                d = new Day();
                days.put(dayKey(e.timestamp), d);
            }
            d.observed = true;
            int type = SymptomTypes.clamp(e.type);
            if (type != SymptomTypes.FINE) d.severity[type] = Math.max(d.severity[type], Math.max(1, e.severity));
        }
        if (readings != null) {
            for (EnvLog l : readings) {
                Day d = days.get(dayKey(l.timestamp));
                if (d == null) continue;
                d.sum[F_PM25] += l.pm25;
                d.sum[F_PM10] += l.pm10;
                d.sum[F_HUM] += l.humidity;
                d.sum[F_AQI] += l.aqi;
                d.maxTemp = Math.max(d.maxTemp, l.temperature);
                d.readings++;
            }
        }

        List<Day> usable = new ArrayList<>();
        for (Day d : days.values()) {
            if (!d.observed || d.readings < 2) continue;
            d.exposure = new double[]{
                    d.sum[F_PM25] / d.readings, d.sum[F_PM10] / d.readings, d.maxTemp,
                    d.sum[F_HUM] / d.readings, d.sum[F_AQI] / d.readings};
            usable.add(d);
        }
        report.observedDays = usable.size();
        if (usable.size() < MIN_OBSERVED_DAYS) {
            report.daysToUnlock = MIN_OBSERVED_DAYS - usable.size();
            return report;
        }

        int n = usable.size();
        for (int s = 0; s < SymptomTypes.FINE; s++) {
            int symptomDays = 0;
            for (Day d : usable) if (d.severity[s] > 0) symptomDays++;
            if (symptomDays < MIN_SYMPTOM_DAYS || n - symptomDays < 2) continue;

            Insight best = null;
            for (int f = 0; f < 5; f++) {
                Insight cand = evaluate(usable, s, f, symptomDays);
                if (cand == null) continue;
                if (best == null || cand.pValue < best.pValue
                        || (cand.pValue == best.pValue && cand.riskRatio > best.riskRatio)) {
                    best = cand;
                }
            }
            if (best != null) report.insights.add(best);
        }
        Collections.sort(report.insights, new Comparator<Insight>() {
            @Override
            public int compare(Insight a, Insight b) {
                return Double.compare(a.pValue, b.pValue);
            }
        });
        return report;
    }

    private static Insight evaluate(List<Day> days, int symptom, int factor, int symptomDays) {
        int n = days.size();
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = days.get(i).exposure[factor];
        double[] sorted = v.clone();
        Arrays.sort(sorted);
        if (sorted[n - 1] - sorted[0] < 1e-9) return null; // no variation at all
        double threshold = sorted[n / 2];

        int nHigh = 0, nLow = 0, aHigh = 0, aLow = 0;
        for (int i = 0; i < n; i++) {
            boolean has = days.get(i).severity[symptom] > 0;
            if (v[i] >= threshold) {
                nHigh++;
                if (has) aHigh++;
            } else {
                nLow++;
                if (has) aLow++;
            }
        }
        if (nHigh == 0 || nLow == 0) return null;

        double rateHigh = (aHigh + 0.5) / (nHigh + 1.0);
        double rateLow = (aLow + 0.5) / (nLow + 1.0);
        double rr = rateHigh / rateLow;
        double p = fisherUpper(n, symptomDays, nHigh, aHigh);
        double r = pearson(v, days, symptom);

        if (rr < MIN_RISK_RATIO || p > MAX_P || r < MIN_R) return null;

        Insight in = new Insight();
        in.symptom = symptom;
        in.factor = factor;
        in.threshold = threshold;
        in.riskRatio = rr;
        in.pValue = p;
        in.r = r;
        in.symptomDaysHigh = aHigh;
        in.nHigh = nHigh;
        in.symptomDaysLow = aLow;
        in.nLow = nLow;
        in.confidence = p <= 0.05 ? "Strong" : p <= 0.10 ? "Likely" : "Possible";
        return in;
    }

    private static double pearson(double[] x, List<Day> days, int symptom) {
        int n = x.length;
        double mx = 0, my = 0;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = days.get(i).severity[symptom];
            mx += x[i];
            my += y[i];
        }
        mx /= n;
        my /= n;
        double sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
            sxy += (x[i] - mx) * (y[i] - my);
        }
        if (sxx < 1e-12 || syy < 1e-12) return 0;
        return sxy / Math.sqrt(sxx * syy);
    }

    /**
     * One-sided Fisher exact test: probability of seeing at least {@code a} symptom days among the
     * {@code nHigh} high-exposure days if symptoms were spread randomly over {@code total} days.
     */
    static double fisherUpper(int total, int symptomDays, int nHigh, int a) {
        double[] lf = new double[total + 2];
        for (int i = 2; i < lf.length; i++) lf[i] = lf[i - 1] + Math.log(i);
        double denom = logC(lf, total, nHigh);
        double p = 0;
        int max = Math.min(symptomDays, nHigh);
        for (int x = a; x <= max; x++) {
            if (nHigh - x > total - symptomDays) continue;
            p += Math.exp(logC(lf, symptomDays, x) + logC(lf, total - symptomDays, nHigh - x) - denom);
        }
        return Math.min(1.0, p);
    }

    private static double logC(double[] lf, int n, int k) {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY;
        return lf[n] - lf[k] - lf[n - k];
    }

    // ---------------------------------------------------------------- today's risk

    /**
     * Compares today's conditions (latest reading + next 12 forecast hours) with the user's trigger
     * levels and returns human-readable warnings.
     */
    public static List<String> todayRisks(List<Insight> insights, EnvLog now,
                                          List<HourPoint> forecast, long nowMs) {
        List<String> out = new ArrayList<>();
        if (insights == null || insights.isEmpty() || now == null) return out;
        for (Insight in : insights) {
            double sum = value(in.factor, now), max = sum;
            int count = 1;
            if (forecast != null) {
                for (HourPoint p : forecast) {
                    if (p.time < nowMs - 1_800_000L || p.time > nowMs + 12 * 3_600_000L) continue;
                    double val = value(in.factor, p);
                    sum += val;
                    max = Math.max(max, val);
                    count++;
                }
            }
            double expected = in.factor == F_TEMP ? max : sum / count; // matches how days were measured
            if (expected >= in.threshold) {
                out.add(String.format(Locale.US,
                        "%s %s: today's %s is about %s%s, above your trigger level of %s%s.",
                        SymptomTypes.EMOJI[in.symptom], SymptomTypes.NAMES[in.symptom],
                        FACTOR_NAMES[in.factor], fmt(in.factor, expected), FACTOR_UNITS[in.factor],
                        fmt(in.factor, in.threshold), FACTOR_UNITS[in.factor]));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static double value(int factor, EnvLog l) {
        switch (factor) {
            case F_PM25: return l.pm25;
            case F_PM10: return l.pm10;
            case F_TEMP: return l.temperature;
            case F_HUM: return l.humidity;
            default: return l.aqi;
        }
    }

    private static double value(int factor, HourPoint p) {
        switch (factor) {
            case F_PM25: return p.pm25;
            case F_PM10: return p.pm10;
            case F_TEMP: return p.temp;
            case F_HUM: return p.humidity;
            default: return p.aqi;
        }
    }

    private static String fmt(int factor, double v) {
        return (factor == F_PM25 || factor == F_PM10)
                ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.0f", v);
    }

    static int dayKey(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }
}
