package com.ecosense.app.data.remote;

import android.content.Context;

import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.utils.LocationHelper;

import java.io.IOException;

import retrofit2.Response;

/** Fetches live data and caches it in Room. Blocking: call from a background thread only. */
public final class EnvRepository {
    private EnvRepository() {}

    public static EnvLog fetchAndStore(Context context) throws IOException {
        Context app = context.getApplicationContext();
        double[] loc = LocationHelper.getLocation(app);
        WeatherApi api = ApiClient.getApi();

        Response<WeatherResponse> w = api.getWeather(loc[0], loc[1],
                "temperature_2m,relative_humidity_2m").execute();
        Response<AirQualityResponse> a = api.getAirQuality(loc[0], loc[1],
                "pm10,pm2_5,us_aqi").execute();

        if (!w.isSuccessful() || w.body() == null || w.body().current == null) {
            throw new IOException("Weather request failed (HTTP " + w.code() + ")");
        }
        if (!a.isSuccessful() || a.body() == null || a.body().current == null) {
            throw new IOException("Air-quality request failed (HTTP " + a.code() + ")");
        }

        WeatherResponse.Current wc = w.body().current;
        AirQualityResponse.Current ac = a.body().current;

        EnvLog log = new EnvLog();
        log.timestamp = System.currentTimeMillis();
        log.temperature = wc.temperature != null ? wc.temperature : 0;
        log.humidity = wc.humidity != null ? wc.humidity : 0;
        log.pm25 = ac.pm25 != null ? ac.pm25 : 0;
        log.pm10 = ac.pm10 != null ? ac.pm10 : 0;
        log.aqi = ac.usAqi != null ? (int) Math.round(ac.usAqi) : 0;
        log.synced = false;
        log.id = AppDatabase.getInstance(app).envLogDao().insert(log);
        return log;
    }
}
