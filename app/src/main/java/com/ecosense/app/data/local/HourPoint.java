package com.ecosense.app.data.local;

/** One forecast hour (air quality + weather). Serialized to JSON for the offline cache. */
public class HourPoint {
    public long time;        // epoch millis (start of the hour)
    public double temp;      // Celsius
    public double humidity;  // percent
    public double pm25;
    public double pm10;
    public int aqi;          // US AQI

    public HourPoint() {}
}
