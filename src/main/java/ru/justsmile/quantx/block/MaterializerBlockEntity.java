package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.justsmile.quantx.QuantX;

public class MaterializerBlockEntity extends BlockEntity {

    public MaterializerBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATERIALIZER_BE.get(), pos, state);
    }
}