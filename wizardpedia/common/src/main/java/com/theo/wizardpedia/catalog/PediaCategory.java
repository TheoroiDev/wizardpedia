package com.theo.wizardpedia.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

/**
 * A catalog category (book bookmark). Sourced from datapack JSON or the wire
 * format (provider push); fields follow the wire contract in
 * docs/wizardpedia.md §4 (namespaced ids, lang keys, "" = no icon).
 */
public record PediaCategory(String id, String nameKey, String iconItem, int sortIndex) {

    public static final int MAX_ID = 128;
    public static final int MAX_NAME_KEY = 128;
    public static final int MAX_ICON = 128;

    public static final Codec<PediaCategory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(PediaCategory::id),
            Codec.STRING.fieldOf("name_key").forGetter(PediaCategory::nameKey),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(PediaCategory::iconItem),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(PediaCategory::sortIndex)
    ).apply(i, PediaCategory::new));

    public static void write(FriendlyByteBuf buf, PediaCategory category) {
        buf.writeUtf(category.id, MAX_ID);
        buf.writeUtf(category.nameKey, MAX_NAME_KEY);
        buf.writeUtf(category.iconItem, MAX_ICON);
        buf.writeVarInt(category.sortIndex);
    }

    public static PediaCategory read(FriendlyByteBuf buf) {
        return new PediaCategory(
                buf.readUtf(MAX_ID),
                buf.readUtf(MAX_NAME_KEY),
                buf.readUtf(MAX_ICON),
                buf.readVarInt());
    }
}
