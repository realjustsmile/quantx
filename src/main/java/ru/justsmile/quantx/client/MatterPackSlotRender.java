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
import ru.justsmile.quantx.menu.GogglesContainer;
import ru.justsmile.quantx.menu.MatterPackContainer;
import ru.justsmile.quantx.mixin.client.SlotAccessor;

/**
 * РЎРІРѕСЂР°С‡РёРІР°РµРјР°СЏ РїР°РЅРµР»СЊ СЃРѕ СЃР»РѕС‚РѕРј РјР°С‚РµСЂРёР°Р»СЊРЅРѕРіРѕ СЂР°РЅС†Р° вЂ” РјР°Р»РµРЅСЊРєРѕРµ
 * В«РѕРєРѕС€РєРѕВ», РїСЂРёР»РµРїР»РµРЅРЅРѕРµ Рє Р»РµРІРѕР№ РєСЂРѕРјРєРµ СЂР°РјРєРё РёРЅРІРµРЅС‚Р°СЂСЏ. Р Р°Р±РѕС‚Р°РµС‚
 * Рё РІ РІС‹Р¶РёРІР°РЅРёРё (InventoryScreen), Рё РІ РєСЂРµР°С‚РёРІРµ (SlotWrapper РІРЅСѓС‚СЂРё
 * CreativeModeInventoryScreen).
 *
 * РЈС€РєРѕ С€РёСЂРёРЅРѕР№ 8px С‚РѕСЂС‡РёС‚ Сѓ Р»РµРІРѕР№ РєСЂРѕРјРєРё РІСЃРµРіРґР°: РєР»РёРє РїРѕ РЅРµРјСѓ
 * СЂР°Р·РІРѕСЂР°С‡РёРІР°РµС‚/СЃРІРѕСЂР°С‡РёРІР°РµС‚ РїР°РЅРµР»СЊ СЃ СЏС‡РµР№РєРѕР№. РџСЂРё СЃРІРѕСЂР°С‡РёРІР°РЅРёРё
 * СЃР»РѕС‚ СЃС‚Р°РЅРѕРІРёС‚СЃСЏ РЅРµР°РєС‚РёРІРЅС‹Рј (Slot.isActive) вЂ” РЅРµ СЂРёСЃСѓРµС‚СЃСЏ
 * Рё РЅРµ РєР»РёРєР°РµС‚СЃСЏ, РѕСЃС‚Р°С‘С‚СЃСЏ РѕРґРЅРѕ СѓС€РєРѕ РЅР° РєСЂРѕРјРєРµ.
 */
public final class MatterPackSlotRender {

    private static final Identifier INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    /** РџРѕР·РёС†РёРё СЃР»РѕС‚РѕРІ РІ РјРµРЅСЋ (РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ guiLeft/guiTop) вЂ” РѕС‡РєРё СЃРІРµСЂС…Сѓ, СЂР°РЅРµС† РЅРёР¶Рµ. */
    private static final int GOGGLES_SLOT_X = -29;
    private static final int GOGGLES_SLOT_Y = 4;
    private static final int PACK_SLOT_X = -29;
    private static final int PACK_SLOT_Y = 25;

    /** РЈС€РєРѕ РЅР° Р»РµРІРѕР№ РєСЂРѕРјРєРµ СЂР°РјРєРё: РІРµСЂС…РЅРёР№ РєСЂР°Р№ СЂРѕРІРЅРѕ РїРѕ РІРµСЂС…Сѓ РёРЅС‚РµСЂС„РµР№СЃР°. */
    private static final int EAR_W = 8;
    private static final int EAR_Y = 0;
    private static final int EAR_H = 46;

    /** Р Р°СЃРєСЂС‹С‚Р°СЏ РїР°РЅРµР»СЊ СЃР»РµРІР° РѕС‚ СѓС€РєР° вЂ” РІРµСЂС…РЅРёР№ РєСЂР°Р№ СЂРѕРІРЅРѕ РїРѕ РІРµСЂС…Сѓ РёРЅС‚РµСЂС„РµР№СЃР°. */
    private static final int PANEL_W = 26;
    private static final int PANEL_H = 46;

    /** РџР°РЅРµР»СЊ СЃРІС‘СЂРЅСѓС‚Р°? РЎРµСЃСЃРёРѕРЅРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ вЂ” СЃРј. MatterPackContainer. */

    private MatterPackSlotRender() {}

    public static void register() {
        // Background СЃС‚СЂРµР»СЏРµС‚ РїРѕСЃР»Рµ С„РѕРЅР° СЌРєСЂР°РЅР°, РЅРѕ Р”Рћ СЃР»РѕС‚РѕРІ вЂ” РїР°РЅРµР»СЊ
        // Рё СѓС€РєРѕ Р»РѕР¶Р°С‚СЃСЏ РїРѕРґ РїСЂРµРґРјРµС‚ Рё РёРєРѕРЅРєСѓ РїСѓСЃС‚РѕРіРѕ СЃР»РѕС‚Р°.
        ContainerScreenEvent.Render.Background.BUS.addListener(event -> {
            AbstractContainerScreen<?> screen = event.getContainerScreen();
            Slot pack = findGearSlot(screen, MatterPackContainer.class);
            Slot goggles = findGearSlot(screen, GogglesContainer.class);
            if (pack == null || goggles == null) return;
            boolean collapsed = MatterPackContainer.packSlotCollapsed;

            // Р’ РєСЂРµР°С‚РёРІРµ ItemPickerMenu СЂР°Р·Р±СЂР°СЃС‹РІР°РµС‚ SlotWrapper'С‹ РїРѕ СЃРІРѕРµР№
            // СЂР°СЃРєР»Р°РґРєРµ вЂ” РЅР°С€Рё РІСЃС‚Р°РІР°Р»Рё Р±С‹ РІРѕ РІС‚РѕСЂСѓСЋ СЏС‡РµР№РєСѓ С…РѕС‚Р±Р°СЂР°.
            // РџРµСЂРµСЃС‚Р°РІР»СЏРµРј РёС… РїСЂСЏРјРѕ РЅР° РєРѕРѕСЂРґРёРЅР°С‚С‹ РЅР°С€РёС… СЏС‡РµРµРє: СЃР»РѕС‚С‹
            // РѕСЃС‚Р°СЋС‚СЃСЏ Р°РєС‚РёРІРЅС‹РјРё, Рё РІР°РЅРёР»СЊРЅС‹Рµ СЂРµРЅРґРµСЂ/РєР»РёРє/С‚СѓР»С‚РёРї СЂР°Р±РѕС‚Р°СЋС‚
            // СЃР°РјРё, РєР°Рє Сѓ Р»СЋР±РѕРіРѕ РґСЂСѓРіРѕРіРѕ СЃР»РѕС‚Р°.
            if (screen instanceof CreativeModeInventoryScreen) {
                if (pack instanceof SlotAccessor acc) {
                    acc.quantx$setX(PACK_SLOT_X);
                    acc.quantx$setY(PACK_SLOT_Y);
                }
                if (goggles instanceof SlotAccessor acc) {
                    acc.quantx$setX(GOGGLES_SLOT_X);
                    acc.quantx$setY(GOGGLES_SLOT_Y);
                }
            }

            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            GuiGraphicsExtractor g = event.getGuiGraphics();
            Minecraft mc = Minecraft.getInstance();

            if (!collapsed) {
                // РћРєРѕС€РєРѕ СЃ РґРІСѓРјСЏ СЏС‡РµР№РєР°РјРё: СЃРµСЂР°СЏ РїР°РЅРµР»СЊ РєР°Рє Сѓ РІР°РЅРёР»СЊРЅС‹С… СЃР»РѕС‚РѕРІ.
                drawBox(g, left - EAR_W - PANEL_W, top + EAR_Y, PANEL_W, PANEL_H);
                // 18x18 СЂР°РјРєРё СЏС‡РµРµРє РёР· РІР°РЅРёР»СЊРЅРѕР№ С‚РµРєСЃС‚СѓСЂС‹ РёРЅРІРµРЅС‚Р°СЂСЏ.
                g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                        left + GOGGLES_SLOT_X - 1, top + GOGGLES_SLOT_Y - 1,
                        7, 83, 18, 18, 256, 256);
                g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                        left + PACK_SLOT_X - 1, top + PACK_SLOT_Y - 1,
                        7, 83, 18, 18, 256, 256);
            }

            // РЈС€РєРѕ-РїРµСЂРµРєР»СЋС‡Р°С‚РµР»СЊ РЅР° РєСЂРѕРјРєРµ СЂР°РјРєРё вЂ” РІРёРґРЅРѕ РІСЃРµРіРґР°.
            drawBox(g, left - EAR_W, top + EAR_Y, EAR_W, EAR_H);
            Component arrow = Component.literal(collapsed ? ">" : "<");
            g.text(mc.font, arrow,
                    left - EAR_W + (EAR_W - mc.font.width(arrow)) / 2,
                    top + EAR_Y + (EAR_H - mc.font.lineHeight) / 2 + 1,
                    0xFFF0F0F0, false);
        });

        // РљР»РёРє РїРѕ СѓС€РєСѓ вЂ” СЃРІРµСЂРЅСѓС‚СЊ/СЂР°Р·РІРµСЂРЅСѓС‚СЊ. Predicate-СЃР»СѓС€Р°С‚РµР»СЊ:
        // return true РѕС‚РјРµРЅСЏРµС‚ СЃРѕР±С‹С‚РёРµ, С‡С‚РѕР±С‹ РєР»РёРє РЅРµ РґРѕСЃС‚Р°Р»СЃСЏ СЃР»РѕС‚Р°Рј
        // Рё РІРёРґР¶РµС‚Р°Рј РїРѕРґ РЅРёРј.
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener(event -> {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (findGearSlot(screen, MatterPackContainer.class) == null) return false;

            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            double mx = event.getMouseX();
            double my = event.getMouseY();
            if (my < top + EAR_Y || my >= top + EAR_Y + EAR_H) return false;
             // РљР»РёРєРё Р»РµРІРµРµ РєСЂРѕРјРєРё СЂР°РјРєРё РіР°СЃРёРј С†РµР»РёРєРѕРј вЂ” РёРЅР°С‡Рµ СЌРєСЂР°РЅ С‚СЂР°РєС‚СѓРµС‚
            // РёС… РєР°Рє В«РєР»РёРє РјРёРјРѕ СЃР»РѕС‚РѕРІВ» Рё РІС‹Р±СЂР°СЃС‹РІР°РµС‚ РїСЂРµРґРјРµС‚ СЃ РєСѓСЂСЃРѕСЂР°.
            if (mx < left - EAR_W - PANEL_W || mx >= left) return false;

            // РџРµСЂРµРєР»СЋС‡РµРЅРёРµ вЂ” СѓС€РєРѕ РЅР° РєСЂРѕРјРєРµ.
            if (mx >= left - EAR_W) {
                MatterPackContainer.packSlotCollapsed =
                        !MatterPackContainer.packSlotCollapsed;
                return true;
            }
            if (MatterPackContainer.packSlotCollapsed) return true;

            // РЇС‡РµР№РєРё вЂ” РЅР°СЃС‚РѕСЏС‰РёРµ Р°РєС‚РёРІРЅС‹Рµ СЃР»РѕС‚С‹ (РІ РєСЂРµР°С‚РёРІРµ вЂ” РїРµСЂРµСЃС‚Р°РІР»РµРЅРЅС‹Рµ
            // SlotWrapper'С‹), РєР»РёРєРё РѕС‚РґР°С‘Рј СЌРєСЂР°РЅСѓ; РѕСЃС‚Р°Р»СЊРЅСѓСЋ РїР°РЅРµР»СЊ РіР»СѓС€РёРј.
            return !isInsideCell(mx, my, left, top);
        });

        // РћС‚РїСѓСЃРєР°РЅРёРµ РєРЅРѕРїРєРё Рё РїРµСЂРµС‚Р°СЃРєРёРІР°РЅРёРµ РІ Р·РѕРЅРµ РїР°РЅРµР»Рё С‚РѕР¶Рµ РіР»СѓС€РёРј:
        // release СЃ РїСЂРµРґРјРµС‚РѕРј РЅР° РєСѓСЂСЃРѕСЂРµ СЌРєСЂР°РЅ С‚СЂР°РєС‚СѓРµС‚ РєР°Рє В«РєР»РёРє РјРёРјРѕВ»
        // Рё РІС‹Р±СЂР°СЃС‹РІР°РµС‚ РїСЂРµРґРјРµС‚ РЅР° Р·РµРјР»СЋ.
        ScreenEvent.MouseButtonReleased.Pre.BUS.addListener(event -> {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (findGearSlot(screen, MatterPackContainer.class) == null) return false;
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            double my = event.getMouseY();
            if (my < top + EAR_Y || my >= top + EAR_Y + EAR_H) return false;
            double mx = event.getMouseX();
            return mx >= left - EAR_W - PANEL_W && mx < left;
        });
        ScreenEvent.MouseDragged.Pre.BUS.addListener(event -> {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (findGearSlot(screen, MatterPackContainer.class) == null) return false;
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            double my = event.getMouseY();
            if (my < top + EAR_Y || my >= top + EAR_Y + EAR_H) return false;
            double mx = event.getMouseX();
            return mx >= left - EAR_W - PANEL_W && mx < left;
        });
    }

    /** РџРѕРїР°РґР°РЅРёРµ РєСѓСЂСЃРѕСЂР° РІ РѕРґРЅСѓ РёР· СЏС‡РµРµРє СЃР»РѕС‚РѕРІ (РѕС‡РєРё РёР»Рё СЂР°РЅРµС†). */
    private static boolean isInsideCell(double mx, double my, int left, int top) {
        boolean inX = mx >= left + PACK_SLOT_X && mx < left + PACK_SLOT_X + 16;
        return inX && (inCellY(my, top, GOGGLES_SLOT_Y)
                || inCellY(my, top, PACK_SLOT_Y));
    }

    private static boolean inCellY(double my, int top, int slotY) {
        return my >= top + slotY && my < top + slotY + 16;
    }

    /** РЎР»РѕС‚ СЃРЅР°СЂСЏР¶РµРЅРёСЏ РІ РјРµРЅСЋ СЌРєСЂР°РЅР° (РІ РєСЂРµР°С‚РёРІРµ вЂ” РµРіРѕ SlotWrapper). */
    private static Slot findGearSlot(AbstractContainerScreen<?> screen,
                                     Class<?> containerType) {
        for (Slot slot : screen.getMenu().slots) {
            if (containerType.isInstance(slot.container)) return slot;
        }
        return null;
    }

    /** Р’Р°РЅРёР»СЊРЅР°СЏ В«РїР°РЅРµР»СЊВ»-РєРѕСЂРѕР±РѕС‡РєР°: СЃРІРµС‚Р»Р°СЏ РєСЂРѕРјРєР°, С‚С‘РјРЅС‹Р№ Р±РѕСЂС‚, СЃРµСЂС‹Р№ С„РѕРЅ. */
    private static void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF8B8B8B);
    }
}
