package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;

public class MaterializerBlock extends Block implements EntityBlock {

    /** Работает ли машина — синхронизируется клиенту для частиц. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MaterializerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /**
     * Клиентские частицы: у работающего блока вокруг парит красная пыль.
     * Вызывается только на клиенте, ~раз в тик.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        // ~1 частица в 3 тика — редкое таинственное свечение.
        if (random.nextInt(3) != 0) return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.4;
        double y = pos.getY() + 0.2 + random.nextDouble();
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.4;
        level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.01, 0.0);
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