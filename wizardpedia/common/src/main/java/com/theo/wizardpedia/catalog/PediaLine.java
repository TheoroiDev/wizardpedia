package com.theo.wizardpedia.catalog;

import java.util.Map;

/**
 * One structured chant line (catalog wire contract v4, annotation layer):
 * the display text plus the provider-derived {@code readings} map — fixed
 * key set {@code pinyin / romaji / ipa} (present keys only, possibly empty;
 * fail-closed on the provider side). wizardpedia renders these strings
 * blindly: it never interprets the keys, and no provider class is referenced.
 *
 * <p>Wire caps (§4): text {@link PediaEntry#MAX_LINE_KEY}, reading keys
 * {@link #MAX_READING_KEY}, reading values {@link #MAX_READING_VALUE};
 * oversized values are truncated, never rejected.
 */
public record PediaLine(String text, Map<String, String> readings) {

    /** Wire cap for one readings key (two are defined today). */
    public static final int MAX_READING_KEY = 8;
    /** Wire cap for one readings value (128 UTF-16 units). */
    public static final int MAX_READING_VALUE = 128;

    public PediaLine {
        if (text == null) text = "";
        readings = readings == null || readings.isEmpty() ? Map.of() : Map.copyOf(readings);
    }

    /** Line without readings (unannotated bucket / plain text). */
    public static PediaLine plain(String text) {
        return new PediaLine(text, Map.of());
    }
}
