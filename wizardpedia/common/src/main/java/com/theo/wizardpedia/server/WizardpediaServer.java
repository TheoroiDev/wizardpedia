package com.theo.wizardpedia.server;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import com.theo.wizardpedia.net.CatalogNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side holder of the datapack-sourced catalog. Entries live under
 * {@code data/<ns>/wizardpedia/categories/*.json} and
 * {@code data/<ns>/wizardpedia/entries/*.json} in any namespace; parsing is
 * strict ({@code result()}): a file that fails to decode is skipped whole
 * with a warn log (SpellDataLoader precedent).
 *
 * <p>Reload listeners (Fabric {@code SimpleSynchronousResourceReloadListener}
 * on SERVER_DATA / Forge {@code AddReloadListenerEvent}) call {@link #load};
 * after a load the full catalog is re-pushed to every online player, and
 * each {@code PLAYER_JOIN} pushes it to the joining player (S2C
 * {@code wizardpedia:catalog} FULL_SYNC).
 */
public enum WizardpediaServer {
    INSTANCE;

    private static final String CATEGORIES_DIR = "wizardpedia/categories";
    private static final String ENTRIES_DIR = "wizardpedia/entries";

    /** Server captured at SERVER_STARTING (datapack load runs before STARTED). */
    private static volatile MinecraftServer server;

    private final Map<String, PediaCategory> categories = new LinkedHashMap<>();
    private final Map<String, PediaEntry> entries = new LinkedHashMap<>();

    /** Called from the server lifecycle hooks (see {@link Wizardpedia#init()}). */
    public static void setServer(MinecraftServer s) {
        server = s;
    }

    public synchronized Collection<PediaCategory> categories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    public synchronized Collection<PediaEntry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /**
     * Rebuild the catalog from the given {@link ResourceManager}. Safe to
     * call from any reload executor; a following {@link #syncAll} runs on the
     * server thread if a server is present.
     */
    public synchronized void load(ResourceManager manager) {
        categories.clear();
        entries.clear();

        List<Map.Entry<ResourceLocation, Resource>> badFiles = new ArrayList<>();
        loadCategories(manager, badFiles);
        loadEntries(manager, badFiles);

        int cats = categories.size();
        int ents = entries.size();
        Wizardpedia.LOGGER.info("Wizardpedia datapack loaded: {} categories, {} entries ({} files skipped)",
                cats, ents, badFiles.size());
        syncAll();
    }

    private void loadCategories(ResourceManager manager, List<Map.Entry<ResourceLocation, Resource>> badFiles) {
        Map<ResourceLocation, Resource> files =
                manager.listResources(CATEGORIES_DIR, path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            try (BufferedReader reader = file.getValue().openAsReader()) {
                var result = PediaCategory.CODEC.parse(
                        com.mojang.serialization.JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseReader(reader));
                Optional<PediaCategory> parsed = result.result();
                if (parsed.isPresent()) {
                    categories.put(parsed.get().id(), parsed.get());
                } else {
                    badFiles.add(file);
                    result.error().ifPresent(e -> Wizardpedia.LOGGER.warn(
                            "Wizardpedia category parse failed in {}: {}", file.getKey(), e));
                }
            } catch (Exception e) {
                badFiles.add(file);
                Wizardpedia.LOGGER.warn("Failed to read Wizardpedia category {}", file.getKey(), e);
            }
        }
    }

    private void loadEntries(ResourceManager manager, List<Map.Entry<ResourceLocation, Resource>> badFiles) {
        Map<ResourceLocation, Resource> files =
                manager.listResources(ENTRIES_DIR, path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            try (BufferedReader reader = file.getValue().openAsReader()) {
                var result = PediaEntry.CODEC.parse(
                        com.mojang.serialization.JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseReader(reader));
                Optional<PediaEntry> parsed = result.result();
                if (parsed.isPresent()) {
                    entries.put(parsed.get().id(), parsed.get());
                } else {
                    badFiles.add(file);
                    result.error().ifPresent(e -> Wizardpedia.LOGGER.warn(
                            "Wizardpedia entry parse failed in {}: {}", file.getKey(), e));
                }
            } catch (Exception e) {
                badFiles.add(file);
                Wizardpedia.LOGGER.warn("Failed to read Wizardpedia entry {}", file.getKey(), e);
            }
        }
    }

    /** Push the full catalog to every online player (server thread). */
    public void syncAll() {
        MinecraftServer s = server;
        if (s == null) return;
        s.execute(() -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                sync(player);
            }
        });
    }

    /** Push the full catalog to one player (FULL_SYNC replaces their datapack-source set). */
    public void sync(ServerPlayer player) {
        List<PediaCategory> cats = new ArrayList<>(categories());
        cats.sort(Comparator.comparingInt(PediaCategory::sortIndex));
        CatalogNetwork.sendFullSync(player, cats, entries());
    }
}
