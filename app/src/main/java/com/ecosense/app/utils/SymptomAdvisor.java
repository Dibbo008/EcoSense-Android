package com.ecosense.app.utils;

import com.ecosense.app.data.local.EnvLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based symptom helper: from a list of symptoms, strength, duration, a free-text note and
 * the current air/weather it explains what may be happening, why, what to do, and when to see a
 * doctor. Pure Java, works offline, and never claims to diagnose.
 */
public final class SymptomAdvisor {
    public static final String[] DURATION = {"just today", "2\u20133 days", "4+ days"};

    public static final class Input {
        public final List<Integer> symptoms = new ArrayList<>(); // SymptomTypes ids 0..6
        public int severity = 1;     // 1 mild, 2 moderate, 3 severe
        public int duration = 0;     // index into DURATION
        public String note = "";
        public EnvLog env;           // latest reading, may be null
        public int profile = AdvisorEngine.NORMAL;
        public final List<String> personal = new ArrayList<>(); // lines from the user's own diary patterns
    }

    // exposure factors
    private static final int PM25 = 0, PM10 = 1, HOT = 2, HUMID = 3, DRY = 4;
    /** Which exposures are plausible contributors for each symptom (index = symptom id). */
    private static final int[][] RELEVANT = {
            {PM25, HOT},                // headache
            {PM25, PM10, DRY},          // cough
            {PM25, PM10, DRY},          // eye/throat irritation
            {PM25, PM10, HUMID, HOT},   // breathing trouble
            {HOT, PM25},                // fatigue
            {HOT, HUMID},               // poor sleep
            {PM10, HUMID, DRY}};        // sneezing / allergy

    private static final String[] RED_FLAGS = {
            "chest pain", "chest tight", "can't breathe", "cant breathe", "cannot breathe",
            "short of breath", "shortness of breath", "struggling to breathe", "faint", "passed out",
            "unconscious", "blue lips", "confused", "seizure", "coughing blood", "blood"};

    private SymptomAdvisor() {}

    public static boolean hasRedFlag(Input in) {
        if (in.symptoms.contains(3) && in.severity >= 3) return true; // severe breathing trouble
        String n = in.note == null ? "" : in.note.toLowerCase(Locale.US);
        for (String k : RED_FLAGS) {
            if (n.contains(k)) return true;
        }
        return false;
    }

    public static String advise(Input in) {
        StringBuilder sb = new StringBuilder();

        if (hasRedFlag(in)) {
            sb.append("\uD83D\uDEA8 This could be serious\n");
            sb.append("What you described (such as trouble breathing, chest pain, fainting or blood) needs ")
                    .append("medical attention. Please call your local emergency number or go to the nearest ")
                    .append("hospital now. Do not wait for the air to improve.\n\n");
            sb.append("While you get help: sit upright, stay calm, move to cleaner air if you can, and tell ")
                    .append("someone nearby.\n\n");
            sb.append(FOOTER);
            return sb.toString();
        }

        boolean[] flagged = flaggedFactors(in);
        boolean hasEnv = in.env != null;

        // ---- What may be happening
        sb.append("\uD83E\uDE7A What may be happening\n").append(whatHappening(in, flagged)).append("\n\n");

        // ---- Why
        sb.append("\uD83D\uDD0E Why this may be happening\n");
        List<String> why = whyLines(in, flagged, hasEnv);
        for (String w : why) sb.append("\u2022 ").append(w).append('\n');
        sb.append('\n');

        // ---- What to do
        sb.append("\u2705 What you can do\n");
        List<String> todo = todoLines(in, flagged);
        int shown = 0;
        for (String t : todo) {
            if (shown++ >= 6) break;
            sb.append("\u2022 ").append(t).append('\n');
        }
        sb.append('\n');

        // ---- See a doctor
        sb.append("\u26A0\uFE0F See a doctor if\n");
        for (String d : doctorLines(in)) sb.append("\u2022 ").append(d).append('\n');
        sb.append('\n').append(FOOTER);
        return sb.toString();
    }

    private static final String FOOTER =
            "This is general guidance based on your answers and local air data. It is not a diagnosis.";

    // ------------------------------------------------------------------ exposures

    private static boolean[] flaggedFactors(Input in) {
        boolean[] f = new boolean[5];
        if (in.env == null) return f;
        float[] lim = AdvisorEngine.recommendedLimits(in.profile);
        double heatStart = (in.profile == AdvisorEngine.NORMAL || in.profile == AdvisorEngine.ATHLETE) ? 32 : 30;
        f[PM25] = in.env.pm25 > lim[1];
        f[PM10] = in.env.pm10 > lim[2];
        f[HOT] = in.env.temperature >= heatStart;
        f[HUMID] = in.env.humidity >= 80;
        f[DRY] = in.env.humidity > 0 && in.env.humidity <= 30;
        return f;
    }

    private static boolean relevant(Input in, int factor, boolean[] flagged) {
        if (!flagged[factor]) return false;
        for (int s : in.symptoms) {
            if (s < 0 || s >= RELEVANT.length) continue;
            for (int f : RELEVANT[s]) if (f == factor) return true;
        }
        return false;
    }

    private static boolean has(Input in, int symptom) {
        return in.symptoms.contains(symptom);
    }

    private static int count(Input in, int... ids) {
        int c = 0;
        for (int id : ids) if (has(in, id)) c++;
        return c;
    }

    // ------------------------------------------------------------------ sections

    private static String whatHappening(Input in, boolean[] flagged) {
        StringBuilder sb = new StringBuilder();
        boolean envPm = flagged[PM25] || flagged[PM10];
        int respiratory = count(in, 1, 2, 3);

        if (respiratory >= 2) {
            sb.append(envPm
                    ? "Your cough, throat/eye irritation or breathing symptoms fit irritation of the airways, "
                    + "which is common when the air is polluted or dusty."
                    : "Your cough, throat/eye irritation or breathing symptoms point to irritated airways. "
                    + "Dry air, an allergy, or a cold or flu can do this.");
        }
        if (count(in, 0, 4, 5) >= 2 && flagged[HOT]) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("Headache, tiredness and poor sleep together fit heat stress and dehydration.");
        }
        if (has(in, 6) && (has(in, 2) || flagged[PM10] || flagged[HUMID])) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("Sneezing with an irritated nose or eyes suggests a reaction to dust, pollen or mould.");
        }
        if (sb.length() == 0) {
            int added = 0;
            for (int s : in.symptoms) {
                if (added++ >= 3) break;
                if (sb.length() > 0) sb.append(' ');
                sb.append(single(s));
            }
        }
        if (in.severity >= 2 || in.duration >= 1) {
            sb.append(" It is ").append(in.severity >= 3 ? "severe" : in.severity == 2 ? "moderate" : "mild")
                    .append(" and has lasted ").append(DURATION[Math.max(0, Math.min(2, in.duration))]).append('.');
        }
        return sb.toString().trim();
    }

    private static String single(int s) {
        switch (s) {
            case 0: return "A headache can come from pollution, heat, dehydration, poor sleep or stress.";
            case 1: return "A cough is often the airways reacting to dust, smoke, dry air or a cold.";
            case 2: return "Itchy or burning eyes and throat are a classic reaction to particles, smoke and dry air.";
            case 3: return "Breathing trouble needs extra care, especially when the air is polluted.";
            case 4: return "Tiredness can come from heat, poor sleep, polluted air or an infection.";
            case 5: return "Poor sleep is often linked to heat, humidity and stuffy air.";
            default: return "Sneezing or allergy symptoms often follow dust, pollen or mould.";
        }
    }

    private static List<String> whyLines(Input in, boolean[] flagged, boolean hasEnv) {
        List<String> out = new ArrayList<>();
        EnvLog e = in.env;
        if (!hasEnv) {
            out.add("I don't have a current air reading. Open the dashboard and tap refresh for more specific advice.");
        } else {
            float[] lim = AdvisorEngine.recommendedLimits(in.profile);
            if (relevant(in, PM25, flagged)) {
                out.add(String.format(Locale.US, "Fine particles (PM2.5) are %.0f \u00B5g/m\u00B3, above the %.0f limit for your profile. "
                        + "They irritate the eyes, nose, throat and airways and can trigger headaches and coughing.", e.pm25, lim[1]));
            }
            if (relevant(in, PM10, flagged)) {
                out.add(String.format(Locale.US, "Dust (PM10) is %.0f \u00B5g/m\u00B3, above %.0f. Coarse dust commonly causes coughing, "
                        + "sneezing and irritated eyes.", e.pm10, lim[2]));
            }
            if (relevant(in, HOT, flagged)) {
                out.add(String.format(Locale.US, "It is %.0f\u00B0C. Heat can cause headaches, tiredness, poor sleep and dehydration.", e.temperature));
            }
            if (relevant(in, HUMID, flagged)) {
                out.add(String.format(Locale.US, "Humidity is %.0f%%. Damp air encourages mould and dust mites, which can affect "
                        + "breathing, allergies and sleep.", e.humidity));
            }
            if (relevant(in, DRY, flagged)) {
                out.add(String.format(Locale.US, "The air is very dry (%.0f%%), which dries and irritates the throat, nose and eyes.", e.humidity));
            }
            if (out.isEmpty()) {
                out.add(String.format(Locale.US, "Air and weather look normal right now (AQI %d, %.0f\u00B0C), so the environment is less likely "
                        + "to be the main cause. Think about sleep, hydration, stress, an infection, or allergens indoors.",
                        e.aqi, e.temperature));
            }
        }
        for (String p : in.personal) out.add("From your own diary: " + p);
        return out;
    }

    private static List<String> todoLines(Input in, boolean[] flagged) {
        List<String> t = new ArrayList<>();
        boolean pm = flagged[PM25] || flagged[PM10];
        if (pm && (relevant(in, PM25, flagged) || relevant(in, PM10, flagged))) {
            t.add("Stay indoors where you can, keep windows closed, and use an air purifier if you have one.");
            t.add("If you must go out, wear a well-fitting N95/KN95 mask and avoid busy roads.");
            t.add("Skip outdoor exercise until the air improves.");
        }
        if (in.profile == AdvisorEngine.SENSITIVE) {
            t.add("Keep any prescribed inhaler or medicines with you and follow your doctor's action plan.");
        }
        if (relevant(in, HOT, flagged)) {
            t.add("Move to a cool, shaded place, wear light clothes and avoid heavy activity in the afternoon.");
        }
        t.add("Drink water regularly and get some rest.");
        if (relevant(in, HUMID, flagged)) {
            t.add("Dry damp areas and ventilate when the outdoor air is clean to limit mould and dust mites.");
        }
        if (relevant(in, DRY, flagged)) {
            t.add("Keep indoor air from getting too dry and sip warm fluids.");
        }
        if (has(in, 0)) t.add("Rest in a quiet, dim room and take a break from screens.");
        if (has(in, 1)) t.add("Warm fluids can soothe a cough.");
        if (has(in, 2) || has(in, 6)) t.add("Rinse your face and eyes with clean water; a saline nasal rinse can ease irritation.");
        if (has(in, 5)) t.add("Keep the bedroom cool and ventilated when the air is clean, and avoid screens before bed.");
        t.add("Log how you feel again later so EcoSense can learn your triggers.");
        return t;
    }

    private static List<String> doctorLines(Input in) {
        List<String> d = new ArrayList<>();
        boolean higherRisk = in.profile == AdvisorEngine.SENSITIVE || in.profile == AdvisorEngine.ELDERLY
                || in.profile == AdvisorEngine.CHILD;
        if (in.severity >= 3 || in.duration >= 2 || (higherRisk && (in.severity >= 2 || in.duration >= 1))) {
            d.add(in.severity >= 3
                    ? "Because it is severe, it is a good idea to see a doctor soon."
                    : in.duration >= 2
                    ? "Because it has lasted 4 days or more, it is a good idea to see a doctor soon."
                    : "Because you are in a higher-risk group, do not wait too long before getting it checked.");
        }
        d.add("You have trouble breathing, chest pain, fainting, confusion or a very high fever (seek emergency care).");
        d.add("It lasts more than 3 days or keeps getting worse.");
        d.add("It keeps coming back on days when the air is polluted.");
        return d;
    }
}
