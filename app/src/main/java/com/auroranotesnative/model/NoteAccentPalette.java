package com.auroranotesnative.model;

/**
 * Preset accent colors for note tags ({@link Note#getColorTag()}). 0 means none.
 */
public final class NoteAccentPalette {

    private NoteAccentPalette() {
    }

    public static final int[] CHOICES = {
            0xFFE11D48,
            0xFFEA580C,
            0xFFCA8A04,
            0xFF16A34A,
            0xFF2563EB,
            0xFF9333EA,
    };
}
