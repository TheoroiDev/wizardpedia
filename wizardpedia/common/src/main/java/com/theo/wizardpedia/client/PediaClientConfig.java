package com.theo.wizardpedia.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theo.wizardpedia.Wizardpedia;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal client-side config ({@code config/wizardpedia/client.json}), parsed
 * with the Gson bundled by vanilla — same as the catalog export. Missing file
 * → a documented default template is written; unknown fields are ignored
 * (forward-compatible). Unknown values fall back to the defaults.
 *
 * <pre>
 * { "uiSkin": "vanilla",              // vanilla | homm | tome | flat | manuscript
 *   "chantLanguagePolicy": "auto",    // auto | off | selected | all (D2)
 *   "chantReadLanguages": [],         // selected policy: two-letter buckets ("ja","zh",...)
 *   "methodPinyin": true,             // D6注音法: pinyin (toned) — default on
 *   "methodRomaji": true,             //           Hepburn romaji — default on
 *   "methodIpa": false }              //           IPA — advanced tier, default off
 * </pre>
 */
public final class PediaClientConfig {

    /** JSON shape of the config file. */
    public static class Data {
        public String uiSkin = PediaSkin.VANILLA.name().toLowerCase(java.util.Locale.ROOT);
        /** D2 language policy id (auto | off | selected | all). */
        public String chantLanguagePolicy = "auto";
        /** D2 {@code selected} policy: two-letter language buckets to annotate. */
        public java.util.List<String> chantReadLanguages = new java.util.ArrayList<>();
        /** D6 annotation methods: pinyin and romaji default on, IPA off. */
        public boolean methodPinyin = true;
        /** D6 annotation methods. */
        public boolean methodRomaji = true;
        /** D6 annotation methods. */
        public boolean methodIpa = false;
    }

    /** D2 language policy for chant annotations (same semantics as the
     *  wizardreal HUD config, independently implemented — zero cross-repo
     *  dependency). */
    public enum LanguagePolicy {
        /** Annotate only languages that are NOT the MC display language. */
        AUTO,
        /** Never annotate. */
        OFF,
        /** Annotate the configured bucket set only. */
        SELECTED,
        /** Annotate anything that has a reading. */
        ALL
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Data data = new Data();

    private PediaClientConfig() {}

    /** Book skin (defaults to {@link PediaSkin#VANILLA} until first load). */
    public static PediaSkin skin() {
        return PediaSkin.byId(data.uiSkin);
    }

    /** D2 chant-annotation language policy (defaults to {@code AUTO}). */
    public static LanguagePolicy chantLanguagePolicy() {
        String v = data.chantLanguagePolicy;
        if (v != null) {
            try {
                return LanguagePolicy.valueOf(v.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return LanguagePolicy.AUTO;
    }

    /** Two-letter buckets for the {@code selected} policy (lowercased). */
    public static java.util.Set<String> chantReadLanguages() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (data.chantReadLanguages != null) {
            for (String lang : data.chantReadLanguages) {
                if (lang != null && !lang.isBlank()) {
                    out.add(lang.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return out;
    }

    /** D6 method switch: toned pinyin (default on). */
    public static boolean methodPinyin() {
        return data.methodPinyin;
    }

    /** D6 method switch: Hepburn romaji (default on). */
    public static boolean methodRomaji() {
        return data.methodRomaji;
    }

    /** D6 method switch: IPA (advanced tier, default off). */
    public static boolean methodIpa() {
        return data.methodIpa;
    }

    /** Load once from the given game directory (client thread). */
    public static void load(Path gameDir) {
        Path file = gameDir.resolve("config").resolve("wizardpedia").resolve("client.json");
        try {
            if (Files.exists(file)) {
                data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
                if (data == null) data = new Data();
            } else {
                data = new Data();
                writeTemplate(file);
            }
        } catch (Exception e) {
            Wizardpedia.LOGGER.warn("Failed to read wizardpedia client config, using defaults", e);
            data = new Data();
        }
    }

    private static void writeTemplate(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new Data()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // read-only environments keep the default skin
        }
    }
}
