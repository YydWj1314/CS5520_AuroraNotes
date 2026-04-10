package com.auroranotesnative.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextWatcher;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.R;
import com.auroranotesnative.databinding.ActivityNotesBinding;
import com.auroranotesnative.model.Note;

import java.util.List;

public class NotesActivity extends AppCompatActivity {
    public static final String EXTRA_NOTE = "extra_note";
    private static final String AUTH_PREFS = "auth_prefs";
    private static final String KEY_IS_SIGNED_IN = "is_signed_in";

    private ActivityNotesBinding binding;
    private NotesAdapter adapter;

    private String currentQuery = "";

    private final ActivityResultLauncher<Intent> editNoteLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Notes are persisted via in-app autosave; repository observer will refresh the list.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityNotesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.inflateMenu(R.menu.notes_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_sign_out) {
                signOut();
                return true;
            }
            if (id == R.id.action_weather) {
                startActivity(new Intent(this, WeatherActivity.class));
                return true;
            }
            if (id == R.id.action_translate) {
                startActivity(new Intent(this, TranslateActivity.class));
                return true;
            }
            return false;
        });

        adapter = new NotesAdapter(this::openEdit);
        binding.recyclerNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNotes.setAdapter(adapter);

        binding.fabAdd.setOnClickListener(v -> openEdit(NoteRepository.createEmpty()));
        binding.fabAiChat.setOnClickListener(v -> startActivity(new Intent(this, AiChatActivity.class)));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                renderList();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });

        // Real-time refresh: when notes are autosaved from the editor, update this list too.
        NoteRepository.observeAll().observe(this, notes -> renderList());

        // Initial render.
        renderList();
    }

    private void openEdit(Note note) {
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra(EXTRA_NOTE, note);
        editNoteLauncher.launch(intent);
    }

    private void renderList() {
        String trimmed = currentQuery == null ? "" : currentQuery.trim();

        List<Note> items;
        if (trimmed.isEmpty()) {
            items = NoteRepository.getAll();
        } else if (isAiPrompt(trimmed)) {
            items = NoteRepository.applyAiPrompt(trimmed);
        } else {
            items = NoteRepository.searchSemantic(trimmed);
        }

        adapter.submitList(items);
    }

    private boolean isAiPrompt(String prompt) {
        String normalized = prompt.toLowerCase();
        return normalized.startsWith("ai:") || normalized.startsWith("/ai");
    }

    private void signOut() {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_SIGNED_IN, false).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
