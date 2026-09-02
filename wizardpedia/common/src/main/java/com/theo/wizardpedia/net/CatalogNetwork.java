package com.theo.wizardpedia.net;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import com.theo.wizardpedia.client.PediaState;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The public S2C catalog channel ({@code wizardpedia:catalog}) — the wire
 * contract any provider mod can target with zero compile-time dependency.
 *
 * <p>Packet layout (docs/wizardpedia.md §4):
 * <pre>
 * byte  formatVersion = 1
 * byte  type          // 0 = FULL_SYNC (replace client datapack-source set)
 *                     // 1 = PROVIDER_PUSH (upsert by id into provider-source set)
 * varInt catCount  { utf catId(≤128), utf nameKey(≤128), utf iconItem(≤128), varInt sortIndex }
 * varInt entryCount { utf entryId(≤128), utf catId(≤128), utf titleKey(≤128), bool locked,
 *                     utf iconItem(≤128), varInt aliasCount{utf alias≤96},
 *                     varInt lineCount{utf lineKey≤160} }
 * </pre>
 *
 * <p><b>Compatibility:</b> append-only fields; a format change bumps
 * {@link #FORMAT_VERSION} and older receivers reject the packet with a warn.
 *
 * <p>The S2C receiver must be registered from platform <em>client</em> init:
 * Fabric {@code ClientModInitializer} / Forge {@code FMLClientSetupEvent} on
 * <b>Bus.MOD</b> (default FORGE bus silently never fires — ChantNetwork lesson).
 */
public final class CatalogNetwork {
    public static final ResourceLocation CHANNEL = Wizardpedia.id("catalog");

    public static final byte FORMAT_VERSION = 1;
    public static final byte FULL_SYNC = 0;
    public static final byte PROVIDER_PUSH = 1;

    private CatalogNetwork() {}

    /** Client-side receiver registration (platform client init only). */
    public static void registerClientReceiver() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), CHANNEL, CatalogNetwork::handle);
    }

    /** Build a FULL_SYNC packet for the given catalog snapshot. */
    public static FriendlyByteBuf fullSync(Collection<PediaCategory> categories, Collection<PediaEntry> entries) {
        return write(FULL_SYNC, categories, entries);
    }

    public static void sendFullSync(net.minecraft.server.level.ServerPlayer player,
                                    Collection<PediaCategory> categories, Collection<PediaEntry> entries) {
        NetworkManager.sendToPlayer(player, CHANNEL, fullSync(categories, entries));
    }

    /** Serialize a typed catalog packet (shared by server sync and tests). */
    public static FriendlyByteBuf write(byte type, Collection<PediaCategory> categories,
                                        Collection<PediaEntry> entries) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(FORMAT_VERSION);
        buf.writeByte(type);
        buf.writeVarInt(categories.size());
        for (PediaCategory category : categories) PediaCategory.write(buf, category);
        buf.writeVarInt(entries.size());
        for (PediaEntry entry : entries) PediaEntry.write(buf, entry);
        return buf;
    }

    /** One parsed catalog packet. */
    public record Parsed(byte type, List<PediaCategory> categories, List<PediaEntry> entries) {}

    /**
     * Deserialize a catalog packet. Returns {@code null} when the leading
     * formatVersion does not match (caller logs and drops).
     */
    public static Parsed read(FriendlyByteBuf buf) {
        byte version = buf.readByte();
        if (version != FORMAT_VERSION) return null;
        byte type = buf.readByte();
        int catCount = buf.readVarInt();
        List<PediaCategory> categories = new ArrayList<>(catCount);
        for (int i = 0; i < catCount; i++) categories.add(PediaCategory.read(buf));
        int entryCount = buf.readVarInt();
        List<PediaEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) entries.add(PediaEntry.read(buf));
        return new Parsed(type, List.copyOf(categories), List.copyOf(entries));
    }

    private static void handle(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        Parsed parsed = read(buf);
        if (parsed == null) {
            Wizardpedia.LOGGER.warn("Rejected wizardpedia:catalog packet: formatVersion mismatch (expected {})",
                    FORMAT_VERSION);
            return;
        }
        switch (parsed.type()) {
            case FULL_SYNC -> ctx.queue(() -> {
                PediaState.handleFullSync(parsed.categories(), parsed.entries());
                // Client-only path: refresh the game-dir JSON export.
                com.theo.wizardpedia.client.CatalogExporter.export();
            });
            case PROVIDER_PUSH -> ctx.queue(() -> {
                PediaState.handleProviderPush(parsed.categories(), parsed.entries());
                com.theo.wizardpedia.client.CatalogExporter.export();
            });
            default -> Wizardpedia.LOGGER.warn("Ignored wizardpedia:catalog packet: unknown type {}", parsed.type());
        }
    }
}
