package com.auroranotesnative.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.auroranotesnative.model.Note;
import com.auroranotesnative.util.RichEditorHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * NoteRepository
 *
 * Responsibilities:
 * 1. Handle Room database access
 * 2. Provide basic + semantic search
 * 3. Support AI features (catalog for Gemini)
 */
public class NoteRepository {
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)]]");

    /** Stopwords for filtering meaningless tokens */
    private static final Set<String> STOPWORDS = new HashSet<>();

    static {
        Collections.addAll(STOPWORDS,
                "the","a","an","and","or","but","if","then","else",
                "to","of","in","on","for","with","at","by","from",
                "is","are","was","were","be","been","being","it","this","that",
                "as","i","you","he","she","they","we","them","us",
                "your","my","our","their","not","no","yes","do","does","did",
                "can","could","would","should","will","just"
        );
    }

    /** Get DAO instance */
    private static NoteDao dao(Context context) {
        return AppDatabase.getInstance(context).noteDao();
    }

    /** Get all notes */
    public static List<Note> getAll(Context context) {
        return dao(context).getAllNotes();
    }

    /** Observe notes in real-time */
    public static LiveData<List<Note>> observeAll(Context context) {
        return dao(context).observeAllNotes();
    }

    /** Save (insert or update) */
    public static void save(Context context, Note note) {
        note.setUpdatedAt(System.currentTimeMillis());

        if (note.getId() == 0) {
            long newId = dao(context).insert(note);
            note.setId((int) newId);
        } else {
            dao(context).update(note);
        }
    }

    /** Delete note */
    public static void delete(Context context, Note note) {
        dao(context).delete(note);
    }

    /** Notes with a due date on the same local calendar day as {@code referenceMillis}. */
    public static List<Note> getDueOnSameDay(Context context, long referenceMillis) {
        List<Note> all = getAll(context);
        List<Note> out = new ArrayList<>();
        java.util.Calendar ref = java.util.Calendar.getInstance();
        ref.setTimeInMillis(referenceMillis);
        int y = ref.get(java.util.Calendar.YEAR);
        int d = ref.get(java.util.Calendar.DAY_OF_YEAR);

        for (Note n : all) {
            long due = n.getDueDateMillis();
            if (due <= 0) continue;
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(due);
            if (c.get(java.util.Calendar.YEAR) == y && c.get(java.util.Calendar.DAY_OF_YEAR) == d) {
                out.add(n);
            }
        }
        return out;
    }

    /** Create empty note */
    public static Note createEmpty() {
        return new Note("", "", false, System.currentTimeMillis(), 0, "", 0L, "");
    }

    public static List<String> extractWikiLinkTitles(String content) {
        List<String> titles = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return titles;
        }

        // HTML / spans can split brackets; match on plain text.
        String plain = RichEditorHelper.toPlainText(content);
        Matcher matcher = WIKI_LINK_PATTERN.matcher(plain);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw == null) continue;
            String normalized = raw.trim();
            if (normalized.isEmpty()) continue;
            String key = normalized.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                titles.add(normalized);
            }
        }
        return titles;
    }

    public static List<Note> getLinkedNotes(Context context, Note sourceNote) {
        List<Note> linked = new ArrayList<>();
        if (sourceNote == null) return linked;

        for (String title : extractWikiLinkTitles(sourceNote.getContent())) {
            Note target = dao(context).getNoteByTitle(title);
            if (target == null) continue;
            if (sourceNote.getId() != 0 && sourceNote.getId() == target.getId()) continue;
            linked.add(target);
        }
        return linked;
    }

    public static List<Note> getBacklinkedNotes(Context context, Note targetNote) {
        List<Note> backlinks = new ArrayList<>();
        if (targetNote == null) return backlinks;

        String targetTitle = safe(targetNote.getTitle()).trim();
        if (targetTitle.isEmpty()) return backlinks;
        String normalizedTarget = targetTitle.toLowerCase(Locale.ROOT);

        for (Note note : getAll(context)) {
            if (targetNote.getId() != 0 && note.getId() == targetNote.getId()) continue;
            List<String> links = extractWikiLinkTitles(note.getContent());
            for (String linkedTitle : links) {
                if (normalizedTarget.equals(linkedTitle.toLowerCase(Locale.ROOT))) {
                    backlinks.add(note);
                    break;
                }
            }
        }
        return backlinks;
    }

    /**
     * Build catalog string for LLM prompt
     */
    public static String buildAiCatalog(Context context) {
        List<Note> notes = getAll(context);
        StringBuilder builder = new StringBuilder();

        for (Note note : notes) {
            builder.append("- id: ").append(note.getId());
            builder.append("\n  title: ").append(safe(note.getTitle()));
            builder.append("\n  content: ").append(RichEditorHelper.toPlainText(safe(note.getContent())));
            builder.append("\n  pinned: ").append(note.isPinned());
            builder.append("\n  updatedAt: ").append(note.getUpdatedAt());
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * Keyword search: case-insensitive substring match in title and content.
     * Uses raw text (not tokenized TF-IDF) so short queries like "ss" behave as users expect.
     */
    public static List<Note> search(Context context, String query) {
        List<Note> all = getAll(context);

        if (query == null || query.trim().isEmpty()) return all;

        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Note> results = new ArrayList<>();

        for (Note note : all) {
            String title = safe(note.getTitle()).toLowerCase(Locale.ROOT);
            String content = RichEditorHelper.toPlainText(safe(note.getContent())).toLowerCase(Locale.ROOT);

            if (title.contains(needle) || content.contains(needle)) {
                results.add(note);
            }
        }
        return results;
    }

    /**
     * Keyword matches (substring in title or plain body) for the raw prompt and optional
     * interpreted query, then fills with semantic-ranked notes without duplicates.
     */
    public static List<Note> searchHybrid(Context context, String userPrompt, String interpretedQuery, int maxResults, int semanticCap) {
        String raw = userPrompt == null ? "" : userPrompt.trim();
        String interp = interpretedQuery == null ? "" : interpretedQuery.trim();
        if (raw.isEmpty() && interp.isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashMap<Integer, Note> ordered = new LinkedHashMap<>();

        if (!raw.isEmpty()) {
            for (Note n : search(context, raw)) {
                if (ordered.size() >= maxResults) {
                    break;
                }
                ordered.putIfAbsent(n.getId(), n);
            }
        }
        if (!interp.isEmpty() && !interp.equalsIgnoreCase(raw)) {
            for (Note n : search(context, interp)) {
                if (ordered.size() >= maxResults) {
                    break;
                }
                ordered.putIfAbsent(n.getId(), n);
            }
        }

        String semQuery = !interp.isEmpty() ? interp : raw;
        SemanticSearchResult sem = searchSemanticWithAdaptiveTopN(context, semQuery);
        int cap = Math.min(sem.notes.size(), Math.max(semanticCap, sem.suggestedTopN));
        for (int i = 0; i < cap && ordered.size() < maxResults; i++) {
            Note n = sem.notes.get(i);
            ordered.putIfAbsent(n.getId(), n);
        }

        return new ArrayList<>(ordered.values());
    }

    private static String noteTextForIndexing(Note note) {
        return safe(note.getTitle()) + " " + RichEditorHelper.toPlainText(safe(note.getContent()));
    }

    /**
     * Compatibility method:
     * returns all scored notes sorted by semantic similarity
     */
    public static List<Note> searchSemantic(Context context, String query) {
        return searchSemanticWithAdaptiveTopN(context, query).notes;
    }

    /**
     * TF-IDF semantic search with adaptive topN
     */
    public static SemanticSearchResult searchSemanticWithAdaptiveTopN(Context context, String query) {
        List<Note> allNotes = getAll(context);

        if (query == null || query.trim().isEmpty()) {
            return new SemanticSearchResult(new ArrayList<>(), 0);
        }

        List<String> queryTokens = tokenize(query);
        // Only grammar/stopwords (e.g. "who you are") — do not fall back to "all notes".
        if (queryTokens.isEmpty()) {
            return new SemanticSearchResult(new ArrayList<>(), 0);
        }

        Map<String, Integer> tfQuery = termFrequency(queryTokens);
        Set<String> queryTokenSet = tfQuery.keySet();

        int nDocs = allNotes.size();
        Map<String, Integer> df = new HashMap<>();

        for (String t : queryTokenSet) {
            df.put(t, 0);
        }

        // Compute document frequency
        for (Note note : allNotes) {
            Set<String> seen = new HashSet<>();
            List<String> tokens = tokenize(noteTextForIndexing(note));

            for (String tok : tokens) {
                if (queryTokenSet.contains(tok)) {
                    seen.add(tok);
                }
            }

            for (String tok : seen) {
                df.put(tok, df.get(tok) + 1);
            }
        }

        // Compute query vector
        Map<String, Double> wq = new HashMap<>();
        double normQ = 0.0;

        for (String tok : tfQuery.keySet()) {
            int tf = tfQuery.get(tok);
            int d = df.getOrDefault(tok, 0);

            double idf = Math.log((nDocs + 1.0) / (d + 1.0)) + 1.0;
            double w = tf * idf;

            wq.put(tok, w);
            normQ += w * w;
        }

        normQ = Math.sqrt(normQ);
        if (normQ == 0.0) {
            return new SemanticSearchResult(new ArrayList<>(), 0);
        }

        List<ScoredNote> scored = new ArrayList<>();

        // Compute cosine similarity
        for (Note note : allNotes) {
            List<String> tokens = tokenize(noteTextForIndexing(note));
            Map<String, Integer> tfNote = termFrequency(tokens);

            double dot = 0.0;
            double normD = 0.0;

            for (String tok : queryTokenSet) {
                int tfN = tfNote.getOrDefault(tok, 0);
                int d = df.getOrDefault(tok, 0);

                double idf = Math.log((nDocs + 1.0) / (d + 1.0)) + 1.0;
                double wN = tfN * idf;
                double wQ = wq.get(tok);

                dot += wN * wQ;
                normD += wN * wN;
            }

            normD = Math.sqrt(normD);
            double score = normD == 0.0 ? 0.0 : dot / (normQ * normD);

            // Normalize note text into a few core concepts
            String fullText = normalizeSynonyms(noteTextForIndexing(note)).toLowerCase(Locale.ROOT);

            // Small pinned boost
            if (note.isPinned()) {
                score += 0.05;
            }

            // Concept-level boost only
            if (fullText.contains("urgent")) {
                score += 0.08;
            }
            if (fullText.contains("task")) {
                score += 0.04;
            }
            if (fullText.contains("today") || fullText.contains("tomorrow")) {
                score += 0.04;
            }

            scored.add(new ScoredNote(note, score));
        }

        // Sort by score
        scored.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return Long.compare(b.note.getUpdatedAt(), a.note.getUpdatedAt());
        });

        List<Note> results = new ArrayList<>();
        for (ScoredNote s : scored) {
            results.add(s.note);
        }

        int adaptiveTopN = chooseAdaptiveTopN(scored);
        return new SemanticSearchResult(results, adaptiveTopN);
    }

    /**
     * Decide how many results to return based on score distribution
     */
    private static int chooseAdaptiveTopN(List<ScoredNote> scored) {
        if (scored == null || scored.isEmpty()) return 0;

        double top = scored.get(0).score;
        if (top <= 0.0) return 5;

        int count = 0;
        for (ScoredNote s : scored) {
            if (s.score >= top * 0.45) {
                count++;
            } else {
                break;
            }
        }

        return Math.max(1, Math.min(count, 8));
    }

    /**
     * Normalize synonyms before tokenization
     */
    private static String normalizeSynonyms(String text) {
        if (text == null) return "";

        String normalized = text.toLowerCase(Locale.ROOT);

        // ---- DAY ----
        normalized = normalized.replace("tmr", "tomorrow");
        normalized = normalized.replace("tmrw", "tomorrow");
        normalized = normalized.replace("tommorrow", "tomorrow");

        // tomorrow → day + week
        normalized = normalized.replace("tomorrow", "tomorrow day week");
        normalized = normalized.replace("today", "today day week");

        // ---- WEEK ----
        normalized = normalized.replace("this week", "week");
        normalized = normalized.replace("next week", "week");
        normalized = normalized.replace("weekend", "week");

        // ---- MONTH ----
        normalized = normalized.replace("this month", "month");
        normalized = normalized.replace("next month", "month");

        return normalized;
    }

    /**
     * Simple token-form normalization
     * Example: tasks -> task
     */
    private static String normalizeTokenForm(String token) {
        if (token == null) return "";

        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }

        return token;
    }

    /** Tokenize text */
    private static List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();

        String normalized = normalizeSynonyms(text)
                .replaceAll("[^a-z0-9]+", " ");

        String trimmed = normalized.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();

        String[] parts = trimmed.split("\\s+");

        List<String> tokens = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p.isEmpty()) continue;

            p = normalizeTokenForm(p);

            if (STOPWORDS.contains(p)) continue;
            tokens.add(p);
        }
        return tokens;
    }

    /** Term frequency */
    private static Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.put(t, tf.getOrDefault(t, 0) + 1);
        }
        return tf;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Internal scored wrapper */
    private static class ScoredNote {
        final Note note;
        final double score;

        ScoredNote(Note note, double score) {
            this.note = note;
            this.score = score;
        }
    }

    /** Result wrapper */
    public static class SemanticSearchResult {
        public final List<Note> notes;
        public final int suggestedTopN;

        public SemanticSearchResult(List<Note> notes, int suggestedTopN) {
            this.notes = notes;
            this.suggestedTopN = suggestedTopN;
        }
    }
}