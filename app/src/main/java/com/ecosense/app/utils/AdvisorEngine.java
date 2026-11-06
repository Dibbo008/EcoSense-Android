package com.ecosense.app.utils;

import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.HourPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Turns raw numbers into decisions. Pure Java rules (no network, no Android UI):
 * the person's health profile scales the AQI, then simple rules produce advice
 * and find the best / worst 2-hour windows in the forecast.
 */
public final class AdvisorEngine {
    public static final int NORMAL = 0, SENSITIVE = 1, ELDERLY = 2, CHILD = 3, ATHLETE = 4;
    public static final String[] PROFILE_NAMES = {"Normal", "Sensitive", "Elderly", "Child", "Athlete"};
    public static final String[] PROFILE_DESC = {
            "Standard advice for a healthy adult.",
            "Asthma, allergies or heart/lung conditions. Advice is stricter.",
            "Older adults are more affected by pollution and heat.",
            "Children breathe faster and are more sensitive to pollution.",
            "Hard exercise means deeper breathing, so pollution matters more."};

    /** Multiplies the real AQI to get the "effective" AQI for this person. */
    private static final double[] FACTOR = {1.0, 1.4, 1.2, 1.25, 1.1};
    /** Recommended alert limits per profile: {max temp C, PM2.5, PM10}. */
    private static final float[][] LIMITS = {
            {35f, 35f, 100f}, {33f, 15f, 45f}, {33f, 25f, 75f}, {32f, 20f, 60f}, {32f, 25f, 80f}};

    public static final int OK = 0, CAUTION = 1, AVOID = 2;
    private static final long HOUR = 3_600_000L;

    public static final class Advice {
        public final String emoji, title, shortLabel, detail, status;
        public final int level;

        Advice(String emoji, String title, String shortLabel, int level, String status, String detail) {
            this.emoji = emoji;
            this.title = title;
            this.shortLabel = shortLabel;
            this.level = level;
            this.status = status;
            this.detail = detail;
        }
    }

    public static final class Window {
        public final long start, end;
        public final double avgAqi, avgScore;
        public final int level;

        Window(long start, long end, double avgAqi, double avgScore) {
            this.start = start;
            this.end = end;
            this.avgAqi = avgAqi;
            this.avgScore = avgScore;
            this.level = levelForScore(avgScore);
        }
    }

    public static final class Plan {
        public String headline = "";
        public int level = OK;
        public final List<Advice> advice = new ArrayList<>();
        public Window best;   // may be null (no forecast)
        public Window worst;  // may be null (no clearly bad period)
    }

    private AdvisorEngine() {}

    // ------------------------------------------------------------------ profile

    public static int clampProfile(int p) {
        return (p < 0 || p >= FACTOR.length) ? NORMAL : p;
    }

    public static double factor(int profile) {
        return FACTOR[clampProfile(profile)];
    }

    public static float[] recommendedLimits(int profile) {
        return LIMITS[clampProfile(profile)].clone();
    }

    // ------------------------------------------------------------------ scoring

    /** 0 good, 1 moderate, 2 unhealthy for sensitive, 3 unhealthy, 4 very unhealthy. */
    public static int category(double effectiveAqi) {
        if (effectiveAqi <= 50) return 0;
        if (effectiveAqi <= 100) return 1;
        if (effectiveAqi <= 150) return 2;
        if (effectiveAqi <= 200) return 3;
        return 4;
    }

    /** Lower is better: profile-adjusted AQI plus a penalty for heat. */
    public static double hourScore(int profile, HourPoint p) {
        double s = p.aqi * factor(profile);
        if (p.temp > 30) s += (p.temp - 30) * 4;
        return s;
    }

    public static int levelForScore(double score) {
        return score <= 60 ? OK : score <= 110 ? CAUTION : AVOID;
    }

    public static int hourLevel(int profile, HourPoint p) {
        return levelForScore(hourScore(profile, p));
    }

    // --------------------------------------------------------------------- plan

    /** @param forecast may be null/empty: then only "right now" advice is produced. */
    public static Plan build(int profile, EnvLog now, List<HourPoint> forecast, long nowMillis) {
        profile = clampProfile(profile);
        Plan plan = new Plan();
        double aqi = now.aqi;
        double t = now.temperature;
        double h = now.humidity;
        double e = aqi * factor(profile);
        int cat = category(e);

        switch (cat) {
            case 0: plan.headline = "Great air today. Enjoy the outdoors."; break;
            case 1: plan.headline = profile == NORMAL
                    ? "Air is acceptable. Most people can carry on as usual."
                    : "Moderate air. Take it a bit easier than usual."; break;
            case 2: plan.headline = "Air is unhealthy for sensitive people. Limit time outside."; break;
            case 3: plan.headline = "Unhealthy air. Avoid long outdoor activity."; break;
            default: plan.headline = "Very unhealthy air. Stay indoors if you can."; break;
        }
        if (t >= 35) plan.headline = "Heat warning. " + plan.headline;
        plan.level = cat == 0 ? OK : cat <= 2 ? CAUTION : AVOID;
        if (t >= 35) plan.level = AVOID;
        else if (t >= 31 && plan.level == OK) plan.level = CAUTION;

        // 1. Exercise
        if (e <= 60 && t <= 32) {
            add(plan, "\uD83C\uDFC3", "Outdoor exercise", "Exercise", OK, st("Good", "Careful", "Avoid"),
                    "Good conditions for a run or workout.");
        } else if (e <= 100 && t <= 35) {
            add(plan, "\uD83C\uDFC3", "Outdoor exercise", "Exercise", CAUTION, st("Good", "Careful", "Avoid"),
                    "Keep it light and shorter than usual. Avoid busy roads.");
        } else {
            add(plan, "\uD83C\uDFC3", "Outdoor exercise", "Exercise", AVOID, st("Good", "Careful", "Avoid"),
                    t > 35 ? "Too hot outside. Exercise indoors or after sunset."
                            : "Air quality is poor. Exercise indoors today.");
        }

        // 2. Walk / cycle
        if (e <= 100 && t <= 35) {
            add(plan, "\uD83D\uDEB6", "Walk or cycle", "Walk", OK, st("Good", "Careful", "Avoid"),
                    "Fine for walking, cycling and errands.");
        } else if (e <= 150 && t <= 38) {
            add(plan, "\uD83D\uDEB6", "Walk or cycle", "Walk", CAUTION, st("Good", "Careful", "Avoid"),
                    "Short trips are fine. Carry water and avoid rush hour.");
        } else {
            add(plan, "\uD83D\uDEB6", "Walk or cycle", "Walk", AVOID, st("Good", "Careful", "Avoid"),
                    "Skip it if you can. Use a vehicle or wait for better air.");
        }

        // 3. Windows
        String[] win = st("Open", "Briefly", "Keep shut");
        if (e <= 75) {
            add(plan, "\uD83E\uDE9F", "Open windows", "Windows", OK, win,
                    "Fresh air is fine. Good time to ventilate your home.");
        } else if (e <= 100) {
            add(plan, "\uD83E\uDE9F", "Open windows", "Windows", CAUTION, win,
                    "Ventilate briefly, ideally when the air is cleanest.");
        } else {
            add(plan, "\uD83E\uDE9F", "Open windows", "Windows", AVOID, win,
                    "Keep windows closed. Use a purifier if you have one.");
        }

        // 4. Mask
        String[] mask = st("Not needed", "Recommended", "Required");
        if (e <= 100) {
            add(plan, "\uD83D\uDE37", "Wear a mask", "Mask", OK, mask, "No mask needed outdoors.");
        } else if (e <= 150) {
            add(plan, "\uD83D\uDE37", "Wear a mask", "Mask", CAUTION, mask,
                    "A well-fitting mask is recommended if you go out.");
        } else {
            add(plan, "\uD83D\uDE37", "Wear a mask", "Mask", AVOID, mask,
                    "Wear an N95/KN95 mask outdoors.");
        }

        // 5. Laundry
        String[] dry = st("Go ahead", "Slow", "Not today");
        if (h <= 70 && e <= 100) {
            add(plan, "\uD83D\uDC55", "Dry clothes outside", "Laundry", OK, dry,
                    "Low humidity and clean air. Clothes will dry well.");
        } else if (h <= 85 && e <= 150) {
            add(plan, "\uD83D\uDC55", "Dry clothes outside", "Laundry", CAUTION, dry,
                    h > 70 ? "Humid. Clothes will dry slowly." : "Dust may settle on clothes.");
        } else {
            add(plan, "\uD83D\uDC55", "Dry clothes outside", "Laundry", AVOID, dry,
                    e > 150 ? "Polluted air. Dry clothes indoors." : "Very humid. Dry clothes indoors.");
        }

        // 6. Children & elders (always at least 1.3x as sensitive)
        double e2 = aqi * Math.max(factor(profile), 1.3);
        String[] kids = st("Fine", "Limit", "Keep indoors");
        if (e2 <= 75 && t <= 33) {
            add(plan, "\uD83E\uDDD2", "Kids & older adults outside", "Kids", OK, kids,
                    "Safe for normal outdoor time.");
        } else if (e2 <= 130 && t <= 36) {
            add(plan, "\uD83E\uDDD2", "Kids & older adults outside", "Kids", CAUTION, kids,
                    "Keep outdoor time short and take breaks in the shade.");
        } else {
            add(plan, "\uD83E\uDDD2", "Kids & older adults outside", "Kids", AVOID, kids,
                    "Best to stay indoors right now.");
        }

        // 7. Heat / hydration
        String[] heat = st("Comfortable", "Warm", "Heat risk");
        if (t >= 35) {
            add(plan, "\uD83D\uDCA7", "Heat & hydration", "Heat", AVOID, heat,
                    "Heat risk. Stay in shade or indoors at midday and drink plenty of water.");
        } else if (t >= 31) {
            add(plan, "\uD83D\uDCA7", "Heat & hydration", "Heat", CAUTION, heat,
                    "Warm. Drink water regularly and rest in the shade.");
        } else {
            add(plan, "\uD83D\uDCA7", "Heat & hydration", "Heat", OK, heat,
                    "Temperature is comfortable.");
        }

        findWindows(plan, profile, forecast, nowMillis);
        return plan;
    }

    private static String[] st(String ok, String caution, String avoid) {
        return new String[]{ok, caution, avoid};
    }

    private static void add(Plan plan, String emoji, String title, String shortLabel,
                            int level, String[] labels, String detail) {
        plan.advice.add(new Advice(emoji, title, shortLabel, level, labels[level], detail));
    }

    // ------------------------------------------------------------------ windows

    /** Best/worst 2-hour daytime (05:00-20:00) windows within the next 24 hours. */
    private static void findWindows(Plan plan, int profile, List<HourPoint> pts, long nowMs) {
        if (pts == null || pts.size() < 2) return;
        Window best = null, worst = null;
        long limit = nowMs + 24 * HOUR;
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i + 1 < pts.size(); i++) {
            HourPoint a = pts.get(i), b = pts.get(i + 1);
            if (a.time < nowMs - HOUR / 2 || a.time > limit) continue;
            if (b.time - a.time != HOUR) continue;
            cal.setTimeInMillis(a.time);
            int hr = cal.get(Calendar.HOUR_OF_DAY);
            if (hr < 5 || hr > 19) continue;
            double score = (hourScore(profile, a) + hourScore(profile, b)) / 2.0;
            double aq = (a.aqi + b.aqi) / 2.0;
            Window w = new Window(a.time, b.time + HOUR, aq, score);
            if (best == null || score < best.avgScore) best = w;
            if (worst == null || score > worst.avgScore) worst = w;
        }
        plan.best = best;
        plan.worst = (best != null && worst != null && worst.avgScore - best.avgScore >= 15) ? worst : null;
    }

    /** e.g. "Today 6\u20138 AM" or "Tomorrow 11 AM\u20131 PM". */
    public static String formatWindow(Window w) {
        Calendar s = Calendar.getInstance();
        s.setTimeInMillis(w.start);
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(w.end);
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        String day;
        if (sameDay(s, today)) day = "Today";
        else if (sameDay(s, tomorrow)) day = "Tomorrow";
        else day = new SimpleDateFormat("EEE", Locale.getDefault()).format(w.start);

        SimpleDateFormat hOnly = new SimpleDateFormat("h", Locale.getDefault());
        SimpleDateFormat hAmPm = new SimpleDateFormat("h a", Locale.getDefault());
        boolean sameHalf = s.get(Calendar.AM_PM) == e.get(Calendar.AM_PM);
        String range = (sameHalf ? hOnly.format(w.start) : hAmPm.format(w.start))
                + "\u2013" + hAmPm.format(w.end);
        return day + " " + range;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
