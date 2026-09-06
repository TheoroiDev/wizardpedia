package com.theo.wizardpedia.client;

import com.theo.wizardpedia.Wizardpedia;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Book skin registry: the shipped GUI skins (docs/plans/
 * wizardpedia_homm_layout.md) and their code-side palettes. The texture
 * supplies the cover/frame/spine feel; content areas are painted over with
 * opaque parchment in code (per-skin {@link #page}), so content rendering
 * stays skin-independent. Selected via {@code [client] uiSkin} in
 * {@code config/wizardpedia/wizardpedia.toml}.
 */
public enum PediaSkin {
    /** Vanilla MC book GUI tone — light parchment, plain leather (default). */
    VANILLA(0xFFA78156, 0xFF33220E, 0x598A7345, 0xFFC9A55C),
    /** HOMM tribute — golden frame, deep leather. */
    HOMM(0xFFB5956C, 0xFF2E1F0E, 0x598A7345, 0xFFD9B45C),
    /** Dark grimoire — purple gem, cool ink. */
    TOME(0xFFAF7E6D, 0xFF2A1420, 0x59832E45, 0xFFB58AD9),
    /** Minimal flat — brightest page, highest contrast. */
    FLAT(0xFFCFB28D, 0xFF3A2A12, 0x597A7345, 0xFFF2D24A),
    /** Illuminated manuscript — warm cream, red-gold frame. */
    MANUSCRIPT(0xFFB19063, 0xFF33220E, 0x598A7345, 0xFFC9A55C);

    public final ResourceLocation texture;
    /** Opaque parchment tone painted over content areas (sampled per skin). */
    public final int page;
    /** Ink color for primary text. */
    public final int text;
    /** Translucent tint for inset cells / separators. */
    public final int cell;
    /** Highlight/selection accent. */
    public final int accent;

    PediaSkin(int page, int text, int cell, int accent) {
        this.texture = Wizardpedia.id("textures/gui/skins/" + name().toLowerCase(Locale.ROOT) + "/book.png");
        this.page = page;
        this.text = text;
        this.cell = cell;
        this.accent = accent;
    }

    /** Resolve a skin id from config; unknown/empty falls back to VANILLA. */
    public static PediaSkin byId(String id) {
        if (id != null && !id.isBlank()) {
            for (PediaSkin skin : values()) {
                if (skin.name().toLowerCase(Locale.ROOT).equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return skin;
                }
            }
        }
        return VANILLA;
    }
}
