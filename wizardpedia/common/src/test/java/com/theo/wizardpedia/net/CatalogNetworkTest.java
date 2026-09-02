package com.theo.wizardpedia.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wire-contract tests for the S2C {@code wizardpedia:catalog} channel
 * (docs/wizardpedia.md §4): roundtrip for both packet types, the
 * formatVersion rejection gate, and §4 cap truncation (code-point safe).
 */
class CatalogNetworkTest {

    private static final List<PediaCategory> CATEGORIES = List.of(
            new PediaCategory("wizardreal:wizardry", "origin.wizardreal.wizardry",
                    "wizardreal:staff_apprentice", 10),
            new PediaCategory("wizardpedia:guide", "wizardpedia.category.guide", "minecraft:book", 0));

    private static final List<PediaEntry> ENTRIES = List.of(
            new PediaEntry("wizardreal:explosion", "wizardreal:wizardry", "spell.wizardreal:explosion.name",
                    true, "wizardreal:spell_tome",
                    List.of("explosion", "爆裂"),
                    List.of("wizardreal.chant.explosion.en.l1", "wizardreal.chant.explosion.en.l2")),
            new PediaEntry("wizardreal:vitae", "wizardreal:wizardry", "spell.wizardreal:vitae.name",
                    false, "", List.of(), List.of()));

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
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte((byte) 99); // future/unknown format version
        buf.writeByte(CatalogNetwork.FULL_SYNC);
        assertNull(CatalogNetwork.read(buf), "mismatched formatVersion must be rejected (return null)");
    }

    @Test
    void cjkAliasesSurviveWire() {
        // Multi-language aliases (CJK) ride the wire verbatim within the utf caps.
        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.PROVIDER_PUSH, List.of(), List.of(
                new PediaEntry("x:y", "x:cat", "x.key", false, "", List.of("爆裂", "_thunder_"), List.of())));
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);
        assertNotNull(parsed);
        assertEquals("爆裂", parsed.entries().get(0).aliases().get(0));
    }

    @Test
    void oversizedFieldsTruncatedOnWire() {
        String longStr = "y".repeat(300);
        String astral = new String(Character.toChars(0x1D400)).repeat(100);
        String pairAtCut = "a".repeat(95) + new String(Character.toChars(0x1D400));
        List<PediaCategory> categories = List.of(
                new PediaCategory("wizardreal:wizardry", longStr, longStr, 3));
        List<PediaEntry> entries = List.of(
                new PediaEntry(longStr, longStr, longStr, false, longStr,
                        List.of("a".repeat(150), astral, pairAtCut),
                        List.of("l".repeat(200))));

        FriendlyByteBuf buf = CatalogNetwork.write(CatalogNetwork.FULL_SYNC, categories, entries);
        CatalogNetwork.Parsed parsed = CatalogNetwork.read(buf);

        assertNotNull(parsed, "oversized fields must truncate, not break the sync");
        assertEquals(CatalogNetwork.FULL_SYNC, parsed.type());
        PediaCategory category = parsed.categories().get(0);
        assertEquals(PediaCategory.MAX_NAME_KEY, category.nameKey().length());
        assertEquals(PediaCategory.MAX_ICON, category.iconItem().length());
        PediaEntry entry = parsed.entries().get(0);
        assertEquals(PediaEntry.MAX_ID, entry.id().length());
        assertEquals(PediaEntry.MAX_ALIAS, entry.aliases().get(0).length());
        assertEquals(PediaEntry.MAX_LINE_KEY, entry.lines().get(0).length());
        String truncated = entry.aliases().get(1);
        assertEquals(PediaEntry.MAX_ALIAS, truncated.length());
        assertEquals(PediaEntry.MAX_ALIAS / 2, truncated.codePointCount(0, truncated.length()));
        assertFalse(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)),
                "truncation must not split surrogate pairs");
        String boundary = entry.aliases().get(2);
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
        JsonArray aliases = new JsonArray();
        aliases.add("a".repeat(150));
        entryJson.add("aliases", aliases);
        JsonArray lines = new JsonArray();
        lines.add("k".repeat(200));
        entryJson.add("lines_key", lines);

        PediaEntry entry = PediaEntry.CODEC.parse(JsonOps.INSTANCE, entryJson).result().orElseThrow();
        assertEquals(PediaEntry.MAX_ID, entry.id().length());
        assertEquals(PediaEntry.MAX_ICON, entry.iconItem().length());
        assertEquals(PediaEntry.MAX_ALIAS, entry.aliases().get(0).length());
        assertEquals(PediaEntry.MAX_LINE_KEY, entry.lines().get(0).length());

        JsonObject categoryJson = new JsonObject();
        categoryJson.addProperty("id", longStr);
        categoryJson.addProperty("name_key", longStr);
        PediaCategory category = PediaCategory.CODEC.parse(JsonOps.INSTANCE, categoryJson).result().orElseThrow();
        assertEquals(PediaCategory.MAX_ID, category.id().length());
        assertEquals(PediaCategory.MAX_NAME_KEY, category.nameKey().length());
    }
}
