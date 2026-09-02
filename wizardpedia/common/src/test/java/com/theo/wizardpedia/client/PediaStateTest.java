package com.theo.wizardpedia.client;

import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client merge semantics: FULL_SYNC replaces the datapack-source set,
 * PROVIDER_PUSH upserts the provider-source set, provider wins id conflicts,
 * categories sort by sortIndex.
 *
 * <p>{@link PediaState} is a global static — tests use per-test unique ids
 * and subset/order assertions instead of absolute sizes so they are
 * order-independent.
 */
class PediaStateTest {

    private static PediaCategory cat(String id, int sort) {
        return new PediaCategory(id, id + ".name", "", sort);
    }

    private static PediaEntry entry(String id, String cat, boolean locked) {
        return new PediaEntry(id, cat, id + ".title", locked, "", List.of(), List.of());
    }

    private static List<String> ids(List<PediaCategory> categories) {
        return categories.stream().map(PediaCategory::id).toList();
    }

    private static List<String> entryIds(List<PediaEntry> entries) {
        return entries.stream().map(PediaEntry::id).toList();
    }

    @Test
    void fullSyncReplacesDatapackSet() {
        PediaState.handleFullSync(List.of(cat("t1-a", 0)), List.of(entry("t1:e1", "t1-a", false)));
        PediaState.handleFullSync(List.of(cat("t1-b", 0)), List.of(entry("t1:e2", "t1-b", false)));

        var catIds = ids(PediaState.categories());
        assertFalse(catIds.contains("t1-a"), "previous datapack category must be gone after FULL_SYNC");
        assertTrue(catIds.contains("t1-b"), "latest FULL_SYNC content must be present");

        var eIds = entryIds(PediaState.entries());
        assertFalse(eIds.contains("t1:e1"), "previous datapack entry must be gone after FULL_SYNC");
        assertTrue(eIds.contains("t1:e2"), "latest FULL_SYNC entry must be present");
    }

    @Test
    void providerPushUpsertsAndWinsConflicts() {
        PediaState.handleFullSync(List.of(cat("t2-dp", 0)), List.of(entry("t2:e", "t2-dp", false)));
        PediaState.handleProviderPush(
                List.of(cat("t2-prov", 5)),
                List.of(entry("t2:e", "t2-prov", true), entry("t2:pe", "t2-prov", false)));

        var catIds = ids(PediaState.categories());
        assertTrue(catIds.contains("t2-prov"), "provider category upserted");
        // id conflict: provider copy wins — locked flipped to true
        assertTrue(PediaState.isLocked("t2:e"), "provider override wins the locked flag");
        var eIds = entryIds(PediaState.entries());
        assertTrue(eIds.contains("t2:pe"), "provider entry visible in the merged view");
    }

    @Test
    void categoriesSortedBySortIndex() {
        PediaState.handleFullSync(List.of(cat("t3-late", 10), cat("t3-early", -5)), List.of());
        PediaState.handleProviderPush(List.of(cat("t3-mid", 3)), List.of());

        var sorted = ids(PediaState.categories()).stream()
                .filter(id -> id.startsWith("t3-")).toList();
        assertEquals(List.of("t3-early", "t3-mid", "t3-late"), sorted);
    }

    @Test
    void lockedFallsBackToDatapackSource() {
        PediaState.handleFullSync(List.of(), List.of(entry("t4:locked", "t4", true)));
        assertTrue(PediaState.isLocked("t4:locked"), "datapack locked flag respected");
        PediaState.handleProviderPush(List.of(), List.of(entry("t4:locked", "t4", false)));
        assertFalse(PediaState.isLocked("t4:locked"), "provider override wins over datapack locked");
    }
}
