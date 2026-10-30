package com.ecosense.app.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Central place for runtime-permission checks (Location, Notifications, Storage). */
public final class PermissionHelper {
    private PermissionHelper() {}

    private static boolean granted(Context c, String p) {
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocation(Context c) {
        return granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static boolean hasNotifications(Context c) {
        return Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Public Downloads needs WRITE_EXTERNAL_STORAGE only on Android 9 (API 28) and below. */
    public static boolean hasLegacyStorage(Context c) {
        return Build.VERSION.SDK_INT > 28 || granted(c, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    /** Location + notification permissions still to request. */
    public static String[] missingRuntimePermissions(Context c) {
        List<String> list = new ArrayList<>();
        if (!hasLocation(c)) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!hasNotifications(c)) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return list.toArray(new String[0]);
    }
}
