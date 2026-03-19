package com.auroranotesnative.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
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
    private ActivityAiChatBinding binding;

    private final GeminiClient geminiClient = new GeminiClient();
    private final SemanticSearchPromptService promptService = new SemanticSearchPromptService(geminiClient);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Seed message to guide the demo.
        addAiMessage(getString(R.string.ai_chat_empty));

        binding.btnSend.setOnClickListener(v -> onSendClicked());
    }

    private void onSendClicked() {
        String prompt = binding.etPrompt.getText() == null ? "" : binding.etPrompt.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) {
            Toast.makeText(this, "Type a prompt first", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.etPrompt.setText("");
        binding.btnSend.setEnabled(false);

        addUserMessage(prompt);

        new Thread(() -> {
            String apiKey = getString(R.string.gemini_api_key);
            List<Note> results = new ArrayList<>();
            String finalQuery = "";

            try {
                SemanticSearchPromptService.Result interpreted = promptService.interpretPromptToSearch(apiKey, prompt);
                finalQuery = interpreted.searchQuery == null ? "" : interpreted.searchQuery.trim();
                results = NoteRepository.searchSemantic(finalQuery);

                // Bound top-N for output.
                if (interpreted.topN > 0 && results.size() > interpreted.topN) {
                    results = results.subList(0, interpreted.topN);
                }
            } catch (IOException e) {
                // If Gemini fails (network or key), do local semantic search with the raw prompt.
                finalQuery = prompt;
                results = NoteRepository.searchSemantic(prompt);
            }

            final String queryToShow = finalQuery;
            final List<Note> finalResults = results;

            runOnUiThread(() -> {
                addAiMessage("Searching for: " + (TextUtils.isEmpty(queryToShow) ? prompt : queryToShow));

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
        TextView header = buildMessageText("Top matches:", false);
        header.setPadding(header.getPaddingLeft(), header.getPaddingTop() + 8, header.getPaddingRight(), header.getPaddingBottom());
        binding.messageContainer.addView(header);

        for (Note note : notes) {
            String title = note.getTitle().isEmpty() ? "Untitled" : note.getTitle();
            String summary = SummaryEngine.summarize(note.getTitle(), note.getContent());
            if (!TextUtils.isEmpty(summary)) {
                title = title + " - " + summary;
            }

            TextView tv = new TextView(this);
            tv.setText(title);
            tv.setTextSize(14f);
            tv.setPadding(16, 10, 16, 10);
            tv.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
            tv.setOnClickListener(v -> openEdit(note));
            binding.messageContainer.addView(tv);
        }

        scrollToBottomAsync();
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
        tv.setPadding(16, 10, 16, 10);
        tv.setBackgroundResource(isUser
                ? android.R.drawable.alert_light_frame
                : android.R.drawable.dialog_holo_light_frame);
        return tv;
    }

    private void scrollToBottomAsync() {
        binding.scrollMessages.post(() -> binding.scrollMessages.fullScroll(View.FOCUS_DOWN));
    }
}

