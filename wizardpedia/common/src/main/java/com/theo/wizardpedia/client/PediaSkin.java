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
 * {@code config/wizardpedia/client.json}.
 *
 * <p>Two accent tones per skin: {@link #accent} (border/selection/hover —
 * can stay saturated) and {@link #accentText} (darkened for small text on
 * parchment — WCAG-minded, ≥4:1 against {@link #page}). {@link
 * #activeText} is the label color on an active (accent-filled) ribbon.
 */
public enum PediaSkin {
    /** Vanilla MC book GUI tone — light parchment, plain leather (default). */
    VANILLA(0xFFE8DAB2, 0xFF43301C, 0xFF33220E, 0x338A7345, 0xFF8A6A1F, 0xFF6E520F, 0xFF33220E, false),
    /** HOMM tribute — golden frame, deep leather. */
    HOMM(0xFFE3D1A4, 0xFF33220C, 0xFF2E1F0E, 0x338A7345, 0xFF9A7415, 0xFF6E520F, 0xFF33220E, true),
    /** Dark grimoire — purple gem, cool ink. */
    TOME(0xFFD9C9B6, 0xFF2C1E38, 0xFF2A1420, 0x33832E45, 0xFF7B4FA8, 0xFF5E3A8C, 0xFFE8DCBA, false),
    /** Minimal flat — brightest page, highest contrast. */
    FLAT(0xFFEFE3C6, 0xFF51412E, 0xFF3A2A12, 0x337A7345, 0xFFA67C00, 0xFF7A5A00, 0xFF33220E, false),
    /** Illuminated manuscript — warm cream, red-gold frame. */
    MANUSCRIPT(0xFFF0DDB0, 0xFF5E2A20, 0xFF33220E, 0x338A7345, 0xFF9A7415, 0xFF7A5210, 0xFF33220E, false);

    public final ResourceLocation texture;
    /** Opaque parchment tone painted over content areas (sampled per skin). */
    public final int page;
    /** Leather/cover tone for the book frame band (tints the texture edge). */
    public final int cover;
    /** Ink color for primary text. */
    public final int text;
    /** Translucent tint for inset cells / separators. */
    public final int cell;
    /** Highlight/selection accent (borders, arrows, interactive states). */
    public final int accent;
    /** Darkened accent for small text on parchment (section headers etc.). */
    public final int accentText;
    /** Label color on an active (accent-filled) ribbon. */
    public final int activeText;
    /** Structural signature: ribbon tabs drawn as hanging banners (HOMM). */
    public final boolean bannerTabs;

    PediaSkin(int page, int cover, int text, int cell, int accent, int accentText, int activeText,
              boolean bannerTabs) {
        this.texture = Wizardpedia.id("textures/gui/skins/" + name().toLowerCase(Locale.ROOT) + "/book.png");
        this.page = page;
        this.cover = cover;
        this.text = text;
        this.cell = cell;
        this.accent = accent;
        this.accentText = accentText;
        this.activeText = activeText;
        this.bannerTabs = bannerTabs;
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
