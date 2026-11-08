package com.ecosense.app.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.remote.EnvRepository;
import com.ecosense.app.data.remote.FirebaseHelper;
import com.ecosense.app.utils.AlertChecker;
import com.ecosense.app.utils.NotificationHelper;
import com.ecosense.app.utils.PermissionHelper;
import com.ecosense.app.utils.Prefs;
import com.ecosense.app.utils.SystemMonitor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that polls weather + air quality at a user-defined interval.
 * It reacts to system broadcasts through {@link SystemMonitor}:
 *  - battery low: polls 3x less often (power saving)
 *  - airplane mode / no internet: pauses polling and says so in the notification
 *  - network or airplane mode restored: polls immediately and syncs unsynced readings
 * and it keeps its notification text up to date after every check.
 */
public class MonitoringService extends Service {
    public static final String ACTION_NEW_READING = "com.ecosense.app.ACTION_NEW_READING";
    private static final String TAG = "MonitoringService";

    private Handler handler;
    private ExecutorService executor;
    private volatile boolean running;
    private volatile String lastSummary = "Checking air quality\u2026";
    private boolean wasBlocked;

    private final SystemMonitor.Listener systemListener = this::onSystemChanged;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            executor.execute(MonitoringService.this::poll);
            handler.postDelayed(this, intervalMs());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        NotificationHelper.createChannels(this);
        SystemMonitor.register(this, systemListener);
        wasBlocked = !SystemMonitor.canFetch();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.ID_MONITOR,
                    NotificationHelper.buildMonitoring(this, statusText()),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationHelper.ID_MONITOR,
                    NotificationHelper.buildMonitoring(this, statusText()));
        }
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    /** Battery low = power saving: at least 3x the normal interval and never faster than 30 min. */
    private long intervalMs() {
        long base = Math.max(1, Prefs.get(this).getIntervalMinutes()) * 60_000L;
        return SystemMonitor.isBatteryLow() ? Math.max(base * 3, 30 * 60_000L) : base;
    }

    private String statusText() {
        if (SystemMonitor.isAirplane()) return "Paused \u00B7 airplane mode is on";
        if (!SystemMonitor.isOnline()) return "Paused \u00B7 no internet connection";
        return lastSummary + (SystemMonitor.isBatteryLow() ? " \u00B7 power saving" : "");
    }

    @SuppressLint("MissingPermission")
    private void updateNotification() {
        if (!PermissionHelper.hasNotifications(this)) return;
        NotificationManagerCompat.from(this).notify(NotificationHelper.ID_MONITOR,
                NotificationHelper.buildMonitoring(this, statusText()));
    }

    /** Runs on the main thread whenever a system broadcast changes battery / airplane / network state. */
    private void onSystemChanged() {
        updateNotification();
        boolean blocked = !SystemMonitor.canFetch();
        if (running && wasBlocked && !blocked) {
            executor.execute(this::poll); // connection is back: refresh and sync right away
        }
        wasBlocked = blocked;
        if (running) {
            // apply a new interval immediately (battery low <-> ok)
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, intervalMs());
        }
    }

    private void poll() {
        if (!SystemMonitor.canFetch()) {
            updateNotification();
            return;
        }
        try {
            EnvLog log = EnvRepository.fetchAndStore(getApplicationContext());
            lastSummary = String.format(Locale.US, "AQI %d \u00B7 %.0f\u00B0C \u00B7 updated %s",
                    log.aqi, log.temperature,
                    new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            Intent i = new Intent(ACTION_NEW_READING);
            i.setPackage(getPackageName());
            sendBroadcast(i);
            AlertChecker.dispatch(getApplicationContext(), log);
            if (FirebaseHelper.isSignedIn()) {
                FirebaseHelper.uploadUnsynced(getApplicationContext(), null);
            }
        } catch (Exception e) {
            Log.w(TAG, "Polling failed: " + e.getMessage());
        }
        updateNotification();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        executor.shutdownNow();
        SystemMonitor.unregister(systemListener);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
