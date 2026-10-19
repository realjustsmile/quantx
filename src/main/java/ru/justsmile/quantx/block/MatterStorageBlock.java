package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.matter.MatterValues;

/**
 * Хранилище материи: пассивный бак. Увеличивает ёмкость материализатора,
 * будучи подключённым напрямую или через сеть волокон/хабов.
 * Материя перекачивается квантовыми волокнами.
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

    /** ПКМ — показать заполненность бака. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MatterTank tank) {
            player.sendSystemMessage(Component.translatable(
                    "block.quantx.matter_storage.matter",
                    MatterValues.formatMicroPlain(tank.getMatterMicro()),
                    MatterValues.formatMicroPlain(tank.getCapacityMicro())));
        }
        return InteractionResult.SUCCESS;
    }

    /** При разрушении игроком материя разливается по соседним узлам сети. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof MatterStorageBlockEntity storage) {
            MatterNetwork.spill(level, pos, storage.getMatterMicro());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
