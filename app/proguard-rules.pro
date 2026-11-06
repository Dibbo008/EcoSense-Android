# Gson models used by Retrofit
-keep class com.ecosense.app.data.remote.WeatherResponse { *; }
-keep class com.ecosense.app.data.remote.WeatherResponse$* { *; }
-keep class com.ecosense.app.data.remote.AirQualityResponse { *; }
-keep class com.ecosense.app.data.remote.AirQualityResponse$* { *; }
-keepattributes Signature, *Annotation*

# Hourly forecast models (Gson) and offline cache
-keep class com.ecosense.app.data.remote.HourlyWeatherResponse { *; }
-keep class com.ecosense.app.data.remote.HourlyWeatherResponse$* { *; }
-keep class com.ecosense.app.data.remote.HourlyAirResponse { *; }
-keep class com.ecosense.app.data.remote.HourlyAirResponse$* { *; }
-keep class com.ecosense.app.data.local.HourPoint { *; }
-keep class com.ecosense.app.data.local.ForecastCache$Blob { *; }
