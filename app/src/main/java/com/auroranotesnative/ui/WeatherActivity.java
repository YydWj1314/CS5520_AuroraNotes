package com.auroranotesnative.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.ai.GeminiApiKeys;
import com.auroranotesnative.ai.GeminiClient;
import com.auroranotesnative.databinding.ActivityWeatherBinding;
import com.auroranotesnative.weather.WeatherClient;

import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;

public class WeatherActivity extends AppCompatActivity {

    private ActivityWeatherBinding binding;
    private WeatherClient.WeatherResult lastWeather;
    private String lastBaseText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWeatherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnFetchWeather.setOnClickListener(v -> {
            String city = binding.etCity.getText() == null ? "" : binding.etCity.getText().toString().trim();
            if (TextUtils.isEmpty(city)) {
                Toast.makeText(this, R.string.weather_error_empty_city, Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnFetchWeather.setEnabled(false);
            binding.btnGeminiBrief.setEnabled(false);
            lastWeather = null;
            lastBaseText = null;
            new Thread(() -> {
                try {
                    WeatherClient.WeatherResult r = WeatherClient.fetchCurrentWeather(city);
                    String text = formatResult(r);
                    runOnUiThread(() -> {
                        lastWeather = r;
                        lastBaseText = text;
                        binding.tvWeatherResult.setText(text);
                        binding.btnFetchWeather.setEnabled(true);
                        binding.btnGeminiBrief.setEnabled(true);
                    });
                } catch (IOException | JSONException e) {
                    runOnUiThread(() -> {
                        lastWeather = null;
                        lastBaseText = null;
                        binding.tvWeatherResult.setText(getString(R.string.weather_error, e.getMessage()));
                        binding.btnFetchWeather.setEnabled(true);
                        binding.btnGeminiBrief.setEnabled(false);
                        Toast.makeText(this, R.string.weather_error_toast, Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        binding.btnGeminiBrief.setOnClickListener(v -> {
            if (lastWeather == null || lastBaseText == null) {
                Toast.makeText(this, R.string.weather_error_empty_city, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!GeminiApiKeys.isConfigured(this)) {
                Toast.makeText(this, R.string.weather_gemini_need_key, Toast.LENGTH_LONG).show();
                return;
            }
            binding.btnGeminiBrief.setEnabled(false);
            binding.btnFetchWeather.setEnabled(false);
            binding.tvWeatherResult.setText(lastBaseText + "\n\n" + getString(R.string.weather_gemini_loading));

            String apiKey = GeminiApiKeys.getKeyOrEmpty(this);
            String prompt = buildBriefPrompt(lastWeather);
            new Thread(() -> {
                try {
                    GeminiClient client = new GeminiClient();
                    String advice = client.generateText(apiKey, prompt, 384);
                    if (advice == null) {
                        advice = "";
                    }
                    advice = advice.trim();
                    String finalAdvice = advice;
                    String composed = lastBaseText + "\n\n--- "
                            + getString(R.string.weather_gemini_section) + " ---\n" + finalAdvice;
                    runOnUiThread(() -> {
                        binding.tvWeatherResult.setText(composed);
                        binding.btnGeminiBrief.setEnabled(true);
                        binding.btnFetchWeather.setEnabled(true);
                    });
                } catch (IOException e) {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    runOnUiThread(() -> {
                        binding.tvWeatherResult.setText(lastBaseText + "\n\n"
                                + getString(R.string.weather_gemini_failed, msg));
                        binding.btnGeminiBrief.setEnabled(true);
                        binding.btnFetchWeather.setEnabled(true);
                        Toast.makeText(this, R.string.weather_error_toast, Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
    }

    private static String buildBriefPrompt(WeatherClient.WeatherResult r) {
        return "You help a university student plan their day.\n"
                + "Weather facts:\n"
                + "- Location: " + r.locationLabel + "\n"
                + "- Temperature (C): " + r.tempCelsius + "\n"
                + "- Conditions: " + r.conditionSummary + "\n"
                + "- Humidity (%): " + r.humidityPercent + "\n"
                + "- Wind (km/h): " + r.windKmh + "\n\n"
                + "Reply with 3-5 short bullet lines: what to wear, umbrella yes/no, "
                + "and one tip for studying or commuting. Be practical. English only.";
    }

    private String formatResult(WeatherClient.WeatherResult r) {
        return getString(R.string.weather_result_template,
                r.locationLabel,
                r.tempCelsius,
                r.conditionSummary,
                r.humidityPercent,
                r.windKmh,
                String.format(Locale.US, "%.4f", r.latitude),
                String.format(Locale.US, "%.4f", r.longitude));
    }
}
