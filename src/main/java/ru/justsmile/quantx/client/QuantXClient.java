package ru.justsmile.quantx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.item.MatterGunItem;

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
        AddGuiOverlayLayersEvent.BUS.addListener(e -> {
            e.getLayeredDraw().addAbove(
                    ForgeLayeredDraw.HOTBAR_AND_DECOS,
                    Identifier.fromNamespaceAndPath(QuantX.MODID, "matter_gun"),
                    ForgeLayeredDraw.ITEM_HOTBAR,
                    MatterGunHud::extract);
            // Объём материи блока под прицелом (материализатор, хранилище).
            e.getLayeredDraw().addAbove(
                    ForgeLayeredDraw.HOTBAR_AND_DECOS,
                    Identifier.fromNamespaceAndPath(QuantX.MODID, "matter_tank"),
                    ForgeLayeredDraw.ITEM_HOTBAR,
                    MatterTankHud::extract);
        });

        // Рамка слота ранца материи в экране инвентаря.
        MatterPackSlotRender.register();

        // ЛКМ по спецблоку БЕЗ мат-пушки в руке — гасим ввод на уровне
        // InputEvent: клик вообще не доходит до ванили, поэтому нет ни маха
        // рукой, ни частиц разрушения, ни пакета на сервер. С пушкой клик
        // пропускаем — серверный LeftClickBlock в QuantX.gunTapModBlock
        // снимает блок в инвентарь.
        InputEvent.MouseButton.Pre.BUS.addListener(event -> {
            // press=1 (GLFW), ЛКМ=1 — та же конвенция button(), что
            // и у MouseButtonEvent в экранах (проверено в живой сессии).
            if (event.getAction() != 1 || event.getButton() != 1) return false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.gui.screen() != null
                    || !(mc.hitResult instanceof BlockHitResult hit)) {
                return false;
            }
            if (!QuantX.isModBlock(mc.level.getBlockState(hit.getBlockPos()))) {
                return false;
            }
            // true = событие отменено: ваниль клик не обрабатывает вообще.
            return !(mc.player.getMainHandItem().getItem() instanceof MatterGunItem);
        });

        // Пушка в ЛЕВОЙ руке: ПКМ работает так же, как в основной.
        // UseItem стреляет по каждой руке, но при отмене startUseItem
        // выходит из цикла — до левой руки ваниль не доходит. Поэтому
        // вызываем useItemOn/useItem для OFF_HAND сами и гасим MAIN_HAND.
        InputEvent.InteractionKeyMappingTriggered.UseItem.BUS.addListener(event -> {
            if (event.getHand() != InteractionHand.MAIN_HAND) return false;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null || mc.gameMode == null
                    || !(player.getOffhandItem().getItem() instanceof MatterGunItem)) {
                return false;
            }
            if (mc.hitResult instanceof BlockHitResult hit) {
                InteractionResult res =
                        mc.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hit);
                if (res instanceof InteractionResult.Success) {
                    player.swing(InteractionHand.OFF_HAND,
                            player.getOffhandItem().getInteractAnimation(), false);
                    event.setSwingHand(false);
                    return true;
                }
                if (res instanceof InteractionResult.Fail) {
                    event.setSwingHand(false);
                    return true;
                }
            }
            if (mc.gameMode.useItem(player, InteractionHand.OFF_HAND)
                    instanceof InteractionResult.Success) {
                player.swing(InteractionHand.OFF_HAND,
                        player.getOffhandItem().getInteractAnimation(), false);
                event.setSwingHand(false);
                return true;
            }
            return false;
        });
    }
}
