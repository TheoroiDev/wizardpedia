package com.theo.wizardpedia.catalog;

import com.mojang.serialization.Codec;

/**
 * String caps helpers for the wire contract and datapack JSON
 * (docs/wizardpedia.md §4): oversized values are truncated instead of
 * rejected, so one bad field can neither break a whole sync nor a datapack
 * load. The cap is measured in UTF-16 units (what {@code writeUtf} enforces)
 * and the cut always lands on a code-point boundary — surrogate pairs are
 * never split.
 */
public final class WireText {

    private WireText() {}

    /** Truncate to at most {@code maxLen} UTF-16 units, never splitting a surrogate pair (null-safe). */
    public static String truncate(String value, int maxLen) {
        if (value == null) return "";
        if (value.length() <= maxLen) return value;
        int end = Math.max(0, maxLen);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    /** {@link Codec#STRING} that truncates parsed values to {@code maxLen} code points. */
    public static Codec<String> capped(int maxLen) {
        return Codec.STRING.xmap(s -> truncate(s, maxLen), s -> s);
    }
}
