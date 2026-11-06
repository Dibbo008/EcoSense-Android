package com.ecosense.app.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApi {
    // Absolute URLs override the Retrofit base URL, so one interface serves both hosts.
    @GET("https://api.open-meteo.com/v1/forecast")
    Call<WeatherResponse> getWeather(@Query("latitude") double lat,
                                     @Query("longitude") double lon,
                                     @Query("current") String fields);

    @GET("https://api.open-meteo.com/v1/forecast")
    Call<HourlyWeatherResponse> getHourlyWeather(@Query("latitude") double lat,
                                                 @Query("longitude") double lon,
                                                 @Query("hourly") String fields,
                                                 @Query("forecast_days") int days,
                                                 @Query("timezone") String timezone);

    @GET("https://air-quality-api.open-meteo.com/v1/air-quality")
    Call<HourlyAirResponse> getHourlyAir(@Query("latitude") double lat,
                                         @Query("longitude") double lon,
                                         @Query("hourly") String fields,
                                         @Query("forecast_days") int days,
                                         @Query("timezone") String timezone);

    @GET("https://air-quality-api.open-meteo.com/v1/air-quality")
    Call<AirQualityResponse> getAirQuality(@Query("latitude") double lat,
                                           @Query("longitude") double lon,
                                           @Query("current") String fields);
}
