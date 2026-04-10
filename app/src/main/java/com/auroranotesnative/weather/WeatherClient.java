package com.auroranotesnative.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Fetches current weather using Open-Meteo (no API key).
 */
public final class WeatherClient {

    public static final class WeatherResult {
        public final String locationLabel;
        public final double latitude;
        public final double longitude;
        public final double tempCelsius;
        public final int humidityPercent;
        public final double windKmh;
        public final String conditionSummary;

        public WeatherResult(String locationLabel, double latitude, double longitude,
                             double tempCelsius, int humidityPercent, double windKmh,
                             String conditionSummary) {
            this.locationLabel = locationLabel;
            this.latitude = latitude;
            this.longitude = longitude;
            this.tempCelsius = tempCelsius;
            this.humidityPercent = humidityPercent;
            this.windKmh = windKmh;
            this.conditionSummary = conditionSummary;
        }
    }

    private WeatherClient() {
    }

    public static WeatherResult fetchCurrentWeather(String cityQuery) throws IOException, JSONException {
        if (cityQuery == null || cityQuery.trim().isEmpty()) {
            throw new IOException("City name is empty");
        }
        String encoded = URLEncoder.encode(cityQuery.trim(), StandardCharsets.UTF_8.name());
        String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=en";
        String geoJson = httpGet(geoUrl);
        JSONObject geoRoot = new JSONObject(geoJson);
        JSONArray results = geoRoot.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new IOException("City not found");
        }
        JSONObject place = results.getJSONObject(0);
        double lat = place.getDouble("latitude");
        double lon = place.getDouble("longitude");
        String name = place.optString("name", cityQuery.trim());
        String country = place.optString("country", "");
        String admin = place.optString("admin1", "");
        String label = name;
        if (!admin.isEmpty()) {
            label += ", " + admin;
        }
        if (!country.isEmpty()) {
            label += " (" + country + ")";
        }

        String forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                + "&temperature_unit=celsius&wind_speed_unit=kmh";
        String fcJson = httpGet(forecastUrl);
        JSONObject fcRoot = new JSONObject(fcJson);
        JSONObject current = fcRoot.getJSONObject("current");
        double temp = current.getDouble("temperature_2m");
        int humidity = current.getInt("relative_humidity_2m");
        int wmo = current.getInt("weather_code");
        double wind = current.getDouble("wind_speed_10m");
        String summary = wmoToSummary(wmo);

        return new WeatherResult(label, lat, lon, temp, humidity, wind, summary);
    }

    private static String wmoToSummary(int code) {
        if (code == 0) return "Clear sky";
        if (code <= 3) return "Mostly cloudy / overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Rain showers";
        if (code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Weather code " + code;
    }

    private static String httpGet(String urlString) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + ": " + body);
            }
            return body;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
