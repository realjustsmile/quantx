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
 * Бак хранилища: 10 000 мк (1 000 ед. материи).
 * Подключённое к материализатору хранилище увеличивает его счётную
 * ёмкость на свою величину (400 + 1000 на каждое), но материя
 * считается отдельно per-block и перекачивается сетью.
 * Пассивен: не тикает, материю вливают/выкачивают волокна и материализатор.
 */
public class MatterStorageBlockEntity extends BlockEntity implements MatterStorageProvider {

    /** Ёмкость хранилища = 1 000 ед. материи. */
    public static final long CAPACITY_MICRO = 1_000L
            * ru.justsmile.quantx.matter.MatterValues.MICRO_PER_UNIT;

    private long matterMicro = 0L;

    public MatterStorageBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATTER_STORAGE_BE.get(), pos, state);
    }

    @Override
    public long getMatterMicro() {
        return matterMicro;
    }

    @Override
    public long getCapacityMicro() {
        return CAPACITY_MICRO;
    }

    @Override
    public long getMatterCapacityBonusMicro() {
        return CAPACITY_MICRO;
    }

    @Override
    public long addMatter(long micro) {
        if (micro <= 0) return 0L;
        long accepted = Math.min(micro, CAPACITY_MICRO - matterMicro);
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
