package com.auroranotesnative.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Gemini REST client for demo usage.
 * Uses local in-memory notes and returns model text.
 */
public final class GeminiClient {
    private static final String MODEL = "gemini-2.5-flash";

    public String generateText(String apiKey, String prompt) throws IOException {
        return generateText(apiKey, prompt, 256);
    }

    /**
     * @param maxOutputTokens cap for model output (use higher values for longer translations).
     */
    public String generateText(String apiKey, String prompt, int maxOutputTokens) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Gemini API key is empty");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL
                + ":generateContent?key="
                + apiKey.trim();

        JSONObject body = new JSONObject();
        try {
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("role", "user");
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);

            body.put("contents", contents);
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.2);
            generationConfig.put("maxOutputTokens", Math.max(64, Math.min(maxOutputTokens, 2048)));
            body.put("generationConfig", generationConfig);
        } catch (JSONException e) {
            // Should never happen with static prompt structure.
            throw new IOException("Failed to build request JSON", e);
        }

        return doPostAndExtract(url, body.toString());
    }

    private static String doPostAndExtract(String urlString, String jsonBody) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String respText = readStream(is);

            if (code < 200 || code >= 300) {
                throw new IOException("Gemini request failed: HTTP " + code + " " + respText);
            }

            return extractText(respText);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String extractText(String responseBody) throws IOException {
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return "";

            JSONObject cand0 = candidates.optJSONObject(0);
            if (cand0 == null) return "";

            JSONObject content = cand0.optJSONObject("content");
            if (content == null) return "";

            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) return "";

            JSONObject part0 = parts.optJSONObject(0);
            if (part0 == null) return "";

            return part0.optString("text", "");
        } catch (JSONException e) {
            throw new IOException("Failed to parse Gemini response JSON", e);
        }
    }
}

