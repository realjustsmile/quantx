package ru.justsmile.quantx.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Общие операции сети материи. */
public final class MatterNetwork {

    private MatterNetwork() {
    }

    /** Максимум узлов при обходе — защита от гигантских петель. */
    private static final int SCAN_LIMIT = 256;

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

    /**
     * Сколько материализаторов делят это хранилище: BFS от хранилища
     * по {@link MatterConduit} (волокна, помпы, счётчики), считаем
     * уникальные материализаторы-концы. Минимум 1 — ни с кем не
     * соединённое хранилище делителя не требует.
     * Другие хранилища и прочие блоки — тупики, через них не ходим.
     */
    public static int countConsumers(Level level, BlockPos storagePos) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(storagePos);
        for (Direction dir : Direction.values()) {
            queue.add(storagePos.relative(dir));
        }
        int consumers = 0;
        while (!queue.isEmpty() && visited.size() < SCAN_LIMIT) {
            BlockPos p = queue.poll();
            if (!visited.add(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof MaterializerBlockEntity) {
                consumers++;
            } else if (be instanceof MatterConduit) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = p.relative(dir);
                    if (!visited.contains(next)) queue.add(next);
                }
            }
        }
        return Math.max(1, consumers);
    }
}
