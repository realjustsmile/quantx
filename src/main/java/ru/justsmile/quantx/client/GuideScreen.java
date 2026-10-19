package ru.justsmile.quantx.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.justsmile.quantx.QuantX;

/**
 * Экран справочника QuantX: тёмная минималистичная «бумажная» панель,
 * рецепты нарисованы как на верстаке — сетка 3x3 с иконками
 * ингредиентов, стрелка и слот результата. Наведение на ячейку
 * показывает ванильный тултип предмета.
 *
 * Страницы: обложка + по 4 рецепта (сетка 2x2) + советы.
 * Листание — кнопками ‹ › или стрелками клавиатуры, Esc закрывает.
 */
public final class GuideScreen extends Screen {

    // ===== Палитра: тёмный текст на бумаге, без теней =====
    private static final int DIM        = 0xC0141210; // затемнение мира
    private static final int PAPER      = 0xFFF2E6C8; // фон панели
    private static final int PAPER_EDGE = 0xFF3B2F22; // рамка панели
    private static final int TITLE      = 0xFF2E2416; // заголовки
    private static final int TEXT       = 0xFF4A3A26; // обычный текст
    private static final int NOTE       = 0xFF857050; // примечания
    private static final int ACCENT     = 0xFF2E8F86; // бирюзовый акцент QuantX
    private static final int SLOT_EDGE  = 0xFF6B5638;
    private static final int SLOT_BG    = 0xFFCDB691;
    private static final int ARROW      = 0xFF5A4530;

    // GLFW-коды стрелок (org.lwjgl.glfw недоступен в compile classpath).
    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 196;
    private static final int CELL = 18;
    private static final int CARD_COL_W = 136; // шаг колонки карточек
    private static final int CARDS_PER_PAGE = 4;

    private int page;
    private List<RecipeCard> cards;

    /** Одна карточка рецепта: заголовок + до 9 ингредиентов + результат. */
    private record RecipeCard(Component title, ItemStack[] grid,
                              ItemStack result, boolean shapeless) {
    }

    private static RecipeCard card(String nameKey, ItemStack[] grid,
                                   ItemStack result, boolean shapeless) {
        return new RecipeCard(Component.translatable("guide.quantx.name." + nameKey),
                grid, result, shapeless);
    }

    private GuideScreen() {
        super(Component.translatable("item.quantx.guide_book"));
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new GuideScreen());
    }

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top()  { return (this.height - PANEL_H) / 2; }

    private int pageCount() {
        // 0 — обложка, последняя — рецепты + советы.
        return 1 + (cards.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE;
    }

    @Override
    protected void init() {
        if (cards == null) {
            cards = buildCards();
        }
        int cy = top() + PANEL_H - 18;
        int mid = this.width / 2;
        addRenderableWidget(Button.builder(Component.literal("‹"),
                b -> page = Math.max(0, page - 1))
                .pos(mid - 34, cy).size(14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("›"),
                b -> page = Math.min(pageCount() - 1, page + 1))
                .pos(mid + 20, cy).size(14, 14).build());
    }

    // ===== Данные рецептов =====

    private static ItemStack s(net.minecraft.world.level.ItemLike item) {
        return new ItemStack(item);
    }

    private static ItemStack s(net.minecraft.world.level.ItemLike item, int count) {
        return new ItemStack(item, count);
    }

    /** Сетка 3x3 из паттерна: строки через '/', '.' — пусто. */
    private static ItemStack[] grid(Map<Character, ItemStack> key, String... rows) {
        ItemStack[] g = new ItemStack[9];
        java.util.Arrays.fill(g, ItemStack.EMPTY);
        for (int r = 0; r < rows.length && r < 3; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length() && c < 3; c++) {
                char ch = row.charAt(c);
                if (ch != '.' && key.containsKey(ch)) {
                    g[r * 3 + c] = key.get(ch);
                }
            }
        }
        return g;
    }

    private static ItemStack[] loose(ItemStack... stacks) {
        ItemStack[] g = new ItemStack[9];
        java.util.Arrays.fill(g, ItemStack.EMPTY);
        System.arraycopy(stacks, 0, g, 0, Math.min(stacks.length, 9));
        return g;
    }

    private static List<RecipeCard> buildCards() {
        ItemStack G = s(Items.GRANITE);          ItemStack D = s(QuantX.GRANITE_DUST.get());
        ItemStack P = s(QuantX.GRANITE_PLATE.get()); ItemStack F = s(QuantX.GRANITE_FRAME.get());
        ItemStack F2 = s(QuantX.GRANITE_FRAME_2.get()); ItemStack F3 = s(QuantX.GRANITE_FRAME_3.get());
        ItemStack I = s(Items.IRON_INGOT);       ItemStack R = s(Items.REDSTONE);
        ItemStack GL = s(Items.GLASS);           ItemStack FB = s(QuantX.QUANTUM_FIBER_ITEM.get());
        ItemStack DM = s(Items.DIAMOND_BLOCK);   ItemStack L = s(Items.LEATHER);
        ItemStack E = s(Items.ENDER_PEARL);      ItemStack AU = s(Items.GOLD_INGOT);
        ItemStack DI = s(Items.DIAMOND);

        List<RecipeCard> c = new ArrayList<>();
        // Материалы
        c.add(card("granite_dust", loose(G), s(QuantX.GRANITE_DUST.get(), 2), true));
        c.add(card("granite_plate", grid(Map.of('D', D), "DD", "DD"), s(P.getItem()), false));
        c.add(card("granite_frame", grid(Map.of('P', P, 'I', I), "PPP", "PIP", "PPP"),
                s(F.getItem()), false));
        c.add(card("granite_frame_2", grid(Map.of('G', AU, 'F', F), "GGG", "GFG", "GGG"),
                s(F2.getItem()), false));
        c.add(card("granite_frame_3", grid(Map.of('D', DI, 'F', F2), "DDD", "DFD", "DDD"),
                s(F3.getItem()), false));
        c.add(card("punch_card", loose(s(Items.PAPER), s(Items.COPPER_INGOT), R, D),
                s(QuantX.PUNCH_CARD.get()), true));
        // Сеть
        c.add(card("quantum_fiber", grid(Map.of('D', D, 'R', R), "DRD"),
                s(FB.getItem(), 4), false));
        c.add(card("matter_storage", grid(Map.of('P', P, 'G', GL, 'F', F), "PGP", "GFG", "PGP"),
                s(QuantX.MATTER_STORAGE_ITEM.get()), false));
        c.add(card("matter_storage_2", grid(Map.of('P', P, 'F', F2, 'S', s(QuantX.MATTER_STORAGE_ITEM.get())),
                "PFP", "FSF", "PFP"), s(QuantX.MATTER_STORAGE_2_ITEM.get()), false));
        c.add(card("matter_storage_3", grid(Map.of('F', F2, 'T', F3, 'S', s(QuantX.MATTER_STORAGE_2_ITEM.get())),
                "FTF", "TST", "FTF"), s(QuantX.MATTER_STORAGE_3_ITEM.get()), false));
        c.add(card("matter_accelerator", grid(Map.of('P', P, 'D', D, 'G', FB), "PDP", "DGD", "PDP"),
                s(QuantX.MATTER_ACCELERATOR_ITEM.get()), false));
        c.add(card("matter_valve", loose(s(QuantX.MATTER_ACCELERATOR_ITEM.get()), s(Items.REDSTONE_TORCH)),
                s(QuantX.MATTER_VALVE_ITEM.get()), true));
        // Машины и инструменты
        c.add(card("matter_flow_meter", loose(FB, s(Items.COMPARATOR)),
                s(QuantX.MATTER_FLOW_METER_ITEM.get()), true));
        c.add(card("materializer", grid(Map.of('P', P, 'I', I, 'R', R, 'F', F), "PIP", "RFR", "PIP"),
                s(QuantX.MATERIALIZER_ITEM.get()), false));
        c.add(card("materializer_2", grid(Map.of('F', F2, 'M', s(QuantX.MATERIALIZER_ITEM.get())),
                "FFF", "FMF", "FFF"), s(QuantX.MATERIALIZER_2_ITEM.get()), false));
        c.add(card("materializer_3", grid(Map.of('F', F3, 'M', s(QuantX.MATERIALIZER_2_ITEM.get())),
                "FFF", "FMF", "FFF"), s(QuantX.MATERIALIZER_3_ITEM.get()), false));
        c.add(card("materializer_upgrade_2", grid(Map.of('F', F2, 'R', R), "FFF", "FRF", "FFF"),
                s(QuantX.MATERIALIZER_UPGRADE_2.get()), false));
        c.add(card("materializer_upgrade_3", grid(Map.of('F', F3, 'R', R), "FFF", "FRF", "FFF"),
                s(QuantX.MATERIALIZER_UPGRADE_3.get()), false));
        c.add(card("matter_gun", grid(Map.of('P', P, 'D', DM, 'R', R, 'F', F), "PDP", "RFR", ".P."),
                s(QuantX.MATTER_GUN.get()), false));
        c.add(card("matter_pack", grid(Map.of('L', L, 'E', E, 'P', P), "LEL", "PLP", "LLL"),
                s(QuantX.MATTER_PACK.get()), false));
        c.add(card("matter_pack_2", grid(Map.of('F', F2, 'P', s(QuantX.MATTER_PACK.get())),
                ".F.", "FPF", ".F."), s(QuantX.MATTER_PACK_2.get()), false));
        c.add(card("matter_pack_3", grid(Map.of('F', F3, 'P', s(QuantX.MATTER_PACK_2.get())),
                ".F.", "FPF", ".F."), s(QuantX.MATTER_PACK_3.get()), false));
        // Сама книга
        c.add(card("guide_book", loose(s(Items.BOOK), D), s(QuantX.GUIDE_BOOK.get()), true));
        return c;
    }

    // ===== Рендер =====

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY,
                                   float partialTick) {
        int px = left();
        int py = top();

        gfx.fill(0, 0, this.width, this.height, DIM);
        gfx.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, PAPER_EDGE);
        gfx.fill(px, py, px + PANEL_W, py + PANEL_H, PAPER);

        if (page == 0) {
            drawCover(gfx, px, py);
        } else {
            drawRecipePage(gfx, px, py, mouseX, mouseY);
        }

        // Номер страницы по центру внизу.
        centered(gfx, (page + 1) + " / " + pageCount(), py + PANEL_H - 15, NOTE);

        super.extractRenderState(gfx, mouseX, mouseY, partialTick); // кнопки ‹ ›
    }

    /** Обложка: название, подзаголовок, пара строк-подсказок. */
    private void drawCover(GuiGraphicsExtractor gfx, int px, int py) {
        int mid = this.width / 2;
        centered(gfx, "§lQuantX", py + 34, ACCENT);
        gfx.fill(mid - 40, py + 48, mid + 40, py + 49, ACCENT);
        centered(gfx, Component.translatable("guide.quantx.subtitle"),
                py + 58, TITLE);
        centered(gfx, Component.translatable("guide.quantx.cover.1"),
                py + 84, TEXT);
        centered(gfx, Component.translatable("guide.quantx.cover.2"),
                py + 96, TEXT);
        centered(gfx, Component.translatable("guide.quantx.cover.3"),
                py + 120, NOTE);
    }

    /** Центрированный текст без тени (centeredText рисует тень). */
    private void centered(GuiGraphicsExtractor gfx, Component text, int y, int color) {
        gfx.text(font, text, this.width / 2 - font.width(text) / 2, y, color, false);
    }

    private void centered(GuiGraphicsExtractor gfx, String text, int y, int color) {
        gfx.text(font, text, this.width / 2 - font.width(text) / 2, y, color, false);
    }

    /** До 4 карточек рецептов + блок советов на последней странице. */
    private void drawRecipePage(GuiGraphicsExtractor gfx, int px, int py,
                                int mouseX, int mouseY) {
        int start = (page - 1) * CARDS_PER_PAGE;
        int end = Math.min(cards.size(), start + CARDS_PER_PAGE);

        for (int i = start; i < end; i++) {
            int col = (i - start) % 2;
            int row = (i - start) / 2;
            int cx = px + 18 + col * CARD_COL_W;
            int cy = py + 14 + row * 80;
            drawCard(gfx, cards.get(i), cx, cy, mouseX, mouseY);
        }

        // Последняя страница — советы справа от единственной карточки.
        // Текст заворачивается по ширине колонки, чтобы не вылезти за край.
        if (end == cards.size() && (end - start) == 1) {
            int tx = px + 18 + CARD_COL_W;
            int tw = px + PANEL_W - 14 - tx; // до правой кромки панели
            int ty = py + 14;
            gfx.text(font, Component.translatable("guide.quantx.tips.title"),
                    tx, ty, TITLE, false);
            ty += font.lineHeight + 4;
            for (int t = 1; t <= 3; t++) {
                var lines = font.split(
                        Component.translatable("guide.quantx.tips." + t), tw);
                for (var line : lines) {
                    gfx.text(font, line, tx, ty, TEXT, false);
                    ty += font.lineHeight + 2;
                }
                ty += 3; // отступ между советами
            }
        }
    }

    /** Карточка: название, сетка 3x3, стрелка, слот результата. */
    private void drawCard(GuiGraphicsExtractor gfx, RecipeCard card,
                          int cx, int cy, int mouseX, int mouseY) {
        gfx.text(font, card.title(), cx, cy, TITLE, false);

        int gy = cy + 12;
        for (int i = 0; i < 9; i++) {
            int sx = cx + (i % 3) * CELL;
            int sy = gy + (i / 3) * CELL;
            drawSlot(gfx, sx, sy);
            ItemStack stack = card.grid()[i];
            if (!stack.isEmpty()) {
                gfx.item(stack, sx, sy);
                if (mouseX >= sx && mouseX < sx + CELL && mouseY >= sy && mouseY < sy + CELL) {
                    gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }

        int midY = gy + CELL + CELL / 2; // середина сетки по вертикали
        drawArrow(gfx, cx + 58, midY);

        int rx = cx + 78;
        int ry = midY - CELL / 2;
        drawSlot(gfx, rx, ry);
        gfx.item(card.result(), rx, ry);
        gfx.itemDecorations(font, card.result(), rx, ry);
        if (mouseX >= rx && mouseX < rx + CELL && mouseY >= ry && mouseY < ry + CELL) {
            gfx.setTooltipForNextFrame(font, card.result(), mouseX, mouseY);
        }

        if (card.shapeless()) {
            gfx.text(font, Component.translatable("guide.quantx.shapeless"),
                    rx, ry + CELL + 4, NOTE, false);
        }
    }

    /** Ячейка сетки: тёмная кромка + светлое дно. */
    private static void drawSlot(GuiGraphicsExtractor gfx, int x, int y) {
        gfx.fill(x - 1, y - 1, x + CELL + 1, y + CELL + 1, SLOT_EDGE);
        gfx.fill(x, y, x + CELL, y + CELL, SLOT_BG);
    }

    /** Стрелка «→» 14x7, нарисованная заливками. */
    private static void drawArrow(GuiGraphicsExtractor gfx, int x, int midY) {
        gfx.fill(x, midY - 1, x + 10, midY + 2, ARROW);          // древко
        gfx.fill(x + 8, midY - 4, x + 14, midY + 5, ARROW);      // наконечник
        gfx.fill(x + 8, midY - 1, x + 11, midY + 2, PAPER_EDGE); // выемка — «остриё»
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == KEY_LEFT) {
            page = Math.max(0, page - 1);
            return true;
        }
        if (event.key() == KEY_RIGHT) {
            page = Math.min(pageCount() - 1, page + 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
