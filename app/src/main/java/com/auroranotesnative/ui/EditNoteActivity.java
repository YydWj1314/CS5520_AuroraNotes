package com.auroranotesnative.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Calendar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.util.RichEditorHelper;

import com.auroranotesnative.ai.GeminiApiKeys;
import com.auroranotesnative.ai.GeminiClient;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.databinding.ActivityEditNoteBinding;
import com.auroranotesnative.model.Note;
import com.auroranotesnative.model.NoteAccentPalette;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EditNoteActivity extends AppCompatActivity {
    private ActivityEditNoteBinding binding;
    private Note note;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GeminiClient geminiClient = new GeminiClient();

    private static final long AUTOSAVE_DEBOUNCE_MS = 700;
    private static final long SUMMARY_DEBOUNCE_MS = 300;
    private static final long LINKS_DEBOUNCE_MS = 350;

    private boolean persisted = false;
    private boolean dirty = false;

    private Runnable pendingAutoSave;
    private Runnable pendingSummary;
    private Runnable pendingLinksRefresh;

    private boolean dueSpinnerProgrammatic;

    private static final long MS_PER_DAY = 86_400_000L;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException ignored) {
                }
                note.setImageUri(uri.toString());
                dirty = true;
                renderAttachedImage();
                scheduleAutoSave();
            });

    private final ActivityResultLauncher<String[]> importFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::appendImportedText);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditNoteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationIcon(com.google.android.material.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        Object extra = getIntent().getSerializableExtra(NotesActivity.EXTRA_NOTE);
        if (extra instanceof Note) {
            note = (Note) extra;
        } else {
            note = NoteRepository.createEmpty();
        }

        binding.etTitle.setText(note.getTitle());
        RichEditorHelper.setHtmlOrPlain(binding.etContent, note.getContent());
        binding.cbPinned.setChecked(note.isPinned());
        binding.etReminderItem.setText(note.getReminderText());
        setupDuePresetSpinner();
        binding.btnPickDueDate.setOnClickListener(v -> showDueDatePicker());
        binding.btnAddLinkedNote.setOnClickListener(v -> showPickLinkedNoteDialog());

        for (Note existing : NoteRepository.getAll(this)) {
            if (existing.getId() == note.getId()) {
                persisted = true;
                break;
            }
        }

        binding.etContent.setMovementMethod(ScrollingMovementMethod.getInstance());
        binding.etContent.setVerticalScrollBarEnabled(true);

        wireListeners();
        setupScrollForTextSelection();
        setupColorChips();
        updateWordCharCount();
        updateSummaryText();
        updateLinkPanels();
        renderAttachedImage();

        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveNowAndFinish());
        binding.btnAttachImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnRemoveImage.setOnClickListener(v -> {
            note.setImageUri("");
            dirty = true;
            renderAttachedImage();
            scheduleAutoSave();
        });

        binding.btnFormatBold.setOnClickListener(v -> applyFormat(() -> RichEditorHelper.toggleBold(binding.etContent)));
        binding.btnFormatItalic.setOnClickListener(v -> applyFormat(() -> RichEditorHelper.toggleItalic(binding.etContent)));
        binding.btnFormatLarger.setOnClickListener(v -> applyFormat(() -> RichEditorHelper.enlargeSelection(binding.etContent)));

        binding.btnImportText.setOnClickListener(v ->
                importFileLauncher.launch(new String[]{"text/*", "text/plain", "application/json", "*/*"}));
        binding.btnAiSummary.setOnClickListener(v -> runAiSummary());
        binding.btnAiCheck.setOnClickListener(v -> runAiWritingCheck());
        binding.btnRefreshLocalSummary.setOnClickListener(v -> updateSummaryText());
    }

    private void applyFormat(Runnable op) {
        int start = binding.etContent.getSelectionStart();
        int end = binding.etContent.getSelectionEnd();
        if (start == end) {
            Toast.makeText(this, R.string.edit_select_text_first, Toast.LENGTH_SHORT).show();
            return;
        }
        op.run();
        syncNoteFromUi();
        dirty = true;
        updateWordCharCount();
        scheduleSummary();
        scheduleLinksRefresh();
        scheduleAutoSave();
    }

    private void appendImportedText(Uri uri) {
        if (uri == null) return;
        try {
            String text = readUtf8FromUri(uri);
            if (text.trim().isEmpty()) {
                Toast.makeText(this, R.string.translate_error_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            Editable ed = binding.etContent.getText();
            int pos = binding.etContent.getSelectionStart();
            if (pos < 0 || pos > ed.length()) {
                pos = ed.length();
            }
            ed.insert(pos, (pos > 0 ? "\n\n" : "") + text.trim() + "\n");
            dirty = true;
            updateWordCharCount();
            scheduleSummary();
            scheduleLinksRefresh();
            scheduleAutoSave();
        } catch (IOException e) {
            if ("binary".equals(e.getMessage())) {
                Toast.makeText(this, R.string.edit_import_binary, Toast.LENGTH_LONG).show();
                return;
            }
            String msg = e.getMessage() == null ? "" : e.getMessage();
            Toast.makeText(this, getString(R.string.edit_import_failed, msg), Toast.LENGTH_LONG).show();
        }
    }

    private String readUtf8FromUri(Uri uri) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) {
            return "";
        }
        try (InputStream in = is) {
            byte[] data = readAllBytes(in);
            if (looksLikeBinary(data)) {
                throw new IOException("binary");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static boolean looksLikeBinary(byte[] data) {
        int n = Math.min(data.length, 8192);
        if (n == 0) {
            return false;
        }
        int nul = 0;
        for (int i = 0; i < n; i++) {
            if (data[i] == 0) {
                nul++;
            }
        }
        return nul > 2;
    }

    private void runAiSummary() {
        if (!GeminiApiKeys.isConfigured(this)) {
            Toast.makeText(this, R.string.edit_ai_need_key, Toast.LENGTH_LONG).show();
            return;
        }
        syncNoteFromUi();
        String title = binding.etTitle.getText() == null ? "" : binding.etTitle.getText().toString().trim();
        String bodyPlain = RichEditorHelper.toPlainText(note.getContent());
        if (title.isEmpty() && bodyPlain.trim().isEmpty()) {
            Toast.makeText(this, R.string.login_error_empty_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.btnAiSummary.setEnabled(false);
        binding.tvSummary.setText(getString(R.string.edit_ai_summary_loading));

        String apiKey = GeminiApiKeys.getKeyOrEmpty(this);
        String prompt = "Summarize this student note in 2–4 short sentences. Plain text only, no markdown.\n"
                + "Title: " + title + "\nBody:\n" + bodyPlain;

        new Thread(() -> {
            try {
                String out = geminiClient.generateText(apiKey, prompt, 512);
                String cleaned = out == null ? "" : out.trim();
                runOnUiThread(() -> {
                    binding.tvSummary.setText(cleaned.isEmpty() ? getString(R.string.summary_placeholder) : cleaned);
                    binding.btnAiSummary.setEnabled(true);
                });
            } catch (IOException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                runOnUiThread(() -> {
                    binding.tvSummary.setText(getString(R.string.edit_ai_summary_failed, msg));
                    binding.btnAiSummary.setEnabled(true);
                });
            }
        }).start();
    }

    private void runAiWritingCheck() {
        if (!GeminiApiKeys.isConfigured(this)) {
            Toast.makeText(this, R.string.edit_ai_need_key, Toast.LENGTH_LONG).show();
            return;
        }
        syncNoteFromUi();
        String body = RichEditorHelper.toPlainText(note.getContent()).trim();
        if (body.isEmpty()) {
            Toast.makeText(this, R.string.translate_error_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.btnAiCheck.setEnabled(false);

        String apiKey = GeminiApiKeys.getKeyOrEmpty(this);
        String prompt = "You are an English writing tutor. Read the student's note text below.\n"
                + "List up to 6 concrete suggestions (grammar, spelling, clarity). Use plain text, numbered 1–6.\n"
                + "If it looks fine, reply with a single line: Looks good.\n\n---\n" + body;

        new Thread(() -> {
            try {
                String out = geminiClient.generateText(apiKey, prompt, 640);
                String cleaned = out == null ? "" : out.trim();
                runOnUiThread(() -> {
                    binding.btnAiCheck.setEnabled(true);
                    new AlertDialog.Builder(EditNoteActivity.this)
                            .setTitle(R.string.edit_ai_check_title)
                            .setMessage(cleaned.isEmpty() ? getString(R.string.edit_ai_check_failed, "") : cleaned)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            } catch (IOException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                runOnUiThread(() -> {
                    binding.btnAiCheck.setEnabled(true);
                    Toast.makeText(EditNoteActivity.this, getString(R.string.edit_ai_check_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setupDuePresetSpinner() {
        String[] items = new String[] {
                getString(R.string.due_preset_none),
                getString(R.string.due_preset_today),
                getString(R.string.due_preset_tomorrow),
                getString(R.string.due_preset_week),
                getString(R.string.due_preset_pick),
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                items
        );
        binding.spDuePreset.setAdapter(adapter);

        dueSpinnerProgrammatic = true;
        binding.spDuePreset.setSelection(spinnerIndexForDueMillis(note.getDueDateMillis()));

        binding.spDuePreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (dueSpinnerProgrammatic) {
                    return;
                }
                if (position == 0) {
                    note.setDueDateMillis(0L);
                } else if (position == 1) {
                    note.setDueDateMillis(startOfLocalDayMillis(System.currentTimeMillis()));
                } else if (position == 2) {
                    note.setDueDateMillis(startOfLocalDayMillis(System.currentTimeMillis() + MS_PER_DAY));
                } else if (position == 3) {
                    note.setDueDateMillis(startOfLocalDayMillis(System.currentTimeMillis() + 7 * MS_PER_DAY));
                } else {
                    showDueDatePicker();
                    return;
                }
                refreshDueDateUi();
                dirty = true;
                scheduleAutoSave();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        binding.spDuePreset.post(() -> dueSpinnerProgrammatic = false);

        refreshDueDateUi();
    }

    private void refreshDueDateUi() {
        boolean on = note.getDueDateMillis() > 0L;
        binding.layoutDueDateRow.setVisibility(on ? View.VISIBLE : View.GONE);
        updateDueDateLabel();
    }

    private void syncSpinnerFromDueMillis() {
        dueSpinnerProgrammatic = true;
        binding.spDuePreset.setSelection(spinnerIndexForDueMillis(note.getDueDateMillis()));
        binding.spDuePreset.post(() -> dueSpinnerProgrammatic = false);
        refreshDueDateUi();
    }

    private int spinnerIndexForDueMillis(long dueMillis) {
        if (dueMillis <= 0L) {
            return 0;
        }
        long t0 = startOfLocalDayMillis(System.currentTimeMillis());
        long t1 = startOfLocalDayMillis(System.currentTimeMillis() + MS_PER_DAY);
        long t7 = startOfLocalDayMillis(System.currentTimeMillis() + 7 * MS_PER_DAY);
        if (sameLocalDay(dueMillis, t0)) {
            return 1;
        }
        if (sameLocalDay(dueMillis, t1)) {
            return 2;
        }
        if (sameLocalDay(dueMillis, t7)) {
            return 3;
        }
        return 4;
    }

    private static boolean sameLocalDay(long aMillis, long bMillis) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(aMillis);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(bMillis);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void updateDueDateLabel() {
        long due = note.getDueDateMillis();
        if (due <= 0L) {
            binding.tvDueDateValue.setText("");
            return;
        }
        java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(this);
        binding.tvDueDateValue.setText(df.format(due));
    }

    private void showDueDatePicker() {
        Calendar cal = Calendar.getInstance();
        long due = note.getDueDateMillis();
        if (due > 0L) {
            cal.setTimeInMillis(due);
        }
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);
        new DatePickerDialog(
                this,
                (view, year, monthOfYear, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.clear();
                    picked.set(year, monthOfYear, dayOfMonth);
                    note.setDueDateMillis(picked.getTimeInMillis());
                    syncSpinnerFromDueMillis();
                    dirty = true;
                    scheduleAutoSave();
                },
                y,
                m,
                d
        ).show();
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

    private void showPickLinkedNoteDialog() {
        syncNoteFromUi();
        List<Note> all = NoteRepository.getAll(this);
        List<String> labels = new ArrayList<>();
        List<Note> choices = new ArrayList<>();
        for (Note n : all) {
            if (note.getId() != 0 && n.getId() == note.getId()) {
                continue;
            }
            String t = n.getTitle().trim();
            if (t.isEmpty()) {
                continue;
            }
            labels.add(t);
            choices.add(n);
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, R.string.edit_link_no_targets, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_add_link_note)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> insertWikiLinkForNote(choices.get(which)))
                .show();
    }

    private void insertWikiLinkForNote(Note target) {
        String title = target.getTitle().trim();
        if (title.contains("]]")) {
            Toast.makeText(this, R.string.edit_link_invalid_title, Toast.LENGTH_SHORT).show();
            return;
        }
        String insert = "[[" + title + "]]";
        Editable ed = binding.etContent.getText();
        int pos = binding.etContent.getSelectionStart();
        if (pos < 0 || pos > ed.length()) {
            pos = ed.length();
        }
        ed.insert(pos, (pos > 0 && ed.charAt(Math.max(0, pos - 1)) != '\n' ? " " : "") + insert + " ");
        dirty = true;
        updateWordCharCount();
        scheduleLinksRefresh();
        scheduleAutoSave();
    }

    private void setupScrollForTextSelection() {
        ViewGroup scroll = (ViewGroup) binding.getRoot();
        binding.etContent.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    scroll.requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scroll.requestDisallowInterceptTouchEvent(false);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void wireListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                syncNoteFromUi();
                dirty = true;
                updateWordCharCount();
                scheduleSummary();
                scheduleLinksRefresh();
                scheduleAutoSave();
            }
        };

        binding.etTitle.addTextChangedListener(watcher);
        binding.etContent.addTextChangedListener(watcher);
        binding.etReminderItem.addTextChangedListener(watcher);

        binding.cbPinned.setOnCheckedChangeListener((buttonView, isChecked) -> {
            note.setPinned(isChecked);
            dirty = true;
            scheduleAutoSave();
        });
    }

    private void syncNoteFromUi() {
        String title = binding.etTitle.getText() == null
                ? ""
                : binding.etTitle.getText().toString().trim();

        Editable body = binding.etContent.getText();
        String html = body != null ? RichEditorHelper.contentToHtml(body) : "";

        String reminder = binding.etReminderItem.getText() == null
                ? ""
                : binding.etReminderItem.getText().toString().trim();

        note.setTitle(title);
        note.setContent(html);
        note.setReminderText(reminder);
    }

    private void updateWordCharCount() {
        String title = binding.etTitle.getText() == null ? "" : binding.etTitle.getText().toString();
        String content = binding.etContent.getText() == null ? "" : binding.etContent.getText().toString();
        String combined = title + content;
        int chars = combined.length();
        int words = countWords(combined);
        binding.tvWordCharCount.setText(getString(R.string.note_word_char_count, words, chars));
    }

    private static int countWords(String s) {
        if (s == null) {
            return 0;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return 0;
        }
        return t.split("\\s+").length;
    }

    private void setupColorChips() {
        LinearLayout row = binding.layoutColorChips;
        row.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int marginPx = Math.round(6 * density);
        int sizePx = Math.round(28 * density);

        addColorChip(row, 0, sizePx, marginPx, density);
        for (int c : NoteAccentPalette.CHOICES) {
            addColorChip(row, c, sizePx, marginPx, density);
        }
    }

    private void addColorChip(LinearLayout row, int color, int sizePx, int marginPx, float density) {
        View chip = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.setMargins(marginPx, 0, marginPx, 0);
        chip.setLayoutParams(lp);
        chip.setTag(color);
        styleColorChip(chip, color, note.getColorTag() == color, density);
        chip.setOnClickListener(v -> {
            note.setColorTag(color);
            dirty = true;
            refreshColorChipStyles(row, density);
            scheduleAutoSave();
        });
        row.addView(chip);
    }

    private void refreshColorChipStyles(LinearLayout row, float density) {
        int selected = note.getColorTag();
        for (int i = 0; i < row.getChildCount(); i++) {
            View chip = row.getChildAt(i);
            Object tag = chip.getTag();
            if (!(tag instanceof Integer)) {
                continue;
            }
            int color = (Integer) tag;
            styleColorChip(chip, color, color == selected, density);
        }
    }

    private static void styleColorChip(View chip, int color, boolean selected, float density) {
        int strokePx = Math.max(1, Math.round(density * 3));
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        if (color == 0) {
            gd.setColor(0xFFE2E8F0);
            if (selected) {
                gd.setStroke(strokePx, 0xFF6366F1);
            } else {
                gd.setStroke(Math.max(1, Math.round(density)), 0xFFCBD5E1);
            }
        } else {
            gd.setColor(color);
            if (selected) {
                gd.setStroke(Math.max(2, Math.round(density * 3)), Color.WHITE);
            } else {
                gd.setStroke(0, Color.TRANSPARENT);
            }
        }
        chip.setBackground(gd);
    }

    private void scheduleAutoSave() {
        if (pendingAutoSave != null) {
            handler.removeCallbacks(pendingAutoSave);
        }

        pendingAutoSave = () -> {
            syncNoteFromUi();
            if (!shouldPersistNote()) return;

            note.setUpdatedAt(System.currentTimeMillis());
            NoteRepository.save(this, note);

            persisted = true;
            dirty = false;
        };

        handler.postDelayed(pendingAutoSave, AUTOSAVE_DEBOUNCE_MS);
    }

    private void scheduleSummary() {
        if (pendingSummary != null) {
            handler.removeCallbacks(pendingSummary);
        }

        pendingSummary = this::updateSummaryText;
        handler.postDelayed(pendingSummary, SUMMARY_DEBOUNCE_MS);
    }

    private void scheduleLinksRefresh() {
        if (pendingLinksRefresh != null) {
            handler.removeCallbacks(pendingLinksRefresh);
        }
        pendingLinksRefresh = this::updateLinkPanels;
        handler.postDelayed(pendingLinksRefresh, LINKS_DEBOUNCE_MS);
    }

    private boolean shouldPersistNote() {
        String title = note.getTitle() == null ? "" : note.getTitle().trim();
        String plain = RichEditorHelper.toPlainText(note.getContent());

        return !TextUtils.isEmpty(title)
                || !TextUtils.isEmpty(plain)
                || !TextUtils.isEmpty(note.getReminderText())
                || note.getColorTag() != 0
                || !TextUtils.isEmpty(note.getImageUri())
                || note.getDueDateMillis() > 0L;
    }

    private void updateSummaryText() {
        syncNoteFromUi();
        String title = note.getTitle() == null ? "" : note.getTitle().trim();
        String plain = RichEditorHelper.toPlainText(note.getContent());
        String summary = SummaryEngine.summarize(title, plain);
        if (summary == null || summary.isEmpty()) {
            binding.tvSummary.setText(getString(R.string.summary_placeholder));
            return;
        }
        binding.tvSummary.setText(summary);
    }

    private void renderAttachedImage() {
        String imageUri = note.getImageUri();
        boolean hasImage = imageUri != null && !imageUri.trim().isEmpty();
        binding.ivNoteImage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        binding.tvImagePlaceholder.setVisibility(hasImage ? View.GONE : View.VISIBLE);
        binding.btnRemoveImage.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        if (hasImage) {
            Uri u = Uri.parse(imageUri.trim());
            try (InputStream is = getContentResolver().openInputStream(u)) {
                Bitmap bm = is == null ? null : BitmapFactory.decodeStream(is);
                if (bm != null) {
                    binding.ivNoteImage.setImageBitmap(bm);
                } else {
                    binding.ivNoteImage.setImageURI(u);
                }
            } catch (Exception e) {
                try {
                    binding.ivNoteImage.setImageURI(u);
                } catch (Exception ignored) {
                    binding.ivNoteImage.setImageBitmap(null);
                }
            }
        } else {
            binding.ivNoteImage.setImageBitmap(null);
        }
    }

    private void updateLinkPanels() {
        syncNoteFromUi();
        renderNotesPanel(
                binding.layoutLinkedNotes,
                NoteRepository.getLinkedNotes(this, note),
                getString(R.string.linked_notes_empty)
        );

        binding.layoutBacklinkedNotes.removeAllViews();
        String myTitle = note.getTitle() == null ? "" : note.getTitle().trim();
        if (myTitle.isEmpty()) {
            android.widget.TextView hint = new android.widget.TextView(this);
            hint.setText(R.string.referenced_by_need_title);
            hint.setTextSize(12f);
            hint.setTextColor(0xFF64748B);
            hint.setLineSpacing(0f, 1.3f);
            binding.layoutBacklinkedNotes.addView(hint);
        } else {
            renderNotesPanel(
                    binding.layoutBacklinkedNotes,
                    NoteRepository.getBacklinkedNotes(this, note),
                    getString(R.string.backlinked_notes_empty)
            );
        }
    }

    private void renderNotesPanel(android.widget.LinearLayout container, java.util.List<Note> notes, String emptyText) {
        container.removeAllViews();
        if (notes == null || notes.isEmpty()) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText(emptyText);
            empty.setTextSize(12f);
            empty.setTextColor(0xFF64748B);
            container.addView(empty);
            return;
        }

        for (Note linkedNote : notes) {
            android.widget.TextView item = new android.widget.TextView(this);
            String title = linkedNote.getTitle().trim().isEmpty()
                    ? getString(R.string.untitled_note)
                    : linkedNote.getTitle().trim();
            item.setText("• " + title);
            item.setTextSize(13f);
            item.setTextColor(0xFF4F46E5);
            item.setPadding(0, 6, 0, 6);
            item.setOnClickListener(v -> openLinkedNote(linkedNote));
            container.addView(item);
        }
    }

    private void openLinkedNote(Note linkedNote) {
        if (dirty && shouldPersistNote()) {
            syncNoteFromUi();
            note.setUpdatedAt(System.currentTimeMillis());
            NoteRepository.save(this, note);
            persisted = true;
            dirty = false;
        }

        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra(NotesActivity.EXTRA_NOTE, linkedNote);
        startActivity(intent);
    }

    private void saveNowAndFinish() {
        syncNoteFromUi();

        if (!shouldPersistNote()) {
            Toast.makeText(this, R.string.edit_save_need_content, Toast.LENGTH_SHORT).show();
            return;
        }

        note.setPinned(binding.cbPinned.isChecked());
        note.setUpdatedAt(System.currentTimeMillis());

        NoteRepository.save(this, note);

        persisted = true;
        dirty = false;
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (pendingAutoSave != null) {
            handler.removeCallbacks(pendingAutoSave);
        }
        if (pendingLinksRefresh != null) {
            handler.removeCallbacks(pendingLinksRefresh);
        }

        if (dirty && shouldPersistNote()) {
            syncNoteFromUi();
            note.setUpdatedAt(System.currentTimeMillis());
            NoteRepository.save(this, note);
            persisted = true;
            dirty = false;
        }
    }
}
