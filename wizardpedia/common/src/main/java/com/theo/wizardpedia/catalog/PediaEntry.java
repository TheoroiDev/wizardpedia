package com.theo.wizardpedia.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

/**
 * A catalog entry (book grid cell). Sourced from datapack JSON or the wire
 * format (provider push); fields follow the wire contract in
 * docs/wizardpedia.md §4. {@code aliases} are free-form keywords (trigger
 * words etc., any language kept as-is); {@code lines} are lang keys resolved
 * client-side (description / chant lines).
 */
public record PediaEntry(String id, String categoryId, String titleKey, boolean locked,
                         String iconItem, List<String> aliases, List<String> lines) {

    public static final int MAX_ID = 128;
    public static final int MAX_CATEGORY = 128;
    public static final int MAX_TITLE_KEY = 128;
    public static final int MAX_ICON = 128;
    public static final int MAX_ALIAS = 96;
    public static final int MAX_LINE_KEY = 160;

    public static final Codec<PediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            WireText.capped(MAX_ID).fieldOf("id").forGetter(PediaEntry::id),
            WireText.capped(MAX_CATEGORY).fieldOf("category").forGetter(PediaEntry::categoryId),
            WireText.capped(MAX_TITLE_KEY).fieldOf("title_key").forGetter(PediaEntry::titleKey),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(PediaEntry::locked),
            WireText.capped(MAX_ICON).optionalFieldOf("icon", "").forGetter(PediaEntry::iconItem),
            WireText.capped(MAX_ALIAS).listOf().optionalFieldOf("aliases", List.of()).forGetter(PediaEntry::aliases),
            WireText.capped(MAX_LINE_KEY).listOf().optionalFieldOf("lines_key", List.of()).forGetter(PediaEntry::lines)
    ).apply(i, PediaEntry::new));

    public static void write(FriendlyByteBuf buf, PediaEntry entry) {
        buf.writeUtf(WireText.truncate(entry.id, MAX_ID), MAX_ID);
        buf.writeUtf(WireText.truncate(entry.categoryId, MAX_CATEGORY), MAX_CATEGORY);
        buf.writeUtf(WireText.truncate(entry.titleKey, MAX_TITLE_KEY), MAX_TITLE_KEY);
        buf.writeBoolean(entry.locked);
        buf.writeUtf(WireText.truncate(entry.iconItem, MAX_ICON), MAX_ICON);
        buf.writeVarInt(entry.aliases.size());
        for (String alias : entry.aliases) buf.writeUtf(WireText.truncate(alias, MAX_ALIAS), MAX_ALIAS);
        buf.writeVarInt(entry.lines.size());
        for (String line : entry.lines) buf.writeUtf(WireText.truncate(line, MAX_LINE_KEY), MAX_LINE_KEY);
    }

    public static PediaEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_ID);
        String category = buf.readUtf(MAX_CATEGORY);
        String titleKey = buf.readUtf(MAX_TITLE_KEY);
        boolean locked = buf.readBoolean();
        String icon = buf.readUtf(MAX_ICON);
        int aliasCount = buf.readVarInt();
        List<String> aliases = new java.util.ArrayList<>(aliasCount);
        for (int i = 0; i < aliasCount; i++) aliases.add(buf.readUtf(MAX_ALIAS));
        int lineCount = buf.readVarInt();
        List<String> lines = new java.util.ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) lines.add(buf.readUtf(MAX_LINE_KEY));
        return new PediaEntry(id, category, titleKey, locked, icon, List.copyOf(aliases), List.copyOf(lines));
    }
}
