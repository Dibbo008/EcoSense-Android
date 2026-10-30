package com.ecosense.app.data.remote;

import com.google.gson.annotations.SerializedName;

/** Open-Meteo forecast API (current block). */
public class WeatherResponse {
    public Current current;

    public static class Current {
        @SerializedName("temperature_2m")
        public Double temperature;
        @SerializedName("relative_humidity_2m")
        public Double humidity;
    }
}
