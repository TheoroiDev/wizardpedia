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
 * format (provider push); fields follow the wire contract v4 in
 * docs/wizardpedia.md §4. Content-model (provider-agnostic):
 * <ul>
 *   <li>{@code entityId} — optional living-entity id; the detail page renders
 *       a live entity preview instead of the item icon (mob entries);</li>
 *   <li>{@code tags} — free-form filter keywords (spell schools etc.), the
 *       right bookmark rail offers one filter tab per tag;</li>
 *   <li>{@code aliases} — keywords per two-letter language code ({@code ""} =
 *       neutral bucket, shown on every language page);</li>
 *   <li>{@code desc} — description lines per language (lang keys or literal
 *       text; the translatable missing-key fallback renders both);</li>
 *   <li>{@code chants} — per language → chant variants → ordered structured
 *       {@link PediaLine}s (text + provider-derived readings; the variant
 *       switcher cycles the middle level);</li>
 *   <li>{@code stages} — ascending tier ladder (e.g. spell chant stages):
 *       gate values + per-stage description lines.</li>
 * </ul>
 */
public record PediaEntry(String id, String categoryId, String titleKey, boolean locked, float learning,
                         int manaCost, float cooldownSeconds, float difficulty,
                         String iconItem, String entityId, List<String> tags,
                         Map<String, List<String>> aliases,
                         Map<String, List<String>> desc,
                         Map<String, List<List<PediaLine>>> chants,
                         List<PediaStage> stages) {

    /** Learning value meaning "not provided by the source" (datapack entries). */
    public static final float LEARNING_UNKNOWN = -1f;
    /** Scalar value meaning "not provided by the source". */
    public static final int COST_UNKNOWN = -1;
    /** Scalar value meaning "not provided by the source". */
    public static final float DIFFICULTY_UNKNOWN = -1f;

    /** Language-bucket key for language-neutral (language-less) data. */
    public static final String LANG_NEUTRAL = "";

    public static final int MAX_ID = 128;
    public static final int MAX_CATEGORY = 128;
    public static final int MAX_TITLE_KEY = 128;
    public static final int MAX_ICON = 128;
    public static final int MAX_ENTITY = 128;
    public static final int MAX_TAG = 32;
    public static final int MAX_ALIAS = 96;
    public static final int MAX_LINE_KEY = 160;
    public static final int MAX_LANG = 8;

    /** One tier of an entry's stage ladder (e.g. spell chant stages):
     *  {@code manaCost}/{@code cooldownSeconds} {@code -1} = inherit the
     *  spell's base value. */
    public record PediaStage(int afterLines, float mastery, int manaCost, float cooldownSeconds,
                             Map<String, List<String>> desc) {

        public static final Codec<PediaStage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("after_lines").forGetter(PediaStage::afterLines),
                Codec.FLOAT.optionalFieldOf("mastery", 0f).forGetter(PediaStage::mastery),
                Codec.INT.optionalFieldOf("mana_cost", -1).forGetter(PediaStage::manaCost),
                Codec.FLOAT.optionalFieldOf("cooldown_seconds", -1f).forGetter(PediaStage::cooldownSeconds),
                langMapCodec(MAX_LINE_KEY).optionalFieldOf("desc", Map.of()).forGetter(PediaStage::desc)
        ).apply(i, PediaStage::new));

        public PediaStage {
            desc = ordered(desc);
        }

        public static void write(FriendlyByteBuf buf, PediaStage stage) {
            buf.writeVarInt(stage.afterLines);
            buf.writeFloat(stage.mastery);
            buf.writeVarInt(stage.manaCost);
            buf.writeFloat(stage.cooldownSeconds);
            writeLangMap(buf, stage.desc, MAX_LINE_KEY);
        }

        public static PediaStage read(FriendlyByteBuf buf) {
            return new PediaStage(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                    readLangMap(buf, MAX_LINE_KEY));
        }
    }

    public PediaEntry {
        if (entityId == null) entityId = "";
        tags = tags == null ? List.of() : List.copyOf(tags);
        aliases = ordered(aliases);
        desc = ordered(desc);
        chants = orderedChants(chants);
        stages = stages == null ? List.of() : List.copyOf(stages);
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

    private static Map<String, List<List<PediaLine>>> orderedChants(Map<String, List<List<PediaLine>>> in) {
        if (in == null || in.isEmpty()) return Map.of();
        Map<String, List<List<PediaLine>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<PediaLine>>> e : in.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
            List<List<PediaLine>> variants = new ArrayList<>(e.getValue().size());
            for (List<PediaLine> lines : e.getValue()) {
                variants.add(lines == null ? List.of() : List.copyOf(lines));
            }
            out.put(e.getKey(), Collections.unmodifiableList(variants));
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }

    /** One structured chant line (text + readings map). */
    public static final Codec<PediaLine> LINE_CODEC = RecordCodecBuilder.create(i -> i.group(
            WireText.capped(MAX_LINE_KEY).fieldOf("text").forGetter(PediaLine::text),
            Codec.unboundedMap(WireText.capped(PediaLine.MAX_READING_KEY),
                    WireText.capped(PediaLine.MAX_READING_VALUE))
                    .optionalFieldOf("readings", Map.of()).forGetter(PediaLine::readings)
    ).apply(i, PediaLine::new));

    public static final Codec<PediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            WireText.capped(MAX_ID).fieldOf("id").forGetter(PediaEntry::id),
            WireText.capped(MAX_CATEGORY).fieldOf("category").forGetter(PediaEntry::categoryId),
            WireText.capped(MAX_TITLE_KEY).fieldOf("title_key").forGetter(PediaEntry::titleKey),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(PediaEntry::locked),
            Codec.FLOAT.optionalFieldOf("learning", LEARNING_UNKNOWN).forGetter(PediaEntry::learning),
            Codec.INT.optionalFieldOf("mana_cost", COST_UNKNOWN).forGetter(PediaEntry::manaCost),
            Codec.FLOAT.optionalFieldOf("cooldown_seconds", (float) COST_UNKNOWN).forGetter(PediaEntry::cooldownSeconds),
            Codec.FLOAT.optionalFieldOf("difficulty", DIFFICULTY_UNKNOWN).forGetter(PediaEntry::difficulty),
            WireText.capped(MAX_ICON).optionalFieldOf("icon", "").forGetter(PediaEntry::iconItem),
            WireText.capped(MAX_ENTITY).optionalFieldOf("entity", "").forGetter(PediaEntry::entityId),
            WireText.capped(MAX_TAG).listOf().optionalFieldOf("tags", List.of()).forGetter(PediaEntry::tags),
            langMapCodec(MAX_ALIAS).optionalFieldOf("aliases", Map.of()).forGetter(PediaEntry::aliases),
            langMapCodec(MAX_LINE_KEY).optionalFieldOf("lines_key", Map.of()).forGetter(PediaEntry::desc),
            Codec.unboundedMap(WireText.capped(MAX_LANG),
                    LINE_CODEC.listOf().listOf())
                    .optionalFieldOf("chants", Map.of()).forGetter(PediaEntry::chants),
            PediaStage.CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(PediaEntry::stages)
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
        buf.writeFloat(entry.learning);
        buf.writeVarInt(entry.manaCost);
        buf.writeFloat(entry.cooldownSeconds);
        buf.writeFloat(entry.difficulty);
        buf.writeUtf(WireText.truncate(entry.iconItem, MAX_ICON), MAX_ICON);
        buf.writeUtf(WireText.truncate(entry.entityId, MAX_ENTITY), MAX_ENTITY);
        buf.writeVarInt(entry.tags.size());
        for (String tag : entry.tags) buf.writeUtf(WireText.truncate(tag, MAX_TAG), MAX_TAG);
        writeLangMap(buf, entry.aliases, MAX_ALIAS);
        writeLangMap(buf, entry.desc, MAX_LINE_KEY);
        buf.writeVarInt(entry.chants.size());
        for (Map.Entry<String, List<List<PediaLine>>> e : entry.chants.entrySet()) {
            buf.writeUtf(WireText.truncate(e.getKey(), MAX_LANG), MAX_LANG);
            buf.writeVarInt(e.getValue().size());
            for (List<PediaLine> lines : e.getValue()) {
                buf.writeVarInt(lines.size());
                for (PediaLine line : lines) {
                    buf.writeUtf(WireText.truncate(line.text(), MAX_LINE_KEY), MAX_LINE_KEY);
                    buf.writeVarInt(line.readings().size());
                    for (Map.Entry<String, String> r : line.readings().entrySet()) {
                        buf.writeUtf(WireText.truncate(r.getKey(), PediaLine.MAX_READING_KEY),
                                PediaLine.MAX_READING_KEY);
                        buf.writeUtf(WireText.truncate(r.getValue(), PediaLine.MAX_READING_VALUE),
                                PediaLine.MAX_READING_VALUE);
                    }
                }
            }
        }
        buf.writeVarInt(entry.stages.size());
        for (PediaStage stage : entry.stages) PediaStage.write(buf, stage);
    }

    public static PediaEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_ID);
        String category = buf.readUtf(MAX_CATEGORY);
        String titleKey = buf.readUtf(MAX_TITLE_KEY);
        boolean locked = buf.readBoolean();
        float learning = buf.readFloat();
        int manaCost = buf.readVarInt();
        float cooldownSeconds = buf.readFloat();
        float difficulty = buf.readFloat();
        String icon = buf.readUtf(MAX_ICON);
        String entity = buf.readUtf(MAX_ENTITY);
        int tagCount = buf.readVarInt();
        List<String> tags = new ArrayList<>(tagCount);
        for (int i = 0; i < tagCount; i++) tags.add(buf.readUtf(MAX_TAG));
        return new PediaEntry(id, category, titleKey, locked, learning, manaCost, cooldownSeconds,
                difficulty, icon, entity, List.copyOf(tags),
                readLangMap(buf, MAX_ALIAS), readLangMap(buf, MAX_LINE_KEY),
                readChantMap(buf), readStages(buf));
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

    private static Map<String, List<List<PediaLine>>> readChantMap(FriendlyByteBuf buf) {
        int langCount = buf.readVarInt();
        Map<String, List<List<PediaLine>>> out = new LinkedHashMap<>(langCount);
        for (int i = 0; i < langCount; i++) {
            String lang = buf.readUtf(MAX_LANG);
            int variantCount = buf.readVarInt();
            List<List<PediaLine>> variants = new ArrayList<>(variantCount);
            for (int v = 0; v < variantCount; v++) {
                int lineCount = buf.readVarInt();
                List<PediaLine> lines = new ArrayList<>(lineCount);
                for (int l = 0; l < lineCount; l++) {
                    String text = buf.readUtf(MAX_LINE_KEY);
                    int readingCount = buf.readVarInt();
                    Map<String, String> readings = new LinkedHashMap<>(readingCount);
                    for (int r = 0; r < readingCount; r++) {
                        readings.put(buf.readUtf(PediaLine.MAX_READING_KEY),
                                buf.readUtf(PediaLine.MAX_READING_VALUE));
                    }
                    lines.add(new PediaLine(text, readings));
                }
                variants.add(List.copyOf(lines));
            }
            out.put(lang, List.copyOf(variants));
        }
        return orderedChants(out);
    }

    private static List<PediaStage> readStages(FriendlyByteBuf buf) {
        int stageCount = buf.readVarInt();
        List<PediaStage> out = new ArrayList<>(stageCount);
        for (int i = 0; i < stageCount; i++) out.add(PediaStage.read(buf));
        return List.copyOf(out);
    }

    // ---- language accessors (UI + export) --------------------------------

    /** Language codes carrying any data, insertion-ordered, neutral excluded. */
    public Set<String> languages() {
        LinkedHashSet<String> out = new LinkedHashSet<>(aliases.keySet());
        out.addAll(desc.keySet());
        out.addAll(chants.keySet());
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

    /** Description lines for one language page: that bucket + the neutral bucket. */
    public List<String> descFor(String language) {
        return merged(desc, language);
    }

    /** Chant variants for one language: that language's variants, else the
     *  neutral variants, else empty (no cross-language merge — variants are
     *  per-language performances). */
    public List<List<PediaLine>> chantsFor(String language) {
        String lang = language == null ? LANG_NEUTRAL : language;
        List<List<PediaLine>> exact = chants.get(lang);
        if (exact != null) return exact;
        return chants.getOrDefault(LANG_NEUTRAL, List.of());
    }

    /** The language bucket actually backing {@link #chantsFor} for one page
     *  (the exact bucket when present, else the neutral bucket) — the
     *  annotation layer's D2 policy input (wo_b §1.1/§1.2). */
    public String chantBucketFor(String language) {
        String lang = language == null ? LANG_NEUTRAL : language;
        return chants.containsKey(lang) ? lang : LANG_NEUTRAL;
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
