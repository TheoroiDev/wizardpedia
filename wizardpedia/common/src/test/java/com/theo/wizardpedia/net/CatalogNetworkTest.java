package com.theo.wizardpedia.net;

import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wire-contract tests for the S2C {@code wizardpedia:catalog} channel
 * (docs/wizardpedia.md §4): roundtrip for both packet types and the
 * formatVersion rejection gate.
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
}
