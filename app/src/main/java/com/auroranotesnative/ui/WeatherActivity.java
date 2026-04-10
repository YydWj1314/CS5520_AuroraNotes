package com.auroranotesnative.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.databinding.ActivityWeatherBinding;
import com.auroranotesnative.weather.WeatherClient;

import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;

public class WeatherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityWeatherBinding binding = ActivityWeatherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnFetchWeather.setOnClickListener(v -> {
            String city = binding.etCity.getText() == null ? "" : binding.etCity.getText().toString().trim();
            if (TextUtils.isEmpty(city)) {
                Toast.makeText(this, R.string.weather_error_empty_city, Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnFetchWeather.setEnabled(false);
            new Thread(() -> {
                try {
                    WeatherClient.WeatherResult r = WeatherClient.fetchCurrentWeather(city);
                    String text = formatResult(r);
                    runOnUiThread(() -> {
                        binding.tvWeatherResult.setText(text);
                        binding.btnFetchWeather.setEnabled(true);
                    });
                } catch (IOException | JSONException e) {
                    runOnUiThread(() -> {
                        binding.tvWeatherResult.setText(getString(R.string.weather_error, e.getMessage()));
                        binding.btnFetchWeather.setEnabled(true);
                        Toast.makeText(this, R.string.weather_error_toast, Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
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
