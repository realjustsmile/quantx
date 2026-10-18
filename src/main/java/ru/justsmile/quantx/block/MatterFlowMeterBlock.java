package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;

/**
 * Счётчик потока материи — пассивный сегмент сети: проводит поток
 * как волокно, но измеряет проходящий объём и выдаёт компараторный
 * сигнал 0–15 пропорционально мгновенной скорости (15 ≈ полный
 * поток одной помпы). Сам материю не переносит — как и волокно,
 * работает только когда помпа тянет поток через него.
 */
public class MatterFlowMeterBlock extends Block implements EntityBlock {

    /** true — по счётчику идёт поток (текстура ядра анимируется). */
    public static final BooleanProperty FLOW = BooleanProperty.create("flow");

    /** Визуальное соединение со стороны d (сосед — узел сети или бак). */
    public static final BooleanProperty CONN_DOWN  = BooleanProperty.create("conn_down");
    public static final BooleanProperty CONN_UP    = BooleanProperty.create("conn_up");
    public static final BooleanProperty CONN_NORTH = BooleanProperty.create("conn_north");
    public static final BooleanProperty CONN_SOUTH = BooleanProperty.create("conn_south");
    public static final BooleanProperty CONN_WEST  = BooleanProperty.create("conn_west");
    public static final BooleanProperty CONN_EAST  = BooleanProperty.create("conn_east");

    /** Центральный узел — как у помпы (6px). */
    private static final VoxelShape NODE = Block.box(5, 5, 5, 11, 11, 11);

    /** Отростки 4px от центра до грани блока, по каждому направлению. */
    private static final VoxelShape ARM_DOWN  = Block.box(6, 0, 6, 10, 5, 10);
    private static final VoxelShape ARM_UP    = Block.box(6, 11, 6, 10, 16, 10);
    private static final VoxelShape ARM_NORTH = Block.box(6, 6, 0, 10, 10, 5);
    private static final VoxelShape ARM_SOUTH = Block.box(6, 6, 11, 10, 10, 16);
    private static final VoxelShape ARM_WEST  = Block.box(0, 6, 6, 5, 10, 10);
    private static final VoxelShape ARM_EAST  = Block.box(11, 6, 6, 16, 10, 10);

    public MatterFlowMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(withConns(defaultBlockState()
                .setValue(FLOW, false), null, null));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLOW, CONN_DOWN, CONN_UP, CONN_NORTH,
                CONN_SOUTH, CONN_WEST, CONN_EAST);
    }

    /** Сосед изменился — пересчитать флаг соединения на той стороне. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction dir,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(connProp(dir), connectsTo(level, neighborPos));
    }

    /**
     * Счётчик соединяется ТОЛЬКО с узлами сети (волокна, помпы,
     * другие счётчики). Бак вплотную — не подключение: нет
     * визуального рукава, нет передачи материи.
     */
    private static boolean connectsTo(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MatterConduit;
    }

    private static BooleanProperty connProp(Direction dir) {
        return switch (dir) {
            case DOWN -> CONN_DOWN;
            case UP -> CONN_UP;
            case NORTH -> CONN_NORTH;
            case SOUTH -> CONN_SOUTH;
            case WEST -> CONN_WEST;
            case EAST -> CONN_EAST;
        };
    }

    /** Выставить все conn-флаги по соседям (level == null — все false). */
    private static BlockState withConns(BlockState state, @Nullable BlockGetter level,
                                        @Nullable BlockPos pos) {
        for (Direction dir : Direction.values()) {
            boolean conn = level != null && pos != null
                    && connectsTo(level, pos.relative(dir));
            state = state.setValue(connProp(dir), conn);
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        VoxelShape shape = NODE;
        if (state.getValue(CONN_DOWN))  shape = Shapes.or(shape, ARM_DOWN);
        if (state.getValue(CONN_UP))    shape = Shapes.or(shape, ARM_UP);
        if (state.getValue(CONN_NORTH)) shape = Shapes.or(shape, ARM_NORTH);
        if (state.getValue(CONN_SOUTH)) shape = Shapes.or(shape, ARM_SOUTH);
        if (state.getValue(CONN_WEST))  shape = Shapes.or(shape, ARM_WEST);
        if (state.getValue(CONN_EAST))  shape = Shapes.or(shape, ARM_EAST);
        return shape;
    }

    // ===== Redstone =====

    /** Счётчик — источник сигнала: к нему тянется пыль, повторители. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Слабый сигнал: пыль и лампы рядом получают силу потока. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level,
                            BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof MatterFlowMeterBlockEntity meter
                && meter.getLevel() != null) {
            return meter.signal(meter.getLevel().getGameTime());
        }
        return 0;
    }

    /** Сильный сигнал: повторители и блоки, питаемые вплотную. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level,
                                  BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    // ===== Компаратор =====

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level,
                                      BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof MatterFlowMeterBlockEntity meter) {
            return meter.signal(level.getGameTime());
        }
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatterFlowMeterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (type != QuantX.MATTER_FLOW_METER_BE.get()) return null;
        return (lvl, pos, st, be) ->
                MatterFlowMeterBlockEntity.tick(lvl, pos, st, (MatterFlowMeterBlockEntity) be);
    }
}
