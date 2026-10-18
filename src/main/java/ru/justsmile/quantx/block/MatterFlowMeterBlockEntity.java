package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.justsmile.quantx.QuantX;

/**
 * Счётчик потока — узел сети, считающий объём материи, прошедшей
 * сквозь него за последний активный тик. Компаратор выдаёт
 * 0–15: 15 соответствует полной пропускной способности одной
 * помпы (RATE_MICRO). Значение держится ~10 тиков после
 * последнего потока, затем падает в ноль.
 */
public class MatterFlowMeterBlockEntity extends BlockEntity implements MatterConduit {

    /** Сколько тиков после последнего потока сигнал ещё держится. */
    private static final int HOLD_TICKS = 10;

    /** Последний тик с потоком. */
    private long lastFlowTick = Long.MIN_VALUE;

    /** Сумма материи, прошедшей в последний активный тик. */
    private long lastAmount;

    /** Последний «излучённый» уровень сигнала — чтобы не дёргать соседей зря. */
    private int emittedSignal = -1;

    public MatterFlowMeterBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATTER_FLOW_METER_BE.get(), pos, state);
    }

    /**
     * Отметить проход amount материи через счётчик. Несколько
     * помп/веток в один тик складываются в общий поток.
     */
    public void markFlow(long gameTime, long amount) {
        if (level == null) return;
        if (lastFlowTick != gameTime) {
            lastFlowTick = gameTime;
            lastAmount = amount;
        } else {
            lastAmount += amount;
        }
        BlockState state = getBlockState();
        if (!state.getValue(MatterFlowMeterBlock.FLOW)) {
            level.setBlock(worldPosition,
                    state.setValue(MatterFlowMeterBlock.FLOW, true), 3);
        }
    }

    /** Компараторный сигнал: доля от полной скорости помпы. */
    public int signal(long gameTime) {
        if (gameTime - lastFlowTick > HOLD_TICKS) return 0;
        return Math.min(15, Math.round(
                lastAmount * 15f / MatterAcceleratorBlockEntity.RATE_MICRO));
    }

    /** Тик: гашение анимации потока и обновление компаратора. */
    public static void tick(Level level, BlockPos pos, BlockState state,
                            MatterFlowMeterBlockEntity be) {
        if (level.isClientSide()) return;

        if (state.getValue(MatterFlowMeterBlock.FLOW)
                && level.getGameTime() - be.lastFlowTick > 4) {
            level.setBlock(pos,
                    state.setValue(MatterFlowMeterBlock.FLOW, false), 3);
        }

        int signal = be.signal(level.getGameTime());
        if (signal != be.emittedSignal) {
            be.emittedSignal = signal;
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }
}
