package com.auroranotesnative.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.ai.GeminiApiKeys;
import com.auroranotesnative.ai.GeminiClient;
import com.auroranotesnative.ai.SemanticSearchPromptService;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.databinding.ActivityAiChatBinding;
import com.auroranotesnative.model.Note;
import com.auroranotesnative.util.RichEditorHelper;

import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class AiChatActivity extends AppCompatActivity {
    private static final String TAG = "AiChatActivity";
    private static final int MAX_RESULTS = 14;

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

        binding.btnSend.setOnClickListener(v -> onSendClicked());
    }

    private void onSendClicked() {
        String prompt = binding.etPrompt.getText() == null
                ? ""
                : binding.etPrompt.getText().toString().trim();

        if (TextUtils.isEmpty(prompt)) {
            Toast.makeText(this, R.string.ai_chat_need_input, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.etPrompt.setText("");
        binding.btnSend.setEnabled(false);
        binding.tvSearchIntro.setVisibility(View.GONE);

        binding.messageContainer.removeAllViews();
        addUserBubble(prompt);

        new Thread(() -> {
            String apiKey = GeminiApiKeys.getKeyOrEmpty(this);
            boolean geminiConfigured = GeminiApiKeys.isConfigured(this);

            String interpretedQuery = "";
            int semanticCap = 8;
            boolean interpretFailed = false;

            try {
                SemanticSearchPromptService.Result interpreted;
                if (geminiConfigured) {
                    interpreted = promptService.interpretPromptToSearch(apiKey, prompt);
                } else {
                    interpreted = new SemanticSearchPromptService.Result(prompt, 6);
                }
                interpretedQuery = interpreted.searchQuery == null ? "" : interpreted.searchQuery.trim();
                semanticCap = Math.max(5, Math.min(12, interpreted.topN));
            } catch (IOException e) {
                Log.e(TAG, "Gemini interpret failed, using raw prompt", e);
                interpretFailed = true;
                interpretedQuery = prompt;
                semanticCap = 8;
            }

            String effectiveForHybrid = TextUtils.isEmpty(interpretedQuery) ? prompt : interpretedQuery;
            List<Note> results = NoteRepository.searchHybrid(
                    AiChatActivity.this,
                    prompt,
                    effectiveForHybrid,
                    MAX_RESULTS,
                    semanticCap
            );

            final String effectiveQueryFinal = effectiveForHybrid;
            final List<Note> finalResults = results;
            final boolean showGeminiOff = !geminiConfigured;
            final boolean showInterpretFallback = interpretFailed && geminiConfigured;

            runOnUiThread(() -> {
                addResultsPanel(
                        prompt,
                        effectiveQueryFinal,
                        finalResults,
                        showGeminiOff,
                        showInterpretFallback
                );
                binding.btnSend.setEnabled(true);
            });
        }).start();
    }

    private void addUserBubble(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(15f);
        tv.setLineSpacing(0f, 1.25f);
        tv.setPadding(dp(18), dp(12), dp(18), dp(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END;
        params.bottomMargin = dp(12);
        tv.setLayoutParams(params);
        tv.setBackgroundResource(R.drawable.bg_message_user);
        tv.setTextColor(getColor(android.R.color.white));

        binding.messageContainer.addView(tv);
        scrollToBottomAsync();
    }

    private void addResultsPanel(
            String userPrompt,
            String effectiveQuery,
            List<Note> notes,
            boolean geminiOff,
            boolean interpretFallback
    ) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(0xFFE2E8F0);
        card.setCardBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(14), dp(16), dp(16));

        TextView headline = new TextView(this);
        int n = notes == null ? 0 : notes.size();
        headline.setText(getString(R.string.ai_search_headline, n));
        headline.setTextSize(16f);
        headline.setTextColor(0xFF0F172A);
        headline.setTypeface(headline.getTypeface(), Typeface.BOLD);
        inner.addView(headline);

        if (!TextUtils.isEmpty(effectiveQuery)
                && !effectiveQuery.trim().equalsIgnoreCase(userPrompt.trim())) {
            TextView sub = new TextView(this);
            sub.setText(getString(R.string.ai_search_interpreted_line, effectiveQuery.trim()));
            sub.setTextSize(12f);
            sub.setTextColor(0xFF64748B);
            sub.setPadding(0, dp(4), 0, 0);
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            inner.addView(sub);
        }

        if (geminiOff) {
            TextView tip = new TextView(this);
            tip.setText(R.string.ai_search_mode_no_gemini);
            tip.setTextSize(12f);
            tip.setTextColor(0xFF6366F1);
            tip.setPadding(0, dp(8), 0, 0);
            inner.addView(tip);
        } else if (interpretFallback) {
            TextView tip = new TextView(this);
            tip.setText(R.string.ai_search_interpret_failed);
            tip.setTextSize(12f);
            tip.setTextColor(0xFFB45309);
            tip.setPadding(0, dp(8), 0, 0);
            inner.addView(tip);
        }

        if (notes == null || notes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.ai_search_empty);
            empty.setTextSize(14f);
            empty.setTextColor(0xFF475569);
            empty.setPadding(0, dp(12), 0, 0);
            empty.setLineSpacing(0f, 1.35f);
            inner.addView(empty);
        } else {
            TextView dividerLabel = new TextView(this);
            dividerLabel.setText(R.string.ai_search_results_label);
            dividerLabel.setTextSize(11f);
            dividerLabel.setTextColor(0xFF94A3B8);
            dividerLabel.setPadding(0, dp(12), 0, dp(6));
            inner.addView(dividerLabel);

            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                inner.addView(buildResultRow(note, userPrompt, effectiveQuery, i < notes.size() - 1));
            }
        }

        card.addView(inner);
        binding.messageContainer.addView(card);
        scrollToBottomAsync();
    }

    private View buildResultRow(Note note, String userPrompt, String effectiveQuery, boolean drawDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(10);
        int padV = dp(10);
        row.setPadding(padH, padV, padH, padV);

        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        TextView title = new TextView(this);
        String titleText = note.getTitle().trim().isEmpty()
                ? getString(R.string.untitled_note)
                : note.getTitle().trim();
        title.setText(titleText);
        title.setTextSize(15f);
        title.setTextColor(0xFF0F172A);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView snippet = new TextView(this);
        snippet.setText(buildSnippet(note, userPrompt, effectiveQuery));
        snippet.setTextSize(13f);
        snippet.setTextColor(0xFF64748B);
        snippet.setMaxLines(2);
        snippet.setEllipsize(android.text.TextUtils.TruncateAt.END);
        snippet.setPadding(0, dp(4), 0, 0);
        snippet.setLineSpacing(0f, 1.25f);

        row.addView(title);
        row.addView(snippet);

        if (drawDivider) {
            View line = new View(this);
            line.setBackgroundColor(0xFFE2E8F0);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
            );
            lineLp.topMargin = dp(2);
            line.setLayoutParams(lineLp);
            row.addView(line);
        }

        row.setOnClickListener(v -> openEdit(note));
        return row;
    }

    private String buildSnippet(Note note, String userPrompt, String effectiveQuery) {
        String plain = RichEditorHelper.toPlainText(note.getContent());
        String fromQuery = snippetAroundNeedle(plain, userPrompt);
        if (!TextUtils.isEmpty(fromQuery)) {
            return fromQuery;
        }
        if (!TextUtils.isEmpty(effectiveQuery)
                && !effectiveQuery.trim().equalsIgnoreCase(userPrompt.trim())) {
            String second = snippetAroundNeedle(plain, effectiveQuery);
            if (!TextUtils.isEmpty(second)) {
                return second;
            }
        }
        String summary = SummaryEngine.summarize(note.getTitle(), plain);
        if (!TextUtils.isEmpty(summary)) {
            return summary;
        }
        return safePreview(plain);
    }

    private static String snippetAroundNeedle(String plain, String needleRaw) {
        if (TextUtils.isEmpty(plain) || TextUtils.isEmpty(needleRaw)) {
            return "";
        }
        String needle = needleRaw.trim();
        if (needle.length() < 2) {
            return "";
        }
        if (needle.length() > 48) {
            needle = needle.substring(0, 48);
        }
        String p = plain;
        int idx = p.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return "";
        }
        int start = Math.max(0, idx - 36);
        int end = Math.min(p.length(), idx + needle.length() + 48);
        String frag = p.substring(start, end).replace('\n', ' ').trim();
        String prefix = start > 0 ? "…" : "";
        String suffix = end < p.length() ? "…" : "";
        return prefix + frag + suffix;
    }

    private String safePreview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.replace('\n', ' ').trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }
        return trimmed.substring(0, 100) + "…";
    }

    private void openEdit(Note note) {
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra(NotesActivity.EXTRA_NOTE, note);
        startActivity(intent);
    }

    private void scrollToBottomAsync() {
        binding.scrollMessages.post(() -> binding.scrollMessages.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
