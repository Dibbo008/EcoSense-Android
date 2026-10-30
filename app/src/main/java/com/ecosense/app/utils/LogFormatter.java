package com.ecosense.app.utils;

import android.content.Context;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ecosense.app.R;
import com.ecosense.app.data.local.EnvLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formatting + shared view-binding helpers for list/recycler rows. */
public final class LogFormatter {
    private LogFormatter() {}

    private static final ThreadLocal<SimpleDateFormat> DF = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        }
    };

    public static String date(long ts) {
        return DF.get().format(new Date(ts));
    }

    public static String temp(double v) { return String.format(Locale.US, "%.1f\u00B0C", v); }
    public static String hum(double v) { return String.format(Locale.US, "%.0f%%", v); }
    public static String pm(double v) { return String.format(Locale.US, "%.1f", v); }

    /** Lower-case text used by the search bar. */
    public static String searchText(EnvLog l) {
        return (date(l.timestamp) + " " + temp(l.temperature) + " " + hum(l.humidity) + " "
                + pm(l.pm25) + " " + pm(l.pm10) + " " + l.aqi + " " + l.source)
                .toLowerCase(Locale.getDefault());
    }

    public static String aqiLabel(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for sensitive groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    public static void bind(Context ctx, EnvLog l, TextView tvDate, TextView tvStatus,
                            TextView tvTemp, TextView tvHum, TextView tvPm25, TextView tvPm10) {
        tvDate.setText(date(l.timestamp));
        tvTemp.setText("Temp " + temp(l.temperature));
        tvHum.setText("Hum " + hum(l.humidity));
        tvPm25.setText("PM2.5 " + pm(l.pm25));
        tvPm10.setText("PM10 " + pm(l.pm10));
        boolean unsafe = AlertChecker.isUnsafe(ctx, l);
        tvStatus.setText(unsafe ? "ALERT" : "SAFE");
        tvStatus.setTextColor(ContextCompat.getColor(ctx, unsafe ? R.color.eco_alert : R.color.eco_safe));
    }
}
