package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Волоконный клапан материи — помпа, которая качает только когда
 * открыта redstone-сигналом (по умолчанию: сигнал → открыт).
 * ПКМ пустой рукой — инвертировать режим (качать БЕЗ сигнала).
 * Shift+ПКМ пустой рукой — поменять вход и выход местами.
 * Закрытый клапан — тупик для соседних помп: сквозь него поток не идёт.
 */
public class MatterValveBlock extends MatterAcceleratorBlock {

    /** Есть ли redstone-сигнал у блока. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** true — инвертирован: качает БЕЗ сигнала, стоит при сигнале. */
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

    public MatterValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(POWERED, false)
                .setValue(INVERTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, INVERTED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(INVERTED, false);
    }

    /** Синхронизируем POWERED с реальным сигналом у блока. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, Orientation orientation,
                                   boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 3);
        }
    }

    /**
     * ПКМ пустой рукой — инвертировать redstone-режим.
     * Shift+ПКМ — как у ускорителя: разворот входа/выхода.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean inverted = !state.getValue(INVERTED);
        level.setBlock(pos, state.setValue(INVERTED, inverted), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS, 0.4f, inverted ? 1.0f : 1.4f);
        return InteractionResult.SUCCESS;
    }
}
