package com.theo.wizardpedia.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chant-annotation config (R-B, D2/D6): defaults on fresh install, value
 *  parsing and invalid-value fallbacks in {@code client.json}. */
class PediaClientConfigTest {

    @TempDir
    Path gameDir;

    @Test
    void freshInstallWritesAnnotationDefaults() throws Exception {
        PediaClientConfig.load(gameDir);
        assertEquals(PediaClientConfig.LanguagePolicy.AUTO, PediaClientConfig.chantLanguagePolicy());
        assertTrue(PediaClientConfig.chantReadLanguages().isEmpty());
        assertTrue(PediaClientConfig.methodPinyin());  // D6: pinyin default on
        assertTrue(PediaClientConfig.methodRomaji());  // D6: romaji default on
        assertTrue(!PediaClientConfig.methodIpa());    // D6: IPA advanced tier off
        String json = Files.readString(
                gameDir.resolve("config").resolve("wizardpedia").resolve("client.json"));
        assertTrue(json.contains("chantLanguagePolicy"));
        assertTrue(json.contains("methodIpa"));
        assertTrue(json.contains("uiSkin"));
    }

    @Test
    void parsesAnnotationOverrides() throws Exception {
        Path file = gameDir.resolve("config").resolve("wizardpedia").resolve("client.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {
                  "uiSkin": "homm",
                  "chantLanguagePolicy": "selected",
                  "chantReadLanguages": ["JA", "ko"],
                  "methodPinyin": false,
                  "methodRomaji": true,
                  "methodIpa": true
                }
                """);
        PediaClientConfig.load(gameDir);
        assertEquals(PediaClientConfig.LanguagePolicy.SELECTED, PediaClientConfig.chantLanguagePolicy());
        assertEquals(java.util.Set.of("ja", "ko"), PediaClientConfig.chantReadLanguages());
        assertTrue(!PediaClientConfig.methodPinyin());
        assertTrue(PediaClientConfig.methodRomaji());
        assertTrue(PediaClientConfig.methodIpa());
        assertEquals(PediaSkin.HOMM, PediaClientConfig.skin());
    }

    @Test
    void invalidPolicyFallsBackToAuto() throws Exception {
        Path file = gameDir.resolve("config").resolve("wizardpedia").resolve("client.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ \"chantLanguagePolicy\": \"sometimes\" }");
        PediaClientConfig.load(gameDir);
        assertEquals(PediaClientConfig.LanguagePolicy.AUTO, PediaClientConfig.chantLanguagePolicy());
        // absent switches keep their defaults
        assertTrue(PediaClientConfig.methodPinyin());
        assertTrue(!PediaClientConfig.methodIpa());
    }
}
