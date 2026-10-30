package com.ecosense.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.ecosense.app.utils.NotificationHelper;
import com.ecosense.app.utils.Prefs;

/** Applies the saved theme and creates notification channels once per process. */
public class EcoSenseApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Prefs.get(this).isDark()
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        NotificationHelper.createChannels(this);
    }
}
