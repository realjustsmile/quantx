package ru.justsmile.quantx.client;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import ru.justsmile.quantx.QuantX;

/**
 * Клиентская инициализация. Вызывается из конструктора мода ТОЛЬКО на клиенте
 * (dist-guard) — классы net.minecraft.client на сервере не трогаем.
 *
 * Регистрация HUD-слоёв здесь, а не в FMLClientSetupEvent: AddGuiOverlayLayersEvent
 * отстреливает при resolveLayers() во время создания Hud внутри Minecraft.<init>,
 * т.е. РАНЬШЕ clientSetup — тогда слой уже не попадает в список.
 */
public final class QuantXClient {

    private QuantXClient() {}

    public static void registerOverlays() {
        // item_hotbar живёт внутри сабстека "hotbar" — указываем его явно,
        // иначе addAbove не находит цель в корне и слой молча пропадает.
        AddGuiOverlayLayersEvent.BUS.addListener(e ->
                e.getLayeredDraw().addAbove(
                        ForgeLayeredDraw.HOTBAR_AND_DECOS,
                        Identifier.fromNamespaceAndPath(QuantX.MODID, "matter_gun"),
                        ForgeLayeredDraw.ITEM_HOTBAR,
                        MatterGunHud::extract));
    }
}
