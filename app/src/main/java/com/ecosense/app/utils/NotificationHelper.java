package com.ecosense.app.utils;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.ecosense.app.R;
import com.ecosense.app.ui.dashboard.DashboardActivity;

public final class NotificationHelper {
    public static final String CH_ALERTS = "eco_alerts";
    public static final String CH_MONITOR = "eco_monitor";
    public static final int ID_MONITOR = 1001;
    public static final int ID_ALERT = 2001;

    private NotificationHelper() {}

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel alerts = new NotificationChannel(CH_ALERTS, "Environmental alerts",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Warnings when temperature, humidity or particulate matter exceed your limits");
        NotificationChannel monitor = new NotificationChannel(CH_MONITOR, "Background monitoring",
                NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("Shown while EcoSense monitors the environment");
        nm.createNotificationChannel(alerts);
        nm.createNotificationChannel(monitor);
    }

    private static PendingIntent openDashboard(Context c) {
        Intent i = new Intent(c, DashboardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static Notification buildMonitoring(Context c) {
        return new NotificationCompat.Builder(c, CH_MONITOR)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle("EcoSense is monitoring")
                .setContentText("Checking air quality and weather in the background")
                .setContentIntent(openDashboard(c))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @SuppressLint("MissingPermission")
    public static void showAlert(Context c, String title, String text) {
        if (!Prefs.get(c).notificationsEnabled() || !PermissionHelper.hasNotifications(c)) return;
        Notification n = new NotificationCompat.Builder(c, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(title)
                .setContentText(text.split("\n")[0])
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openDashboard(c))
                .build();
        NotificationManagerCompat.from(c).notify(ID_ALERT, n);
    }
}
