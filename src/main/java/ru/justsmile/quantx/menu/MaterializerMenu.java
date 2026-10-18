package ru.justsmile.quantx.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.block.MaterializerBlockEntity;

public class MaterializerMenu extends AbstractContainerMenu {

    @Nullable
    private final MaterializerBlockEntity blockEntity;
    private final MatterContainerData matterData;

    // ===== Клиентский конструктор =====
    public MaterializerMenu(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        this(windowId, playerInv, (MaterializerBlockEntity) null);
    }

    // ===== Серверный конструктор =====
    public MaterializerMenu(int windowId, Inventory playerInv,
                            @Nullable MaterializerBlockEntity be) {
        super(QuantX.MATERIALIZER_MENU.get(), windowId);
        this.blockEntity = be;
        this.matterData = new MatterContainerData(be);

        ItemStackHandler inv = (be != null)
                ? be.getInventory()
                : new ItemStackHandler(MaterializerBlockEntity.SLOT_COUNT);

        // Слоты машины по кастомному макету: вход слева, выход справа.
        addSlot(new SlotItemHandler(inv, MaterializerBlockEntity.SLOT_INPUT, 28, 22));
        addSlot(new SlotItemHandler(inv, MaterializerBlockEntity.SLOT_OUTPUT, 84, 22));

        // Призрачный слот (индекс 2): показывает выбранный рецепт.
        // Стек в нём синхронизируется автоматически, но брать/класть нельзя.
        ItemStackHandler display = (be != null)
                ? be.getDisplayInventory()
                : new ItemStackHandler(1);
        addSlot(new SlotItemHandler(display, 0, 142, 22) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(@NotNull Player player) {
                return false;
            }
        });

        // Инвентарь игрока: 3 ряда по 9 (по текстуре ячейки от x=10,y=86).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                        10 + col * 18, 86 + row * 18));
            }
        }

        // Хотбар: 1 ряд по 9 (ячейки от x=10,y=142).
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 10 + col * 18, 142));
        }

        // Регистрируем данные для синхронизации.
        addDataSlots(matterData);
    }

    // ===== Геттеры материи (для Screen) =====

    public long getMatterMicro() {
        return MatterContainerData.packLong(
                matterData.get(MatterContainerData.IDX_MATTER),
                matterData.get(MatterContainerData.IDX_MATTER + 1),
                matterData.get(MatterContainerData.IDX_MATTER + 2),
                matterData.get(MatterContainerData.IDX_MATTER + 3));
    }

    @Nullable
    public MaterializerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    // ===== Данные для Screen (через matterData — работает и на клиенте) =====

    public int getModeOrdinal() {
        return matterData.get(MatterContainerData.IDX_MODE);
    }

    public int getProgress() {
        return matterData.get(MatterContainerData.IDX_PROGRESS);
    }

    public int getMaxProgress() {
        return matterData.get(MatterContainerData.IDX_MAX_PROGRESS);
    }

    public int getKnownRecipeCount() {
        return matterData.get(MatterContainerData.IDX_KNOWN_COUNT);
    }

    /** Стоимость выбранного рецепта в микро-серых (0 — не выбран). */
    public long getSelectedCostMicro() {
        return MatterContainerData.packLong(
                matterData.get(MatterContainerData.IDX_COST),
                matterData.get(MatterContainerData.IDX_COST + 1),
                matterData.get(MatterContainerData.IDX_COST + 2),
                matterData.get(MatterContainerData.IDX_COST + 3));
    }

    /** Полная ёмкость бака (база + соседние хранилища) в микро-единицах. */
    public long getCapacityMicro() {
        return MatterContainerData.packLong(
                matterData.get(MatterContainerData.IDX_CAPACITY),
                matterData.get(MatterContainerData.IDX_CAPACITY + 1),
                matterData.get(MatterContainerData.IDX_CAPACITY + 2),
                matterData.get(MatterContainerData.IDX_CAPACITY + 3));
    }

    /** Машина запущена (Старт/Стоп). */
    public boolean isRunning() {
        return matterData.get(MatterContainerData.IDX_RUNNING) != 0;
    }

    /** Иконка выбранного рецепта из призрачного слота (индекс 2). */
    public ItemStack getRecipeIcon() {
        return slots.get(2).getItem();
    }

    // ===== Кнопки GUI (ванильный механизм clickMenuButton) =====

    // Идентификаторы кнопок — константы, чтобы Screen и Menu их разделяли.
    public static final int BTN_CYCLE_MODE = 0;
    public static final int BTN_NEXT_RECIPE = 1;
    public static final int BTN_PREV_RECIPE = 2;
    public static final int BTN_START_STOP = 3; // запуск/остановка машины

    /**
     * Вызывается на сервере, когда клиент нажимает кнопку контейнера.
     * Клиент отправляет нажатие через gameMode.handleInventoryButtonClick.
     */
    @Override
    public boolean clickMenuButton(@NotNull Player player, int buttonId) {
        if (blockEntity == null) return false;
        switch (buttonId) {
            case BTN_CYCLE_MODE -> blockEntity.cycleMode();
            case BTN_NEXT_RECIPE -> blockEntity.cycleRecipe(1);
            case BTN_PREV_RECIPE -> blockEntity.cycleRecipe(-1);
            case BTN_START_STOP -> blockEntity.toggleRunning();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stackInSlot = slot.getItem();
        ItemStack copy = stackInSlot.copy();

        // 0-1 — слоты машины, 2 — призрачный (игнорируем), 3+ — игрок.
        final int machineSlots = 2;
        final int playerStart = 3;
        final int playerEnd = playerStart + 36;

        if (index == 2) {
            return ItemStack.EMPTY;
        }

        if (index < machineSlots) {
            if (!moveItemStackTo(stackInSlot, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stackInSlot,
                    MaterializerBlockEntity.SLOT_INPUT,
                    MaterializerBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}