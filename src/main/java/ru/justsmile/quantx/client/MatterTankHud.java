package ru.justsmile.quantx.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.justsmile.quantx.block.MatterTank;
import ru.justsmile.quantx.matter.MatterValues;
import ru.justsmile.quantx.menu.GogglesContainer;

/**
 * HUD при наведении на бак материи (материализатор, хранилище):
 * показывает «Материя: x / y» под перекрестием без нажатия ПКМ.
 * Значения приходят с сервера через синк BE (getUpdateTag/пакеты).
 */
public final class MatterTankHud {

    private MatterTankHud() {}

    private static final double REACH = 5.0;

    /** ForgeLayer: рисуем уровень материи блока под прицелом. */
    public static void extract(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;

        // Без очков уровень баков не виден — HUD ванилен.
        if (!GogglesContainer.isWearingGoggles(player)) return;

        HitResult hit = player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);

        // Только баки имеют ёмкость — провода (волокна, ускорители)
        // материю не хранят и в HUD не отображаются.
        if (!(be instanceof MatterTank tank)) return;
        long micro = tank.getMatterMicro();
        long capacity = tank.getCapacityMicro();

        Component text = Component.translatable(
                "block.quantx.matter_storage.matter",
                MatterValues.formatMicroPlain(micro),
                MatterValues.formatMicroPlain(capacity));

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 - mc.font.width(text) / 2;
        int y = h / 2 + 14;
        gfx.text(mc.font, text, x, y, 0xFF55DDCC, true);
    }
}
