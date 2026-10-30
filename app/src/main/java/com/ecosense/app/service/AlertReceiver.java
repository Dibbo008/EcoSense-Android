package com.ecosense.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ecosense.app.utils.NotificationHelper;

/** Manifest-registered receiver that turns threshold broadcasts into push notifications. */
public class AlertReceiver extends BroadcastReceiver {
    public static final String ACTION_ALERT = "com.ecosense.app.ACTION_ALERT";
    public static final String EXTRA_MESSAGE = "message";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ALERT.equals(intent.getAction())) return;
        String msg = intent.getStringExtra(EXTRA_MESSAGE);
        if (msg == null || msg.isEmpty()) return;
        NotificationHelper.showAlert(context, "EcoSense alert: unsafe conditions", msg);
    }
}
