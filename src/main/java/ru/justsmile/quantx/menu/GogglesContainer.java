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

    public GogglesContainer(Player owner) {
        super(1);
        this.owner = owner;
        ItemStack saved = read(owner);
        if (!saved.isEmpty()) {
            setItem(0, saved);
        }
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
