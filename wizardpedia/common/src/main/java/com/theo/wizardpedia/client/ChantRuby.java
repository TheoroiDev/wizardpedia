package com.theo.wizardpedia.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/**
 * Ruby reading primitive for the book page (annotation layer R-B, plan §9):
 * renders one chant line's text plus an OPTIONAL half-size reading row
 * ABOVE it (ruby convention, user ruling 2026-09-22) — the reusable
 * "text + readings + switches" unit for every body-line consumer.
 * Skin-independent by construction: the reading tone is the line's own ink
 * color at reduced alpha, which reads as gray on every skin's parchment
 * (the most natural fit inside the existing palette system — no new
 * per-skin field; the five page tones are all light, so translucent ink
 * lands in the same gray family on each).
 *
 * <p>Layout contract (plain-path zero regression): a line without a reading
 * consumes exactly the historical 10px stride; a line with a reading consumes
 * {@link #RUBY_STRIDE} (5px reading row on top + 10px text). The reading is
 * clipped to the caller's width (drawn at half scale, so it may be twice as
 * long as the text budget before clipping).
 */
public final class ChantRuby {
    private ChantRuby() {}

    /** Historical body-line stride (unchanged for plain lines). */
    public static final int STRIDE = 10;
    /** Stride of a line with a reading row: half-size row ~5px + text 10px. */
    public static final int RUBY_STRIDE = 15;
    /** Text offset inside an annotated stride (the reading row sits on top). */
    private static final int TEXT_LEAD = 5;
    /** Reading ink alpha (~55%): gray-on-parchment on all five skins. */
    private static final int READING_ALPHA = 0x8C;

    /** Height consumed by one body line ({@link #STRIDE} when plain). */
    public static int stride(String reading) {
        return reading == null || reading.isEmpty() ? STRIDE : RUBY_STRIDE;
    }

    /**
     * Draw one line + optional ruby row (reading above the text).
     *
     * @param reading  the selected reading ({@code null}/empty = plain line)
     * @param maxWidth width budget for BOTH rows (the reading is drawn at
     *                 half scale, so its glyph budget is {@code maxWidth * 2})
     * @return the height consumed (use it to advance the line cursor)
     */
    public static int render(GuiGraphics g, Font font, FormattedCharSequence text, String reading,
                             int x, int y, int color, int maxWidth) {
        if (reading == null || reading.isEmpty()) {
            g.drawString(font, text, x, y, color, false);
            return STRIDE;
        }
        String clipped = font.plainSubstrByWidth(reading, Math.max(1, maxWidth * 2));
        int tone = (READING_ALPHA << 24) | (color & 0xFFFFFF);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.drawString(font, clipped, 0, 0, tone, false);
        g.pose().popPose();
        g.drawString(font, text, x, y + TEXT_LEAD, color, false);
        return RUBY_STRIDE;
    }
}
