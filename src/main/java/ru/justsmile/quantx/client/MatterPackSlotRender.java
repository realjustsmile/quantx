package ru.justsmile.quantx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import ru.justsmile.quantx.menu.MatterPackContainer;
import ru.justsmile.quantx.mixin.client.SlotAccessor;

/**
 * Сворачиваемая панель со слотом материального ранца — маленькое
 * «окошко», прилепленное к левой кромке рамки инвентаря. Работает
 * и в выживании (InventoryScreen), и в креативе (SlotWrapper внутри
 * CreativeModeInventoryScreen).
 *
 * Ушко шириной 8px торчит у левой кромки всегда: клик по нему
 * разворачивает/сворачивает панель с ячейкой. При сворачивании
 * слот становится неактивным (Slot.isActive) — не рисуется
 * и не кликается, остаётся одно ушко на кромке.
 */
public final class MatterPackSlotRender {

    private static final Identifier INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    /** Позиция слота в меню (относительно guiLeft/guiTop) — внутри панели. */
    private static final int SLOT_X = -29;
    private static final int SLOT_Y = 19;

    /** Ушко на левой кромке рамки: клик — свернуть/развернуть. */
    private static final int EAR_W = 8;
    private static final int EAR_Y = 14;
    private static final int EAR_H = 26;

    /** Раскрытая панель слева от ушка. */
    private static final int PANEL_W = 26;
    private static final int PANEL_H = 26;

    /** Панель свёрнута? Сессионное состояние — см. MatterPackContainer. */

    private MatterPackSlotRender() {}

    public static void register() {
        // Background стреляет после фона экрана, но ДО слотов — панель
        // и ушко ложатся под предмет и иконку пустого слота.
        ContainerScreenEvent.Render.Background.BUS.addListener(event -> {
            AbstractContainerScreen<?> screen = event.getContainerScreen();
            Slot pack = findPackSlot(screen);
            if (pack == null) return;
            boolean collapsed = MatterPackContainer.packSlotCollapsed;

            // В креативе ItemPickerMenu разбрасывает SlotWrapper'ы по своей
            // раскладке — наш вставал бы во вторую ячейку хотбара.
            // Переставляем его прямо на координаты нашей ячейки: слот
            // остаётся активным, и ванильные рендер/клик/тултип работают
            // сами, как у любого другого слота.
            if (screen instanceof CreativeModeInventoryScreen
                    && pack instanceof SlotAccessor acc) {
                acc.quantx$setX(SLOT_X);
                acc.quantx$setY(SLOT_Y);
            }

            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            GuiGraphicsExtractor g = event.getGuiGraphics();
            Minecraft mc = Minecraft.getInstance();

            if (!collapsed) {
                // Окошко с ячейкой: серая панель как у ванильных слотов.
                drawBox(g, left - EAR_W - PANEL_W, top + EAR_Y, PANEL_W, PANEL_H);
                // 18x18 рамка ячейки из ванильной текстуры инвентаря.
                g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                        left + SLOT_X - 1, top + SLOT_Y - 1,
                        7, 83, 18, 18, 256, 256);
            }

            // Ушко-переключатель на кромке рамки — видно всегда.
            drawBox(g, left - EAR_W, top + EAR_Y, EAR_W, EAR_H);
            Component arrow = Component.literal(collapsed ? ">" : "<");
            g.text(mc.font, arrow,
                    left - EAR_W + (EAR_W - mc.font.width(arrow)) / 2,
                    top + EAR_Y + (EAR_H - mc.font.lineHeight) / 2 + 1,
                    0xFFF0F0F0, false);
        });

        // Клик по ушку — свернуть/развернуть. Predicate-слушатель:
        // return true отменяет событие, чтобы клик не достался слотам
        // и виджетам под ним.
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener(event -> {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (findPackSlot(screen) == null) return false;

            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            double mx = event.getMouseX();
            double my = event.getMouseY();
            if (my < top + EAR_Y || my >= top + EAR_Y + EAR_H) return false;
             // Клики левее кромки рамки гасим целиком — иначе экран трактует
            // их как «клик мимо слотов» и выбрасывает предмет с курсора.
            if (mx < left - EAR_W - PANEL_W || mx >= left) return false;

            // Переключение — ушко на кромке.
            if (mx >= left - EAR_W) {
                MatterPackContainer.packSlotCollapsed =
                        !MatterPackContainer.packSlotCollapsed;
                return true;
            }
            if (MatterPackContainer.packSlotCollapsed) return true;

            // Ячейка — настоящий активный слот (в креативе — переставленный
            // SlotWrapper), клики отдаём экрану; остальную панель глушим.
            return !isInsideCell(mx, my, left, top);
        });

        // Отпускание кнопки и перетаскивание в зоне панели тоже глушим:
        // release с предметом на курсоре экран трактует как «клик мимо»
        // и выбрасывает предмет на землю.
        ScreenEvent.MouseButtonReleased.Pre.BUS.addListener(event -> {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (findPackSlot(screen) == null) return false;
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            double my = event.getMouseY();
            if (my < top + EAR_Y || my >= top + EAR_Y + EAR_H) return false;
            double mx = event.getMouseX();
            return mx >= left - EAR_W - PANEL_W && mx < left;
        });
        ScreenEvent.MouseDragged.Pre.BUS.addListener(event -> {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (findPackSlot(screen) == null) return false;
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            double my = event.getMouseY();
            if (my < top + EAR_Y || my >= top + EAR_Y + EAR_H) return false;
            double mx = event.getMouseX();
            return mx >= left - EAR_W - PANEL_W && mx < left;
        });
    }

    /** Попадание курсора в ячейку слота. */
    private static boolean isInsideCell(double mx, double my, int left, int top) {
        return mx >= left + SLOT_X && mx < left + SLOT_X + 16
                && my >= top + SLOT_Y && my < top + SLOT_Y + 16;
    }

    /** Слот ранца в меню экрана (в креативе — его SlotWrapper). */
    private static Slot findPackSlot(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof MatterPackContainer) return slot;
        }
        return null;
    }

    /** Ванильная «панель»-коробочка: светлая кромка, тёмный борт, серый фон. */
    private static void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF8B8B8B);
    }
}
