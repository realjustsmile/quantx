package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.justsmile.quantx.QuantX;

/**
 * Хранилище материи. Само материю НЕ хранит — это чистый расширитель:
 * будучи достижимым из материализатора (напрямую или через волокна),
 * добавляет тому свою ёмкость. Вся материя физически лежит в баке
 * материализатора. Одно хранилище, подключённое к нескольким
 * материализаторам, делит добавку поровну.
 * Пассивен: не тикает, данных не хранит.
 */
public class MatterStorageBlockEntity extends BlockEntity implements MatterStorageProvider {

    /** Добавка к ёмкости материализатора по тирам (в микро-единицах):
     *  I — 1 000 ед., II — 10 000 ед., III — 100 000 ед. */
    private static final long[] CAPACITY_BONUS_MICRO = {
            1_000L * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT,
            10_000L * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT,
            100_000L * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT};

    /** Тир этого блока (1..3) — читается из BlockState. */
    private int tier() {
        return (getBlockState().getBlock() instanceof MatterStorageBlock b)
                ? Math.min(3, Math.max(1, b.getTier()))
                : 1;
    }

    public MatterStorageBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATTER_STORAGE_BE.get(), pos, state);
    }

    @Override
    public long getMatterCapacityBonusMicro() {
        return CAPACITY_BONUS_MICRO[tier() - 1];
    }
}
