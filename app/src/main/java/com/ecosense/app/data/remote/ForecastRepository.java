package com.ecosense.app.data.remote;

import android.content.Context;

import com.ecosense.app.data.local.ForecastCache;
import com.ecosense.app.data.local.HourPoint;
import com.ecosense.app.utils.LocationHelper;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Response;

/** Downloads the next ~48 hours of air quality + weather and caches it. Blocking: background thread only. */
public final class ForecastRepository {
    private static final long FRESH_MS = 30L * 60_000L;

    public static final class Result {
        public final List<HourPoint> points;
        public final boolean fromCache;
        public final long fetchedAt;
        public final String error; // null when OK

        Result(List<HourPoint> points, boolean fromCache, long fetchedAt, String error) {
            this.points = points;
            this.fromCache = fromCache;
            this.fetchedAt = fetchedAt;
            this.error = error;
        }
    }

    private ForecastRepository() {}

    public static Result load(Context context, boolean forceNetwork) {
        Context app = context.getApplicationContext();
        LocationHelper.Located loc = LocationHelper.locate(app);
        ForecastCache.Blob cache = ForecastCache.read(app);
        boolean cacheMatches = cache != null && sameArea(cache, loc);

        if (!forceNetwork && cacheMatches
                && System.currentTimeMillis() - cache.fetchedAt < FRESH_MS) {
            return new Result(cache.points, true, cache.fetchedAt, null);
        }
        try {
            List<HourPoint> pts = download(loc.lat, loc.lon);
            ForecastCache.Blob blob = new ForecastCache.Blob();
            blob.fetchedAt = System.currentTimeMillis();
            blob.lat = loc.lat;
            blob.lon = loc.lon;
            blob.points = pts;
            ForecastCache.save(app, blob);
            return new Result(pts, false, blob.fetchedAt, null);
        } catch (Exception e) {
            if (cacheMatches) {
                return new Result(cache.points, true, cache.fetchedAt, e.getMessage());
            }
            return new Result(new ArrayList<HourPoint>(), false, 0, e.getMessage());
        }
    }

    private static boolean sameArea(ForecastCache.Blob b, LocationHelper.Located loc) {
        return Math.abs(b.lat - loc.lat) < 0.1 && Math.abs(b.lon - loc.lon) < 0.1; // ~10 km
    }

    private static List<HourPoint> download(double lat, double lon) throws IOException {
        WeatherApi api = ApiClient.getApi();
        Response<HourlyWeatherResponse> w = api.getHourlyWeather(lat, lon,
                "temperature_2m,relative_humidity_2m", 2, "auto").execute();
        Response<HourlyAirResponse> a = api.getHourlyAir(lat, lon,
                "pm10,pm2_5,us_aqi", 2, "auto").execute();

        if (!w.isSuccessful() || w.body() == null || w.body().hourly == null
                || w.body().hourly.time == null) {
            throw new IOException("Forecast request failed (HTTP " + w.code() + ")");
        }
        if (!a.isSuccessful() || a.body() == null || a.body().hourly == null
                || a.body().hourly.time == null) {
            throw new IOException("Air-quality forecast failed (HTTP " + a.code() + ")");
        }

        HourlyWeatherResponse.Hourly wh = w.body().hourly;
        HourlyAirResponse.Hourly ah = a.body().hourly;
        int offset = w.body().utcOffsetSeconds != null ? w.body().utcOffsetSeconds : 0;
        int n = Math.min(wh.time.size(), ah.time.size());

        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));

        List<HourPoint> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Double temp = at(wh.temperature, i);
            Double aqi = at(ah.usAqi, i);
            if (temp == null || aqi == null) continue; // skip incomplete hours
            HourPoint p = new HourPoint();
            try {
                p.time = f.parse(wh.time.get(i)).getTime() - offset * 1000L;
            } catch (ParseException e) {
                throw new IOException("Bad time format in forecast");
            }
            p.temp = temp;
            Double hum = at(wh.humidity, i);
            p.humidity = hum != null ? hum : 0;
            Double p25 = at(ah.pm25, i);
            Double p10 = at(ah.pm10, i);
            p.pm25 = p25 != null ? p25 : 0;
            p.pm10 = p10 != null ? p10 : 0;
            p.aqi = (int) Math.round(aqi);
            out.add(p);
        }
        if (out.size() < 2) throw new IOException("Forecast data was empty");
        return out;
    }

    private static Double at(List<Double> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }
}
