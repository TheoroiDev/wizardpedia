package com.theo.wizardpedia.client;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side merged catalog state. Two independent sources feed it:
 * <ul>
 *   <li>datapack source — replaced wholesale by {@code FULL_SYNC};</li>
 *   <li>provider source — upserted by id on {@code PROVIDER_PUSH}
 *       (providers may override {@code locked} and their own categories).</li>
 * </ul>
 * The merged view (provider wins id conflicts, categories sorted by
 * {@code sortIndex}) is what the UI (M3) and the JSON export (M4) consume.
 */
public final class PediaState {

    private static final Map<String, PediaCategory> datapackCategories = new LinkedHashMap<>();
    private static final Map<String, PediaEntry> datapackEntries = new LinkedHashMap<>();
    private static final Map<String, PediaCategory> providerCategories = new LinkedHashMap<>();
    private static final Map<String, PediaEntry> providerEntries = new LinkedHashMap<>();

    private PediaState() {}

    /** FULL_SYNC: replace the whole datapack-source set. */
    public static synchronized void handleFullSync(List<PediaCategory> categories, List<PediaEntry> entries) {
        datapackCategories.clear();
        datapackEntries.clear();
        for (PediaCategory category : categories) datapackCategories.put(category.id(), category);
        for (PediaEntry entry : entries) datapackEntries.put(entry.id(), entry);
        Wizardpedia.LOGGER.info("Wizardpedia catalog synced: {} categories, {} entries (datapack source)",
                categories.size(), entries.size());
    }

    /** PROVIDER_PUSH: upsert by id into the provider-source set. */
    public static synchronized void handleProviderPush(List<PediaCategory> categories, List<PediaEntry> entries) {
        for (PediaCategory category : categories) providerCategories.put(category.id(), category);
        for (PediaEntry entry : entries) providerEntries.put(entry.id(), entry);
        Wizardpedia.LOGGER.info("Wizardpedia catalog pushed: {} categories, {} entries (provider upsert; now {} categories, {} entries total)",
                categories.size(), entries.size(),
                providerCategories.size(), providerEntries.size());
    }

    /** Merged category view: provider wins id conflicts; sorted by sortIndex. */
    public static synchronized List<PediaCategory> categories() {
        Map<String, PediaCategory> merged = new LinkedHashMap<>(datapackCategories);
        merged.putAll(providerCategories);
        List<PediaCategory> out = new ArrayList<>(merged.values());
        out.sort(Comparator.comparingInt(PediaCategory::sortIndex));
        return out;
    }

    /** Merged entry view: provider wins id conflicts. */
    public static synchronized List<PediaEntry> entries() {
        Map<String, PediaEntry> merged = new LinkedHashMap<>(datapackEntries);
        merged.putAll(providerEntries);
        return List.copyOf(merged.values());
    }

    /** True if the entry is locked in either source (provider override wins). */
    public static synchronized boolean isLocked(String entryId) {
        PediaEntry provider = providerEntries.get(entryId);
        if (provider != null) return provider.locked();
        PediaEntry datapack = datapackEntries.get(entryId);
        return datapack != null && datapack.locked();
    }
}
