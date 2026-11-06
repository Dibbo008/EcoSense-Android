package com.ecosense.app.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Open-Meteo hourly air quality. */
public class HourlyAirResponse {
    @SerializedName("utc_offset_seconds")
    public Integer utcOffsetSeconds;
    public Hourly hourly;

    public static class Hourly {
        public List<String> time;
        @SerializedName("pm10")
        public List<Double> pm10;
        @SerializedName("pm2_5")
        public List<Double> pm25;
        @SerializedName("us_aqi")
        public List<Double> usAqi;
    }
}
