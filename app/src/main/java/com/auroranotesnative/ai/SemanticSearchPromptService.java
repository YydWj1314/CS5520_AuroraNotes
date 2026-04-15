package com.auroranotesnative.ai;

import android.content.Context;

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
    private final Context context;

    public SemanticSearchPromptService(Context context, GeminiClient geminiClient) {
        this.context = context.getApplicationContext();
        this.geminiClient = geminiClient;
    }

    public Result interpretPromptToSearch(String apiKey, String userPrompt) throws IOException {
        if (userPrompt == null) userPrompt = "";
        String cleaned = userPrompt.trim();
        if (cleaned.isEmpty()) {
            System.out.println("[SemanticSearch] Empty user prompt, skip Gemini.");
            return new Result("", 5);
        }

        // If the api key is missing, keep the app runnable by falling back.
        if (apiKey == null || apiKey.trim().isEmpty()
                || apiKey.contains("REPLACE_WITH_YOUR_GEMINI_API_KEY")) {
            System.out.println("[SemanticSearch] Invalid API key, fallback only. No Gemini call.");
            return new Result(cleaned, 5);
        }

        String notesCatalog = buildTruncatedCatalog();

        String prompt =
                "You are a search assistant for a note-taking app.\n"
                        + "Convert the user's natural language request into a short semantic search query for local note retrieval.\n"
                        + "\n"
                        + "Important goal:\n"
                        + "- Preserve the user's intent, not just shorten the sentence.\n"
                        + "- Keep important semantic concepts if they matter for retrieval.\n"
                        + "\n"
                        + "Rules:\n"
                        + "- Remove filler words and unnecessary grammar words.\n"
                        + "- Keep the core intent and important concepts.\n"
                        + "- Prefer concise keyword-style output, but do NOT over-compress.\n"
                        + "- Preserve urgency concepts when present or implied: urgent, asap, priority, deadline.\n"
                        + "- Preserve task/action concepts when present or implied: task, submit, assignment, review, onboarding.\n"
                        + "- Preserve time scope when present or implied: today, tomorrow, week, month.\n"
                        + "- If multiple important concepts appear, keep all of them.\n"
                        + "- Return only JSON with exactly these keys:\n"
                        + "  searchQuery (string)\n"
                        + "  topN (number between 3 and 10)\n"
                        + "\n"
                        + "Examples:\n"
                        + "User: what are the urgent things I need to do today\n"
                        + "Output: {\"searchQuery\":\"urgent tasks today priority\",\"topN\":5}\n"
                        + "\n"
                        + "User: what do I need to finish asap tomorrow\n"
                        + "Output: {\"searchQuery\":\"urgent tasks tomorrow deadline asap\",\"topN\":5}\n"
                        + "\n"
                        + "User: what assignments do I need to submit this week\n"
                        + "Output: {\"searchQuery\":\"submit assignment week deadline tasks\",\"topN\":5}\n"
                        + "\n"
                        + "User: show me notes about hash maps and duplicate detection\n"
                        + "Output: {\"searchQuery\":\"hash maps duplicate detection\",\"topN\":5}\n"
                        + "\n"
                        + "Notes catalog (for context):\n"
                        + notesCatalog + "\n\n"
                        + "User request:\n"
                        + cleaned + "\n";

        System.out.println("[SemanticSearch] Calling Gemini...");
        String modelText = geminiClient.generateText(apiKey, prompt);
        System.out.println("[SemanticSearch] Gemini raw response: " + modelText);

        return parseModelResult(modelText, cleaned);
    }

    private String buildTruncatedCatalog() {
        String catalog = NoteRepository.buildAiCatalog(context);

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