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
import android.widget.Toast;

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
import com.auroranotesnative.ai.GeminiApiKeys;
import com.auroranotesnative.ai.GeminiClient;
import com.auroranotesnative.data.NoteRepository;
import com.auroranotesnative.databinding.ActivityNotesBinding;
import com.auroranotesnative.model.Note;
import com.auroranotesnative.weather.WeatherClient;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public class NotesActivity extends AppCompatActivity {
    public static final String EXTRA_NOTE = "extra_note";
    private static final String AUTH_PREFS = "auth_prefs";
    private static final String KEY_IS_SIGNED_IN = "is_signed_in";
    private static final String NOTES_UI_PREFS = "notes_ui";
    private static final String KEY_SORT_MODE = "sort_mode";
    private static final String KEY_HOME_CITY = "home_city";

    private final GeminiClient geminiClient = new GeminiClient();

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

    /** User tapped Hide on the due-date banner; show again next app launch. */
    private boolean dueBannerSessionDismissed;

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

        loadHomeCity();
        binding.btnHomeWeatherRefresh.setOnClickListener(v -> refreshHomeWeatherAndOutfit());
        binding.getRoot().post(this::refreshHomeWeatherAndOutfit);

        binding.btnDismissDueBanner.setOnClickListener(v -> {
            dueBannerSessionDismissed = true;
            binding.cardDueBanner.setVisibility(View.GONE);
        });

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
                if (id == R.id.action_review) {
                    startActivity(new Intent(this, ReviewActivity.class));
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

    private void loadHomeCity() {
        SharedPreferences prefs = getSharedPreferences(NOTES_UI_PREFS, MODE_PRIVATE);
        String city = prefs.getString(KEY_HOME_CITY, "").trim();
        if (city.isEmpty()) {
            city = "Boston";
        }
        binding.etHomeCity.setText(city);
    }

    private void saveHomeCity(String city) {
        getSharedPreferences(NOTES_UI_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_HOME_CITY, city.trim())
                .apply();
    }

    private void refreshHomeWeatherAndOutfit() {
        String city = binding.etHomeCity.getText() == null ? "" : binding.etHomeCity.getText().toString().trim();
        if (city.isEmpty()) {
            Toast.makeText(this, R.string.weather_error_empty_city, Toast.LENGTH_SHORT).show();
            return;
        }
        saveHomeCity(city);
        binding.btnHomeWeatherRefresh.setEnabled(false);
        binding.tvHomeTempBig.setText("…");
        binding.tvHomeLocationLine.setText(getString(R.string.home_weather_loading));
        binding.tvHomeConditionLine.setText("");
        binding.tvHomeMetaLine.setText("");
        if (GeminiApiKeys.isConfigured(this)) {
            binding.tvHomeOutfitAdvice.setText(getString(R.string.home_outfit_loading));
        } else {
            binding.tvHomeOutfitAdvice.setText(getString(R.string.home_outfit_need_key));
        }

        new Thread(() -> {
            try {
                WeatherClient.WeatherResult r = WeatherClient.fetchCurrentWeather(city);
                runOnUiThread(() -> applyWeatherToHomeCard(r));

                if (!GeminiApiKeys.isConfigured(NotesActivity.this)) {
                    runOnUiThread(() -> binding.btnHomeWeatherRefresh.setEnabled(true));
                    return;
                }

                String apiKey = GeminiApiKeys.getKeyOrEmpty(NotesActivity.this);
                String prompt = buildHomeOutfitPrompt(r);
                try {
                    String advice = geminiClient.generateText(apiKey, prompt, 640);
                    String cleaned = formatOutfitPlain(advice);
                    if (cleaned.length() < 20) {
                        cleaned = fallbackOutfitAdvice(r);
                    }
                    String finalAdvice = cleaned;
                    runOnUiThread(() -> {
                        binding.tvHomeOutfitAdvice.setText(finalAdvice);
                        binding.btnHomeWeatherRefresh.setEnabled(true);
                    });
                } catch (IOException ge) {
                    String gmsg = ge.getMessage() == null ? "" : ge.getMessage();
                    runOnUiThread(() -> {
                        binding.tvHomeOutfitAdvice.setText(getString(R.string.home_outfit_failed, gmsg));
                        binding.btnHomeWeatherRefresh.setEnabled(true);
                    });
                }
            } catch (IOException | JSONException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                runOnUiThread(() -> {
                    binding.tvHomeTempBig.setText("—");
                    binding.tvHomeLocationLine.setText(getString(R.string.weather_error, msg));
                    binding.tvHomeConditionLine.setText("");
                    binding.tvHomeMetaLine.setText("");
                    binding.tvHomeOutfitAdvice.setText("");
                    binding.btnHomeWeatherRefresh.setEnabled(true);
                });
            }
        }).start();
    }

    private void applyWeatherToHomeCard(WeatherClient.WeatherResult r) {
        binding.tvHomeWeatherEmoji.setText(weatherEmoji(r.conditionSummary));
        binding.tvHomeTempBig.setText(getString(R.string.home_temp_format, r.tempCelsius));
        binding.tvHomeLocationLine.setText(r.locationLabel);
        binding.tvHomeConditionLine.setText(r.conditionSummary);
        binding.tvHomeMetaLine.setText(getString(
                R.string.home_meta_format,
                r.humidityPercent,
                r.windKmh
        ));
    }

    private static String weatherEmoji(String conditionSummary) {
        if (conditionSummary == null) return "🌤️";
        String s = conditionSummary.toLowerCase(Locale.ROOT);
        if (s.contains("clear")) return "☀️";
        if (s.contains("cloud") || s.contains("overcast")) return "☁️";
        if (s.contains("fog")) return "🌫️";
        if (s.contains("drizzle") || s.contains("rain") || s.contains("shower")) return "🌧️";
        if (s.contains("snow")) return "❄️";
        if (s.contains("thunder")) return "⛈️";
        return "🌤️";
    }

    private static String formatOutfitPlain(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("(?i)^here'?s your concise outfit advice:?\\s*", "");
        s = s.replaceAll("(?i)Line\\s*\\d+\\s*:\\s*", "• ");
        s = s.replaceAll("\\*\\*", "");
        s = s.replaceAll("(?m)^\\s*[-*•]+\\s*", "• ");
        s = s.replaceAll("\\*{1,3}", "");
        s = s.replaceAll("#+\\s*", "");
        while (s.contains("\n\n\n")) {
            s = s.replace("\n\n\n", "\n\n");
        }
        return s.trim();
    }

    private static String fallbackOutfitAdvice(WeatherClient.WeatherResult r) {
        boolean wet = r.conditionSummary != null
                && (r.conditionSummary.toLowerCase(Locale.ROOT).contains("rain")
                || r.conditionSummary.toLowerCase(Locale.ROOT).contains("drizzle")
                || r.conditionSummary.toLowerCase(Locale.ROOT).contains("shower"));
        boolean cold = r.tempCelsius < 10;
        boolean hot = r.tempCelsius > 26;
        String layer = cold ? "Wear a warm coat and layers." : hot ? "Light breathable clothes; stay hydrated." : "Light jacket or hoodie you can remove indoors.";
        String rain = wet ? "Bring a compact umbrella or rain shell." : "Umbrella probably optional.";
        String wind = r.windKmh > 25 ? "It is breezy—secure loose items and add a wind layer." : "Wind is mild.";
        return "• " + layer + "\n• " + rain + "\n• " + wind;
    }

    private static String buildHomeOutfitPrompt(WeatherClient.WeatherResult r) {
        return "You help a university student pick clothes for today.\n"
                + "Weather facts:\n"
                + "- Location: " + r.locationLabel + "\n"
                + "- Temperature (C): " + r.tempCelsius + "\n"
                + "- Conditions: " + r.conditionSummary + "\n"
                + "- Humidity (%): " + r.humidityPercent + "\n"
                + "- Wind (km/h): " + r.windKmh + "\n\n"
                + "Reply in PLAIN TEXT only (no markdown, no asterisks). "
                + "Write exactly 3 lines, each starting with the word Line and a number, like:\n"
                + "Line1: ...\nLine2: ...\nLine3: ...\n"
                + "Line1 = what to wear. Line2 = umbrella yes or no and why. Line3 = one commuting tip.";
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
            items.addAll(NoteRepository.search(this, trimmed));
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
            binding.tvSearchLog.setVisibility(View.GONE);
        } else {
            binding.tvNoteCount.setText(
                    getString(R.string.note_count_search_with_total, items.size(), totalNotes));
            binding.tvSearchLog.setVisibility(View.VISIBLE);
            binding.tvSearchLog.setText(buildSearchLog(trimmed, items));
        }

        refreshDueBanner();
    }

    private void refreshDueBanner() {
        if (dueBannerSessionDismissed) {
            binding.cardDueBanner.setVisibility(View.GONE);
            return;
        }
        binding.cardDueBanner.setVisibility(View.VISIBLE);
        binding.layoutDueBannerItems.removeAllViews();
        List<Note> dueToday = NoteRepository.getDueOnSameDay(this, System.currentTimeMillis());
        if (dueToday.isEmpty()) {
            android.widget.TextView none = new android.widget.TextView(this);
            none.setText(R.string.due_banner_none);
            none.setTextSize(14f);
            none.setTextColor(0xFF9A3412);
            none.setPadding(0, 4, 0, 4);
            binding.layoutDueBannerItems.addView(none);
            return;
        }
        for (Note n : dueToday) {
            android.widget.TextView item = new android.widget.TextView(this);
            String reminder = n.getReminderText() == null ? "" : n.getReminderText().trim();
            String title = n.getTitle() == null ? "" : n.getTitle().trim();
            String line = !reminder.isEmpty()
                    ? reminder
                    : (title.isEmpty() ? getString(R.string.untitled_note) : title);
            item.setText(getString(R.string.due_banner_item_format, line));
            item.setTextSize(14f);
            item.setTextColor(0xFFB45309);
            item.setPadding(0, 6, 0, 6);
            item.setOnClickListener(v -> openEdit(n));
            binding.layoutDueBannerItems.addView(item);
        }
    }

    private String buildSearchLog(String keyword, List<Note> items) {
        StringJoiner joiner = new StringJoiner(", ");
        int top = Math.min(5, items.size());
        for (int i = 0; i < top; i++) {
            String title = items.get(i).getTitle().trim();
            joiner.add(title.isEmpty() ? getString(R.string.untitled_note) : title);
        }
        String labels = top == 0 ? "-" : joiner.toString();
        return getString(R.string.search_log_template, keyword, items.size(), labels);
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