package com.theo.wizardpedia.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Book-page annotation selection matrix (wo_b G-B1): language policy ×
 * annotation methods × present reading keys. Same semantics as the
 * wizardreal HUD selector, independently implemented (wo_b iron rule ①).
 */
class ReadingSelectorTest {

    private static final Map<String, String> ZH = Map.of("pinyin", "zhēn kōng rèn", "ipa", "ʈʂən kʰʊŋ ʐən");
    private static final Map<String, String> JA = Map.of("romaji", "a ne mo su", "ipa", "a nɛ mo sɯ");

    private static String sel(String bucket, String mc, PediaClientConfig.LanguagePolicy policy,
                              Set<String> langs, boolean pinyin, boolean romaji, boolean ipa,
                              Map<String, String> readings) {
        return ReadingSelector.select(bucket, mc, policy, langs, pinyin, romaji, ipa, readings);
    }

    // ---- policy × bucket × display language --------------------------------

    @Test
    void autoAnnotatesOnlyNonDisplayLanguages() {
        // default methods (pinyin+romaji on, ipa off)
        assertNull(sel("zh", "zh_cn", PediaClientConfig.LanguagePolicy.AUTO, Set.of(), true, true, false, ZH));
        assertEquals("zhēn kōng rèn",
                sel("zh", "en_us", PediaClientConfig.LanguagePolicy.AUTO, Set.of(), true, true, false, ZH));
        assertEquals("a ne mo su",
                sel("ja", "zh_cn", PediaClientConfig.LanguagePolicy.AUTO, Set.of(), true, true, false, JA));
        // neutral/unknown bucket never qualifies
        assertNull(sel("", "en_us", PediaClientConfig.LanguagePolicy.AUTO, Set.of(), true, true, false, ZH));
        assertNull(sel(null, "en_us", PediaClientConfig.LanguagePolicy.AUTO, Set.of(), true, true, false, ZH));
        // unknown display language: nothing to compare against
        assertNull(sel("zh", null, PediaClientConfig.LanguagePolicy.AUTO, Set.of(), true, true, false, ZH));
    }

    @Test
    void offNeverAnnotates() {
        assertNull(sel("ja", "en_us", PediaClientConfig.LanguagePolicy.OFF, Set.of("ja"), true, true, true, JA));
        assertNull(sel("zh", "en_us", PediaClientConfig.LanguagePolicy.OFF, Set.of(), true, true, true, ZH));
    }

    @Test
    void selectedUsesBucketSet() {
        assertNull(sel("zh", "en_us", PediaClientConfig.LanguagePolicy.SELECTED, Set.of("ja"), true, true, false, ZH));
        assertEquals("a ne mo su",
                sel("ja", "en_us", PediaClientConfig.LanguagePolicy.SELECTED, Set.of("ja"), true, true, false, JA));
        // empty set selects nothing; case-normalized codes match
        assertNull(sel("ja", "en_us", PediaClientConfig.LanguagePolicy.SELECTED, Set.of(), true, true, false, JA));
        assertEquals("a ne mo su",
                sel("JA", "en_us", PediaClientConfig.LanguagePolicy.SELECTED, Set.of("ja"), true, true, false, JA));
    }

    @Test
    void allAnnotatesAnythingPresent() {
        assertEquals("zhēn kōng rèn",
                sel("zh", "zh_cn", PediaClientConfig.LanguagePolicy.ALL, Set.of(), true, true, false, ZH));
        assertEquals("a ne mo su",
                sel("ja", "zh_cn", PediaClientConfig.LanguagePolicy.ALL, Set.of(), true, true, false, JA));
    }

    // ---- methods × key existence --------------------------------------------

    @Test
    void disabledMethodsSkipTheirKeys() {
        // ipa-only line with pinyin off -> nothing selectable
        Map<String, String> ipaOnly = Map.of("ipa", "ʈʂən");
        assertNull(sel("zh", "en_us", PediaClientConfig.LanguagePolicy.ALL, Set.of(), false, true, false, ipaOnly));
        assertEquals("ʈʂən",
                sel("zh", "en_us", PediaClientConfig.LanguagePolicy.ALL, Set.of(), false, true, true, ipaOnly));
        // romaji disabled on a ja line -> nothing (ipa off by default)
        assertNull(sel("ja", "en_us", PediaClientConfig.LanguagePolicy.ALL, Set.of(), true, false, false, JA));
        assertEquals("a nɛ mo sɯ",
                sel("ja", "en_us", PediaClientConfig.LanguagePolicy.ALL, Set.of(), true, false, true, JA));
    }

    @Test
    void fixedPriorityPinyinThenRomajiThenIpa() {
        assertEquals("zhēn kōng rèn",
                sel("zh", "en_us", PediaClientConfig.LanguagePolicy.ALL, Set.of(), true, true, true, ZH));
        assertEquals("a ne mo su",
                sel("ja", "en_us", PediaClientConfig.LanguagePolicy.ALL, Set.of(), true, true, true, JA));
    }

    @Test
    void emptyReadingsStayPlain() {
        // the PediaLine.plain path: empty map -> null under every policy
        for (PediaClientConfig.LanguagePolicy policy : PediaClientConfig.LanguagePolicy.values()) {
            assertNull(sel("zh", "en_us", policy, Set.of("zh"), true, true, true, Map.of()));
            assertNull(sel("ja", "en_us", policy, Set.of("ja"), true, true, true, null));
        }
    }
}
