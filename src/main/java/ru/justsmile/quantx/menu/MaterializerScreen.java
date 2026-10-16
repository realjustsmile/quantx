package ru.justsmile.quantx.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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

    public MaterializerScreen(MaterializerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // Ванильная подпись "Инвентарь" на y=72 перекрывает строки материи — прячем за экран.
        this.inventoryLabelY = -40;
    }

    /**
     * Кнопки внутри окна, рядом с тем, что они меняют:
     * Режим и Старт/Стоп — управление машиной, ◀ ▶ — под слотом рецепта.
     */
    @Override
    protected void init() {
        super.init();

        // Кнопка режима: зазор 4px под слотами (слоты кончаются на y=40).
        modeButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendButton(MaterializerMenu.BTN_CYCLE_MODE))
                .pos(leftPos + 8, topPos + 44).size(100, 11).build());
        modeButton.setTooltip(Tooltip.create(
                Component.literal("Переключить режим работы")));

        // Старт/Стоп — квадратная, в промежутке между выходом (x84-102)
        // и призрачным слотом рецепта (x142-160). Рамка-индикатор вокруг — в render.
        startButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> sendButton(MaterializerMenu.BTN_START_STOP))
                .pos(leftPos + 112, topPos + 21).size(18, 18).build());
        startButton.setTooltip(Tooltip.create(
                Component.literal("Запустить/остановить работу")));

        // Листание рецептов — по центру под призрачным слотом (ячейка x142-160).
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"),
                b -> sendButton(MaterializerMenu.BTN_PREV_RECIPE))
                .pos(leftPos + 133, topPos + 44).size(16, 11).build());
        prev.setTooltip(Tooltip.create(Component.literal("Предыдущий рецепт")));
        Button next = addRenderableWidget(Button.builder(Component.literal(">"),
                b -> sendButton(MaterializerMenu.BTN_NEXT_RECIPE))
                .pos(leftPos + 151, topPos + 44).size(16, 11).build());
        next.setTooltip(Tooltip.create(Component.literal("Следующий рецепт")));

    }

    /** Отправляет нажатие кнопки контейнера на сервер (clickMenuButton в Menu). */
    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    /** Русское название режима для строки состояния. */
    private static String modeName(int ordinal) {
        MaterializerBlockEntity.Mode[] modes = MaterializerBlockEntity.Mode.values();
        if (ordinal < 0 || ordinal >= modes.length) return "?";
        return switch (modes[ordinal]) {
            case SCAN -> "Сканирование";
            case DEMATERIALIZE -> "Разбор";
            case MATERIALIZE -> "Сборка";
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
        modeButton.setMessage(Component.literal("Режим: " + modeName(menu.getModeOrdinal())));

        // 3б. Стоимость выбранного рецепта — под кнопками листания.
        //     Красная, если материи не хватает на сборку.
        long costMicro = menu.getSelectedCostMicro();
        if (costMicro > 0) {
            int color = menu.getMatterMicro() >= costMicro ? 0xFFAAAAAA : 0xFFFF5555;
            gfx.text(font, Component.literal("Цена: " + formatMicro(costMicro)),
                    leftPos + 104, topPos + 58, color, false);
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
        gfx.text(font, Component.literal("Материя: " + formatMicro(menu.getMatterMicro())),
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
    }

    /** Можно ли сейчас запустить действие текущего режима (оценка на клиенте). */
    private boolean canStart() {
        int mode = menu.getModeOrdinal();
        if (mode == MaterializerBlockEntity.Mode.SCAN.ordinal()
                || mode == MaterializerBlockEntity.Mode.DEMATERIALIZE.ordinal()) {
            // Скан и разбор: нужен предмет во входном слоте.
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

    private static String formatMicro(long micro) {
        double units = micro / (double) MatterValues.MICRO_PER_UNIT;
        if (units >= 1000) return String.format("%.2f", units);
        if (units >= 1) return String.format("%.3f", units);
        return String.format("%.6f", units);
    }
}