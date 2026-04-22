package com.auroranotesnative.ui;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.databinding.ActivityReviewBinding;
import com.auroranotesnative.model.Note;
import com.auroranotesnative.util.RichEditorHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReviewActivity extends AppCompatActivity {

    private static final long MS_PER_DAY = 86_400_000L;
    private static final Pattern STUDY_LINE = Pattern.compile(
            "^\\s*(.{1,40}?)\\s*[-–—:=]\\s*(.+)$"
    );
    private static final Pattern TAG_STUDY = Pattern.compile("#(vocab|lang|word|phrase)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityReviewBinding binding = ActivityReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        renderReviewPlan(binding);
    }

    private void renderReviewPlan(ActivityReviewBinding binding) {
        List<Note> notes = NoteRepository.getAll(this);
        binding.tvReviewMeta.setText(getString(R.string.review_plan_header, notes.size()));

        binding.tvReviewDue.setText(buildDueSoonSection(notes));
        binding.tvReviewVocab.setText(buildVocabSection(notes));
        binding.tvReviewHigh.setText(buildHighPrioritySection(notes));
        binding.tvReviewRegular.setText(buildRegularSection(notes));
    }

    private String buildDueSoonSection(List<Note> notes) {
        long now = System.currentTimeMillis();
        long today0 = startOfLocalDayMillis(now);
        long lastDay0 = startOfLocalDayMillis(now + 7 * MS_PER_DAY);
        List<String> lines = new ArrayList<>();
        DateFormat df = android.text.format.DateFormat.getMediumDateFormat(this);

        for (Note n : notes) {
            long due = n.getDueDateMillis();
            if (due <= 0L) {
                continue;
            }
            long dueDay0 = startOfLocalDayMillis(due);
            if (dueDay0 < today0 || dueDay0 > lastDay0) {
                continue;
            }
            String label = !TextUtils.isEmpty(n.getReminderText())
                    ? n.getReminderText().trim()
                    : (n.getTitle().trim().isEmpty() ? getString(R.string.untitled_note) : n.getTitle().trim());
            lines.add("• " + label + " — " + df.format(due));
        }
        if (lines.isEmpty()) {
            return getString(R.string.review_empty_due);
        }
        return joinLines(lines);
    }

    private String buildVocabSection(List<Note> notes) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();

        for (Note n : notes) {
            String plain = RichEditorHelper.toPlainText(n.getContent());
            if (plain.isEmpty()) {
                continue;
            }
            String prefix = n.getTitle().trim().isEmpty()
                    ? getString(R.string.untitled_note)
                    : n.getTitle().trim();

            for (String rawLine : plain.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.length() < 3) {
                    continue;
                }
                boolean tagged = TAG_STUDY.matcher(line).find();
                boolean pair = STUDY_LINE.matcher(line).matches() && line.length() <= 120;
                if (!tagged && !pair) {
                    continue;
                }
                String entry = "(" + prefix + ") " + stripWikiLinks(line);
                if (entry.length() > 140) {
                    entry = entry.substring(0, 137) + "…";
                }
                String key = entry.toLowerCase(Locale.ROOT);
                if (seen.add(key) && lines.size() < 14) {
                    lines.add("• " + entry);
                }
            }
        }
        if (lines.isEmpty()) {
            return getString(R.string.review_empty_vocab);
        }
        return joinLines(lines);
    }

    private String buildHighPrioritySection(List<Note> notes) {
        List<String> lines = new ArrayList<>();
        for (Note n : notes) {
            if (isHighPriority(n)) {
                lines.add("• " + formatNoteLine(n));
            }
        }
        if (lines.isEmpty()) {
            return getString(R.string.review_empty_high);
        }
        return joinLines(lines);
    }

    private String buildRegularSection(List<Note> notes) {
        List<String> lines = new ArrayList<>();
        for (Note n : notes) {
            if (!isHighPriority(n)) {
                lines.add("• " + formatNoteLine(n));
            }
            if (lines.size() >= 6) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return getString(R.string.review_empty_regular);
        }
        return joinLines(lines);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private String formatNoteLine(Note n) {
        String title = n.getTitle().trim();
        if (TextUtils.isEmpty(title)) {
            title = getString(R.string.untitled_note);
        }
        String plain = RichEditorHelper.toPlainText(n.getContent());
        String summary = SummaryEngine.summarize(n.getTitle(), plain);
        if (TextUtils.isEmpty(summary)) {
            summary = plain;
        }
        summary = stripWikiLinks(summary == null ? "" : summary.trim());
        if (summary.length() > 88) {
            summary = summary.substring(0, 85) + "…";
        }
        return title + " → " + summary;
    }

    private static String stripWikiLinks(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        Matcher m = Pattern.compile("\\[\\[([^\\]]+)]]").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String inner = m.group(1) == null ? "" : m.group(1).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(inner));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isHighPriority(Note n) {
        if (n.isPinned()) {
            return true;
        }
        String text = (n.getTitle() + " " + RichEditorHelper.toPlainText(n.getContent())).toLowerCase(Locale.ROOT);
        return text.contains("exam")
                || text.contains("quiz")
                || text.contains("deadline")
                || text.contains("urgent")
                || text.contains("tomorrow")
                || text.contains("today");
    }

    private static long startOfLocalDayMillis(long referenceMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(referenceMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
