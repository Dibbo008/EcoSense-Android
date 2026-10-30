package com.ecosense.app.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

/** Last-known location with a safe fallback (Khulna) when permission is missing. */
public final class LocationHelper {
    public static final double DEFAULT_LAT = 22.8456;
    public static final double DEFAULT_LON = 89.5403;

    private LocationHelper() {}

    @SuppressLint("MissingPermission")
    public static double[] getLocation(Context context) {
        if (PermissionHelper.hasLocation(context)) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location best = null;
                try {
                    for (String provider : lm.getProviders(true)) {
                        Location l = lm.getLastKnownLocation(provider);
                        if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) {
                            best = l;
                        }
                    }
                } catch (SecurityException ignored) {
                    // Permission revoked at runtime: fall through to default
                }
                if (best != null) {
                    return new double[]{best.getLatitude(), best.getLongitude()};
                }
            }
        }
        return new double[]{DEFAULT_LAT, DEFAULT_LON};
    }
}
