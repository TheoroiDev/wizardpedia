package com.theo.wizardpedia.client;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.catalog.PediaCategory;
import com.theo.wizardpedia.catalog.PediaEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * HOMM-style paginated book (placeholder flat-color skin; the ComfyUI
 * parchment/engraving skin swaps in at M5). Three levels, zero scrollbars:
 * <ol>
 *   <li>intro page over the vertical category bookmark rail (incl. "All");</li>
 *   <li>entry grid — icon cells with corner badges, locked greyed out,
 *       search box (localized title / aliases / id), ◀ ▶ pagination;</li>
 *   <li>detail page — big icon, title, category, locked state, keywords,
 *       description lines.</li>
 * </ol>
 */
public class WizardpediaScreen extends Screen {

    // ---- book layout (placeholder skin) ---------------------------------
    private static final int BOOK_W = 220;
    private static final int BOOK_H = 190;
    private static final int LEFT_W = 58;
    private static final int PAD = 8;
    private static final int COLS = 4;
    private static final int ROWS = 3;
    private static final int CELL = 32;
    private static final int CELL_GAP = 2;
    private static final int PER_PAGE = COLS * ROWS;

    private static final int COL_BORDER = 0xFF2B1D10;
    private static final int COL_LEATHER = 0xFF4A3524;
    private static final int COL_PARCHMENT = 0xFFE8D9A8;
    private static final int COL_TAB = 0xFF6B5233;
    private static final int COL_TAB_HOVER = 0xFF8A6A3F;
    private static final int COL_TAB_ACTIVE = 0xFFC9A55C;
    private static final int COL_CELL = 0xFFD9C793;
    private static final int COL_CELL_BORDER = 0xFF8A7345;
    private static final int COL_TEXT = 0xFF3A2A12;
    private static final int COL_TEXT_DIM = 0xFF7A6647;
    private static final int COL_BADGE = 0xFFFFD24A;
    private static final int COL_LOCKED = 0x90000000;

    private enum Page { BOOKMARKS, GRID, DETAIL }

    private record Bookmark(PediaCategory category, Component label) {}

    private final List<Bookmark> bookmarks = new ArrayList<>();
    private Page page = Page.BOOKMARKS;
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

    private int[] bookmarkRect(int index) {
        int x = bookX() - 14;
        int y = bookY() + 14 + index * 17;
        return new int[] {x, y, x + LEFT_W + 4, y + 15};
    }

    private int[] cellRect(int row, int col) {
        int x = contentX() + col * (CELL + CELL_GAP);
        int y = bookY() + PAD + 22 + row * (CELL + CELL_GAP);
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

        int gx = contentX();
        int gw = contentW();
        int by = bookY();

        searchBox = new EditBox(this.font, gx + 2, by + PAD + 12, gw - 4, 14,
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

        backButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Page.GRID)
                .bounds(gx, by + PAD, 16, 12).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) searchBox.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page != Page.BOOKMARKS) {
            page = page == Page.DETAIL ? Page.GRID : Page.BOOKMARKS;
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
                page = Page.GRID;
                setFocused(null);
                return true;
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
                            page = Page.DETAIL;
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- data helpers ----------------------------------------------------

    /** Entries for the selected bookmark + search filter (localized title / aliases / id). */
    private List<PediaEntry> visibleEntries() {
        Bookmark sel = bookmarks.get(selectedBookmark);
        List<PediaEntry> out = new ArrayList<>();
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        for (PediaEntry entry : PediaState.entries()) {
            if (sel != null && sel.category() != null && !entry.categoryId().equals(sel.category().id())) {
                continue;
            }
            if (!query.isEmpty()) {
                boolean matches = entry.id().toLowerCase(Locale.ROOT).contains(query)
                        || Component.translatable(entry.titleKey()).getString().toLowerCase(Locale.ROOT).contains(query)
                        || entry.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(query));
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

        // book base + parchment panel
        g.fill(bx - 2, by - 2, bx + BOOK_W + 2, by + BOOK_H + 2, COL_BORDER);
        g.fill(bx, by, bx + BOOK_W, by + BOOK_H, COL_LEATHER);
        g.fill(bx + LEFT_W, by + PAD, bx + BOOK_W - PAD, by + BOOK_H - PAD, COL_PARCHMENT);

        // bookmark rail (always active)
        for (int i = 0; i < bookmarks.size(); i++) {
            int[] r = bookmarkRect(i);
            boolean hover = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
            int color = i == selectedBookmark ? COL_TAB_ACTIVE : hover ? COL_TAB_HOVER : COL_TAB;
            g.fill(r[0], r[1], r[2], r[3], color);
            Bookmark bookmark = bookmarks.get(i);
            String label = font.plainSubstrByWidth(bookmark.label().getString(), LEFT_W - 10);
            g.drawString(font, label, r[0] + 4, r[1] + 4, COL_TEXT, false);
        }

        switch (page) {
            case BOOKMARKS -> renderIntro(g);
            case GRID -> renderGrid(g, mouseX, mouseY);
            case DETAIL -> renderDetail(g);
        }

        // widget visibility per page (widgets draw via super.render)
        boolean grid = page == Page.GRID;
        searchBox.setVisible(grid);
        prevButton.visible = grid && gridPage > 0;
        nextButton.visible = grid && (gridPage + 1) * PER_PAGE < visibleEntries().size();
        backButton.visible = page == Page.DETAIL;

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderIntro(GuiGraphics g) {
        int x = contentX() + 6;
        int y = bookY() + PAD + 12;
        g.drawString(font, title, x, y, COL_TEXT, false);
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
                g.fill(r[0], r[1], r[2], r[3], COL_CELL);
                g.fill(r[0], r[1], r[2], r[1] + 1, COL_CELL_BORDER);
                g.fill(r[0], r[3] - 1, r[2], r[3], COL_CELL_BORDER);
                g.fill(r[0], r[1], r[0] + 1, r[3], COL_CELL_BORDER);
                g.fill(r[2] - 1, r[1], r[2], r[3], COL_CELL_BORDER);

                ItemStack icon = iconStack(entry.iconItem());
                if (!icon.isEmpty()) {
                    g.renderItem(icon, r[0] + (CELL - 16) / 2, r[1] + (CELL - 16) / 2);
                }
                // corner badge
                g.fill(r[2] - 6, r[1] + 2, r[2] - 2, r[1] + 6, COL_BADGE);
                // locked grey-out
                if (PediaState.isLocked(entry.id())) {
                    g.fill(r[0], r[1], r[2], r[3], COL_LOCKED);
                }
                boolean hover = mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
                if (hover) {
                    hoverTitle = Component.translatable(entry.titleKey());
                }
            }
        }
        if (hoverTitle != null) {
            g.renderTooltip(font, hoverTitle, mouseX, mouseY);
        }
    }

    private void renderDetail(GuiGraphics g) {
        if (selected == null) {
            page = Page.GRID;
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

        g.drawString(font, Component.translatable(entry.titleKey()), x, y, COL_TEXT, true);
        y += 13;
        g.drawString(font, Component.translatable("wizardpedia.ui.category",
                categoryLabel(entry.categoryId())), x, y, COL_TEXT_DIM, false);
        y += 11;
        boolean locked = PediaState.isLocked(entry.id());
        g.drawString(font, Component.translatable(locked ? "wizardpedia.ui.locked" : "wizardpedia.ui.unlocked"),
                x, y, locked ? 0xFF9B2C2C : 0xFF2C6E2C, false);
        y += 13;

        if (!entry.aliases().isEmpty()) {
            String keywords = String.join(", ", entry.aliases());
            for (var line : font.split(Component.translatable("wizardpedia.ui.keywords", keywords), w)) {
                g.drawString(font, line, x, y, COL_TEXT, false);
                y += 11;
            }
            y += 3;
        }
        for (String key : entry.lines()) {
            for (var line : font.split(Component.translatable(key), w)) {
                g.drawString(font, line, x, y, COL_TEXT, false);
                y += 11;
                if (y > bookY() + BOOK_H - PAD - 6) return;
            }
            y += 3;
        }
    }
}
