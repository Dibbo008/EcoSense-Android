package com.ecosense.app.data.remote;

import com.google.gson.annotations.SerializedName;

/** Open-Meteo air-quality API (current block). */
public class AirQualityResponse {
    public Current current;

    public static class Current {
        @SerializedName("pm10")
        public Double pm10;
        @SerializedName("pm2_5")
        public Double pm25;
        @SerializedName("us_aqi")
        public Double usAqi;
    }
}
