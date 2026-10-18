package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.justsmile.quantx.QuantX;

/**
 * Квантовое волокно — пассивный «провод» сети материи.
 *
 * Собственной ёмкости у волокна нет и само оно материю не переносит —
 * как труба без помпы. Перекачкой занимаются только ускорители
 * (помпы), которые «дотягиваются» через сеть волокон до баков.
 * Волокно лишь светится, когда через него идёт поток (markFlow),
 * и соединяется визуальными рукавами с соседними узлами.
 */
public class QuantumFiberBlockEntity extends BlockEntity implements MatterConduit {

    /**
     * Эталонная скорость сегмента сети: 50 мк/тик = 1000 мк/с =
     * 100 ед. материи в секунду. Помпы качают кратно ей.
     */
    public static final long RATE_MICRO = 50L;

    /** Последний тик, когда через провод шла материя (для анимации). */
    private long lastFlowTick = Long.MIN_VALUE;

    public QuantumFiberBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.QUANTUM_FIBER_BE.get(), pos, state);
    }

    public Direction getFacing() {
        return getBlockState().getValue(QuantumFiberBlock.FACING);
    }

    /** Отметить проход материи через этот провод — включить анимацию. */
    public void markFlow(long gameTime) {
        if (level == null) return;
        lastFlowTick = gameTime;
        BlockState state = getBlockState();
        if (!state.getValue(QuantumFiberBlock.FLOW)) {
            level.setBlock(worldPosition,
                    state.setValue(QuantumFiberBlock.FLOW, true), 3);
        }
    }

    /** Тик нужен только для гашения анимации потока. */
    public static void tick(Level level, BlockPos pos, BlockState state,
                            QuantumFiberBlockEntity be) {
        if (level.isClientSide()) return;
        if (state.getValue(QuantumFiberBlock.FLOW)
                && level.getGameTime() - be.lastFlowTick > 4) {
            level.setBlock(pos, state.setValue(QuantumFiberBlock.FLOW, false), 3);
        }
    }
}
