package ru.justsmile.quantx.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.event.ContainerScreenEvent;
import ru.justsmile.quantx.menu.MatterPackContainer;

/**
 * Рисует рамку слота ранца материи в экранах инвентаря — и в выживании,
 * и в креативе. Слот живёт в меню на x=-10, y=24 (слева от нагрудника) —
 * снаружи текстуры GUI, поэтому рамку дорисовываем сами. Предмет и иконку
 * пустого слота ванилла рисует автоматически через renderSlot поверх рамки.
 */
public final class MatterPackSlotRender {

    private static final Identifier INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    private MatterPackSlotRender() {}

    public static void register() {
        // Background-событие стреляет после отрисовки фона экрана, но ДО слотов —
        // рамка ложится под предмет, как у ванильных слотов.
        ContainerScreenEvent.Render.Background.BUS.addListener(event -> {
            AbstractContainerScreen<?> screen = event.getContainerScreen();

            // Ищем наш слот в меню текущего экрана: InventoryScreen (выживание)
            // держит его напрямую, CreativeModeInventoryScreen — через SlotWrapper.
            for (Slot slot : screen.getMenu().slots) {
                if (!(slot.container instanceof MatterPackContainer)) continue;
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y - 2;
                // 18x18 рамка слота из ванильной текстуры инвентаря
                // (u=7, v=83 — первый слот сетки инвентаря игрока).
                event.getGuiGraphics().blit(RenderPipelines.GUI_TEXTURED,
                        INVENTORY_TEXTURE, x - 1, y - 1,
                        7, 83, 18, 18, 256, 256);
            }
        });
    }
}
