package com.ecosense.app.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens to SYSTEM broadcasts and exposes the result as simple state:
 *  - ACTION_BATTERY_LOW / ACTION_BATTERY_OKAY
 *  - ACTION_AIRPLANE_MODE_CHANGED
 *  - network availability (ConnectivityManager callback, because CONNECTIVITY_ACTION no longer
 *    reaches apps on modern Android)
 * Receivers are registered once while at least one listener (service or screen) is registered.
 */
public final class SystemMonitor {

    public interface Listener {
        /** Called on the main thread whenever battery, airplane or network state changes. */
        void onSystemStateChanged();
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean batteryLow;
    private static volatile boolean airplane;
    private static volatile boolean online = true;

    private static ConnectivityManager cm;
    private static BroadcastReceiver receiver;
    private static ConnectivityManager.NetworkCallback networkCallback;
    private static Context appContext;

    private SystemMonitor() {}

    // ------------------------------------------------------------------ state

    public static boolean isBatteryLow() { return batteryLow; }
    public static boolean isAirplane() { return airplane; }
    public static boolean isOnline() { return online; }

    /** True when network requests can succeed. */
    public static boolean canFetch() { return !airplane && online; }

    /** One-line status for banners, or null when everything is normal. */
    public static String banner() {
        List<String> parts = new ArrayList<>();
        if (airplane) parts.add("\u2708\uFE0F Airplane mode");
        else if (!online) parts.add("\uD83D\uDCF5 No internet");
        if (batteryLow) parts.add("\uD83D\uDD0B Battery low");
        if (parts.isEmpty()) return null;
        if (!canFetch()) parts.add("showing saved data");
        if (batteryLow) parts.add("background checks slowed");
        return TextUtils.join(" \u00B7 ", parts);
    }

    // ------------------------------------------------------------- registration

    public static synchronized void register(Context context, Listener listener) {
        if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
        if (receiver == null) start(context.getApplicationContext());
    }

    public static synchronized void unregister(Listener listener) {
        LISTENERS.remove(listener);
        if (LISTENERS.isEmpty()) stop();
    }

    private static void start(Context c) {
        appContext = c;
        cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        readInitialState(c);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                boolean changed = false;
                switch (action) {
                    case Intent.ACTION_BATTERY_LOW:
                        changed = !batteryLow;
                        batteryLow = true;
                        break;
                    case Intent.ACTION_BATTERY_OKAY:
                        changed = batteryLow;
                        batteryLow = false;
                        break;
                    case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                        boolean on = intent.getBooleanExtra("state", false);
                        changed = on != airplane;
                        airplane = on;
                        if (!on) refreshOnline();
                        break;
                    default:
                        break;
                }
                if (changed) dispatch();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        ContextCompat.registerReceiver(c, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (cm != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) { refreshOnline(); }

                @Override
                public void onLost(Network network) { refreshOnline(); }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { refreshOnline(); }
            };
            try {
                cm.registerDefaultNetworkCallback(networkCallback);
            } catch (Exception e) {
                networkCallback = null; // e.g. permission/too many callbacks: state stays as last read
            }
        }
    }

    private static void stop() {
        if (appContext != null && receiver != null) {
            try {
                appContext.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
        }
        if (cm != null && networkCallback != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // already unregistered
            }
        }
        receiver = null;
        networkCallback = null;
    }

    // ------------------------------------------------------------------ helpers

    private static void readInitialState(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            float pct = scale > 0 ? level * 100f / scale : 100f;
            batteryLow = level >= 0 && !charging && pct <= 15f;
        }
        airplane = Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        online = computeOnline();
    }

    private static boolean computeOnline() {
        if (cm == null) return true;
        try {
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private static void refreshOnline() {
        boolean now = computeOnline();
        if (now != online) {
            online = now;
            dispatch();
        }
    }

    private static void dispatch() {
        MAIN.post(() -> {
            for (Listener l : LISTENERS) l.onSystemStateChanged();
        });
    }
}
