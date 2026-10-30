package com.ecosense.app.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static volatile WeatherApi api;

    private ApiClient() {}

    public static WeatherApi getApi() {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build();
                    api = new Retrofit.Builder()
                            .baseUrl("https://api.open-meteo.com/")
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(WeatherApi.class);
                }
            }
        }
        return api;
    }
}
