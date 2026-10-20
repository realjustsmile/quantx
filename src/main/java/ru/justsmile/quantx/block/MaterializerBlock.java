package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.matter.MatterValues;

public class MaterializerBlock extends Block implements EntityBlock {

    /** Работает ли машина — синхронизируется клиенту для частиц. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Лицевая сторона — ставится к игроку (как у печки). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Тир машины: 1, 2 или 3. Определяет ёмкость и скорости BE. */
    private final int tier;

    public MaterializerBlock(Properties properties) {
        this(properties, 1);
    }

    public MaterializerBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState()
                .setValue(ACTIVE, false)
                .setValue(FACING, Direction.NORTH));
    }

    public int getTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    /** При установке фронт смотрит на игрока (как печка: opposite от взгляда). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Клиентские частицы: у работающего блока вокруг парит красная пыль.
     * Вызывается только на клиенте, ~раз в тик.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;

        // Редкий тихий гул — работающий материализатор «гудит» как маяк.
        if (random.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.25f, 1.4f, false);
        }

        // ~1 частица в 3 тика — редкое таинственное свечение.
        if (random.nextInt(3) != 0) return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.4;
        double y = pos.getY() + 0.2 + random.nextDouble();
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.4;
        level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.01, 0.0);
    }

    // ===== Компаратор =====

    /**
     * Компаратор читает накопленную материю: 1 уровень сигнала на каждую
     * целую единицу материи, максимум 15. Удобно для автоматики:
     * «останавливать сборку, когда накопилось N единиц».
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level,
                                      BlockPos pos, Direction direction) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MaterializerBlockEntity materializer)) return 0;
        return (int) Math.min(15, materializer.getMatterMicro() / MatterValues.MICRO_PER_UNIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaterializerBlockEntity(pos, state);
    }

    /**
     * Питание редстоуном: пока на блок подаётся сигнал, материализатор
     * работает всегда — как если бы была нажата кнопка «Старт».
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof MaterializerBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
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
            // Запоминаем игрока — ачивки по триггерам уходят ему.
            materializer.setLastUser(player.getUUID());
            // Позиция блока уезжает в клиентское меню — экран читает
            // список изученных рецептов из синхронизированного BE.
            if (player instanceof net.minecraftforge.common.extensions.IForgeServerPlayer fsp) {
                fsp.openMenu(materializer, pos);
            } else {
                player.openMenu(materializer);
            }
        }
        return InteractionResult.SUCCESS;
    }
}