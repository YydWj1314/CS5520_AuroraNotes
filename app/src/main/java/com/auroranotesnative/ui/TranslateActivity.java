package com.auroranotesnative.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.ai.GeminiApiKeys;
import com.auroranotesnative.ai.GeminiClient;
import com.auroranotesnative.databinding.ActivityTranslateBinding;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TranslateActivity extends AppCompatActivity {

    private static final class LangItem {
        final String label;
        final String mlKitCode;

        LangItem(String label, String mlKitCode) {
            this.label = label;
            this.mlKitCode = mlKitCode;
        }
    }

    private static final LangItem[] LANGS = {
            new LangItem("English", TranslateLanguage.ENGLISH),
            new LangItem("Chinese", TranslateLanguage.CHINESE),
            new LangItem("Spanish", TranslateLanguage.SPANISH),
            new LangItem("French", TranslateLanguage.FRENCH),
            new LangItem("German", TranslateLanguage.GERMAN),
            new LangItem("Japanese", TranslateLanguage.JAPANESE),
            new LangItem("Korean", TranslateLanguage.KOREAN),
            new LangItem("Italian", TranslateLanguage.ITALIAN),
            new LangItem("Portuguese", TranslateLanguage.PORTUGUESE),
            new LangItem("Hindi", TranslateLanguage.HINDI),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityTranslateBinding binding = ActivityTranslateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        List<String> labels = new ArrayList<>();
        for (LangItem item : LANGS) {
            labels.add(item.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        binding.spinnerFrom.setAdapter(adapter);
        binding.spinnerTo.setAdapter(adapter);
        binding.spinnerFrom.setSelection(0);
        binding.spinnerTo.setSelection(1);

        binding.btnTranslate.setOnClickListener(v -> runOnDeviceTranslation(binding));
        binding.btnTranslateGemini.setOnClickListener(v -> runGeminiTranslation(binding));
    }

    private void runOnDeviceTranslation(ActivityTranslateBinding binding) {
        CharSequence src = binding.etSource.getText();
        String text = src == null ? "" : src.toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.translate_error_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        int fromIdx = binding.spinnerFrom.getSelectedItemPosition();
        int toIdx = binding.spinnerTo.getSelectedItemPosition();
        String sourceLang = LANGS[fromIdx].mlKitCode;
        String targetLang = LANGS[toIdx].mlKitCode;

        if (sourceLang.equals(targetLang)) {
            Toast.makeText(this, R.string.translate_error_same_lang, Toast.LENGTH_SHORT).show();
            return;
        }

        setTranslatingUi(binding, true, getString(R.string.translate_downloading));

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build();
        final Translator translator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> translator.translate(text)
                        .addOnSuccessListener(result -> {
                            binding.tvTranslated.setText(result);
                            setTranslatingUi(binding, false, null);
                            translator.close();
                        })
                        .addOnFailureListener(failure -> {
                            showOnDeviceFailure(binding, failure);
                            translator.close();
                        }))
                .addOnFailureListener(failure -> {
                    showOnDeviceFailure(binding, failure);
                    translator.close();
                });
    }

    private void runGeminiTranslation(ActivityTranslateBinding binding) {
        CharSequence src = binding.etSource.getText();
        String text = src == null ? "" : src.toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.translate_error_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        int fromIdx = binding.spinnerFrom.getSelectedItemPosition();
        int toIdx = binding.spinnerTo.getSelectedItemPosition();
        if (LANGS[fromIdx].mlKitCode.equals(LANGS[toIdx].mlKitCode)) {
            Toast.makeText(this, R.string.translate_error_same_lang, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!GeminiApiKeys.isConfigured(this)) {
            Toast.makeText(this, R.string.translate_gemini_need_key, Toast.LENGTH_LONG).show();
            return;
        }

        String fromLabel = LANGS[fromIdx].label;
        String toLabel = LANGS[toIdx].label;
        String apiKey = GeminiApiKeys.getKeyOrEmpty(this);
        String prompt = buildGeminiTranslatePrompt(fromLabel, toLabel, text);

        setTranslatingUi(binding, true, getString(R.string.translate_gemini_loading));
        new Thread(() -> {
            try {
                GeminiClient client = new GeminiClient();
                String out = client.generateText(apiKey, prompt, 768);
                if (out == null) {
                    out = "";
                }
                out = out.trim();
                String finalOut = out;
                runOnUiThread(() -> {
                    binding.tvTranslated.setText(finalOut);
                    setTranslatingUi(binding, false, null);
                });
            } catch (IOException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                runOnUiThread(() -> {
                    binding.tvTranslated.setText(R.string.translate_result_placeholder);
                    setTranslatingUi(binding, false, null);
                    Toast.makeText(this, getString(R.string.translate_gemini_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static String buildGeminiTranslatePrompt(String fromLabel, String toLabel, String text) {
        return "You are a professional translator. Translate the following text from "
                + fromLabel + " to " + toLabel + ". Output only the translated text. "
                + "Do not add quotes, labels, or explanations.\n\n" + text;
    }

    private void setTranslatingUi(ActivityTranslateBinding binding, boolean busy, String statusText) {
        binding.btnTranslate.setEnabled(!busy);
        binding.btnTranslateGemini.setEnabled(!busy);
        if (busy && statusText != null) {
            binding.tvTranslated.setText(statusText);
        }
    }

    private void showOnDeviceFailure(ActivityTranslateBinding binding, Exception e) {
        setTranslatingUi(binding, false, null);
        binding.tvTranslated.setText(R.string.translate_result_placeholder);
        String msg = e.getMessage() == null ? "" : e.getMessage();
        Toast.makeText(this, getString(R.string.translate_error_failed, msg), Toast.LENGTH_LONG).show();
    }
}
