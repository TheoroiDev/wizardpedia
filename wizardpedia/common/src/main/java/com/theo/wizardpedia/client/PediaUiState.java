package com.theo.wizardpedia.client;

import com.theo.wizardpedia.catalog.PediaEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * UI state preserved across open/close (static on purpose — the book keeps
 * its page when the player puts it away and comes back). All fields are
 * indices/ids that tolerate the catalog changing underneath: every consumer
 * re-clamps/looks-up defensively.
 */
public final class PediaUiState {

    /** Selected category id ({@code null} = the "All" pseudo-category). */
    public static String categoryId;
    /** Selected right-rail tag filter ({@code null} = no tag filter). */
    public static String selectedTag;
    /** Selected language page ({@code null} = neutral "All languages"). */
    public static String selectedLang;
    /** Selected entry id shown on the left page ({@code null} = none). */
    public static String selectedEntryId;
    /** Right-page grid page index. */
    public static int gridPage;
    /** Chant-variant index within the selected entry. */
    public static int variantIndex;
    /** Stage-ladder index within the selected entry (0 = base). */
    public static int stageIndex;
    /** Left-page body scroll offset in pixels. */
    public static int scroll;
    /** Search box text. */
    public static String search = "";
    /** Detail-navigation history for right-click-back (entry ids). */
    public static final Deque<String> history = new ArrayDeque<>();

    private PediaUiState() {}

    /** Cycle the language page through the given available codes (neutral
     *  first, then each code, then back to neutral). */
    public static void cycleLanguage(List<String> available) {
        List<String> cycle = new java.util.ArrayList<>(available);
        int at = selectedLang == null ? -1 : cycle.indexOf(selectedLang);
        selectedLang = at + 1 >= cycle.size() ? null : cycle.get(at + 1);
    }

    /** Record a navigation into {@code entryId} (pushes the previous id). */
    public static void navigateTo(String entryId) {
        if (selectedEntryId != null && !selectedEntryId.equals(entryId)) {
            history.push(selectedEntryId);
        }
        selectedEntryId = entryId;
        scroll = 0;
        stageIndex = 0;
        variantIndex = 0;
    }

    /** Right-click back: pop the history; {@code false} when already at root. */
    public static boolean goBack() {
        String previous = history.poll();
        if (previous == null) return false;
        selectedEntryId = previous;
        scroll = 0;
        stageIndex = 0;
        variantIndex = 0;
        return true;
    }

    /** Look up the selected entry in the merged view (null-safe). */
    public static PediaEntry selectedEntry() {
        if (selectedEntryId == null) return null;
        for (PediaEntry entry : PediaState.entries()) {
            if (entry.id().equals(selectedEntryId)) return entry;
        }
        return null;
    }
}
