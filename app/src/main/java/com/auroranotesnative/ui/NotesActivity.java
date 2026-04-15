package com.auroranotesnative.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.PopupMenu;

import com.google.android.material.snackbar.Snackbar;
import com.auroranotesnative.R;
import com.auroranotesnative.data.NoteRepository;
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
                // LiveData observer will refresh automatically
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityNotesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnMore.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, binding.btnMore);
            popupMenu.getMenuInflater().inflate(R.menu.notes_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
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

            popupMenu.show();
        });

        adapter = new NotesAdapter(this::openEdit);
        binding.recyclerNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNotes.setAdapter(adapter);

        setupSwipeToDeleteWithConfirm();

        binding.fabAdd.setOnClickListener(v -> openEdit(NoteRepository.createEmpty()));

        binding.fabAiChat.setOnClickListener(v ->
                startActivity(new Intent(this, AiChatActivity.class))
        );

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

        NoteRepository.observeAll(this).observe(this, notes -> renderList());

        renderList();
    }

    private void setupSwipeToDeleteWithConfirm() {
        ItemTouchHelper.SimpleCallback swipeCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getAdapterPosition();
                        Note noteToDelete = adapter.getNoteAt(position);

                        showDeleteConfirmDialog(noteToDelete, position);
                    }
                };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerNotes);
    }

    private void showDeleteConfirmDialog(Note note, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete note")
                .setMessage("Are you sure you want to delete this note?")
                .setNegativeButton("Cancel", (dialog, which) -> {
                    adapter.notifyItemChanged(position);
                    dialog.dismiss();
                })
                .setPositiveButton("Delete", (dialog, which) -> {
                    NoteRepository.delete(this, note);

                    Snackbar.make(binding.recyclerNotes, "Note deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo", v -> {
                                note.setId(0); // re-insert as a new row
                                NoteRepository.save(this, note);
                            })
                            .show();
                })
                .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                .show();
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
            items = NoteRepository.getAll(this);
        } else {
            items = NoteRepository.searchSemantic(this, trimmed);
        }

        adapter.submitList(items);
    }

    private void signOut() {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_SIGNED_IN, false).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}