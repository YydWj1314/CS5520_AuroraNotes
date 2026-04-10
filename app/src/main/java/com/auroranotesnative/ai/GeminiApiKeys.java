package com.auroranotesnative.ai;

import com.auroranotesnative.R;

import android.content.Context;

/**
 * Shared checks for whether the demo Gemini key is configured.
 */
public final class GeminiApiKeys {

    private GeminiApiKeys() {
    }

    public static boolean isConfigured(Context context) {
        String key = context.getString(R.string.gemini_api_key);
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        return !key.contains("REPLACE_WITH_YOUR_GEMINI_API_KEY");
    }

    public static String getKeyOrEmpty(Context context) {
        return context.getString(R.string.gemini_api_key).trim();
    }
}
