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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * HOMM-style paginated book, code-drawn refined skin (docs/plans/
 * wizardpedia_ui_effects.md, option B): layered rendering over procedural
 * parchment/leather textures (generated once at runtime, no assets), ribbon
 * bookmarks, spine shading, and ≤200 ms page-turn slide. The layer split
 * (chrome / ribbons / content) is the swap point for the M5 ComfyUI skin —
 * option A replaces the texture layer only.
 *
 * <p>Three levels, zero scrollbars:
 * <ol>
 *   <li>intro page over the vertical category bookmark rail (incl. "All");</li>
 *   <li>entry grid — language sub-tabs (when entries carry language data),
 *       icon cells with corner ribbons, locked greyed out with a lock badge,
 *       search box (localized title / aliases / id), ◀ ▶ pagination;</li>
 *   <li>detail page — big icon, title, category, locked state, keywords,
 *       description lines (rendered for the selected language page).</li>
 * </ol>
 */
public class WizardpediaScreen extends Screen {

    // ---- book layout -----------------------------------------------------
    private static final int BOOK_W = 220;
    private static final int BOOK_H = 190;
    private static final int LEFT_W = 58;
    private static final int PAD = 8;
    private static final int COLS = 4;
    private static final int ROWS = 3;
    private static final int CELL = 32;
    private static final int CELL_GAP = 2;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int LANG_TAB_W = 34;
    private static final int LANG_TAB_H = 12;
    private static final long PAGE_ANIM_MS = 150;

    // palette (ink on parchment)
    private static final int COL_BORDER = 0xFF1C1208;
    private static final int COL_LEATHER = 0xFF3E2B1A;
    private static final int COL_PARCHMENT = 0xFFE8DCBA;
    private static final int COL_TAB = 0xFF6B5233;
    private static final int COL_TAB_HOVER = 0xFF8A6A3F;
    private static final int COL_TAB_ACTIVE = 0xFFC9A55C;
    private static final int COL_CELL = 0x388A7345;
    private static final int COL_TEXT = 0xFF33220E;
    private static final int COL_TEXT_DIM = 0xFF7A6647;
    private static final int COL_LOCKED = 0x48000000;
    private static final int COL_HOVER_EDGE = 0xFFC9A55C;
    private static final int COL_SPINE = 0x59000000;

    /** Procedural textures (runtime-generated; the M5 ComfyUI skin swaps these). */
    private static final ResourceLocation TEX_PARCHMENT = Wizardpedia.id("textures/gui/book_parchment.png");
    private static final ResourceLocation TEX_LEATHER = Wizardpedia.id("textures/gui/book_leather.png");
    private static boolean texturesRegistered;

    private enum Page { BOOKMARKS, GRID, DETAIL }

    private record Bookmark(PediaCategory category, Component label) {}

    private final List<Bookmark> bookmarks = new ArrayList<>();
    /** Language codes with data, alphabetical; empty = no language pages. */
    private final List<String> langTabs = new ArrayList<>();
    /** Selected language page; {@code null} = the neutral "All languages" page. */
    private String selectedLang;
    private Page page = Page.BOOKMARKS;
    private long pageSwitchAt;
    private int selectedBookmark;
    private int gridPage;
    private PediaEntry selected;

    private EditBox searchBox;
    private Button prevButton;
    private Button nextButton;
    private Button backButton;
    private String search = "";

    public WizardpediaScreen() {
        super(Component.translatable("wizardpedia.ui.title"));
        ensureTextures();
    }

    /** Generate the parchment/leather textures once per game run (client thread). */
    private static void ensureTextures() {
        if (texturesRegistered) return;
        texturesRegistered = true;
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(TEX_PARCHMENT, grainTexture(256, 0xFFE8DCBA, 0.10f, 5));
        textures.register(TEX_LEATHER, grainTexture(64, COL_LEATHER, 0.0f, 4));
    }

    /**
     * Solid base with a vertical darkening gradient and a faint deterministic
     * grain — reads as parchment/leather without shipping a texture file.
     */
    private static net.minecraft.client.renderer.texture.DynamicTexture grainTexture(
            int size, int argb, float bottomDarkening, int grain) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        java.util.Random random = new java.util.Random(0x571A7D); // fixed seed: stable grain
        int baseR = (argb >> 16) & 0xFF;
        int baseG = (argb >> 8) & 0xFF;
        int baseB = argb & 0xFF;
        for (int y = 0; y < size; y++) {
            float shade = 1.0f - bottomDarkening * (y / (float) (size - 1));
            for (int x = 0; x < size; x++) {
                int n = random.nextInt(grain * 2 + 1) - grain;
                int r = clamp255(Math.round(baseR * shade) + n);
                int gr = clamp255(Math.round(baseG * shade) + n);
                int b = clamp255(Math.round(baseB * shade) + n);
                // NativeImage stores pixels ABGR
                image.setPixelRGBA(x, y, (0xFF << 24) | (b << 16) | (gr << 8) | r);
            }
        }
        return new net.minecraft.client.renderer.texture.DynamicTexture(image);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // ---- geometry --------------------------------------------------------

    private int bookX() {
        return (this.width - BOOK_W) / 2;
    }

    private int bookY() {
        return (this.height - BOOK_H) / 2;
    }

    private int contentX() {
        return bookX() + LEFT_W + PAD;
    }

    private int contentW() {
        return BOOK_W - LEFT_W - PAD * 2;
    }

    /** Extra top inset of the content area while language tabs are shown. */
    private int topInset() {
        return langTabs.isEmpty() ? 0 : LANG_TAB_H + 4;
    }

    private int[] langTabRect(int index) {
        // index 0 = neutral "All languages" tab, then one per language code.
        int x = contentX() + index * (LANG_TAB_W + 2);
        int y = bookY() + PAD;
        return new int[] {x, y, x + LANG_TAB_W, y + LANG_TAB_H};
    }

    private int[] bookmarkRect(int index) {
        int x = bookX() - 14;
        int y = bookY() + 14 + index * 17;
        return new int[] {x, y, x + LEFT_W + 4, y + 15};
    }

    private int[] cellRect(int row, int col) {
        int x = contentX() + col * (CELL + CELL_GAP);
        int y = bookY() + PAD + topInset() + 22 + row * (CELL + CELL_GAP);
        return new int[] {x, y, x + CELL, y + CELL};
    }

    // ---- lifecycle -------------------------------------------------------

    @Override
    protected void init() {
        bookmarks.clear();
        bookmarks.add(new Bookmark(null, Component.translatable("wizardpedia.ui.all")));
        for (PediaCategory category : PediaState.categories()) {
            bookmarks.add(new Bookmark(category, Component.translatable(category.nameKey())));
        }
        selectedBookmark = Math.min(selectedBookmark, Math.max(0, bookmarks.size() - 1));

        // Language pages: every language any entry carries, alphabetical.
        java.util.Set<String> languages = new java.util.TreeSet<>();
        for (PediaEntry entry : PediaState.entries()) languages.addAll(entry.languages());
        langTabs.clear();
        langTabs.addAll(languages);
        if (selectedLang == null || !langTabs.contains(selectedLang)) {
            selectedLang = defaultLanguage(languages);
        }

        int gx = contentX();
        int gw = contentW();
        int by = bookY();
        int inset = topInset();

        searchBox = new EditBox(this.font, gx + 2, by + PAD + inset + 12, gw - 4, 14,
                Component.translatable("wizardpedia.ui.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            gridPage = 0;
        });
        addRenderableWidget(searchBox);

        int footerY = by + BOOK_H - PAD - 14;
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (gridPage > 0) gridPage--;
        }).bounds(gx + gw / 2 - 46, footerY, 20, 14).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if ((gridPage + 1) * PER_PAGE < visibleEntries().size()) gridPage++;
        }).bounds(gx + gw / 2 + 26, footerY, 20, 14).build());

        backButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> goTo(Page.GRID))
                .bounds(gx, by + PAD, 16, 12).build());
    }

    /**
     * Default language page: the player's game language when available,
     * else English, else the first page; {@code null} when no entry carries
     * language data (single implicit neutral page).
     */
    private static String defaultLanguage(java.util.Set<String> languages) {
        if (languages.isEmpty()) return null;
        String game = Minecraft.getInstance().getLanguageManager().getSelected();
        String prefix = game == null ? "" : game.split("_")[0];
        if (languages.contains(prefix)) return prefix;
        if (languages.contains("en")) return "en";
        return languages.iterator().next();
    }

    /** Page transition with the slide-in animation clock reset. */
    private void goTo(Page target) {
        if (page != target) {
            page = target;
            pageSwitchAt = Util.getMillis();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) searchBox.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page != Page.BOOKMARKS) {
            goTo(page == Page.DETAIL ? Page.GRID : Page.BOOKMARKS);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < bookmarks.size(); i++) {
            int[] r = bookmarkRect(i);
            if (mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) {
                selectedBookmark = i;
                gridPage = 0;
                goTo(Page.GRID);
                setFocused(null);
                return true;
            }
        }
        if (page == Page.GRID && !langTabs.isEmpty()) {
            for (int i = 0; i <= langTabs.size(); i++) { // index 0 = neutral tab
                int[] r = langTabRect(i);
                if (mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) {
                    selectedLang = i == 0 ? null : langTabs.get(i - 1);
                    gridPage = 0;
                    return true;
                }
            }
        }
        if (page == Page.GRID) {
            List<PediaEntry> entries = visibleEntries();
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int[] r = cellRect(row, col);
                    if (mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) {
                        int index = gridPage * PER_PAGE + row * COLS + col;
                        if (index < entries.size()) {
                            selected = entries.get(index);
                            goTo(Page.DETAIL);
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- data helpers ----------------------------------------------------

    /**
     * Entries for the selected bookmark + language page + search filter.
     * A language page shows entries carrying that language plus
     * language-neutral entries (they belong to every page); the neutral
     * "All languages" page shows everything.
     */
    private List<PediaEntry> visibleEntries() {
        Bookmark sel = bookmarks.get(selectedBookmark);
        List<PediaEntry> out = new ArrayList<>();
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        for (PediaEntry entry : PediaState.entries()) {
            if (sel != null && sel.category() != null && !entry.categoryId().equals(sel.category().id())) {
                continue;
            }
            if (selectedLang != null && entry.hasLanguages() && !entry.languages().contains(selectedLang)) {
                continue;
            }
            if (!query.isEmpty()) {
                boolean matches = entry.id().toLowerCase(Locale.ROOT).contains(query)
                        || Component.translatable(entry.titleKey()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || entry.aliases().values().stream()
                                .flatMap(List::stream)
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

    private Component categoryLabel(String categoryId) {
        for (Bookmark bookmark : bookmarks) {
            if (bookmark.category() != null && bookmark.category().id().equals(categoryId)) {
                return bookmark.label();
            }
        }
        return Component.literal(categoryId);
    }

    // ---- rendering -------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        int bx = bookX();
        int by = bookY();

        renderBookChrome(g, bx, by);
        renderBookmarkRail(g, bx, mouseX, mouseY);

        // Content layer slides in on page turns, clipped to the page area.
        float anim = Math.min(1f, (Util.getMillis() - pageSwitchAt) / (float) PAGE_ANIM_MS);
        int slide = (int) ((1f - anim) * 10);
        g.enableScissor(bx + LEFT_W, by, bx + BOOK_W, by + BOOK_H);
        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);
        switch (page) {
            case BOOKMARKS -> renderIntro(g);
            case GRID -> renderGrid(g, mouseX, mouseY);
            case DETAIL -> renderDetail(g);
        }
        g.pose().popPose();
        g.disableScissor();

        // widget visibility per page (widgets draw via super.render)
        boolean grid = page == Page.GRID;
        searchBox.setVisible(grid);
        prevButton.visible = grid && gridPage > 0;
        nextButton.visible = grid && (gridPage + 1) * PER_PAGE < visibleEntries().size();
        backButton.visible = page == Page.DETAIL;

        super.render(g, mouseX, mouseY, delta);
    }

    /** Chrome layer: leather cover, parchment pages, spine shading. */
    private void renderBookChrome(GuiGraphics g, int bx, int by) {
        // cover: dark edge + leather band around the pages
        g.fill(bx - 3, by - 3, bx + BOOK_W + 3, by + BOOK_H + 3, COL_BORDER);
        g.blit(TEX_LEATHER, bx - 2, by - 2, 0, 0, BOOK_W + 4, BOOK_H + 4, 64, 64);
        // parchment leaf
        g.blit(TEX_PARCHMENT, bx, by, 0, 0, BOOK_W, BOOK_H, 256, 256);
        // page edge highlights (light falls from top-left)
        g.fill(bx, by, bx + BOOK_W, by + 1, 0x2EFFFFFF);
        g.fill(bx, by + BOOK_H - 1, bx + BOOK_W, by + BOOK_H, 0x28000000);
        // spine between the two pages: dark seam + soft falloff
        int spine = bx + LEFT_W;
        g.fill(spine - 1, by, spine, by + BOOK_H, COL_SPINE);
        g.fill(spine, by, spine + 1, by + BOOK_H, COL_SPINE);
        g.fill(spine + 1, by, spine + 4, by + BOOK_H, 0x26000000);
        g.fill(spine - 4, by, spine - 1, by + BOOK_H, 0x18000000);
    }

    /** Ribbon-shaped bookmark: rectangle with a notch cut into its outer edge. */
    private void renderBookmarkRail(GuiGraphics g, int bx, int mouseX, int mouseY) {
        for (int i = 0; i < bookmarks.size(); i++) {
            int[] r = bookmarkRect(i);
            boolean active = i == selectedBookmark;
            // the active ribbon sticks out a little further
            int x0 = r[0] - (active ? 3 : 0);
            boolean hover = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
            int color = active ? COL_TAB_ACTIVE : hover ? COL_TAB_HOVER : COL_TAB;
            int y1 = r[3];
            fillPolygon(g, new int[][] {
                    {x0, r[1]}, {r[2], r[1]}, {r[2], y1},
                    {(x0 + r[2]) / 2, y1 - 4}, {x0, y1}}, color);
            // 1px darker outline along the notch edge for depth
            fillPolygon(g, new int[][] {
                    {x0, r[1]}, {x0 + 1, r[1]}, {x0 + 1, y1}, {x0, y1}}, 0x30000000);

            Bookmark bookmark = bookmarks.get(i);
            ItemStack icon = bookmark.category() == null ? ItemStack.EMPTY
                    : iconStack(bookmark.category().iconItem());
            int textX = x0 + 4;
            int textMax = LEFT_W - 10;
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x0 + 3, r[1] + 4, 0);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
                textX = x0 + 13;
                textMax = r[2] - textX - 2;
            }
            // dark ink on the gold active ribbon, parchment text on dark ones
            int labelColor = active ? COL_TEXT : 0xFFE8DCBA;
            String label = font.plainSubstrByWidth(bookmark.label().getString(), textMax);
            g.drawString(font, label, textX, r[1] + 4, labelColor, false);
        }
    }

    private void renderIntro(GuiGraphics g) {
        int x = contentX() + 6;
        int y = bookY() + PAD + 12;
        g.drawString(font, title, x, y, COL_TEXT, false);
        g.fill(x, y + 11, x + contentW() - 12, y + 12, 0x508A7345);
        y += 16;
        List<PediaEntry> entries = PediaState.entries();
        g.drawString(font, Component.translatable("wizardpedia.ui.entries", entries.size()),
                x, y, COL_TEXT_DIM, false);
        y += 14;
        for (String key : new String[] {"wizardpedia.ui.intro.1", "wizardpedia.ui.intro.2",
                "wizardpedia.ui.intro.3", "wizardpedia.ui.intro.4"}) {
            for (var line : font.split(Component.translatable(key), contentW() - 12)) {
                g.drawString(font, line, x, y, COL_TEXT, false);
                y += 11;
            }
            y += 3;
        }
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        renderLangTabs(g, mouseX, mouseY);
        List<PediaEntry> entries = visibleEntries();
        int first = gridPage * PER_PAGE;

        // page footer label
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        String pageLabel = (gridPage + 1) + " / " + pages;
        int fx = contentX() + contentW() / 2 - font.width(pageLabel) / 2;
        g.drawString(font, pageLabel, fx, bookY() + BOOK_H - PAD - 10, COL_TEXT, false);

        if (entries.isEmpty()) {
            Component empty = Component.translatable("wizardpedia.ui.empty");
            g.drawString(font, empty, contentX() + (contentW() - font.width(empty)) / 2,
                    bookY() + BOOK_H / 2, COL_TEXT_DIM, false);
            return;
        }

        Component hoverTitle = null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = first + row * COLS + col;
                if (index >= entries.size()) continue;
                PediaEntry entry = entries.get(index);
                int[] r = cellRect(row, col);
                boolean hover = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];

                // inset cell: tinted parchment with a 1px inner shadow
                g.fill(r[0], r[1], r[2], r[3], COL_CELL);
                g.fill(r[0], r[1], r[2], r[1] + 1, 0x30000000);
                g.fill(r[0], r[1], r[0] + 1, r[3], 0x22000000);
                g.fill(r[0], r[3] - 1, r[2], r[3], 0x20FFFFFF);
                if (hover) {
                    renderEdge(g, r, COL_HOVER_EDGE);
                    g.fill(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 0x14FFFFFF);
                }

                ItemStack icon = iconStack(entry.iconItem());
                if (!icon.isEmpty()) {
                    g.renderItem(icon, r[0] + (CELL - 16) / 2, r[1] + (CELL - 16) / 2);
                }
                // corner ribbon instead of the flat badge
                fillPolygon(g, new int[][] {
                        {r[2] - 9, r[1]}, {r[2], r[1]}, {r[2], r[1] + 9},
                        {r[2] - 4, r[1] + 4}, {r[2] - 9, r[1]}}, COL_TAB_ACTIVE);

                // locked: translucent veil + small padlock badge
                if (PediaState.isLocked(entry.id())) {
                    g.fill(r[0], r[1], r[2], r[3], COL_LOCKED);
                    int lx = r[0] + CELL / 2 - 3;
                    int ly = r[1] + CELL / 2 - 4;
                    g.fill(lx, ly + 3, lx + 7, ly + 8, 0xFFC9A55C);
                    g.fill(lx + 1, ly, lx + 6, ly + 1, 0xFFC9A55C);
                    g.fill(lx + 1, ly + 1, lx + 2, ly + 3, 0xFFC9A55C);
                    g.fill(lx + 5, ly + 1, lx + 6, ly + 3, 0xFFC9A55C);
                }
                if (hover) {
                    hoverTitle = Component.translatable(entry.titleKey());
                }
            }
        }
        if (hoverTitle != null) {
            g.renderTooltip(font, hoverTitle, mouseX, mouseY);
        }
    }

    /** Language sub-tab row (HOMM bookmark extension): neutral page + one per language. */
    private void renderLangTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i <= langTabs.size(); i++) { // index 0 = neutral tab
            int[] r = langTabRect(i);
            String lang = i == 0 ? null : langTabs.get(i - 1);
            boolean active = selectedLang == null ? lang == null
                    : selectedLang.equals(lang);
            boolean hover = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
            int color = active ? COL_TAB_ACTIVE : hover ? COL_TAB_HOVER : COL_TAB;
            int y1 = r[3];
            fillPolygon(g, new int[][] {
                    {r[0], r[1]}, {r[2], r[1]}, {r[2], y1},
                    {(r[0] + r[2]) / 2, y1 - 3}, {r[0], y1}}, color);
            String label = lang == null
                    ? Component.translatable("wizardpedia.ui.lang.all").getString()
                    : lang.toUpperCase(Locale.ROOT);
            int labelColor = active ? COL_TEXT : 0xFFE8DCBA;
            int tx = r[0] + (LANG_TAB_W - font.width(label)) / 2;
            g.drawString(font, label, tx, r[1] + 1, labelColor, false);
        }
    }

    /** 1px border around a rect. */
    private static void renderEdge(GuiGraphics g, int[] r, int color) {
        g.fill(r[0], r[1], r[2], r[1] + 1, color);
        g.fill(r[0], r[3] - 1, r[2], r[3], color);
        g.fill(r[0], r[1], r[0] + 1, r[3], color);
        g.fill(r[2] - 1, r[1], r[2], r[3], color);
    }

    private void renderDetail(GuiGraphics g) {
        if (selected == null) {
            goTo(Page.GRID);
            return;
        }
        PediaEntry entry = selected;
        int x = contentX() + 22;
        int y = bookY() + PAD + 10;
        int w = contentW() - 28;

        // large icon (2x) top-right
        ItemStack icon = iconStack(entry.iconItem());
        if (!icon.isEmpty()) {
            int ix = contentX() + contentW() - 36;
            g.pose().pushPose();
            g.pose().translate(ix, y + 2, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }

        g.drawString(font, Component.translatable(entry.titleKey()), x, y, COL_TEXT, false);
        g.fill(x, y + 10, x + w, y + 11, 0x508A7345);
        y += 15;
        g.drawString(font, Component.translatable("wizardpedia.ui.category",
                categoryLabel(entry.categoryId())), x, y, COL_TEXT_DIM, false);
        y += 11;
        boolean locked = PediaState.isLocked(entry.id());
        g.drawString(font, Component.translatable(locked ? "wizardpedia.ui.locked" : "wizardpedia.ui.unlocked"),
                x, y, locked ? 0xFF9B2C2C : 0xFF2C6E2C, false);
        y += 13;

        // Keywords + lines for the selected language page (bucket ∪ neutral;
        // the neutral page shows the neutral bucket only).
        List<String> aliases = entry.aliasesFor(selectedLang);
        if (!aliases.isEmpty()) {
            String keywords = String.join(", ", aliases);
            for (var line : font.split(Component.translatable("wizardpedia.ui.keywords", keywords), w)) {
                g.drawString(font, line, x, y, COL_TEXT, false);
                y += 11;
            }
            y += 3;
        }
        for (String key : entry.linesFor(selectedLang)) {
            for (var line : font.split(Component.translatable(key), w)) {
                g.drawString(font, line, x, y, COL_TEXT, false);
                y += 11;
                if (y > bookY() + BOOK_H - PAD - 6) return;
            }
            y += 3;
        }
    }

    /**
     * Fill an arbitrary convex/star-shaped polygon (triangle fan from the
     * first vertex) — ribbons, notched tabs, corner flags.
     */
    private static void fillPolygon(GuiGraphics g, int[][] points, int color) {
        Matrix4f matrix = g.pose().last().pose();
        float a = (color >> 24) & 0xFF;
        float r = (color >> 16) & 0xFF;
        float gr = (color >> 8) & 0xFF;
        float b = color & 0xFF;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        for (int[] p : points) {
            buffer.vertex(matrix, p[0], p[1], 0).color(r / 255f, gr / 255f, b / 255f, a / 255f).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
