package com.ecosense.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.ecosense.app.utils.NotificationHelper;
import com.ecosense.app.utils.Prefs;
import com.google.firebase.database.FirebaseDatabase;

/** Applies the saved theme and creates notification channels once per process. */
public class EcoSenseApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Prefs.get(this).isDark()
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        NotificationHelper.createChannels(this);
        try {
            // Keeps chat history readable offline and queues writes until the network is back.
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {
            // Realtime Database not configured (e.g. placeholder google-services.json): chat runs in local mode
        }
    }
}
