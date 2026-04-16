package com.auroranotesnative.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NotesActivity extends AppCompatActivity {
    public static final String EXTRA_NOTE = "extra_note";
    private static final String AUTH_PREFS = "auth_prefs";
    private static final String KEY_IS_SIGNED_IN = "is_signed_in";
    private static final String NOTES_UI_PREFS = "notes_ui";
    private static final String KEY_SORT_MODE = "sort_mode";

    private enum SortMode {
        NEWEST,
        OLDEST,
        ALPHA,
        PINNED_ONLY;

        static SortMode fromOrdinal(int ordinal) {
            SortMode[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return NEWEST;
            }
            return values[ordinal];
        }
    }

    private ActivityNotesBinding binding;
    private NotesAdapter adapter;
    private SortMode sortMode = SortMode.NEWEST;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 280;

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

        sortMode = loadSortMode();

        binding.btnMore.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, binding.btnMore);
            popupMenu.getMenuInflater().inflate(R.menu.notes_menu, popupMenu.getMenu());
            applySortCheckMarks(popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.sort_newest) {
                    setSortMode(SortMode.NEWEST);
                    return true;
                }
                if (id == R.id.sort_oldest) {
                    setSortMode(SortMode.OLDEST);
                    return true;
                }
                if (id == R.id.sort_alpha) {
                    setSortMode(SortMode.ALPHA);
                    return true;
                }
                if (id == R.id.sort_pinned_only) {
                    setSortMode(SortMode.PINNED_ONLY);
                    return true;
                }

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
                scheduleRenderList();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });

        NoteRepository.observeAll(this).observe(this, notes -> renderList());

        renderList();
    }

    private SortMode loadSortMode() {
        SharedPreferences prefs = getSharedPreferences(NOTES_UI_PREFS, MODE_PRIVATE);
        return SortMode.fromOrdinal(prefs.getInt(KEY_SORT_MODE, SortMode.NEWEST.ordinal()));
    }

    private void setSortMode(SortMode mode) {
        sortMode = mode;
        getSharedPreferences(NOTES_UI_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_SORT_MODE, mode.ordinal())
                .apply();
        renderList();
    }

    private void applySortCheckMarks(Menu menu) {
        MenuItem anchor = menu.findItem(R.id.menu_sort_anchor);
        if (anchor == null || !anchor.hasSubMenu()) {
            return;
        }
        SubMenu sub = anchor.getSubMenu();
        int checkId = R.id.sort_newest;
        switch (sortMode) {
            case NEWEST:
                checkId = R.id.sort_newest;
                break;
            case OLDEST:
                checkId = R.id.sort_oldest;
                break;
            case ALPHA:
                checkId = R.id.sort_alpha;
                break;
            case PINNED_ONLY:
                checkId = R.id.sort_pinned_only;
                break;
            default:
                break;
        }
        for (int i = 0; i < sub.size(); i++) {
            MenuItem mi = sub.getItem(i);
            mi.setChecked(mi.getItemId() == checkId);
        }
    }

    private static String titleSortKey(Note n) {
        String t = n.getTitle() == null ? "" : n.getTitle().trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? "\uFFFF" : t;
    }

    private void applySortMode(List<Note> items) {
        switch (sortMode) {
            case NEWEST:
                items.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
                break;
            case OLDEST:
                items.sort(Comparator.comparingLong(Note::getUpdatedAt));
                break;
            case ALPHA:
                items.sort(Comparator.comparing(NotesActivity::titleSortKey));
                break;
            case PINNED_ONLY:
                items.removeIf(n -> !n.isPinned());
                items.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
                break;
            default:
                break;
        }
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

        int totalNotes = NoteRepository.getAll(this).size();

        List<Note> items = new ArrayList<>();
        if (trimmed.isEmpty()) {
            items.addAll(NoteRepository.getAll(this));
        } else {
            items.addAll(NoteRepository.searchSemantic(this, trimmed));
        }

        applySortMode(items);

        adapter.submitList(items);

        boolean empty = items.isEmpty();
        binding.layoutEmptyNotes.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerNotes.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (empty) {
            if (!trimmed.isEmpty()) {
                binding.tvEmptyTitle.setText(R.string.notes_empty_search_title);
                binding.tvEmptySubtitle.setText(R.string.notes_empty_search_subtitle);
            } else if (sortMode == SortMode.PINNED_ONLY && totalNotes > 0) {
                binding.tvEmptyTitle.setText(R.string.notes_empty_pinned_title);
                binding.tvEmptySubtitle.setText(R.string.notes_empty_pinned_subtitle);
            } else {
                binding.tvEmptyTitle.setText(R.string.notes_empty_title);
                binding.tvEmptySubtitle.setText(R.string.notes_empty_subtitle);
            }
        }

        if (trimmed.isEmpty()) {
            binding.tvNoteCount.setText(getString(R.string.note_count_all, items.size()));
        } else {
            binding.tvNoteCount.setText(
                    getString(R.string.note_count_search_with_total, items.size(), totalNotes));
        }
    }

    private void scheduleRenderList() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = this::renderList;
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void signOut() {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_SIGNED_IN, false).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
    }
}