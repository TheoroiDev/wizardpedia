package com.theo.wizardpedia.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Writes the merged final catalog view to
 * {@code <game-dir>/wizardpedia/pedia_catalog.json} (export schema §6.2,
 * format 2: aliases/lines keyed by language code, {@code ""} = neutral) —
 * the external-tooling data source. Runs on the client thread after every
 * state change (FULL_SYNC / PROVIDER_PUSH); sources are not distinguished,
 * texts are resolved in the active game language (missing keys fall back to
 * the key itself).
 *
 * <p>Writes are defensive: any failure logs and skips — never breaks the UI.
 */
public final class CatalogExporter {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private CatalogExporter() {}

    /** Per-language map with every line value resolved (missing keys fall
     *  back to the raw key, so literal chant text passes through unchanged). */
    private static Map<String, Object> exportLangMap(java.util.Map<String, java.util.List<String>> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.List<String>> e : map.entrySet()) {
            List<Object> values = new ArrayList<>();
            for (String value : e.getValue()) values.add(Component.translatable(value).getString());
            out.put(e.getKey(), values);
        }
        return out;
    }

    /** Snapshot + write the merged catalog (client thread). */
    public static void export() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path file = mc.gameDirectory.toPath().resolve("wizardpedia").resolve("pedia_catalog.json");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("format", 2);
            root.put("language", mc.getLanguageManager().getSelected());

            List<Object> categories = new ArrayList<>();
            for (PediaCategory category : PediaState.categories()) {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put("id", category.id());
                json.put("name", Component.translatable(category.nameKey()).getString());
                json.put("sort", category.sortIndex());
                categories.add(json);
            }
            root.put("categories", categories);

            List<Object> entries = new ArrayList<>();
            for (PediaEntry entry : PediaState.entries()) {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put("id", entry.id());
                json.put("category", entry.categoryId());
                json.put("title", Component.translatable(entry.titleKey()).getString());
                json.put("locked", PediaState.isLocked(entry.id()));
                json.put("aliases", exportLangMap(entry.aliases()));
                json.put("lines", exportLangMap(entry.lines()));
                entries.add(json);
            }
            root.put("entries", entries);

            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            Wizardpedia.LOGGER.info("Pedia catalog exported: {} ({} categories, {} entries)",
                    file, categories.size(), entries.size());
        } catch (Exception e) {
            Wizardpedia.LOGGER.warn("Failed to export pedia catalog", e);
        }
    }
}
