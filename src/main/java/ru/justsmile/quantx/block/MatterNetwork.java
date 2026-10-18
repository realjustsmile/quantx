package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Общие операции сети материи. */
public final class MatterNetwork {

    private MatterNetwork() {
    }

    /**
     * «Разлить» материю разрушаемого бака в соседние баки.
     * Провода (волокна, ускорители) ёмкости не имеют — влить в них
     * нечего, материя уходит только в баки по соседству.
     * Возвращает сколько удалось передать.
     */
    public static long spill(Level level, BlockPos pos, long micro) {
        if (micro <= 0) return 0L;
        long remaining = micro;
        for (Direction dir : Direction.values()) {
            if (remaining <= 0) break;
            if (level.getBlockEntity(pos.relative(dir)) instanceof MatterTank tank) {
                remaining -= tank.addMatter(remaining);
            }
        }
        return micro - remaining;
    }
}
