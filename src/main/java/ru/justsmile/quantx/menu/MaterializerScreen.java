package ru.justsmile.quantx.menu;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.block.MaterializerBlockEntity;
import ru.justsmile.quantx.matter.MatterValues;

public class MaterializerScreen extends AbstractContainerScreen<MaterializerMenu> {

    // Своя текстура: холст ровно под окно, 176x166.
    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath("quantx", "textures/gui/materializer_menu.png");
    private static final int TEX_W = 176;
    private static final int TEX_H = 166;

    // Кнопка режима — label обновляется каждый кадр (показывает текущий режим).
    private Button modeButton;
    // Кнопка Старт/Стоп — квадратная, иконка и рамка-индикатор рисуются в render.
    private Button startButton;

    // ===== Модуль рецептов: постоянная панель 3x5 справа от окна =====
    private static final int TAB_COLS = 3;
    private static final int TAB_ROWS = 5;
    private static final int CELL = 18;
    private static final int PAD = 4;
    /** Шапка модуля («Рецепты» + «n/1024») над сеткой — две строки. */
    private static final int HEADER_H = 20;
    /** Панель = поля + шапка + сетка + полоска скролла. */
    private static final int PANEL_W = PAD * 2 + TAB_COLS * CELL + 4;
    private static final int PANEL_H = PAD + HEADER_H + TAB_ROWS * CELL + PAD;

    /** Сдвиг прокрутки списка в строках (строка = 3 ячейки). */
    private int scrollRow = 0;

    public MaterializerScreen(MaterializerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // Ванильная подпись "Инвентарь" на y=72 перекрывает строки материи — прячем за экран.
        this.inventoryLabelY = -40;
    }

    /**
     * Кнопки внутри окна, рядом с тем, что они меняют:
     * Режим и Старт/Стоп — управление машиной. Рецепты выбираются
     * в постоянном модуле 3x5 справа от окна.
     */
    @Override
    protected void init() {
        super.init();

        // Кнопка режима: зазор 4px под слотами (слоты кончаются на y=40).
        modeButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendButton(MaterializerMenu.BTN_CYCLE_MODE))
                .pos(leftPos + 8, topPos + 44).size(100, 11).build());
        modeButton.setTooltip(Tooltip.create(
                Component.translatable("gui.quantx.materializer.tooltip.mode")));

        // Старт/Стоп — квадратная, в промежутке между выходом (x84-102)
        // и призрачным слотом рецепта (x142-160). Рамка-индикатор вокруг — в render.
        startButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendButton(MaterializerMenu.BTN_START_STOP))
                .pos(leftPos + 112, topPos + 21).size(18, 18).build());
        startButton.setTooltip(Tooltip.create(
                Component.translatable("gui.quantx.materializer.tooltip.start_stop")));
    }

    /** Отправляет нажатие кнопки контейнера на сервер (clickMenuButton в Menu). */
    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    /** Ключ локализации режима для кнопки. */
    private static String modeKey(int ordinal) {
        MaterializerBlockEntity.Mode[] modes = MaterializerBlockEntity.Mode.values();
        if (ordinal < 0 || ordinal >= modes.length)
            return "gui.quantx.materializer.mode.unknown";
        return switch (modes[ordinal]) {
            case SCAN -> "gui.quantx.materializer.mode.scan";
            case DEMATERIALIZE -> "gui.quantx.materializer.mode.dematerialize";
            case MATERIALIZE -> "gui.quantx.materializer.mode.materialize";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // 1. Фон — ДО всего остального.
        gfx.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                leftPos, topPos, 0, 0, 176, 166, TEX_W, TEX_H);

        // 2. Стандартный рендер: слоты, предметы, тултипы.
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);

        // 3. Кнопка режима показывает текущий режим.
        modeButton.setMessage(Component.translatable("gui.quantx.materializer.mode",
                Component.translatable(modeKey(menu.getModeOrdinal()))));

        // 3б. Стоимость выбранного рецепта — модуль СПРАВА от окна,
        //     под панелью рецептов. Красная, если материи не хватает.
        long costMicro = menu.getSelectedCostMicro();
        if (costMicro > 0) {
            int color = menu.getMatterMicro() >= costMicro ? 0xFFAAAAAA : 0xFFFF5555;
            Component label = Component.translatable("gui.quantx.materializer.cost",
                    MatterValues.formatMicro(costMicro));
            int px = leftPos + imageWidth;
            int py = topPos + 8 + PANEL_H + 4;
            int pw = font.width(label) + 8;
            gfx.fill(px, py, px + pw, py + 18, 0xFF373737);
            gfx.fill(px + 1, py + 1, px + pw - 1, py + 17, 0xFF151515);
            gfx.text(font, label, px + 4, py + 5, color, false);
        }

        // 4. Прогресс-бар: заливка внутренности жёлобка (51,27)-(79,34).
        int max = menu.getMaxProgress();
        int prog = menu.getProgress();
        if (max > 0 && prog > 0) {
            int w = (int) (28L * Math.min(prog, max) / max);
            gfx.fill(leftPos + 51, topPos + 27, leftPos + 51 + w, topPos + 34, 0xFF44CC44);
        }

        // 5. Материя единая: одна строка с бирюзовой меткой.
        gfx.fill(leftPos + 10, topPos + 69, leftPos + 16, topPos + 75, 0xFF55DDCC);
        gfx.text(font, Component.translatable("gui.quantx.materializer.matter",
                        MatterValues.formatMicroPlain(menu.getMatterMicro()),
                        MatterValues.formatMicroPlain(menu.getCapacityMicro())),
                leftPos + 20, topPos + 68, 0xFF7FE8D4, false);

        // 6. Рамка-индикатор вокруг Старт/Стоп (3px):
        //    работает — мигает жёлтым, действие возможно — зелёный, иначе красный.
        int bx = startButton.getX();
        int by = startButton.getY();
        int frameColor;
        if (menu.isRunning()) {
            frameColor = (System.currentTimeMillis() / 500) % 2 == 0
                    ? 0xFFDDDD33 : 0xFF665500;
        } else if (canStart()) {
            frameColor = 0xFF44CC44;
        } else {
            frameColor = 0xFFCC3333;
        }
        drawFrame(gfx, bx - 3, by - 3,
                startButton.getWidth() + 6, startButton.getHeight() + 6, 3, frameColor);

        // Иконка на кнопке: ▶ когда остановлена, ■ когда работает.
        if (menu.isRunning()) {
            gfx.fill(bx + 6, by + 6, bx + 12, by + 12, 0xFFFFFFFF);
        } else {
            drawPlayIcon(gfx, bx + 5, by + 4, 0xFFFFFFFF);
        }

        // 7. Строка состояния над окном + постоянный модуль рецептов справа.
        renderStatusLine(gfx);
        renderRecipeModule(gfx, mouseX, mouseY);
    }

    // ===== Строка состояния =====

    /** Ключ локализации статуса по ordinal из меню. */
    private static String statusKey(int ordinal) {
        MaterializerBlockEntity.Status[] all = MaterializerBlockEntity.Status.values();
        if (ordinal < 0 || ordinal >= all.length)
            return "gui.quantx.materializer.status.idle";
        return switch (all[ordinal]) {
            case IDLE -> "gui.quantx.materializer.status.idle";
            case RUNNING -> "gui.quantx.materializer.status.running";
            case NO_INPUT -> "gui.quantx.materializer.status.no_input";
            case KNOWN_RECIPE -> "gui.quantx.materializer.status.known_recipe";
            case RECIPE_LIMIT -> "gui.quantx.materializer.status.recipe_limit";
            case NO_RECIPE -> "gui.quantx.materializer.status.no_recipe";
            case NO_MATTER -> "gui.quantx.materializer.status.no_matter";
            case TANK_FULL -> "gui.quantx.materializer.status.tank_full";
            case OUTPUT_FULL -> "gui.quantx.materializer.status.output_full";
        };
    }

    /** Цвет статуса: серый «остыв», зелёный «работа», янтарь — замечания, красный — блокеры. */
    private static int statusColor(int ordinal) {
        MaterializerBlockEntity.Status[] all = MaterializerBlockEntity.Status.values();
        if (ordinal < 0 || ordinal >= all.length) return 0xFFAAAAAA;
        return switch (all[ordinal]) {
            case IDLE, TANK_FULL -> 0xFFAAAAAA;
            case RUNNING -> 0xFF44CC44;
            case NO_INPUT, KNOWN_RECIPE, NO_RECIPE -> 0xFFFFAA00;
            case RECIPE_LIMIT, NO_MATTER, OUTPUT_FULL -> 0xFFFF5555;
        };
    }

    /** Тонкая плашка над окном с текущим статусом машины. */
    private void renderStatusLine(GuiGraphicsExtractor gfx) {
        int ordinal = menu.getStatusOrdinal();
        Component text = Component.translatable(statusKey(ordinal));
        int color = statusColor(ordinal);
        int w = Math.min(imageWidth, font.width(text) + 8);
        int x = leftPos;
        int y = topPos - 14;
        gfx.fill(x, y, x + w, y + 12, 0xFF373737);
        gfx.fill(x + 1, y + 1, x + w - 1, y + 11, 0xFF151515);
        gfx.text(font, text, x + 4, y + 2, color, false);
    }

    // ===== Модуль рецептов =====

    /** BE на клиенте — находим по позиции, переданной при открытии меню. */
    @Nullable
    private MaterializerBlockEntity clientBe() {
        BlockPos pos = menu.getBlockPos();
        if (pos == null || minecraft == null || minecraft.player == null) return null;
        return minecraft.player.level().getBlockEntity(pos)
                instanceof MaterializerBlockEntity be ? be : null;
    }

    /** Прямоугольник модуля рецептов справа от окна. */
    private boolean insidePanel(double mx, double my) {
        int right = leftPos + imageWidth + PANEL_W;
        return mx >= leftPos + imageWidth && mx < right
                && my >= topPos + 8 && my < topPos + 8 + PANEL_H;
    }

    /** Индекс рецепта по курсору (−1 — курсор не над ячейкой с рецептом). */
    private int cellIndexAt(double mx, double my, int recipeCount) {
        int px = leftPos + imageWidth;
        int py = topPos + 8;
        int col = (int) (mx - px - PAD) / CELL;
        int row = (int) (my - py - PAD - HEADER_H) / CELL;
        if (col < 0 || col >= TAB_COLS || row < 0 || row >= TAB_ROWS) return -1;
        int index = (scrollRow + row) * TAB_COLS + col;
        return index < recipeCount ? index : -1;
    }

    /**
     * Постоянный модуль рецептов: шапка «Рецепты n/15» и сетка 3x5.
     * Все ячейки всегда на месте — изначально пустые, заполняются
     * по одной по мере изучения. ЛКМ по рецепту — выбрать его.
     */
    private void renderRecipeModule(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        MaterializerBlockEntity be = clientBe();
        List<Identifier> recipes = be != null ? be.orderedRecipeIds() : List.of();
        Identifier selected = be != null ? be.getSelectedRecipe() : null;

        int px = leftPos + imageWidth;
        int py = topPos + 8;
        gfx.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF373737);
        gfx.fill(px + 1, py + 1, px + PANEL_W - 1, py + PANEL_H - 1, 0xFF151515);

        // Шапка в две строки: «Рецепты» и «n/1024» — красная при лимите.
        boolean full = recipes.size() >= MaterializerBlockEntity.MAX_RECIPES;
        Component title = Component.translatable("gui.quantx.materializer.recipes");
        gfx.text(font, title, px + (PANEL_W - font.width(title)) / 2, py + 2,
                0xFF7FE8D4, false);
        Component counter = Component.literal(
                recipes.size() + "/" + MaterializerBlockEntity.MAX_RECIPES);
        gfx.text(font, counter, px + (PANEL_W - font.width(counter)) / 2, py + 12,
                full ? 0xFFFF5555 : 0xFFAAAAAA, false);

        // Сетка 3x5 под шапкой — показывает окно в scrollRow строках списка.
        for (int row = 0; row < TAB_ROWS; row++) {
            for (int col = 0; col < TAB_COLS; col++) {
                int cx = px + PAD + col * CELL;
                int cy = py + PAD + HEADER_H + row * CELL;
                gfx.fill(cx, cy, cx + CELL, cy + CELL, 0xFF373737);
                gfx.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0xFF0B0B0B);

                int index = (scrollRow + row) * TAB_COLS + col;
                if (index >= recipes.size()) continue;
                Identifier id = recipes.get(index);
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null) {
                    gfx.fakeItem(new ItemStack(item), cx + 1, cy + 1);
                }
                if (id.equals(selected)) {
                    drawFrame(gfx, cx, cy, CELL, CELL, 1, 0xFF44CC44);
                }
                if (mouseX >= cx && mouseX < cx + CELL
                        && mouseY >= cy && mouseY < cy + CELL) {
                    gfx.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x40FFFFFF);
                    if (item != null) {
                        gfx.setTooltipForNextFrame(font, new ItemStack(item), mouseX, mouseY);
                    }
                }
            }
        }

        // Скроллбар справа от сетки: трек + ползунок пропорционально видимой части.
        int totalRows = (recipes.size() + TAB_COLS - 1) / TAB_COLS;
        if (totalRows > TAB_ROWS) {
            int trackX = px + PAD + TAB_COLS * CELL + 1;
            int trackY = py + PAD + HEADER_H;
            int trackH = TAB_ROWS * CELL;
            gfx.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF0B0B0B);
            int thumbH = Math.max(10, trackH * TAB_ROWS / totalRows);
            int thumbY = trackY + (trackH - thumbH) * scrollRow / (totalRows - TAB_ROWS);
            gfx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF9E9E9E);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // ЛКМ в этой версии — button() == 1 (см. AbstractContainerScreen).
        if (event.button() == 1 && insidePanel(event.x(), event.y())) {
            MaterializerBlockEntity be = clientBe();
            int count = be != null ? be.getKnownRecipes().size() : 0;
            int index = cellIndexAt(event.x(), event.y(), count);
            if (index >= 0) {
                sendButton(MaterializerMenu.BTN_SELECT_RECIPE_BASE + index);
            }
            // Клик по модулю не проваливается в слоты/кнопки окна.
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (insidePanel(mouseX, mouseY)) {
            MaterializerBlockEntity be = clientBe();
            int count = be != null ? be.getKnownRecipes().size() : 0;
            int totalRows = (count + TAB_COLS - 1) / TAB_COLS;
            int maxScroll = Math.max(0, totalRows - TAB_ROWS);
            scrollRow = Math.max(0, Math.min(maxScroll,
                    scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Можно ли сейчас запустить действие текущего режима (оценка на клиенте). */
    private boolean canStart() {
        int mode = menu.getModeOrdinal();
        if (mode == MaterializerBlockEntity.Mode.SCAN.ordinal()) {
            ItemStack input = menu.slots.get(0).getItem();
            if (input.isEmpty()) return false;
            // Лимит рецептов: скан нового предмета при заполненной сетке не запустится.
            MaterializerBlockEntity be = clientBe();
            if (be != null) {
                Identifier id = BuiltInRegistries.ITEM.getKey(input.getItem());
                if (!be.isRecipeKnown(id)
                        && be.getKnownRecipes().size() >= MaterializerBlockEntity.MAX_RECIPES) {
                    return false;
                }
            }
            return true;
        }
        if (mode == MaterializerBlockEntity.Mode.DEMATERIALIZE.ordinal()) {
            return !menu.slots.get(0).getItem().isEmpty();
        }
        // Сборка: нужен выбранный рецепт, хватка материи и место в выходе.
        ItemStack icon = menu.getRecipeIcon();
        long cost = menu.getSelectedCostMicro();
        if (icon.isEmpty() || cost <= 0) return false;
        if (menu.getMatterMicro() < cost) return false;
        ItemStack out = menu.slots.get(1).getItem();
        return out.isEmpty()
                || (ItemStack.isSameItemSameComponents(out, icon)
                        && out.getCount() + icon.getCount() <= out.getMaxStackSize());
    }

    /** Рамка толщины t вокруг прямоугольника x,y,w,h. */
    private static void drawFrame(GuiGraphicsExtractor gfx,
                                  int x, int y, int w, int h, int t, int color) {
        gfx.fill(x, y, x + w, y + t, color);
        gfx.fill(x, y + h - t, x + w, y + h, color);
        gfx.fill(x, y + t, x + t, y + h - t, color);
        gfx.fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    /** Треугольник «play» 8x10, вершиной вправо. */
    private static void drawPlayIcon(GuiGraphicsExtractor gfx, int x, int y, int color) {
        for (int col = 0; col < 4; col++) {
            int half = 5 - col;
            gfx.fill(x + col * 2, y + 5 - half,
                    x + col * 2 + 2, y + 5 + half, color);
        }
    }
}