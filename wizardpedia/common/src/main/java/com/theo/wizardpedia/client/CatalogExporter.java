package com.theo.wizardpedia.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import com.theo.wizardpedia.catalog.PediaLine;
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
 * format 4: full content model + structured chant lines with readings —
 * language-keyed aliases/desc/chants, tags, entity id, stage ladder) —
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
            root.put("format", 4);
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
                if (entry.learning() >= 0) json.put("learning", entry.learning());
                if (entry.manaCost() >= 0) json.put("mana_cost", entry.manaCost());
                if (entry.cooldownSeconds() >= 0) json.put("cooldown_seconds", entry.cooldownSeconds());
                if (entry.difficulty() >= 0) json.put("difficulty", entry.difficulty());
                if (!entry.entityId().isEmpty()) json.put("entity", entry.entityId());
                if (!entry.tags().isEmpty()) json.put("tags", entry.tags());
                json.put("aliases", exportLangMap(entry.aliases()));
                json.put("lines", exportLangMap(entry.desc()));
                if (!entry.chants().isEmpty()) json.put("chants", exportChantMap(entry.chants()));
                if (!entry.stages().isEmpty()) json.put("stages", exportStages(entry));
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

    private static Map<String, Object> exportChantMap(
            java.util.Map<String, java.util.List<java.util.List<PediaLine>>> chants) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.List<java.util.List<PediaLine>>> e : chants.entrySet()) {
            List<Object> variants = new ArrayList<>();
            for (java.util.List<PediaLine> lines : e.getValue()) {
                List<Object> resolved = new ArrayList<>();
                for (PediaLine line : lines) {
                    Map<String, Object> lineJson = new LinkedHashMap<>();
                    lineJson.put("text", Component.translatable(line.text()).getString());
                    if (!line.readings().isEmpty()) lineJson.put("readings", line.readings());
                    resolved.add(lineJson);
                }
                variants.add(resolved);
            }
            out.put(e.getKey(), variants);
        }
        return out;
    }

    private static List<Object> exportStages(PediaEntry entry) {
        List<Object> out = new ArrayList<>();
        for (PediaEntry.PediaStage stage : entry.stages()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("after_lines", stage.afterLines());
            json.put("mastery", stage.mastery());
            if (stage.manaCost() >= 0) json.put("mana_cost", stage.manaCost());
            if (stage.cooldownSeconds() >= 0) json.put("cooldown_seconds", stage.cooldownSeconds());
            json.put("desc", exportLangMap(stage.desc()));
            out.add(json);
        }
        return out;
    }
}
