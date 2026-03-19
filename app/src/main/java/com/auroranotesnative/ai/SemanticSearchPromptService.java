package com.auroranotesnative.ai;

import com.auroranotesnative.data.NoteRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Turns a natural-language user prompt into a semantic search query
 * using Gemini, then lets the app run local semantic search.
 */
public final class SemanticSearchPromptService {
    public static final class Result {
        public final String searchQuery;
        public final int topN;

        public Result(String searchQuery, int topN) {
            this.searchQuery = searchQuery;
            this.topN = topN;
        }
    }

    private final GeminiClient geminiClient;

    public SemanticSearchPromptService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public Result interpretPromptToSearch(String apiKey, String userPrompt) throws IOException {
        if (userPrompt == null) userPrompt = "";
        String cleaned = userPrompt.trim();
        if (cleaned.isEmpty()) {
            return new Result("", 5);
        }

        // If the api key is missing, keep the app runnable by falling back.
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("REPLACE_WITH_YOUR_GEMINI_API_KEY")) {
            return new Result(cleaned, 5);
        }

        String notesCatalog = buildTruncatedCatalog();
        String prompt =
                "You are a search assistant for a note-taking app.\n"
                        + "Your task: convert the user's natural language request into a short semantic search query.\n"
                        + "Only return a JSON object with exactly these keys:\n"
                        + "  searchQuery (string)\n"
                        + "  topN (number; between 3 and 10)\n"
                        + "searchQuery should contain only the most relevant keywords/phrases from the user's request.\n\n"
                        + "Notes catalog (for context):\n"
                        + notesCatalog + "\n\n"
                        + "User request:\n"
                        + cleaned + "\n";

        String modelText = geminiClient.generateText(apiKey, prompt);
        return parseModelResult(modelText, cleaned);
    }

    private static String buildTruncatedCatalog() {
        String catalog = NoteRepository.buildAiCatalog();
        // Keep the prompt reasonably small for demo usability.
        int max = 4200;
        if (catalog == null) return "";
        if (catalog.length() <= max) return catalog;
        return catalog.substring(0, max) + "\n... (truncated)\n";
    }

    private static Result parseModelResult(String modelText, String fallbackQuery) {
        if (modelText == null) {
            return new Result(fallbackQuery, 5);
        }

        // Gemini sometimes wraps JSON in markdown fences; strip common wrappers.
        String text = modelText.trim();
        if (text.startsWith("```")) {
            // Remove first fence line and last fence line.
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }

        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open >= 0 && close > open) {
            text = text.substring(open, close + 1);
        }

        try {
            JSONObject obj = new JSONObject(text);
            String searchQuery = obj.optString("searchQuery", fallbackQuery);
            int topN = obj.optInt("topN", 5);
            if (topN < 3) topN = 3;
            if (topN > 10) topN = 10;
            return new Result(searchQuery, topN);
        } catch (JSONException e) {
            return new Result(fallbackQuery, 5);
        }
    }
}

