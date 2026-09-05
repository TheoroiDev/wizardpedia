package com.theo.wizardpedia.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A catalog entry (book grid cell). Sourced from datapack JSON or the wire
 * format (provider push); fields follow the wire contract v2 in
 * docs/wizardpedia.md §4. {@code aliases} are free-form keywords (trigger
 * words etc.) keyed by two-letter language code; {@code lines} are lang keys
 * or literal chant text resolved client-side (translatable fallback shows
 * the raw value when no lang entry exists). The {@code ""} key is the
 * language-neutral bucket, shown on every language page.
 */
public record PediaEntry(String id, String categoryId, String titleKey, boolean locked,
                         String iconItem, Map<String, List<String>> aliases, Map<String, List<String>> lines) {

    /** Language-bucket key for language-neutral (language-less) data. */
    public static final String LANG_NEUTRAL = "";

    public static final int MAX_ID = 128;
    public static final int MAX_CATEGORY = 128;
    public static final int MAX_TITLE_KEY = 128;
    public static final int MAX_ICON = 128;
    public static final int MAX_ALIAS = 96;
    public static final int MAX_LINE_KEY = 160;
    public static final int MAX_LANG = 8;

    public PediaEntry {
        aliases = ordered(aliases);
        lines = ordered(lines);
    }

    private static Map<String, List<String>> ordered(Map<String, List<String>> in) {
        if (in == null || in.isEmpty()) return Map.of();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : in.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }

    public static final Codec<PediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            WireText.capped(MAX_ID).fieldOf("id").forGetter(PediaEntry::id),
            WireText.capped(MAX_CATEGORY).fieldOf("category").forGetter(PediaEntry::categoryId),
            WireText.capped(MAX_TITLE_KEY).fieldOf("title_key").forGetter(PediaEntry::titleKey),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(PediaEntry::locked),
            WireText.capped(MAX_ICON).optionalFieldOf("icon", "").forGetter(PediaEntry::iconItem),
            langMapCodec(MAX_ALIAS).optionalFieldOf("aliases", Map.of()).forGetter(PediaEntry::aliases),
            langMapCodec(MAX_LINE_KEY).optionalFieldOf("lines_key", Map.of()).forGetter(PediaEntry::lines)
    ).apply(i, PediaEntry::new));

    /** {@code { "<lang>": ["value", ...] }} with per-value truncation. */
    private static Codec<Map<String, List<String>>> langMapCodec(int maxLen) {
        return Codec.unboundedMap(WireText.capped(MAX_LANG), WireText.capped(maxLen).listOf());
    }

    public static void write(FriendlyByteBuf buf, PediaEntry entry) {
        buf.writeUtf(WireText.truncate(entry.id, MAX_ID), MAX_ID);
        buf.writeUtf(WireText.truncate(entry.categoryId, MAX_CATEGORY), MAX_CATEGORY);
        buf.writeUtf(WireText.truncate(entry.titleKey, MAX_TITLE_KEY), MAX_TITLE_KEY);
        buf.writeBoolean(entry.locked);
        buf.writeUtf(WireText.truncate(entry.iconItem, MAX_ICON), MAX_ICON);
        writeLangMap(buf, entry.aliases, MAX_ALIAS);
        writeLangMap(buf, entry.lines, MAX_LINE_KEY);
    }

    public static PediaEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_ID);
        String category = buf.readUtf(MAX_CATEGORY);
        String titleKey = buf.readUtf(MAX_TITLE_KEY);
        boolean locked = buf.readBoolean();
        String icon = buf.readUtf(MAX_ICON);
        return new PediaEntry(id, category, titleKey, locked, icon,
                readLangMap(buf, MAX_ALIAS), readLangMap(buf, MAX_LINE_KEY));
    }

    private static void writeLangMap(FriendlyByteBuf buf, Map<String, List<String>> map, int maxLen) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            buf.writeUtf(WireText.truncate(e.getKey(), MAX_LANG), MAX_LANG);
            buf.writeVarInt(e.getValue().size());
            for (String value : e.getValue()) buf.writeUtf(WireText.truncate(value, maxLen), maxLen);
        }
    }

    private static Map<String, List<String>> readLangMap(FriendlyByteBuf buf, int maxLen) {
        int langCount = buf.readVarInt();
        Map<String, List<String>> out = new LinkedHashMap<>(langCount);
        for (int i = 0; i < langCount; i++) {
            String lang = buf.readUtf(MAX_LANG);
            int n = buf.readVarInt();
            List<String> values = new ArrayList<>(n);
            for (int k = 0; k < n; k++) values.add(buf.readUtf(maxLen));
            out.put(lang, List.copyOf(values));
        }
        return ordered(out);
    }

    // ---- language accessors (UI + export) --------------------------------

    /** Language codes carrying any data, insertion-ordered, neutral excluded. */
    public Set<String> languages() {
        LinkedHashSet<String> out = new LinkedHashSet<>(aliases.keySet());
        out.addAll(lines.keySet());
        out.remove(LANG_NEUTRAL);
        return out;
    }

    /** True if the entry carries at least one non-neutral language bucket. */
    public boolean hasLanguages() {
        return !languages().isEmpty();
    }

    /** Keywords for one language page: that bucket + the neutral bucket. */
    public List<String> aliasesFor(String language) {
        return merged(aliases, language);
    }

    /** Lines for one language page: that bucket + the neutral bucket. */
    public List<String> linesFor(String language) {
        return merged(lines, language);
    }

    private static List<String> merged(Map<String, List<String>> map, String language) {
        String lang = language == null ? LANG_NEUTRAL : language;
        if (!map.containsKey(lang) || lang.equals(LANG_NEUTRAL)) {
            return map.getOrDefault(LANG_NEUTRAL, List.of());
        }
        LinkedHashSet<String> out = new LinkedHashSet<>(map.get(lang));
        List<String> neutral = map.get(LANG_NEUTRAL);
        if (neutral != null) out.addAll(neutral);
        return List.copyOf(out);
    }
}
