package com.auroranotesnative.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.databinding.ActivityTranslateBinding;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

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

        binding.btnTranslate.setOnClickListener(v -> runTranslation(binding));
    }

    private void runTranslation(ActivityTranslateBinding binding) {
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

        binding.btnTranslate.setEnabled(false);
        binding.tvTranslated.setText(R.string.translate_downloading);

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
                            binding.btnTranslate.setEnabled(true);
                            translator.close();
                        })
                        .addOnFailureListener(failure -> {
                            showFailure(binding, failure);
                            translator.close();
                        }))
                .addOnFailureListener(failure -> {
                    showFailure(binding, failure);
                    translator.close();
                });
    }

    private void showFailure(ActivityTranslateBinding binding, Exception e) {
        binding.btnTranslate.setEnabled(true);
        binding.tvTranslated.setText(R.string.translate_result_placeholder);
        Toast.makeText(this, getString(R.string.translate_error_failed, e.getMessage()), Toast.LENGTH_LONG).show();
    }
}
