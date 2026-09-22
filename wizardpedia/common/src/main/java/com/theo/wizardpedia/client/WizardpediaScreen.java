package com.theo.wizardpedia.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import com.theo.wizardpedia.catalog.PediaLine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * HOMM-style two-page magic book (docs/plans/wizardpedia_homm_layout.md):
 * left page = entry detail (icon/entity preview, stats, scrollable body with
 * trigger words / effect summary / chant variants / stage ladder), right
 * page = the spell compendium grid; left bookmark rail switches chapters
 * (categories), right rail filters by tag (school, color-coded); bottom
 * strip = language chip + search + about/tutorial shortcuts. Skins come
 * from {@code textures/gui/skins/<id>/book.png} selected via
 * {@code [client] uiSkin} — content areas are painted opaque so content
 * rendering is skin-independent.
 *
 * <p>Behavior: ESC and the inventory key both close the book (state kept in
 * {@link PediaUiState} across open/close), right-click goes back, the mouse
 * wheel scrolls the body / flips grid pages / slides bookmark rails. Chant
 * lines may carry a ruby reading row ({@link ChantRuby}) per the D2/D6
 * switches in {@link PediaClientConfig} — lines without a selected reading
 * render exactly as before.
 */
public class WizardpediaScreen extends Screen {

    // ---- book geometry (256x180, both pages always visible) --------------
    private static final int BOOK_W = 256;
    private static final int BOOK_H = 180;
    private static final int PAGE_W = 112;
    private static final int PAGE_TOP = 8;
    private static final int PAGE_BOTTOM = 148;
    private static final int STRIP_Y = 152;
    private static final int STRIP_H = 18;
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 4;
    private static final int CELL = 26;
    private static final long PAGE_ANIM_MS = 150;
    /** Ribbon bookmark rails, protruding outside the cover. Width adapts
     *  to the GUI scale so labels stay readable (tooltips always carry the
     *  full name). */
    private static final int RAIL_H = 16;
    private static final int RAIL_STEP = RAIL_H + 2;

    /** School/tag colors (HOMM-flavored, stable order). */
    private static final int[] TAG_COLORS = {
            0xFFE25822, 0xFFF7D046, 0xFF3B7DD8, 0xFF8B5A2B, 0xFFA0C8E8,
            0xFFF5E9C8, 0xFF6B2FA0, 0xFFC34FD9, 0xFF4CAF50, 0xFF7FDBCA,
            0xFF9B2C2C, 0xFF2C6E2C};

    private PediaSkin skin = PediaSkin.VANILLA;
    private long animStart;

    // transient hover state (for tooltips rendered last)
    private Component hoverTip;
    private boolean searchFocused;

    public WizardpediaScreen() {
        super(Component.translatable("wizardpedia.ui.title"));
    }

    // ---- geometry --------------------------------------------------------

    private int bx() {
        return (this.width - BOOK_W) / 2;
    }

    private int by() {
        return (this.height - BOOK_H) / 2;
    }

    private int leftX() {
        return bx() + 10;
    }

    private int rightX() {
        return bx() + BOOK_W - 10 - PAGE_W;
    }

    private int pageTop() {
        return by() + PAGE_TOP;
    }

    private int pageBottom() {
        return by() + PAGE_BOTTOM;
    }

    private int railW() {
        return Math.max(24, Math.min(56, (this.width - BOOK_W) / 2 - 2));
    }

    private int[] catRect(int index) {
        int y = by() + 6 + index * RAIL_STEP;
        return new int[] {bx() - railW() + 4, y, bx() + 4, y + RAIL_H};
    }

    private int[] tagRect(int index) {
        int y = by() + 6 + index * RAIL_STEP;
        return new int[] {bx() + BOOK_W - 4, y, bx() + BOOK_W + railW() - 4, y + RAIL_H};
    }

    private int[] cellRect(int row, int col) {
        int x = rightX() + 3 + col * (CELL + 2);
        int y = pageTop() + 3 + row * (CELL + 2);
        return new int[] {x, y, x + CELL, y + CELL};
    }

    private int[] stageRect(int x0) {
        return new int[] {x0, pageBottom() - 10, x0 + PAGE_W, pageBottom() + 2};
    }

    // ---- lifecycle -------------------------------------------------------

    @Override
    protected void init() {
        PediaClientConfig.load(this.minecraft.gameDirectory.toPath());
        skin = PediaClientConfig.skin();
        PediaUiState.gridPage = Math.max(0, PediaUiState.gridPage);
    }

    @Override
    public void tick() {
        hoverTip = null;
    }

    private void animate() {
        animStart = Util.getMillis();
    }

    /** ESC closes (vanilla); the inventory key closes too unless the search
     *  box is focused (the user might be typing). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.minecraft.options.keyInventory.matches(keyCode, scanCode) && !searchFocused) {
            onClose();
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !PediaUiState.search.isEmpty()) {
                PediaUiState.search = PediaUiState.search.substring(0, PediaUiState.search.length() - 1);
                PediaUiState.gridPage = 0;
                return true;
            }
            return true; // swallow other keys while typing
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && codePoint >= ' ' && PediaUiState.search.length() < 24) {
            PediaUiState.search += codePoint;
            PediaUiState.gridPage = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** Right-click goes back (detail history); returns false at root. */
    private boolean rightClickBack() {
        if (PediaUiState.goBack()) {
            animate();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (button == 1) {
            return rightClickBack();
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // category rail
        List<PediaCategory> categories = PediaState.categories();
        int railSlots = railCapacity();
        for (int i = 0; i < Math.min(categories.size() + 1 - railOffset, railSlots); i++) {
            int idx = i + railOffset; // 0 = "All", else categories[idx-1]
            int[] r = catRect(i);
            if (hit(mx, my, r)) {
                PediaUiState.categoryId = idx == 0 ? null : categories.get(idx - 1).id();
                PediaUiState.gridPage = 0;
                setFocused(null);
                return true;
            }
        }
        // tag rail
        List<String> tags = visibleTags();
        for (int i = 0; i < Math.min(tags.size() + 1 - tagOffset, railSlots); i++) {
            int idx = i + tagOffset; // 0 = all tags
            int[] r = tagRect(i);
            if (hit(mx, my, r)) {
                PediaUiState.selectedTag = idx == 0 ? null : tags.get(idx - 1);
                PediaUiState.gridPage = 0;
                return true;
            }
        }
        // bottom strip
        if (hit(mx, my, langChipRect())) {
            PediaUiState.cycleLanguage(availableLanguages());
            return true;
        }
        if (hit(mx, my, searchRect())) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (hit(mx, my, aboutRect())) {
            navigateIfExists("wizardpedia:about");
            return true;
        }
        if (hit(mx, my, tutorialRect())) {
            navigateIfExists("wizardpedia:datapack_entries");
            return true;
        }
        // stage cycle (left page bottom corners): base -> stage 1..N -> base
        PediaEntry selected = PediaUiState.selectedEntry();
        if (selected != null && !selected.stages().isEmpty()) {
            int count = selected.stages().size() + 1;
            int mid = leftX() + PAGE_W / 2;
            if (hit(mx, my, new int[] {leftX(), pageBottom() - 10, leftX() + 12, pageBottom() + 2})) {
                PediaUiState.stageIndex = (PediaUiState.stageIndex - 1 + count) % count;
                PediaUiState.scroll = 0;
                return true;
            }
            if (hit(mx, my, new int[] {leftX() + PAGE_W - 12, pageBottom() - 10, leftX() + PAGE_W, pageBottom() + 2})) {
                PediaUiState.stageIndex = (PediaUiState.stageIndex + 1) % count;
                PediaUiState.scroll = 0;
                return true;
            }
            // variant switcher
            int variants = selected.chantsFor(PediaUiState.selectedLang).size();
            if (variants > 1 && my >= pageTop() + 42 && my < pageTop() + 54) {
                if (mx >= leftX() && mx < leftX() + 12) {
                    PediaUiState.variantIndex = (PediaUiState.variantIndex + variants - 1) % variants;
                    return true;
                }
                if (mx >= leftX() + 40 && mx < leftX() + 52) {
                    PediaUiState.variantIndex = (PediaUiState.variantIndex + 1) % variants;
                    return true;
                }
            }
        }
        // grid pagination
        if (hit(mx, my, new int[] {rightX() + 20, pageBottom() - 10, rightX() + 32, pageBottom() + 2})) {
            if (PediaUiState.gridPage > 0) {
                PediaUiState.gridPage--;
                animate();
            }
            return true;
        }
        int pages = Math.max(1, (visibleEntries().size() + perPage() - 1) / perPage());
        if (hit(mx, my, new int[] {rightX() + PAGE_W - 32, pageBottom() - 10, rightX() + PAGE_W - 20, pageBottom() + 2})) {
            if ((PediaUiState.gridPage + 1) * perPage() < visibleEntries().size()) {
                PediaUiState.gridPage++;
                animate();
            }
            return true;
        }
        // grid cells
        List<PediaEntry> entries = visibleEntries();
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int[] r = cellRect(row, col);
                if (!hit(mx, my, r)) continue;
                int index = PediaUiState.gridPage * perPage() + row * GRID_COLS + col;
                if (index < entries.size()) {
                    PediaUiState.navigateTo(entries.get(index).id());
                    animate();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (mx >= leftX() && mx < rightX() + PAGE_W && my >= pageTop() && my < pageBottom() - 12
                && mx < bx() + 122 && PediaUiState.selectedEntry() != null) {
            // left page body scroll
            PediaUiState.scroll = Math.max(0, PediaUiState.scroll - (int) delta * 10);
            return true;
        }
        if (mx >= bx() + 122 && my >= pageTop() && my < pageBottom() - 12) {
            // right page: wheel flips grid pages
            int pages = Math.max(1, (visibleEntries().size() + perPage() - 1) / perPage());
            PediaUiState.gridPage = Math.max(0, Math.min(pages - 1, PediaUiState.gridPage + (int) delta));
            return true;
        }
        if (mx < bx() + 6) {
            railOffset = Math.max(0, railOffset - (int) delta);
            return true;
        }
        if (mx > bx() + BOOK_W - 6) {
            tagOffset = Math.max(0, tagOffset - (int) delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int railOffset;
    private int tagOffset;

    private int railCapacity() {
        return Math.max(1, (PAGE_BOTTOM - 8) / RAIL_STEP);
    }

    private static boolean hit(int mx, int my, int[] r) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    private int perPage() {
        return GRID_COLS * GRID_ROWS;
    }

    /** Navigate only when the target entry exists in the merged view. */
    private void navigateIfExists(String entryId) {
        for (PediaEntry entry : PediaState.entries()) {
            if (entry.id().equals(entryId)) {
                PediaUiState.navigateTo(entryId);
                animate();
                return;
            }
        }
    }

    private int[] langChipRect() {
        return new int[] {leftX(), by() + STRIP_Y + 2, leftX() + 26, by() + STRIP_Y + STRIP_H - 2};
    }

    private int[] searchRect() {
        return new int[] {leftX() + 30, by() + STRIP_Y + 2, leftX() + 150, by() + STRIP_Y + STRIP_H - 2};
    }

    private int[] aboutRect() {
        return new int[] {leftX() + 154, by() + STRIP_Y + 2, leftX() + 194, by() + STRIP_Y + STRIP_H - 2};
    }

    private int[] tutorialRect() {
        return new int[] {leftX() + 196, by() + STRIP_Y + 2, leftX() + 236, by() + STRIP_Y + STRIP_H - 2};
    }

    // ---- data helpers ----------------------------------------------------

    private List<PediaCategory> categories() {
        return PediaState.categories();
    }

    /** Tags present in the current category (before the tag filter). */
    private List<String> visibleTags() {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (PediaEntry entry : PediaState.entries()) {
            if (PediaUiState.categoryId != null && !entry.categoryId().equals(PediaUiState.categoryId)) continue;
            tags.addAll(entry.tags());
        }
        return List.copyOf(tags);
    }

    private List<String> availableLanguages() {
        LinkedHashSet<String> langs = new LinkedHashSet<>();
        for (PediaEntry entry : PediaState.entries()) langs.addAll(entry.languages());
        return List.copyOf(langs);
    }

    /** Entries for the current category + tag + search filters. */
    private List<PediaEntry> visibleEntries() {
        List<PediaEntry> out = new ArrayList<>();
        String query = PediaUiState.search == null ? "" : PediaUiState.search.trim().toLowerCase(Locale.ROOT);
        for (PediaEntry entry : PediaState.entries()) {
            if (PediaUiState.categoryId != null && !entry.categoryId().equals(PediaUiState.categoryId)) continue;
            if (PediaUiState.selectedTag != null && !entry.tags().contains(PediaUiState.selectedTag)) continue;
            if (!query.isEmpty()) {
                boolean matches = entry.id().toLowerCase(Locale.ROOT).contains(query)
                        || Component.translatable(entry.titleKey()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || entry.aliases().values().stream().flatMap(List::stream)
                                .anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(query));
                if (!matches) continue;
            }
            out.add(entry);
        }
        return out;
    }

    private static ItemStack iconStack(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        try {
            ResourceLocation rl = new ResourceLocation(id);
            if (!BuiltInRegistries.ITEM.containsKey(rl)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(rl));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static String tagLabel(String tag) {
        String key = "wizardpedia.tag." + tag;
        return net.minecraft.locale.Language.getInstance().has(key)
                ? Component.translatable(key).getString()
                : Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
    }

    private static int tagColor(int tagIndex) {
        return TAG_COLORS[Math.floorMod(tagIndex, TAG_COLORS.length)];
    }

    // ---- rendering -------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        hoverTip = null;

        renderChrome(g);
        renderCatRail(g, mouseX, mouseY);
        renderTagRail(g, mouseX, mouseY);
        renderRightPage(g, mouseX, mouseY);
        renderLeftPage(g, mouseX, mouseY);
        renderBottomStrip(g, mouseX, mouseY);

        if (hoverTip != null) {
            g.renderTooltip(this.font, hoverTip, mouseX, mouseY);
        }
    }

    /**
     * Skin texture + uniform cover frame + opaque content areas. The frame
     * band heavily tints the texture edge so noisy source-art margins can
     * never leak into the layout; ornament still reads through at ~35%.
     */
    private void renderChrome(GuiGraphics g) {
        int bx = bx();
        int by = by();
        g.blit(skin.texture, bx, by, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
        int ring = (0xA6 << 24) | (skin.cover & 0xFFFFFF); // ~65% cover tint
        g.fill(bx, by, bx + BOOK_W, by + BOOK_H, ring);
        // solid cover margins framing the content block
        g.fill(bx, by, bx + BOOK_W, by + 2, skin.cover);
        g.fill(bx, by + 170, bx + BOOK_W, by + BOOK_H, skin.cover);
        g.fill(bx, by + 2, leftX(), by + 170, skin.cover);
        g.fill(rightX() + PAGE_W, by + 2, bx + BOOK_W, by + 170, skin.cover);
        // opaque parchment over the content zones
        g.fill(leftX(), by + PAGE_TOP, leftX() + PAGE_W, by + PAGE_BOTTOM + 2, skin.page);
        g.fill(rightX(), by + PAGE_TOP, rightX() + PAGE_W, by + PAGE_BOTTOM + 2, skin.page);
        g.fill(leftX(), by + STRIP_Y, rightX() + PAGE_W, by + STRIP_Y + STRIP_H, skin.page);
        // spine between the pages (soft groove, not a hard black seam);
        // faint continuation through the strip so it reads as dirt-free
        int spine = bx + BOOK_W / 2;
        g.fill(spine - 5, by + PAGE_TOP, spine + 5, by + 170, 0x22000000);
        g.fill(spine - 1, by + PAGE_TOP, spine + 1, by + 170, 0x44000000);
        // content/strip separator
        g.fill(leftX(), by + STRIP_Y - 1, rightX() + PAGE_W, by + STRIP_Y, 0x508A7345);
        // cover inner edge highlight
        g.fill(leftX() - 1, by + 1, leftX(), by + 170, 0x40FFFFFF);
        g.fill(rightX() + PAGE_W, by + 1, rightX() + PAGE_W + 1, by + 170, 0x40000000);
    }

    private void renderCatRail(GuiGraphics g, double mx, double my) {
        List<PediaCategory> categories = categories();
        int slots = railCapacity();
        Component allLabel = Component.translatable("wizardpedia.ui.all");
        for (int i = 0; i < slots; i++) {
            int idx = i + railOffset;
            String label;
            Component tip;
            boolean active = idx == 0 ? PediaUiState.categoryId == null
                    : PediaUiState.categoryId != null && idx - 1 < categories.size()
                            && categories.get(idx - 1).id().equals(PediaUiState.categoryId);
            if (idx == 0) {
                label = plain(allLabel);
                tip = allLabel;
            } else if (idx - 1 < categories.size()) {
                PediaCategory cat = categories.get(idx - 1);
                label = plain(Component.translatable(cat.nameKey()));
                tip = Component.translatable(cat.nameKey());
            } else {
                return;
            }
            int[] r = catRect(i);
            boolean hover = hit((int) mx, (int) my, r);
            ribbon(g, r, true, active, hover, 0xFF6B5233, 0xFF8A6A3F, 0xFFC9A55C);
            drawClipped(g, label, r[0] + 4, r[1] + 4, railW() - 8,
                    active ? skin.activeText : 0xFFF2E8C8);
            if (hover) hoverTip = tip;
        }
    }

    private void renderTagRail(GuiGraphics g, double mx, double my) {
        List<String> tags = visibleTags();
        int slots = railCapacity();
        for (int i = 0; i < slots; i++) {
            int idx = i + tagOffset;
            String label;
            Component tip;
            boolean active;
            int color;
            int chipColor;
            if (idx == 0) {
                label = plain(Component.translatable("wizardpedia.ui.all"));
                tip = Component.translatable("wizardpedia.ui.all");
                active = PediaUiState.selectedTag == null;
                chipColor = 0xFF6B5233;
            } else if (idx - 1 < tags.size()) {
                String tag = tags.get(idx - 1);
                label = tagLabel(tag);
                tip = Component.translatable("wizardpedia.ui.tag_filter", tagLabel(tag));
                active = tag.equals(PediaUiState.selectedTag);
                chipColor = tagColor(idx - 1);
            } else {
                return;
            }
            int[] r = tagRect(i);
            boolean hover = hit((int) mx, (int) my, r);
            // active fill = skin accent (consistent contrast with activeText);
            // the school color stays on the chip
            color = active ? skin.accent : chipColor;
            ribbon(g, r, false, active, hover, 0xFF6B5233, 0xFF8A6A3F, color);
            // color chip with a dark outline so it reads on any fill
            g.fill(r[0] + 1, r[1] + 2, r[0] + 7, r[3] - 2, 0xFF33220E);
            g.fill(r[0] + 2, r[1] + 3, r[0] + 6, r[3] - 3, chipColor);
            drawClipped(g, label, r[0] + 8, r[1] + 4, railW() - 12,
                    active ? skin.activeText : 0xFFF2E8C8);
            if (hover) hoverTip = tip;
        }
    }

    /** Pointed ribbon tab; outer edge toward the book (left rail points
     *  left, right rail points right). */
    private void ribbon(GuiGraphics g, int[] r, boolean leftSide, boolean active, boolean hover,
                        int idle, int hovered, int activeColor) {
        int color = active ? activeColor : hover ? hovered : idle;
        int midY = (r[1] + r[3]) / 2;
        int tip = leftSide ? r[0] - (active ? 4 : 0) : r[2] + (active ? 4 : 0);
        if (leftSide) {
            fillPolygon(g, new int[][] {{r[2], r[1]}, {r[2], r[3]}, {tip, midY}}, color);
        } else {
            fillPolygon(g, new int[][] {{r[0], r[1]}, {r[0], r[3]}, {tip, midY}}, color);
        }
        int body0 = leftSide ? r[0] + 6 : r[0];
        int body1 = leftSide ? r[2] : r[2] - 6;
        g.fill(body0, r[1], body1, r[3], color);
        if (skin.bannerTabs) { // HOMM signature: hanging banner tail
            int drop = active ? 6 : 4;
            fillPolygon(g, new int[][] {{body0, r[3]}, {body1, r[3]},
                    {(body0 + body1) / 2, r[3] + drop}}, color);
        }
        if (active) { // 1px darker outer edge reinforces the state
            int e0 = leftSide ? body0 : body1 - 1;
            g.fill(e0, r[1], e0 + 1, r[3], 0x40000000);
        }
    }

    private void renderRightPage(GuiGraphics g, double mx, double my) {
        List<PediaEntry> entries = visibleEntries();
        int first = PediaUiState.gridPage * perPage();

        if (entries.isEmpty()) {
            Component empty = Component.translatable("wizardpedia.ui.empty");
            g.drawCenteredString(this.font, empty, rightX() + PAGE_W / 2, pageTop() + 50, skin.text);
        }
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = first + row * GRID_COLS + col;
                if (index >= entries.size()) return;
                PediaEntry entry = entries.get(index);
                int[] r = cellRect(row, col);
                boolean selected = entry.id().equals(PediaUiState.selectedEntryId);
                boolean hover = hit((int) mx, (int) my, r);
                g.fill(r[0], r[1], r[2], r[3], skin.cell);
                if (selected) { // 2px border + brightened fill: strongest state
                    g.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 0x24FFFFFF);
                    renderEdge(g, r, skin.accentText);
                    renderEdge(g, new int[] {r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1}, skin.accentText);
                } else if (hover) {
                    renderEdge(g, r, skin.accent);
                }

                ItemStack icon = iconStack(entry.iconItem());
                if (!icon.isEmpty()) {
                    g.renderItem(icon, r[0] + (CELL - 16) / 2, r[1] + 1);
                }
                String name = plain(Component.translatable(entry.titleKey()));
                drawClipped(g, name, r[0] + 2, r[1] + CELL - 9, CELL - 4, skin.text);

                if (PediaState.isLocked(entry.id())) {
                    g.fill(r[0], r[1], r[2], r[3], 0x48000000);
                    g.fill(r[2] - 5, r[1] + 2, r[2] - 2, r[1] + 5, 0xFFC9A55C);
                }
                if (hover) {
                    hoverTip = Component.translatable(entry.titleKey());
                    if (!entry.tags().isEmpty()) {
                        List<String> tagNames = new ArrayList<>();
                        for (String tag : entry.tags()) tagNames.add(tagLabel(tag));
                        hoverTip = hoverTip.copy().append("\n").append(
                                Component.translatable("wizardpedia.ui.tag_filter",
                                        String.join(", ", tagNames)));
                    }
                }
            }
        }
        // pagination
        int pages = Math.max(1, (entries.size() + perPage() - 1) / perPage());
        String label = (PediaUiState.gridPage + 1) + "/" + pages;
        int cx = rightX() + PAGE_W / 2;
        g.drawString(this.font, label, cx - this.font.width(label) / 2, pageBottom() - 8, skin.text, false);
        boolean hasPrev = PediaUiState.gridPage > 0;
        boolean hasNext = (PediaUiState.gridPage + 1) * perPage() < entries.size();
        g.drawString(this.font, "◀", rightX() + 22, pageBottom() - 8,
                hasPrev ? skin.accentText : 0x557A6647, false);
        g.drawString(this.font, "▶", rightX() + PAGE_W - 30, pageBottom() - 8,
                hasNext ? skin.accentText : 0x557A6647, false);
    }

    private void renderLeftPage(GuiGraphics g, double mx, double my) {
        int lx = leftX();
        int ty = pageTop();
        PediaEntry entry = PediaUiState.selectedEntry();

        if (entry == null) {
            int y = ty + 4;
            g.drawString(this.font, this.title, lx + 2, y, skin.text, false);
            y += 14;
            for (String key : new String[] {"wizardpedia.ui.intro.1", "wizardpedia.ui.intro.2",
                    "wizardpedia.ui.intro.3", "wizardpedia.ui.intro.4"}) {
                for (var line : this.font.split(Component.translatable(key), PAGE_W - 6)) {
                    g.drawString(this.font, line, lx + 2, y, skin.text, false);
                    y += 10;
                }
                y += 3;
            }
            return;
        }

        float anim = Math.min(1f, (Util.getMillis() - animStart) / (float) PAGE_ANIM_MS);
        int slide = (int) ((1f - anim) * 8);

        // header: icon (or entity preview) + title
        g.enableScissor(lx, ty - 2, lx + PAGE_W, pageBottom() + 2);
        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);

        String entityId = entry.entityId();
        boolean drew = false;
        if (!entityId.isEmpty()) drew = renderEntityPreview(g, entityId, lx + 11, ty + 11);
        if (!drew) {
            ItemStack icon = iconStack(entry.iconItem());
            if (!icon.isEmpty()) g.renderItem(icon, lx + 2, ty + 2);
        }
        String title = plain(Component.translatable(entry.titleKey()));
        drawClipped(g, title, lx + 20, ty + 2, PAGE_W - 22, skin.text);
        int titleW = Math.min(this.font.width(title), PAGE_W - 22);
        g.fill(lx + 20, ty + 11, lx + 20 + titleW, ty + 12, skin.accentText);
        // school tag color dots right after the title text
        int dotX = lx + 20 + Math.min(titleW + 4, PAGE_W - 26);
        for (String tag : entry.tags()) {
            int color = tagColor(plain(Component.literal(tag)).hashCode());
            g.fill(dotX, ty + 4, dotX + 3, ty + 7, color);
            dotX += 5;
        }

        // stats lines
        int y = ty + 20;
        List<String> stats = new ArrayList<>();
        if (entry.manaCost() >= 0) stats.add(plain(Component.translatable("wizardpedia.ui.mana", entry.manaCost())));
        if (entry.cooldownSeconds() >= 0) stats.add(plain(Component.translatable("wizardpedia.ui.cooldown",
                String.format(Locale.ROOT, "%.1f", entry.cooldownSeconds()))));
        if (entry.difficulty() >= 0) stats.add(plain(Component.translatable("wizardpedia.ui.difficulty",
                String.format(Locale.ROOT, "%.1f", entry.difficulty()))));
        g.drawString(this.font, String.join(" · ", stats), lx + 4, y, skin.text, false);
        y += 10;
        g.drawString(this.font, plain(Component.translatable("wizardpedia.ui.mastery",
                entry.learning() < 0 ? "—" : String.format(Locale.ROOT, "%.0f%%", entry.learning()))),
                lx + 4, y, skin.text, false);
        y += 12;

        // chant variant switcher
        int variants = entry.chantsFor(PediaUiState.selectedLang).size();
        if (variants > 0) {
            String vLabel = Component.translatable("wizardpedia.ui.variants",
                    Math.min(PediaUiState.variantIndex + 1, variants), variants).getString();
            g.drawString(this.font, plain(Component.translatable("wizardpedia.ui.chant")), lx + 4, y, skin.text, false);
            g.drawString(this.font, "◀", lx + 24, y, skin.accentText, false);
            g.drawString(this.font, vLabel, lx + 34, y, skin.text, false);
            g.drawString(this.font, "▶", lx + 42 + this.font.width(vLabel), y, skin.accentText, false);
            y += 12;
        }

        // scrollable body
        List<BodyLine> lines = buildBody(entry);
        int bodyTop = y;
        int bodyBottom = pageBottom() - (entry.stages().isEmpty() ? 4 : 14);
        int total = 0;
        for (BodyLine line : lines) total += ChantRuby.stride(line.reading());
        int maxScroll = Math.max(0, total - (bodyBottom - bodyTop));
        int scroll = Math.min(PediaUiState.scroll, maxScroll);
        if (maxScroll > 0) { // scroll thumb cue on the body's right edge
            int track = bodyBottom - bodyTop;
            int thumbH = Math.max(6, track * track / total);
            int thumbY = bodyTop + (track - thumbH) * scroll / maxScroll;
            g.fill(lx + PAGE_W - 2, bodyTop, lx + PAGE_W - 1, bodyBottom, 0x208A7345);
            g.fill(lx + PAGE_W - 2, thumbY, lx + PAGE_W - 1, thumbY + thumbH, 0x668A7345);
        }
        g.enableScissor(lx, bodyTop, lx + PAGE_W, bodyBottom);
        int ly = bodyTop - scroll;
        for (BodyLine line : lines) {
            int stride = ChantRuby.stride(line.reading());
            if (ly + stride >= bodyTop && ly <= bodyBottom) {
                ChantRuby.render(g, this.font, line.text(), line.reading(),
                        lx + 4, ly, line.color(), PAGE_W - 8);
            }
            ly += stride;
        }
        g.disableScissor();

        g.pose().popPose();
        g.disableScissor();

        // stage cycle row (bottom corners)
        if (!entry.stages().isEmpty()) {
            int count = entry.stages().size() + 1;
            String stageLabel = PediaUiState.stageIndex == 0
                    ? plain(Component.translatable("wizardpedia.ui.stage.base"))
                    : plain(Component.translatable("wizardpedia.ui.stage",
                            PediaUiState.stageIndex, entry.stages().size()));
            PediaEntry.PediaStage stage = PediaUiState.stageIndex == 0 ? null
                    : entry.stages().get(Math.min(PediaUiState.stageIndex - 1, entry.stages().size() - 1));
            boolean unlocked = stage == null || (entry.learning() < 0 || entry.learning() >= stage.mastery());
            int mid = lx + PAGE_W / 2;
            g.drawString(this.font, "◀", lx, pageBottom() - 8, skin.accentText, false);
            g.drawString(this.font, stageLabel, lx + 12, pageBottom() - 8, skin.text, false);
            if (stage != null) {
                int gateColor = unlocked ? 0xFF1F5A23 : 0xFF8A1F1F;
                g.drawString(this.font, unlocked ? "✓" : "✗",
                        lx + 12 + this.font.width(stageLabel) + 3, pageBottom() - 8, gateColor, false);
            }
            g.drawString(this.font, "▶", lx + PAGE_W - 10, pageBottom() - 8, skin.accentText, false);
            if (hit((int) mx, (int) my, new int[] {lx, pageBottom() - 10, lx + PAGE_W, pageBottom() + 2})) {
                hoverTip = stage == null ? Component.translatable("wizardpedia.ui.stage_tip")
                        : Component.translatable("wizardpedia.ui.stage_gate_tip",
                                stage.afterLines(), (int) stage.mastery());
            }
        }
    }

    /** Body lines: trigger words → effect summary → chant lines (release
     *  line accented; each chant line carries its policy-selected ruby
     *  reading or {@code null} — see {@link ChantRuby}) → current-stage
     *  effect summary. */
    private List<BodyLine> buildBody(PediaEntry entry) {
        List<BodyLine> lines = new ArrayList<>();
        String lang = PediaUiState.selectedLang;
        var aliases = entry.aliasesFor(lang);
        if (!aliases.isEmpty()) {
            lines.add(new BodyLine(Component.translatable("wizardpedia.ui.trigger").getVisualOrderText(), null, skin.accentText));
            lines.add(new BodyLine(net.minecraft.network.chat.Component.literal("  " + String.join(", ", aliases)).getVisualOrderText(), null, skin.text));
        }
        var desc = entry.descFor(lang);
        for (String key : desc) {
            for (var line : this.font.split(Component.translatable(key), PAGE_W - 8)) {
                lines.add(new BodyLine(line, null, skin.text));
            }
        }
        var chants = entry.chantsFor(lang);
        if (!chants.isEmpty()) {
            List<PediaLine> variant = chants.get(Math.min(PediaUiState.variantIndex, chants.size() - 1));
            // D2/D6 selection inputs: the bucket the variants were resolved
            // from + the active display language + the client config.
            String bucket = entry.chantBucketFor(lang);
            String mcLang = this.minecraft.getLanguageManager().getSelected();
            PediaClientConfig.LanguagePolicy policy = PediaClientConfig.chantLanguagePolicy();
            java.util.Set<String> readLangs = PediaClientConfig.chantReadLanguages();
            for (int i = 0; i < variant.size(); i++) {
                boolean release = i == variant.size() - 1 && variant.size() > 1;
                String reading = ReadingSelector.select(bucket, mcLang, policy, readLangs,
                        PediaClientConfig.methodPinyin(), PediaClientConfig.methodRomaji(),
                        PediaClientConfig.methodIpa(), variant.get(i).readings());
                lines.add(new BodyLine((release ? Component.literal("✦ ") : Component.literal("  "))
                        .append(Component.translatable(variant.get(i).text())).getVisualOrderText(),
                        reading,
                        release ? skin.accentText : skin.text));
            }
        }
        int stageIndex = PediaUiState.stageIndex;
        if (stageIndex > 0 && stageIndex <= entry.stages().size()) {
            PediaEntry.PediaStage stage = entry.stages().get(stageIndex - 1);
            lines.add(new BodyLine(Component.translatable("wizardpedia.ui.stage_effects", stageIndex).getVisualOrderText(), null, skin.accentText));
            boolean gateOpen = entry.learning() < 0 || entry.learning() >= stage.mastery();
            lines.add(new BodyLine(Component.literal("  ").append(Component.translatable(
                            "wizardpedia.ui.stage_gate_tip", stage.afterLines(), (int) stage.mastery()))
                    .append(gateOpen ? " ✓" : " ✗").getVisualOrderText(),
                    null,
                    gateOpen ? 0xFF1F5A23 : 0xFF8A1F1F));
            for (String key : stage.desc().getOrDefault(PediaEntry.LANG_NEUTRAL, List.of())) {
                for (var line : this.font.split(Component.translatable(key), PAGE_W - 8)) {
                    lines.add(new BodyLine(line, null, skin.text));
                }
            }
        }
        return lines;
    }

    private record BodyLine(net.minecraft.util.FormattedCharSequence text, String reading, int color) {}

    private void renderBottomStrip(GuiGraphics g, double mx, double my) {
        int by = by() + STRIP_Y;
        // language chip
        int[] chip = langChipRect();
        String langLabel = PediaUiState.selectedLang == null
                ? plain(Component.translatable("wizardpedia.ui.lang.all"))
                : PediaUiState.selectedLang.toUpperCase(Locale.ROOT);
        boolean hover = hit((int) mx, (int) my, chip);
        g.fill(chip[0], chip[1], chip[2], chip[3], hover ? 0x508A7345 : 0x308A7345);
        renderEdge(g, chip, 0x508A7345);
        g.drawCenteredString(this.font, langLabel, (chip[0] + chip[2]) / 2, chip[1] + 5, skin.text);
        if (hover) hoverTip = Component.translatable("wizardpedia.ui.lang_tip");

        // search box (self-drawn: no vanilla dark EditBox)
        int[] sr = searchRect();
        g.fill(sr[0], sr[1], sr[2], sr[3], 0x308A7345);
        renderEdge(g, sr, searchFocused ? skin.accent : 0x508A7345);
        String query = PediaUiState.search;
        String shown = query.isEmpty() && !searchFocused
                ? plain(Component.translatable("wizardpedia.ui.search"))
                : query + (searchFocused && (Util.getMillis() / 400) % 2 == 0 ? "_" : "");
        drawClipped(g, shown, sr[0] + 4, sr[1] + 5, sr[2] - sr[0] - 8,
                query.isEmpty() && !searchFocused ? 0xFF7A6647 : skin.text);

        // about / tutorial shortcuts
        drawTextButton(g, aboutRect(), plain(Component.translatable("wizardpedia.ui.about")), mx, my);
        drawTextButton(g, tutorialRect(), plain(Component.translatable("wizardpedia.ui.tutorial")), mx, my);
    }

    private void drawTextButton(GuiGraphics g, int[] r, String label, double mx, double my) {
        boolean hover = hit((int) mx, (int) my, r);
        g.fill(r[0], r[1], r[2], r[3], hover ? 0x3D8A7345 : 0x338A7345);
        renderEdge(g, r, 0x408A7345); // same chip idiom as the language chip
        g.drawCenteredString(this.font, label, (r[0] + r[2]) / 2, r[1] + 5,
                hover ? skin.accentText : skin.text);
    }

    private static void renderEdge(GuiGraphics g, int[] r, int color) {
        g.fill(r[0], r[1], r[2], r[1] + 1, color);
        g.fill(r[0], r[3] - 1, r[2], r[3], color);
        g.fill(r[0], r[1], r[0] + 1, r[3], color);
        g.fill(r[2] - 1, r[1], r[2], r[3], color);
    }

    private void drawClipped(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        g.drawString(this.font, this.font.plainSubstrByWidth(text, maxWidth), x, y, color, false);
    }

    private static String plain(Component component) {
        return component.getString();
    }

    /** Live entity preview for mob entries; {@code false} when the entity
     *  cannot be created/rendered (falls back to the item icon). */
    private boolean renderEntityPreview(GuiGraphics g, String entityId, int cx, int cy) {
        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(entityId));
            if (type.isEmpty()) return false;
            Entity entity = type.get().create(this.minecraft.level);
            if (entity == null) return false;
            float yaw = 30f;
            entity.setYRot(yaw);
            entity.setYHeadRot(yaw);
            entity.setXRot(0);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 100);
            g.pose().scale(10f, 10f, -10f);
            EntityRenderDispatcher dispatcher = this.minecraft.getEntityRenderDispatcher();
            dispatcher.setLevel(this.minecraft.level);
            MultiBufferSource.BufferSource buffers = this.minecraft.renderBuffers().bufferSource();
            dispatcher.render(entity, 0, 0, 0, 0f, 1f, g.pose(), buffers, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            g.pose().popPose();
            return true;
        } catch (Exception e) {
            Wizardpedia.LOGGER.warn("Entity preview failed for {}", entityId, e);
            return false;
        }
    }

    /** Fill a triangle (ribbon tips). */
    private static void fillPolygon(GuiGraphics g, int[][] points, int color) {
        Matrix4f matrix = g.pose().last().pose();
        float a = (color >> 24) & 0xFF;
        float r = (color >> 16) & 0xFF;
        float gr = (color >> 8) & 0xFF;
        float b = color & 0xFF;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] p : points) {
            buffer.vertex(matrix, p[0], p[1], 0).color(r / 255f, gr / 255f, b / 255f, a / 255f).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
