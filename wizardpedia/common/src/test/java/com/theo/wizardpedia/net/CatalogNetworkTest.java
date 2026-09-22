package com.theo.wizardpedia.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import com.theo.wizardpedia.catalog.PediaLine;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-contract tests for the S2C {@code wizardpedia:catalog} channel
 * (docs/wizardpedia.md §4, formatVersion 2): roundtrip for both packet
 * types, language-bucket annotation, the formatVersion rejection gate, and
 * §4 cap truncation (code-point safe).
 */
class CatalogNetworkTest {

    private static final List<PediaCategory> CATEGORIES = List.of(
            new PediaCategory("wizardreal:wizardry", "origin.wizardreal.wizardry",
                    "wizardreal:staff_apprentice", 10),
            new PediaCategory("wizardpedia:guide", "wizardpedia.category.guide", "minecraft:book", 0));

    private static final Map<String, List<String>> TRIGGER = Map.of(
            "", List.of("_thunder_"),
            "en", List.of("explosion"),
            "zh", List.of("爆裂"));
    private static final Map<String, List<String>> LINES = Map.of(
            "en", List.of("wizardreal.chant.explosion.en.l1", "wizardreal.chant.explosion.en.l2"),
            "zh", List.of("黑袍蔽空"));
    private static final Map<String, List<List<PediaLine>>> CHANTS = Map.of(
            "", List.of(List.of(PediaLine.plain("wizardreal.chant.l1")),
                    List.of(PediaLine.plain("wizardreal.chant.l2a"),
                            new PediaLine("wizardreal.chant.l2b",
                                    Map.of("pinyin", "hēi páo bì kōng")))));
    private static final List<PediaEntry.PediaStage> STAGES = List.of(
            new PediaEntry.PediaStage(3, 25.0f, 20, 6.0f, Map.of("", List.of("wizardreal.effect.explosion"))));

    private static final List<PediaEntry> ENTRIES = List.of(
            new PediaEntry("wizardreal:explosion", "wizardreal:wizardry", "spell.wizardreal:explosion.name",
                    true, 42.5f, 50, 10.0f, 1.5f, "wizardreal:spell_tome", "", List.of("fire"),
                    TRIGGER, LINES, CHANTS, STAGES),
            new PediaEntry("wizardreal:vitae", "wizardreal:wizardry", "spell.wizardreal:vitae.name",
                    false, PediaEntry.LEARNING_UNKNOWN, PediaEntry.COST_UNKNOWN, PediaEntry.COST_UNKNOWN,
                    PediaEntry.DIFFICULTY_UNKNOWN, "", "", List.of(), Map.of(), Map.of(), Map.of(), List.of()));

    @Test
    void fullSyncRoundtrip() {
        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.FULL_SYNC, CATEGORIES, ENTRIES);
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);
        assertNotNull(parsed);
        assertEquals(CatalogNetwork.FULL_SYNC, parsed.type());
        assertEquals(CATEGORIES, parsed.categories());
        assertEquals(ENTRIES, parsed.entries());
    }

    @Test
    void providerPushRoundtrip() {
        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.PROVIDER_PUSH, CATEGORIES, ENTRIES);
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);
        assertNotNull(parsed);
        assertEquals(CatalogNetwork.PROVIDER_PUSH, parsed.type());
        assertEquals(CATEGORIES, parsed.categories());
        assertEquals(ENTRIES, parsed.entries());
    }

    @Test
    void formatVersionMismatchRejected() {
        // v3 (pre structured chant lines) and unknown future versions: rejected.
        for (byte stale : new byte[] {3, 99}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeByte(stale);
            buf.writeByte(CatalogNetwork.FULL_SYNC);
            assertNull(CatalogNetwork.read(buf), "formatVersion " + stale + " must be rejected (return null)");
        }
    }

    @Test
    void languageBucketsSurviveWire() {
        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.PROVIDER_PUSH, List.of(), ENTRIES);
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);
        assertNotNull(parsed);
        PediaEntry explosion = parsed.entries().get(0);
        assertEquals(java.util.Set.of("en", "zh"), explosion.languages());
        assertTrue(explosion.hasLanguages());
        // Language page view = bucket ∪ neutral.
        assertEquals(List.of("explosion", "_thunder_"), explosion.aliasesFor("en"));
        assertEquals(List.of("爆裂", "_thunder_"), explosion.aliasesFor("zh"));
        assertEquals(List.of("_thunder_"), explosion.aliasesFor(PediaEntry.LANG_NEUTRAL));
        // Unknown language falls back to the neutral bucket only.
        assertEquals(List.of("_thunder_"), explosion.aliasesFor("ko"));
        // No-bucket entry: single implicit language page (neutral-only).
        PediaEntry vitae = parsed.entries().get(1);
        assertFalse(vitae.hasLanguages());
        assertEquals(java.util.Set.of(), vitae.languages());
    }

    @Test
    void cjkAliasesSurviveWire() {
        // Multi-language aliases (CJK) ride the wire verbatim within the utf caps.
        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.PROVIDER_PUSH, List.of(), List.of(
                new PediaEntry("x:y", "x:cat", "x.key", false, PediaEntry.LEARNING_UNKNOWN,
                        PediaEntry.COST_UNKNOWN, PediaEntry.COST_UNKNOWN, PediaEntry.DIFFICULTY_UNKNOWN,
                        "", "", List.of(),
                        Map.of("", List.of("爆裂", "_thunder_")), Map.of(), Map.of(), List.of())));
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);
        assertNotNull(parsed);
        assertEquals("爆裂", parsed.entries().get(0).aliases().get("").get(0));
    }

    @Test
    void oversizedFieldsTruncatedOnWire() {
        String longStr = "y".repeat(300);
        String astral = new String(Character.toChars(0x1D400)).repeat(100);
        String pairAtCut = "a".repeat(95) + new String(Character.toChars(0x1D400));
        List<PediaCategory> categories = List.of(
                new PediaCategory("wizardreal:wizardry", longStr, longStr, 3));
        List<PediaEntry> entries = List.of(
                new PediaEntry(longStr, longStr, longStr, false, PediaEntry.LEARNING_UNKNOWN,
                        PediaEntry.COST_UNKNOWN, PediaEntry.COST_UNKNOWN, PediaEntry.DIFFICULTY_UNKNOWN,
                        longStr, "", List.of(),
                        Map.of("", List.of("a".repeat(150), astral, pairAtCut)),
                        Map.of("", List.of("l".repeat(200))), Map.of(), List.of()));

        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.FULL_SYNC, categories, entries);
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);

        assertNotNull(parsed, "oversized fields must truncate, not break the sync");
        assertEquals(CatalogNetwork.FULL_SYNC, parsed.type());
        PediaCategory category = parsed.categories().get(0);
        assertEquals(PediaCategory.MAX_NAME_KEY, category.nameKey().length());
        assertEquals(PediaCategory.MAX_ICON, category.iconItem().length());
        PediaEntry entry = parsed.entries().get(0);
        assertEquals(PediaEntry.MAX_ID, entry.id().length());
        assertEquals(PediaEntry.MAX_ALIAS, entry.aliases().get("").get(0).length());
        assertEquals(PediaEntry.MAX_LINE_KEY, entry.desc().get("").get(0).length());
        String truncated = entry.aliases().get("").get(1);
        assertEquals(PediaEntry.MAX_ALIAS, truncated.length());
        assertEquals(PediaEntry.MAX_ALIAS / 2, truncated.codePointCount(0, truncated.length()));
        assertFalse(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)),
                "truncation must not split surrogate pairs");
        String boundary = entry.aliases().get("").get(2);
        assertEquals(95, boundary.length(), "a pair cut at the cap boundary is dropped, not split");
        assertFalse(Character.isHighSurrogate(boundary.charAt(boundary.length() - 1)),
                "truncation must not split surrogate pairs");
    }

    @Test
    void datapackOversizedFieldsTruncatedAtParse() {
        String longStr = "y".repeat(300);
        JsonObject entryJson = new JsonObject();
        entryJson.addProperty("id", longStr);
        entryJson.addProperty("category", "x:cat");
        entryJson.addProperty("title_key", "x.key");
        entryJson.addProperty("icon", longStr);
        JsonObject aliases = new JsonObject();
        JsonArray neutralAliases = new JsonArray();
        neutralAliases.add("a".repeat(150));
        aliases.add("", neutralAliases);
        entryJson.add("aliases", aliases);
        JsonObject lines = new JsonObject();
        JsonArray neutralLines = new JsonArray();
        neutralLines.add("k".repeat(200));
        lines.add("", neutralLines);
        entryJson.add("lines_key", lines);

        PediaEntry entry = PediaEntry.CODEC.parse(JsonOps.INSTANCE, entryJson).result().orElseThrow();
        assertEquals(PediaEntry.MAX_ID, entry.id().length());
        assertEquals(PediaEntry.MAX_ICON, entry.iconItem().length());
        assertEquals(PediaEntry.MAX_ALIAS, entry.aliases().get("").get(0).length());
        assertEquals(PediaEntry.MAX_LINE_KEY, entry.desc().get("").get(0).length());

        JsonObject categoryJson = new JsonObject();
        categoryJson.addProperty("id", longStr);
        categoryJson.addProperty("name_key", longStr);
        PediaCategory category = PediaCategory.CODEC.parse(JsonOps.INSTANCE, categoryJson).result().orElseThrow();
        assertEquals(PediaCategory.MAX_ID, category.id().length());
        assertEquals(PediaCategory.MAX_NAME_KEY, category.nameKey().length());
    }

    @Test
    void chantLineReadingsSurviveWire() {
        // v4: structured chant lines carry the annotation map (text + readings)
        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.PROVIDER_PUSH, List.of(), ENTRIES);
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);
        assertNotNull(parsed);
        PediaLine bare = parsed.entries().get(0).chants().get("").get(0).get(0);
        assertEquals("wizardreal.chant.l1", bare.text());
        assertEquals(Map.of(), bare.readings(), "plain lines keep an empty readings map");
        PediaLine annotated = parsed.entries().get(0).chants().get("").get(1).get(1);
        assertEquals("wizardreal.chant.l2b", annotated.text());
        assertEquals(Map.of("pinyin", "hēi páo bì kōng"), annotated.readings());
    }

    @Test
    void oversizedReadingsTruncatedOnWire() {
        String longText = "t".repeat(300);
        String longReading = "ā".repeat(200); // multi-byte chars, > 128 UTF-16 units
        Map<String, List<List<PediaLine>>> chants = Map.of("",
                List.of(List.of(new PediaLine(longText, Map.of("pinyin", longReading)))));
        PediaEntry entry = new PediaEntry("x:y", "x:cat", "x.key", false, PediaEntry.LEARNING_UNKNOWN,
                PediaEntry.COST_UNKNOWN, PediaEntry.COST_UNKNOWN, PediaEntry.DIFFICULTY_UNKNOWN,
                "", "", List.of(), Map.of(), Map.of(), chants, List.of());

        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.FULL_SYNC, List.of(), List.of(entry));
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);

        assertNotNull(parsed, "oversized readings must truncate, not break the sync");
        PediaLine line = parsed.entries().get(0).chants().get("").get(0).get(0);
        assertEquals(PediaEntry.MAX_LINE_KEY, line.text().length());
        assertEquals("pinyin", line.readings().keySet().iterator().next());
        String reading = line.readings().values().iterator().next();
        assertEquals(PediaLine.MAX_READING_VALUE, reading.length());
    }
}
