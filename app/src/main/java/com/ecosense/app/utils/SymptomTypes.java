package com.ecosense.app.utils;

/** Symptom catalogue used by the diary and the correlation engine. */
public final class SymptomTypes {
    public static final int FINE = 7; // "Feeling fine": counts as an observed day without symptoms
    public static final int COUNT = 8;

    public static final String[] NAMES = {
            "Headache", "Cough", "Eye / throat irritation", "Breathing trouble",
            "Fatigue", "Poor sleep", "Sneezing / allergy", "Feeling fine"};
    public static final String[] SHORT = {
            "Headache", "Cough", "Irritation", "Breathing", "Fatigue", "Sleep", "Allergy", "Fine"};
    public static final String[] EMOJI = {
            "\uD83E\uDD15", "\uD83D\uDE37", "\uD83D\uDC41\uFE0F", "\uD83C\uDF2C\uFE0F",
            "\uD83D\uDE2B", "\uD83D\uDE34", "\uD83E\uDD27", "\uD83D\uDE0A"};
    public static final String[] SEVERITY = {"", "Mild", "Moderate", "Severe"};

    private SymptomTypes() {}

    public static int clamp(int type) {
        return (type < 0 || type >= COUNT) ? FINE : type;
    }
}
