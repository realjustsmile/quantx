package ru.justsmile.quantx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.matter.MatterTier;

/**
 * Блок-материализатор. Хранит материю как числа (микро-единицы)
 * и инвентарь из 2 слотов (вход/выход).
 */
public class MaterializerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    // ===== Инвентарь =====
    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // В выходной слот нельзя класть ничего вручную
            return slot != SLOT_OUTPUT;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    };

    // ===== Материя (микро-единицы) =====
    private long grayMicro = 0L;
    private long blueMicro = 0L;
    private long goldMicro = 0L;

    public MaterializerBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATERIALIZER_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    // ===== Геттеры материи =====

    public long getGrayMicro() { return grayMicro; }
    public long getBlueMicro() { return blueMicro; }
    public long getGoldMicro() { return goldMicro; }

    public long getMatterMicro(MatterTier tier) {
        return switch (tier) {
            case GRAY -> grayMicro;
            case BLUE -> blueMicro;
            case GOLD -> goldMicro;
        };
    }

    public long getTotalGrayEquivalent() {
        return grayMicro
                + blueMicro * MatterTier.BLUE.getWeightInGray()
                + goldMicro * MatterTier.GOLD.getWeightInGray();
    }

    // ===== Мутаторы материи =====

    public void addMatter(MatterTier tier, long micro) {
        if (micro <= 0) return;
        switch (tier) {
            case GRAY -> grayMicro += micro;
            case BLUE -> blueMicro += micro;
            case GOLD -> goldMicro += micro;
        }
        setChanged();
    }

    private void setMatterMicro(MatterTier tier, long micro) {
        long safe = Math.max(0, micro);
        switch (tier) {
            case GRAY -> grayMicro = safe;
            case BLUE -> blueMicro = safe;
            case GOLD -> goldMicro = safe;
        }
        setChanged();
    }

    public boolean consumeGrayEquivalent(long costMicro) {
        if (costMicro <= 0) return true;
        if (getTotalGrayEquivalent() < costMicro) return false;

        long remaining = costMicro;

        long fromGray = Math.min(grayMicro, remaining);
        grayMicro -= fromGray;
        remaining -= fromGray;

        if (remaining > 0) {
            long blueWeight = MatterTier.BLUE.getWeightInGray();
            long blueNeeded = (remaining + blueWeight - 1) / blueWeight;
            long blueToUse = Math.min(blueMicro, blueNeeded);
            long grayFromBlue = blueToUse * blueWeight;
            blueMicro -= blueToUse;
            if (grayFromBlue > remaining) {
                grayMicro += (grayFromBlue - remaining);
            }
            remaining = 0;
        }

        if (remaining > 0) {
            long goldWeight = MatterTier.GOLD.getWeightInGray();
            long goldNeeded = (remaining + goldWeight - 1) / goldWeight;
            long goldToUse = Math.min(goldMicro, goldNeeded);
            long grayFromGold = goldToUse * goldWeight;
            goldMicro -= goldToUse;
            if (grayFromGold > remaining) {
                grayMicro += (grayFromGold - remaining);
            }
        }

        setChanged();
        return true;
    }

    public boolean convertUp(MatterTier from) {
        MatterTier to = from.nextTier();
        if (to == null) return false;

        long ratio = to.getWeightInGray() / from.getWeightInGray();
        long costMicro = ratio * MatterTier.MICRO_PER_UNIT;
        long available = getMatterMicro(from);

        if (available < costMicro) return false;

        setMatterMicro(from, available - costMicro);
        addMatter(to, MatterTier.MICRO_PER_UNIT);
        return true;
    }

    public boolean convertDown(MatterTier from) {
        MatterTier to = from.previousTier();
        if (to == null) return false;

        long available = getMatterMicro(from);
        if (available < MatterTier.MICRO_PER_UNIT) return false;

        long ratio = from.getWeightInGray() / to.getWeightInGray();
        long resultMicro = (ratio * MatterTier.MICRO_PER_UNIT) / 2;

        setMatterMicro(from, available - MatterTier.MICRO_PER_UNIT);
        addMatter(to, resultMicro);
        return true;
    }

    // ===== Сохранение =====

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        output.putLong("GrayMicro", grayMicro);
        output.putLong("BlueMicro", blueMicro);
        output.putLong("GoldMicro", goldMicro);

        // Инвентарь сохраняем как CompoundTag через HolderLookup.Provider.
        HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
        if (registries != null) {
            CompoundTag invTag = inventory.serializeNBT(registries);
            output.store("Inventory", CompoundTag.CODEC, invTag);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        grayMicro = input.getLongOr("GrayMicro", 0L);
        blueMicro = input.getLongOr("BlueMicro", 0L);
        goldMicro = input.getLongOr("GoldMicro", 0L);

        HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
        if (registries != null) {
            input.read("Inventory", CompoundTag.CODEC).ifPresent(invTag ->
                    inventory.deserializeNBT(registries, invTag));
        }
    }

    // ===== MenuProvider (заглушка — GUI добавим на подшаге 5б) =====

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantx.materializer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        // Пока возвращаем null — на подшаге 5б создадим MaterializerMenu.
        return null;
    }
}