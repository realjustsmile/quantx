package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.justsmile.quantx.QuantX;

/**
 * Бак хранилища. Ёмкость по тирам: I — 1 000 ед., II — 10 000 ед.,
 * III — 100 000 ед. материи. Скорость обмена с сетью: 100 / 1 000 /
 * 10 000 ед. в секунду соответственно.
 * Подключённое к материализатору хранилище увеличивает его счётную
 * ёмкость на свою величину, но материя считается per-block
 * и перекачивается сетью.
 * Пассивен: не тикает, материю вливают/выкачивают волокна и материализатор.
 */
public class MatterStorageBlockEntity extends BlockEntity implements MatterStorageProvider {

    /** Ёмкость хранилища по тирам (в микро-единицах). */
    private static final long[] CAPACITY_MICRO = {
            1_000L * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT,
            10_000L * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT,
            100_000L * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT};

    /** Скорость обмена с сетью по тирам (мк/тик): 100 / 1 000 / 10 000 ед/с. */
    private static final long[] TRANSFER_RATE_MICRO = {50, 500, 5_000};

    private long matterMicro = 0L;

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
    public long getMatterMicro() {
        return matterMicro;
    }

    @Override
    public long getCapacityMicro() {
        return CAPACITY_MICRO[tier() - 1];
    }

    @Override
    public long getMatterCapacityBonusMicro() {
        return CAPACITY_MICRO[tier() - 1];
    }

    @Override
    public long getTransferRateMicro() {
        return TRANSFER_RATE_MICRO[tier() - 1];
    }

    @Override
    public long addMatter(long micro) {
        if (micro <= 0) return 0L;
        long accepted = Math.min(micro, getCapacityMicro() - matterMicro);
        if (accepted <= 0) return 0L;
        matterMicro += accepted;
        setChanged();
        syncToClient();
        return accepted;
    }

    @Override
    public long takeMatter(long micro) {
        if (micro <= 0) return 0L;
        long taken = Math.min(micro, matterMicro);
        if (taken <= 0) return 0L;
        matterMicro -= taken;
        setChanged();
        syncToClient();
        return taken;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("MatterMicro", matterMicro);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        matterMicro = input.getLongOr("MatterMicro", 0L);
    }

    // ===== Синхронизация на клиент (HUD при наведении) =====

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
