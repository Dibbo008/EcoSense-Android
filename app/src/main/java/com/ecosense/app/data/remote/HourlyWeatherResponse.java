package com.ecosense.app.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Open-Meteo hourly weather (temperature, humidity). */
public class HourlyWeatherResponse {
    @SerializedName("utc_offset_seconds")
    public Integer utcOffsetSeconds;
    public Hourly hourly;

    public static class Hourly {
        public List<String> time;
        @SerializedName("temperature_2m")
        public List<Double> temperature;
        @SerializedName("relative_humidity_2m")
        public List<Double> humidity;
    }
}
