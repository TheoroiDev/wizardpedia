package com.theo.wizardpedia.client;

import com.theo.wizardpedia.catalog.PediaEntry;
import com.theo.wizardpedia.catalog.PediaLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ruby primitive layout contract + the chant bucket accessor the annotation
 * policy is anchored to (wo_b G-B1): a plain line consumes the historical
 * 10px stride (zero regression), an annotated line the extended one.
 */
class ChantRubyTest {

    @Test
    void plainLinesKeepHistoricalStride() {
        // no reading -> exactly the pre-annotation layout
        assertEquals(10, ChantRuby.stride(null));
        assertEquals(10, ChantRuby.stride(""));
    }

    @Test
    void annotatedLinesExtendTheStride() {
        assertEquals(15, ChantRuby.stride("zhēn kōng rèn"));
        assertEquals(15, ChantRuby.stride("a ne mo su"));
    }

    // ---- PediaEntry.chantBucketFor -------------------------------------------

    private static PediaEntry entryWithChants(Map<String, List<List<PediaLine>>> chants) {
        return new PediaEntry("wizardreal:anemos", "wizardreal:wizardry", "spell.wizardreal.anemos.name",
                false, -1f, -1, -1f, -1f, "", "", List.of(),
                Map.of(), Map.of(), chants, List.of());
    }

    @Test
    void chantBucketFollowsChantsForResolution() {
        PediaEntry entry = entryWithChants(Map.of(
                "zh", List.of(List.of(new PediaLine("真空刃", Map.of("pinyin", "zhēn kōng rèn"))))));
        assertEquals("zh", entry.chantBucketFor("zh"));
        // zh page without a zh bucket falls back to neutral (legacy data)
        PediaEntry neutral = entryWithChants(Map.of(
                "", List.of(List.of(PediaLine.plain("legacy line")))));
        assertEquals("", neutral.chantBucketFor("zh"));
        assertEquals("", neutral.chantBucketFor(null));
        // no chants at all -> neutral
        assertEquals("", entryWithChants(Map.of()).chantBucketFor("ja"));
    }
}
