package com.auroranotesnative.util;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.EditText;

import androidx.core.text.HtmlCompat;

/**
 * Minimal rich text for the note body: bold / italic / size spans, persisted as HTML.
 */
public final class RichEditorHelper {

    private RichEditorHelper() {
    }

    public static String contentToHtml(Editable editable) {
        if (editable == null || editable.length() == 0) {
            return "";
        }
        return HtmlCompat.toHtml(editable, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
    }

    public static void setHtmlOrPlain(EditText editText, String stored) {
        if (stored == null) {
            editText.setText("");
            return;
        }
        String t = stored.trim();
        if (t.startsWith("<")) {
            editText.setText(HtmlCompat.fromHtml(stored, HtmlCompat.FROM_HTML_MODE_COMPACT));
        } else {
            editText.setText(stored);
        }
    }

    /** Plain text for search / local summarizer (drops tags, keeps visible text). */
    public static String toPlainText(String stored) {
        if (stored == null || stored.trim().isEmpty()) {
            return "";
        }
        if (stored.trim().startsWith("<")) {
            return HtmlCompat.fromHtml(stored, HtmlCompat.FROM_HTML_MODE_COMPACT).toString();
        }
        return stored;
    }

    public static void toggleBold(EditText editText) {
        toggleStyleSpan(editText, Typeface.BOLD);
    }

    public static void toggleItalic(EditText editText) {
        toggleStyleSpan(editText, Typeface.ITALIC);
    }

    private static void toggleStyleSpan(EditText editText, int style) {
        Editable ed = editText.getText();
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > ed.length()) {
            end = ed.length();
        }
        if (start == end) {
            return;
        }

        StyleSpan[] spans = ed.getSpans(start, end, StyleSpan.class);
        for (StyleSpan span : spans) {
            if (span.getStyle() != style) {
                continue;
            }
            int ss = ed.getSpanStart(span);
            int se = ed.getSpanEnd(span);
            if (ss <= start && se >= end) {
                ed.removeSpan(span);
                return;
            }
        }
        ed.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Bump font size on selection (demo “font” control). */
    public static void enlargeSelection(EditText editText) {
        Editable ed = editText.getText();
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > ed.length()) {
            end = ed.length();
        }
        if (start == end) {
            return;
        }
        ed.setSpan(new RelativeSizeSpan(1.18f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
