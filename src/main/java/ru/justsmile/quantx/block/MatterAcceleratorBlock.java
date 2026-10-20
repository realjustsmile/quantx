package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;

/**
 * Ускоритель материи волоконный — активный сегмент сети с одним
 * входом и одним выходом. Каждый тик принудительно перетягивает
 * материю из узла на стороне входа (оппозит FACING) и выталкивает
 * её в узел на стороне выхода (FACING) с повышенной скоростью.
 * Shift+ПКМ пустой рукой — поменять вход и выход местами.
 */
public class MatterAcceleratorBlock extends Block implements EntityBlock {

    /** Направление ВЫХОДА (сюда выталкивается материя). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** true — в буфере есть материя (текстура потока анимируется). */
    public static final BooleanProperty FLOW = BooleanProperty.create("flow");

    /** Визуальное соединение со стороны d (сосед — узел сети или бак). */
    public static final BooleanProperty CONN_DOWN  = BooleanProperty.create("conn_down");
    public static final BooleanProperty CONN_UP    = BooleanProperty.create("conn_up");
    public static final BooleanProperty CONN_NORTH = BooleanProperty.create("conn_north");
    public static final BooleanProperty CONN_SOUTH = BooleanProperty.create("conn_south");
    public static final BooleanProperty CONN_WEST  = BooleanProperty.create("conn_west");
    public static final BooleanProperty CONN_EAST  = BooleanProperty.create("conn_east");

    /** Центральный узел — чуть крупнее волоконного (6px). */
    private static final VoxelShape NODE = Block.box(5, 5, 5, 11, 11, 11);

    /** Отростки 4px от центра до грани блока, по каждому направлению. */
    private static final VoxelShape ARM_DOWN  = Block.box(6, 0, 6, 10, 5, 10);
    private static final VoxelShape ARM_UP    = Block.box(6, 11, 6, 10, 16, 10);
    private static final VoxelShape ARM_NORTH = Block.box(6, 6, 0, 10, 10, 5);
    private static final VoxelShape ARM_SOUTH = Block.box(6, 6, 11, 10, 10, 16);
    private static final VoxelShape ARM_WEST  = Block.box(0, 6, 6, 5, 10, 10);
    private static final VoxelShape ARM_EAST  = Block.box(11, 6, 6, 16, 10, 10);

    public MatterAcceleratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(withConns(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(FLOW, false), null, null));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLOW, CONN_DOWN, CONN_UP, CONN_NORTH,
                CONN_SOUTH, CONN_WEST, CONN_EAST);
    }

    /** Ставится «от» грани, по которой кликнули (выход уходит в эту сторону). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConns(defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(FLOW, false), context.getLevel(), context.getClickedPos());
    }

    /** Сосед изменился — пересчитать флаг соединения на той стороне. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction dir,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(connProp(dir), connectsTo(level, neighborPos));
    }

    /** Соединяемся с узлом сети или баком материи. */
    private static boolean connectsTo(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MatterConduit || be instanceof MatterTank
                || be instanceof MatterStorageProvider;
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

    /** Shift+ПКМ пустой рукой — поменять вход и выход местами. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getOpposite()), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatterAcceleratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (type != QuantX.MATTER_ACCELERATOR_BE.get()) return null;
        return (lvl, pos, st, be) ->
                MatterAcceleratorBlockEntity.tick(lvl, pos, st, (MatterAcceleratorBlockEntity) be);
    }
}
