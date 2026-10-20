package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Хранилище материи: пассивный расширитель ёмкости. Само материю
 * не хранит — будучи подключённым к материализатору напрямую или
 * через сеть волокон/хабов, увеличивает его счётную ёмкость.
 * ПКМ не обрабатывает — взаимодействий нет.
 */
public class MatterStorageBlock extends Block implements EntityBlock {

    /** Тир хранилища: 1, 2 или 3. Определяет ёмкость и скорость обмена. */
    private final int tier;

    public MatterStorageBlock(Properties properties) {
        this(properties, 1);
    }

    public MatterStorageBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatterStorageBlockEntity(pos, state);
    }

}
