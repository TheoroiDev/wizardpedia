package com.theo.wizardpedia.client;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chant-annotation selection for the book page (annotation layer R-B, plan
 * docs/plans/chant_reading_annotation.md §4.4): language policy ∩ annotation
 * methods ∩ the line's actually-present reading keys → the ONE reading string
 * to draw (or {@code null} = plain line). Pure JVM — unit-testable; the
 * semantics mirror the wizardreal HUD selector but this is an independent
 * implementation (zero cross-repo dependency, wo_b iron rule ①).
 *
 * <p><b>Policy (D2)</b>: {@code auto} (default) = annotate only languages
 * that are NOT the MC display language — the line's bucket prefix must
 * differ from the display-language prefix ({@code zh} vs {@code zh_cn} → no,
 * {@code ja} vs {@code zh_cn} → yes); {@code off}; {@code selected} = the
 * configured two-letter bucket set; {@code all} = anything with a reading.
 * Neutral/unknown buckets never qualify under {@code auto} (nothing to
 * compare against).
 *
 * <p><b>Methods (D6)</b>: pinyin and romaji default on, IPA is the advanced
 * tier. Among the enabled methods the FIXED priority is pinyin &gt; romaji
 * &gt; ipa — the readings map is insertion-ordered over the fixed key set,
 * and the first present+enabled key wins (one reading row per line).
 */
public final class ReadingSelector {
    private ReadingSelector() {}

    /**
     * Select the annotation for one chant line.
     *
     * @param bucket       the line's language bucket (the bucket the chant
     *                     variants were resolved from; may be neutral/empty)
     * @param mcLanguage   the MC display language code (e.g. {@code "zh_cn"})
     * @param policy       D2 language policy
     * @param readLanguages two-letter buckets for the {@code selected} policy
     * @param methodPinyin D6 method switch
     * @param methodRomaji D6 method switch
     * @param methodIpa    D6 method switch
     * @param readings     the line's readings map (never null in practice;
     *                     tolerated null/empty)
     * @return the reading string to render, or {@code null} for a plain line
     */
    public static String select(String bucket, String mcLanguage, PediaClientConfig.LanguagePolicy policy,
                                Set<String> readLanguages, boolean methodPinyin, boolean methodRomaji,
                                boolean methodIpa, Map<String, String> readings) {
        if (readings == null || readings.isEmpty() || policy == null) return null;
        switch (policy) {
            case OFF -> {
                return null;
            }
            case AUTO -> {
                if (!bucketDiffersFromDisplay(bucket, mcLanguage)) return null;
            }
            case SELECTED -> {
                if (bucket == null
                        || !readLanguages.contains(bucket.trim().toLowerCase(Locale.ROOT))) {
                    return null;
                }
            }
            case ALL -> { /* any present reading qualifies */ }
        }
        if (methodPinyin) {
            String v = readings.get("pinyin");
            if (v != null && !v.isBlank()) return v;
        }
        if (methodRomaji) {
            String v = readings.get("romaji");
            if (v != null && !v.isBlank()) return v;
        }
        if (methodIpa) {
            String v = readings.get("ipa");
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /** {@code auto}: two-letter bucket prefix vs the display-language prefix. */
    private static boolean bucketDiffersFromDisplay(String bucket, String mcLanguage) {
        String b = prefix(bucket);
        String m = prefix(mcLanguage);
        return !b.isEmpty() && !m.isEmpty() && !b.equalsIgnoreCase(m);
    }

    /** Language-code prefix: everything before {@code _} (or the whole code). */
    private static String prefix(String code) {
        if (code == null) return "";
        String s = code.trim();
        int at = s.indexOf('_');
        return (at >= 0 ? s.substring(0, at) : s).toLowerCase(Locale.ROOT);
    }
}
