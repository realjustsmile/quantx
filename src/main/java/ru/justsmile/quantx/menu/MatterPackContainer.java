package ru.justsmile.quantx.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Однослотовый контейнер ранца материи, привязанный к игроку.
 *
 * Слот добавляется миксином в {@link InventoryMenu} (индекс 46,
 * сразу после ванильных 0-45). Содержимое хранится в persistentData
 * игрока (Forge сохраняет её в NBT и переживает смерть), а на клиент
 * попадает обычной синхронизацией слотов меню.
 */
public class MatterPackContainer extends SimpleContainer {

    private static final String NBT_KEY = "quantx:matter_pack";

    /** Индекс слота ранца в {@link InventoryMenu#slots} (сразу после SHIELD_SLOT=45). */
    public static final int SLOT_INDEX = InventoryMenu.SHIELD_SLOT + 1;

    /**
     * Свёрнута ли клиентская панель ранца. Пишется только на клиенте
     * (MatterPackSlotRender); на сервере всегда false и ни на что не влияет.
     */
    public static boolean packSlotCollapsed = false;

    private final Player owner;

    /**
     * Содержимое ещё не читалось из NBT. InventoryMenu создаётся в
     * конструкторе Player — раньше, чем persistentData заполняется из
     * сохранения, поэтому читаем лениво при первом обращении.
     */
    private boolean loaded;

    public MatterPackContainer(Player owner) {
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

    // ===== Доступ к ранцу с любой стороны (слот синхронизирован) =====

    public static ItemStack getPackStack(Player player) {
        var slots = player.inventoryMenu.slots;
        return slots.size() > SLOT_INDEX ? slots.get(SLOT_INDEX).getItem() : ItemStack.EMPTY;
    }

    public static boolean isWearingPack(Player player) {
        return getPackStack(player).getItem()
                instanceof ru.justsmile.quantx.item.MatterPackItem;
    }
}
