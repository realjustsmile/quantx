package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;

public class MaterializerBlock extends Block implements EntityBlock {

    public MaterializerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaterializerBlockEntity(pos, state);
    }

    /**
     * Тикер: вызывает MaterializerBlockEntity.tick(...) каждый игровой тик.
     * Возвращаем тикер только для нашего типа BlockEntity.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (type != QuantX.MATERIALIZER_BE.get()) return null;
        return (lvl, pos, st, be) ->
                MaterializerBlockEntity.tick(lvl, pos, st, (MaterializerBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MaterializerBlockEntity materializer) {
            // В 26.3 openMenu принимает только один аргумент.
            player.openMenu(materializer);
        }
        return InteractionResult.SUCCESS;
    }
}