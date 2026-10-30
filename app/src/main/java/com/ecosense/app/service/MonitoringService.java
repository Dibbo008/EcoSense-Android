package com.ecosense.app.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.remote.EnvRepository;
import com.ecosense.app.data.remote.FirebaseHelper;
import com.ecosense.app.utils.AlertChecker;
import com.ecosense.app.utils.NotificationHelper;
import com.ecosense.app.utils.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground service that polls weather + air quality at a user-defined interval. */
public class MonitoringService extends Service {
    public static final String ACTION_NEW_READING = "com.ecosense.app.ACTION_NEW_READING";
    private static final String TAG = "MonitoringService";

    private Handler handler;
    private ExecutorService executor;
    private volatile boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            executor.execute(MonitoringService.this::poll);
            long delay = Math.max(1, Prefs.get(MonitoringService.this).getIntervalMinutes()) * 60_000L;
            handler.postDelayed(this, delay);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        NotificationHelper.createChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.ID_MONITOR, NotificationHelper.buildMonitoring(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationHelper.ID_MONITOR, NotificationHelper.buildMonitoring(this));
        }
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private void poll() {
        try {
            EnvLog log = EnvRepository.fetchAndStore(getApplicationContext());
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
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
