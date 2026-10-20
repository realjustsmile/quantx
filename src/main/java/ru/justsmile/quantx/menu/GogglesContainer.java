package ru.justsmile.quantx.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import ru.justsmile.quantx.item.MatterGogglesItem;

/**
 * Однослотовый контейнер очков материалиста, привязанный к игроку.
 *
 * Слот добавляется миксином в {@link InventoryMenu} (индекс 47, сразу
 * после слота ранца). Содержимое хранится в persistentData игрока,
 * на клиент попадает обычной синхронизацией слотов меню.
 */
public class GogglesContainer extends SimpleContainer {

    private static final String NBT_KEY = "quantx:goggles";

    /** Индекс слота очков в {@link InventoryMenu#slots} (после ранца). */
    public static final int SLOT_INDEX = MatterPackContainer.SLOT_INDEX + 1;

    private final Player owner;

    /**
     * Содержимое ещё не читалось из NBT. InventoryMenu создаётся в
     * конструкторе Player — раньше, чем persistentData заполняется из
     * сохранения, поэтому читаем лениво при первом обращении.
     */
    private boolean loaded;

    public GogglesContainer(Player owner) {
        super(1);
        this.owner = owner;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        ItemStack saved = read(owner);
        if (!saved.isEmpty()) {
            super.setItem(0, saved);
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        ensureLoaded();
        return super.getItem(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ensureLoaded();
        super.setItem(slot, stack);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ensureLoaded();
        return super.removeItem(slot, amount);
    }

    @Override
    public boolean isEmpty() {
        ensureLoaded();
        return super.isEmpty();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        write(owner, getItem(0));
    }

    // ===== Чтение/запись NBT =====

    private static ItemStack read(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.read(NBT_KEY, ItemStack.OPTIONAL_CODEC,
                player.registryAccess().createSerializationContext(NbtOps.INSTANCE))
                .orElse(ItemStack.EMPTY);
    }

    private static void write(Player player, ItemStack stack) {
        CompoundTag data = player.getPersistentData();
        if (stack.isEmpty()) {
            data.remove(NBT_KEY);
        } else {
            data.store(NBT_KEY, ItemStack.OPTIONAL_CODEC,
                    player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack);
        }
    }

    // ===== Доступ =====

    public static ItemStack getGogglesStack(Player player) {
        var slots = player.inventoryMenu.slots;
        return slots.size() > SLOT_INDEX ? slots.get(SLOT_INDEX).getItem() : ItemStack.EMPTY;
    }

    /**
     * Очки считаются надетыми и в своём слоте вкладки, и в слоте
     * головы (Equippable): гейтинг показа материи одинаков в обоих.
     */
    public static boolean isWearingGoggles(Player player) {
        if (player == null) return false;
        if (getGogglesStack(player).getItem() instanceof MatterGogglesItem) {
            return true;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem()
                instanceof MatterGogglesItem;
    }
}
