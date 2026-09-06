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
 * { "uiSkin": "vanilla" }   // vanilla | homm | tome | flat | manuscript
 * </pre>
 */
public final class PediaClientConfig {

    /** JSON shape of the config file. */
    public static class Data {
        public String uiSkin = PediaSkin.VANILLA.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Data data = new Data();

    private PediaClientConfig() {}

    /** Book skin (defaults to {@link PediaSkin#VANILLA} until first load). */
    public static PediaSkin skin() {
        return PediaSkin.byId(data.uiSkin);
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
