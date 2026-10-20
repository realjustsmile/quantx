package ru.justsmile.quantx.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.justsmile.quantx.item.MatterGunItem;
import ru.justsmile.quantx.matter.MatterValues;
import ru.justsmile.quantx.menu.GogglesContainer;

/**
 * HUD-индикатор материи в материальной пушке.
 * Рисуется над хотбаром, когда пушка в любой из рук.
 */
public final class MatterGunHud {

    private MatterGunHud() {}

    /** ForgeLayer: рисуем «Материя: X / 10 000» у правого края хотбара. */
    public static void extract(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // Показ материи только через очки материалиста — без них ваниль.
        if (!GogglesContainer.isWearingGoggles(player)) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Стоимость блока под прицелом — при надетых очках, с любым
        // предметом в руках и даже без него. Полупрозрачный белый текст
        // с тенью, справа-снизу от курсора.
        if (player.pick(4.5, 1.0f, false) instanceof BlockHitResult bhr) {
            BlockPos target = bhr.getBlockPos();
            BlockState state = player.level().getBlockState(target);
            if (!state.isAir() && state.getDestroySpeed(player.level(), target) >= 0) {
                long cost = MatterValues.getCostMicro(state.getBlock().asItem());
                Component label = Component.translatable("tooltip.quantx.matter_yield",
                        MatterValues.formatMicro(cost));

                gfx.pose().pushMatrix();
                gfx.pose().translate(w / 2f + 10f, h / 2f + 4f);
                gfx.pose().scale(0.75f, 0.75f);
                gfx.text(mc.font, label, 0, 0, 0xD8FFFFFF, true);
                gfx.pose().popMatrix();
            }
        }

        ItemStack stack = findGun(player);
        if (stack == null) return;

        Component text = Component.translatable("item.quantx.matter_gun.matter",
                MatterValues.formatMicro(MatterGunItem.getMatterMicro(stack)),
                MatterValues.formatMicro(MatterGunItem.capacityFor(player)));

        // Справа от хотбара (его половина ~91px), чуть выше полосы здоровья.
        int x = Math.min(w / 2 + 100, w - mc.font.width(text) - 4);
        int y = h - 45;
        gfx.text(mc.font, text, x, y, 0xFF55DDCC, true);
    }

    /** Локальный игрок для клиентских расчётов (тултипы, полоска бака). */
    public static Player clientPlayer() {
        return Minecraft.getInstance().player;
    }

    private static ItemStack findGun(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MatterGunItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MatterGunItem) return off;
        return null;
    }
}
