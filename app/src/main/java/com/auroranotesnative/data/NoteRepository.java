package com.auroranotesnative.data;

import com.auroranotesnative.model.Note;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NoteRepository {
    private static final List<Note> NOTES = new ArrayList<>();
    private static final MutableLiveData<List<Note>> notesLiveData = new MutableLiveData<>();

    private static final Set<String> STOPWORDS = new HashSet<>();

    static {
        // Small stopword list to keep the demo deterministic and lightweight.
        Collections.addAll(STOPWORDS,
                "the", "a", "an", "and", "or", "but", "if", "then", "else",
                "to", "of", "in", "on", "for", "with", "at", "by", "from",
                "is", "are", "was", "were", "be", "been", "being", "it", "this", "that",
                "as", "i", "you", "he", "she", "they", "we", "them", "us",
                "your", "my", "our", "their", "not", "no", "yes", "do", "does", "did",
                "can", "could", "would", "should", "will", "just"
        );
    }

    static {
        NOTES.add(new Note(
                UUID.randomUUID().toString(),
                "Welcome to Aurora Notes",
                "This is a fully native Java + XML Android Studio starter converted from the original React Native app concept.",
                true,
                now()
        ));
        NOTES.add(new Note(
                UUID.randomUUID().toString(),
                "AI Summary Ideas",
                "You can extend this project with Supabase sync, AI summarization, weather, translation, and news modules.",
                false,
                now()
        ));

        notesLiveData.setValue(getAll());
    }

    public static List<Note> getAll() {
        List<Note> copy = new ArrayList<>(NOTES);
        sort(copy);
        return copy;
    }

    /**
     * Builds a compact catalog of notes for LLM prompting.
     * This is a demo helper (in-memory data) and not intended for production scale.
     */
    public static String buildAiCatalog() {
        StringBuilder builder = new StringBuilder();
        for (Note note : NOTES) {
            builder.append("- id: ").append(note.getId());
            builder.append("\n  title: ").append(escapeForPrompt(note.getTitle()));
            builder.append("\n  content: ").append(escapeForPrompt(note.getContent()));
            builder.append("\n  pinned: ").append(note.isPinned());
            builder.append("\n  updatedAt: ").append(note.getUpdatedAt());
            builder.append("\n");
        }
        return builder.toString();
    }

    public static List<Note> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAll();
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<Note> results = new ArrayList<>();
        for (Note note : NOTES) {
            if (safe(note.getTitle()).toLowerCase(Locale.ROOT).contains(normalized)
                    || safe(note.getContent()).toLowerCase(Locale.ROOT).contains(normalized)) {
                results.add(note);
            }
        }
        sort(results);
        return results;
    }

    public static LiveData<List<Note>> observeAll() {
        return notesLiveData;
    }

    /**
     * Lightweight "semantic" search based on TF-IDF cosine similarity.
     * (No network calls, so it's runnable as a demo.)
     */
    public static List<Note> searchSemantic(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAll();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return getAll();
        }

        Map<String, Integer> tfQuery = termFrequency(queryTokens);
        Set<String> queryTokenSet = tfQuery.keySet();

        // Document frequencies for query tokens only (faster).
        int nDocs = NOTES.size();
        Map<String, Integer> df = new HashMap<>();
        for (String t : queryTokenSet) {
            df.put(t, 0);
        }

        for (Note note : NOTES) {
            Set<String> seenInDoc = new HashSet<>();
            List<String> noteTokens = tokenize(note.getTitle() + " " + note.getContent());
            for (String tok : noteTokens) {
                if (queryTokenSet.contains(tok)) {
                    seenInDoc.add(tok);
                }
            }
            for (String tok : seenInDoc) {
                df.put(tok, df.get(tok) + 1);
            }
        }

        // Precompute query weights.
        Map<String, Double> wq = new HashMap<>();
        double normQ = 0.0;
        for (Map.Entry<String, Integer> e : tfQuery.entrySet()) {
            String tok = e.getKey();
            int tf = e.getValue();
            int docFreq = df.getOrDefault(tok, 0);
            double idf = Math.log((nDocs + 1.0) / (docFreq + 1.0)) + 1.0;
            double weight = tf * idf;
            wq.put(tok, weight);
            normQ += weight * weight;
        }
        normQ = Math.sqrt(normQ);
        if (normQ == 0.0) {
            return getAll();
        }

        List<ScoredNote> scored = new ArrayList<>();
        for (Note note : NOTES) {
            List<String> noteTokens = tokenize(note.getTitle() + " " + note.getContent());
            Map<String, Integer> tfNote = new HashMap<>();
            for (String tok : noteTokens) {
                if (queryTokenSet.contains(tok)) {
                    tfNote.put(tok, tfNote.getOrDefault(tok, 0) + 1);
                }
            }

            double dot = 0.0;
            double normD = 0.0;
            for (String tok : queryTokenSet) {
                int tfN = tfNote.getOrDefault(tok, 0);
                int docFreq = df.getOrDefault(tok, 0);
                double idf = Math.log((nDocs + 1.0) / (docFreq + 1.0)) + 1.0;
                double wN = tfN * idf;

                double wQTok = wq.getOrDefault(tok, 0.0);
                dot += wN * wQTok;
                normD += wN * wN;
            }
            normD = Math.sqrt(normD);

            double score = (normD == 0.0) ? 0.0 : dot / (normQ * normD);
            if (note.isPinned()) {
                // Small UX boost so pinned notes remain easy to find.
                score += 0.05;
            }
            scored.add(new ScoredNote(note, score));
        }

        // Sort by semantic score, then pinned/updated time for stable UX.
        Collections.sort(scored, (a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            if (a.note.isPinned() != b.note.isPinned()) {
                return a.note.isPinned() ? -1 : 1;
            }
            return b.note.getUpdatedAt().compareTo(a.note.getUpdatedAt());
        });

        List<Note> results = new ArrayList<>();
        for (ScoredNote sn : scored) {
            results.add(sn.note);
        }
        return results;
    }

    public static List<Note> applyAiPrompt(String prompt) {
        String normalized = normalizePrompt(prompt);
        if (normalized.isEmpty()) {
            return getAll();
        }

        List<Note> results = new ArrayList<>(NOTES);

        if (containsAny(normalized, "priority", "importance", "important", "prioritize", "urgent")) {
            Collections.sort(results, new Comparator<Note>() {
                @Override
                public int compare(Note a, Note b) {
                    int scoreCompare = Integer.compare(priorityScore(b), priorityScore(a));
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                }
            });
            return results;
        }

        if (containsAny(normalized, "recent", "latest", "newest")) {
            Collections.sort(results, new Comparator<Note>() {
                @Override
                public int compare(Note a, Note b) {
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                }
            });
            return results;
        }

        if (containsAny(normalized, "oldest", "earliest")) {
            Collections.sort(results, new Comparator<Note>() {
                @Override
                public int compare(Note a, Note b) {
                    return a.getUpdatedAt().compareTo(b.getUpdatedAt());
                }
            });
            return results;
        }

        if (containsAny(normalized, "reverse alphabetical", "z-a", "reverse title")) {
            Collections.sort(results, new Comparator<Note>() {
                @Override
                public int compare(Note a, Note b) {
                    return b.getTitle().compareToIgnoreCase(a.getTitle());
                }
            });
            return results;
        }

        if (containsAny(normalized, "alphabetical", "a-z", "title")) {
            Collections.sort(results, new Comparator<Note>() {
                @Override
                public int compare(Note a, Note b) {
                    return a.getTitle().compareToIgnoreCase(b.getTitle());
                }
            });
            return results;
        }

        String fallbackQuery = removeAiPrefix(prompt);
        return search(fallbackQuery);
    }

    public static void save(Note note) {
        for (int i = 0; i < NOTES.size(); i++) {
            if (NOTES.get(i).getId().equals(note.getId())) {
                note.setUpdatedAt(now());
                NOTES.set(i, note);
                publish();
                return;
            }
        }
        note.setUpdatedAt(now());
        NOTES.add(note);
        publish();
    }

    public static Note createEmpty() {
        return new Note(UUID.randomUUID().toString(), "", "", false, now());
    }

    private static int priorityScore(Note note) {
        int score = note.isPinned() ? 100 : 0;
        String text = (safe(note.getTitle()) + " " + safe(note.getContent())).toLowerCase(Locale.ROOT);

        score += keywordMatches(text,
                "urgent", "asap", "important", "priority", "critical",
                "deadline", "today", "tomorrow", "must", "action item",
                "follow up", "blocker", "review");

        if (text.contains("!!!")) {
            score += 15;
        }
        if (text.contains("!!")) {
            score += 10;
        }
        return score;
    }

    private static int keywordMatches(String text, String... keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score += 10;
            }
        }
        return score;
    }

    private static boolean containsAny(String text, String... options) {
        for (String option : options) {
            if (text.contains(option)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePrompt(String prompt) {
        return removeAiPrefix(prompt).trim().toLowerCase(Locale.ROOT);
    }

    private static String removeAiPrefix(String prompt) {
        if (prompt == null) {
            return "";
        }
        String cleaned = prompt.trim();
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("ai:")) {
            return cleaned.substring(3).trim();
        }
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("/ai")) {
            return cleaned.substring(3).trim();
        }
        return cleaned;
    }

    private static void sort(List<Note> notes) {
        Collections.sort(notes, new Comparator<Note>() {
            @Override
            public int compare(Note a, Note b) {
                if (a.isPinned() != b.isPinned()) {
                    return a.isPinned() ? -1 : 1;
                }
                return b.getUpdatedAt().compareTo(a.getUpdatedAt());
            }
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escapeForPrompt(String value) {
        String v = safe(value);
        // Keep prompt readable; avoid huge multi-line blobs.
        return v.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
    }

    private static void publish() {
        notesLiveData.setValue(getAll());
    }

    private static class ScoredNote {
        final Note note;
        final double score;

        ScoredNote(Note note, double score) {
            this.note = note;
            this.score = score;
        }
    }

    private static List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        String[] parts = normalized.trim().split("\\s+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (STOPWORDS.contains(p)) continue;
            tokens.add(p);
        }
        return tokens;
    }

    private static Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.put(t, tf.getOrDefault(t, 0) + 1);
        }
        return tf;
    }
}


