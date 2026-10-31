package com.ecosense.app.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import java.util.List;
import java.util.Locale;

/**
 * Finds the device location (last known fix) and a human-readable place name.
 * Falls back to Khulna when permission is missing or no fix is available.
 */
public final class LocationHelper {
    public static final double DEFAULT_LAT = 22.8456;
    public static final double DEFAULT_LON = 89.5403;
    public static final String DEFAULT_NAME = "Khulna, Bangladesh";

    /** Coordinates plus whether they came from the fallback. */
    public static final class Located {
        public final double lat;
        public final double lon;
        public final boolean usingDefault;

        Located(double lat, double lon, boolean usingDefault) {
            this.lat = lat;
            this.lon = lon;
            this.usingDefault = usingDefault;
        }
    }

    private LocationHelper() {}

    @SuppressLint("MissingPermission")
    public static Located locate(Context context) {
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
                    return new Located(best.getLatitude(), best.getLongitude(), false);
                }
            }
        }
        return new Located(DEFAULT_LAT, DEFAULT_LON, true);
    }

    /** Kept for compatibility. */
    public static double[] getLocation(Context context) {
        Located l = locate(context);
        return new double[]{l.lat, l.lon};
    }

    /**
     * Saves the location (and its place name) to Prefs so the UI can show it.
     * Reverse-geocodes only when the device moved more than ~1 km. Blocking: background thread only.
     */
    public static void updateSavedLocation(Context context, Located loc) {
        Prefs p = Prefs.get(context);
        if (loc.usingDefault) {
            p.saveLocation(loc.lat, loc.lon, DEFAULT_NAME, true);
            return;
        }
        if (p.hasLocation() && !p.isLocationDefault() && !looksLikeCoordinates(p.getLocationName())) {
            float[] dist = new float[1];
            Location.distanceBetween(p.getLocationLat(), p.getLocationLon(), loc.lat, loc.lon, dist);
            if (dist[0] < 1000f) return; // same place, keep the saved name
        }
        p.saveLocation(loc.lat, loc.lon, resolveName(context, loc.lat, loc.lon), false);
    }

    private static boolean looksLikeCoordinates(String s) {
        return !s.isEmpty() && (Character.isDigit(s.charAt(0)) || s.charAt(0) == '-');
    }

    @SuppressWarnings("deprecation")
    private static String resolveName(Context context, double lat, double lon) {
        String fallback = String.format(Locale.US, "%.3f, %.3f", lat, lon);
        try {
            if (!Geocoder.isPresent()) return fallback;
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return fallback;
            Address a = list.get(0);
            String place = firstNonEmpty(a.getLocality(), a.getSubLocality(),
                    a.getSubAdminArea(), a.getAdminArea());
            String country = a.getCountryName();
            if (place == null) return country != null ? country : fallback;
            return (country != null && !country.equals(place)) ? place + ", " + country : place;
        } catch (Exception e) {
            return fallback; // no network / no geocoder backend
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
}
