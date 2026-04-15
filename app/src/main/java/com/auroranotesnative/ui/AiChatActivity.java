package com.auroranotesnative.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.auroranotesnative.R;
import com.auroranotesnative.ai.GeminiApiKeys;
import com.auroranotesnative.ai.GeminiClient;
import com.auroranotesnative.ai.SemanticSearchPromptService;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.databinding.ActivityAiChatBinding;
import com.auroranotesnative.model.Note;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends AppCompatActivity {
    private static final String TAG = "AiChatActivity";

    private ActivityAiChatBinding binding;

    private final GeminiClient geminiClient = new GeminiClient();
    private SemanticSearchPromptService promptService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationIcon(com.google.android.material.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        promptService = new SemanticSearchPromptService(this, geminiClient);

        addAiMessage(getString(R.string.ai_chat_empty));

        binding.btnSend.setOnClickListener(v -> onSendClicked());
    }

    private void onSendClicked() {
        String prompt = binding.etPrompt.getText() == null
                ? ""
                : binding.etPrompt.getText().toString().trim();

        if (TextUtils.isEmpty(prompt)) {
            Toast.makeText(this, "Type a prompt first", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.etPrompt.setText("");
        binding.btnSend.setEnabled(false);

        addUserMessage(prompt);

        new Thread(() -> {
            String apiKey = GeminiApiKeys.getKeyOrEmpty(this);
            List<Note> results = new ArrayList<>();
            String finalQuery = "";
            boolean geminiEnabled = GeminiApiKeys.isConfigured(this);

            try {
                SemanticSearchPromptService.Result interpreted;
                if (geminiEnabled) {
                    interpreted = promptService.interpretPromptToSearch(apiKey, prompt);
                } else {
                    interpreted = new SemanticSearchPromptService.Result(prompt, 5);
                }

                finalQuery = interpreted.searchQuery == null
                        ? ""
                        : interpreted.searchQuery.trim();

                NoteRepository.SemanticSearchResult semanticResult =
                        NoteRepository.searchSemanticWithAdaptiveTopN(
                                AiChatActivity.this,
                                finalQuery
                        );

                results = semanticResult.notes;

                int adaptiveTopN = semanticResult.suggestedTopN;
                if (adaptiveTopN > 0 && results.size() > adaptiveTopN) {
                    results = results.subList(0, adaptiveTopN);
                }

            } catch (IOException e) {
                Log.e(TAG, "Gemini failed, falling back to raw prompt search", e);

                finalQuery = prompt;

                NoteRepository.SemanticSearchResult semanticResult =
                        NoteRepository.searchSemanticWithAdaptiveTopN(
                                AiChatActivity.this,
                                prompt
                        );

                results = semanticResult.notes;

                int adaptiveTopN = semanticResult.suggestedTopN;
                if (adaptiveTopN > 0 && results.size() > adaptiveTopN) {
                    results = results.subList(0, adaptiveTopN);
                }
            }

            final String queryToShow = finalQuery;
            final List<Note> finalResults = results;
            final boolean isGeminiEnabled = geminiEnabled;

            runOnUiThread(() -> {
                if (!isGeminiEnabled) {
                    addAiMessage(getString(R.string.ai_missing_key));
                }
                addAiMessage("Searching for: "
                        + (TextUtils.isEmpty(queryToShow) ? prompt : queryToShow));

                if (finalResults == null || finalResults.isEmpty()) {
                    addAiMessage("No matches found.");
                } else {
                    addAiResults(finalResults);
                }

                binding.btnSend.setEnabled(true);
            });
        }).start();
    }

    private void addUserMessage(String message) {
        TextView tv = buildMessageText(message, true);
        binding.messageContainer.addView(tv);
        scrollToBottomAsync();
    }

    private void addAiMessage(String message) {
        TextView tv = buildMessageText(message, false);
        binding.messageContainer.addView(tv);
        scrollToBottomAsync();
    }

    private void addAiResults(List<Note> notes) {
        TextView header = buildSectionTitle("Top matches");
        binding.messageContainer.addView(header);

        for (Note note : notes) {
            CardView card = new CardView(this);
            card.setRadius(24f);
            card.setCardElevation(6f);
            card.setUseCompatPadding(true);
            card.setCardBackgroundColor(0xFFFFFFFF);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 20);
            card.setLayoutParams(cardParams);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(28, 24, 28, 24);

            TextView titleView = new TextView(this);
            titleView.setText(note.getTitle().isEmpty() ? "Untitled" : note.getTitle());
            titleView.setTextSize(16f);
            titleView.setTextColor(0xFF0F172A);
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);

            String summary = SummaryEngine.summarize(note.getTitle(), note.getContent());

            TextView summaryView = new TextView(this);
            summaryView.setText(summary == null || summary.isEmpty()
                    ? safePreview(note.getContent())
                    : summary);
            summaryView.setTextSize(14f);
            summaryView.setTextColor(0xFF475569);
            summaryView.setLineSpacing(0f, 1.2f);
            summaryView.setPadding(0, 12, 0, 0);

            container.addView(titleView);
            container.addView(summaryView);
            card.addView(container);

            card.setOnClickListener(v -> openEdit(note));
            binding.messageContainer.addView(card);
        }

        scrollToBottomAsync();
    }

    private TextView buildSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(0xFF64748B);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 8, 8, 16);
        tv.setLayoutParams(params);
        return tv;
    }

    private String safePreview(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.length() <= 120) return trimmed;
        return trimmed.substring(0, 120) + "...";
    }

    private void openEdit(Note note) {
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra(NotesActivity.EXTRA_NOTE, note);
        startActivity(intent);
    }

    private TextView buildMessageText(String message, boolean isUser) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(15f);
        tv.setLineSpacing(0f, 1.2f);
        tv.setPadding(24, 16, 24, 16);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 20);

        if (isUser) {
            params.gravity = Gravity.END;
            tv.setBackgroundResource(R.drawable.bg_message_user);
            tv.setTextColor(getColor(android.R.color.white));
        } else {
            params.gravity = Gravity.START;
            tv.setBackgroundResource(R.drawable.bg_message_ai);
            tv.setTextColor(0xFF0F172A);
        }

        tv.setLayoutParams(params);
        return tv;
    }

    private void scrollToBottomAsync() {
        binding.scrollMessages.post(() -> binding.scrollMessages.fullScroll(View.FOCUS_DOWN));
    }
}