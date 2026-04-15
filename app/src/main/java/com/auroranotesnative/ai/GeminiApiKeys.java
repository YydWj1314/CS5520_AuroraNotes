package com.auroranotesnative.ai;

import com.auroranotesnative.BuildConfig;

import android.content.Context;

/**
 * Shared checks for whether the demo Gemini key is configured.
 */
public final class GeminiApiKeys {

    private GeminiApiKeys() {
    }

    public static boolean isConfigured(Context context) {
        String key = BuildConfig.GEMINI_API_KEY;
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        return !key.contains("REPLACE_WITH_YOUR_GEMINI_API_KEY");
    }

    public static String getKeyOrEmpty(Context context) {
        String key = BuildConfig.GEMINI_API_KEY;
        return key == null ? "" : key.trim();
    }
}
