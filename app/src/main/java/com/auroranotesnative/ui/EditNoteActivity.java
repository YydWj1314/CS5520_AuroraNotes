package com.auroranotesnative.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.auroranotesnative.R;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.databinding.ActivityEditNoteBinding;
import com.auroranotesnative.model.Note;

public class EditNoteActivity extends AppCompatActivity {
    private ActivityEditNoteBinding binding;
    private Note note;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final long AUTOSAVE_DEBOUNCE_MS = 700;
    private static final long SUMMARY_DEBOUNCE_MS = 300;

    private boolean persisted = false;
    private boolean dirty = false;

    private Runnable pendingAutoSave;
    private Runnable pendingSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditNoteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Object extra = getIntent().getSerializableExtra(NotesActivity.EXTRA_NOTE);
        if (extra instanceof Note) {
            note = (Note) extra;
        } else {
            note = NoteRepository.createEmpty();
        }

        binding.etTitle.setText(note.getTitle());
        binding.etContent.setText(note.getContent());
        binding.cbPinned.setChecked(note.isPinned());

        // If this note exists in the repository, treat it as persisted (autosave can update it).
        for (Note existing : NoteRepository.getAll(this)) {
            if (existing.getId() == note.getId()) {
                persisted = true;
                break;
            }
        }

        wireListeners();
        updateSummaryText();

        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveNowAndFinish());
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
                scheduleSummary();
                scheduleAutoSave();
            }
        };

        binding.etTitle.addTextChangedListener(watcher);
        binding.etContent.addTextChangedListener(watcher);

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

        String content = binding.etContent.getText() == null
                ? ""
                : binding.etContent.getText().toString().trim();

        note.setTitle(title);
        note.setContent(content);
    }

    private void scheduleAutoSave() {
        if (pendingAutoSave != null) {
            handler.removeCallbacks(pendingAutoSave);
        }

        pendingAutoSave = () -> {
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

    private boolean shouldPersistNote() {
        if (persisted) return true;

        String title = note.getTitle() == null ? "" : note.getTitle().trim();
        String content = note.getContent() == null ? "" : note.getContent().trim();

        // For brand-new notes: avoid inserting an entirely empty record.
        return !TextUtils.isEmpty(title) || !TextUtils.isEmpty(content);
    }

    private void updateSummaryText() {
        String summary = SummaryEngine.summarize(note.getTitle(), note.getContent());
        if (summary == null || summary.isEmpty()) {
            binding.tvSummary.setText(getString(R.string.summary_placeholder));
            return;
        }
        binding.tvSummary.setText(summary);
    }

    private void saveNowAndFinish() {
        String title = binding.etTitle.getText() == null
                ? ""
                : binding.etTitle.getText().toString().trim();

        String content = binding.etContent.getText() == null
                ? ""
                : binding.etContent.getText().toString().trim();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            Toast.makeText(this, "Please enter a title or note content", Toast.LENGTH_SHORT).show();
            return;
        }

        note.setTitle(title);
        note.setContent(content);
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

        if (dirty && shouldPersistNote()) {
            note.setUpdatedAt(System.currentTimeMillis());
            NoteRepository.save(this, note);
            persisted = true;
            dirty = false;
        }
    }
}