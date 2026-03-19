package com.auroranotesnative.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local extractive summarizer to keep this demo fully runnable (no network / API keys).
 */
public final class SummaryEngine {
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "or", "but", "if", "then", "else",
            "to", "of", "in", "on", "for", "with", "at", "by", "from",
            "is", "are", "was", "were", "be", "been", "being", "it", "this", "that",
            "as", "i", "you", "he", "she", "they", "we", "them", "us",
            "your", "my", "our", "their", "not", "no", "yes", "do", "does", "did",
            "can", "could", "would", "should", "will", "just"
    ));

    private SummaryEngine() { }

    public static String summarize(String title, String content) {
        String t = title == null ? "" : title.trim();
        String c = content == null ? "" : content.trim();
        if (t.isEmpty() && c.isEmpty()) return "";

        String combined = t.isEmpty() ? c : (t + ". " + c);
        combined = combined.trim();

        // Small texts: return them as-is for better UX.
        if (combined.length() <= 180) return combined;

        List<String> sentences = splitSentences(combined);
        if (sentences.size() <= 2) return combined;

        Map<String, Integer> tf = termFrequency(tokenize(combined));
        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i).trim();
            if (s.isEmpty()) continue;

            List<String> toks = tokenize(s);
            if (toks.isEmpty()) continue;

            double score = 0.0;
            for (String tok : toks) {
                score += tf.getOrDefault(tok, 0);
            }

            // Penalize very short sentences slightly to reduce noise.
            score = score / (toks.size() + 1.0);
            scored.add(new ScoredSentence(i, s, score));
        }

        if (scored.isEmpty()) return "";

        // Take top N sentences by score, then keep original order.
        int topN = Math.min(3, scored.size());
        Collections.sort(scored, (a, b) -> Double.compare(b.score, a.score));
        List<ScoredSentence> picked = scored.subList(0, topN);
        Collections.sort(picked, (a, b) -> Integer.compare(a.index, b.index));

        int maxChars = 280;
        StringBuilder out = new StringBuilder();
        for (ScoredSentence ss : picked) {
            if (out.length() + ss.sentence.length() + 1 > maxChars) break;
            if (out.length() > 0) out.append(" ");
            out.append(ss.sentence);
        }

        return out.toString().trim();
    }

    private static List<String> splitSentences(String text) {
        // Basic sentence split: punctuation + whitespace/newlines.
        String normalized = text.replace('\n', ' ').trim();
        if (normalized.isEmpty()) return Collections.emptyList();
        String[] parts = normalized.split("(?<=[.!?])\\s+");
        return Arrays.asList(parts);
    }

    private static List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) return Collections.emptyList();
        String[] parts = normalized.split("\\s+");
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

    private static final class ScoredSentence {
        final int index;
        final String sentence;
        final double score;

        private ScoredSentence(int index, String sentence, double score) {
            this.index = index;
            this.sentence = sentence;
            this.score = score;
        }
    }
}

