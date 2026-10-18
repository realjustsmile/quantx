package ru.justsmile.quantx.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import ru.justsmile.quantx.QuantX;

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

    private final Player owner;

    public MatterPackContainer(Player owner) {
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

    // ===== Доступ к ранцу с любой стороны (слот синхронизирован) =====

    public static ItemStack getPackStack(Player player) {
        var slots = player.inventoryMenu.slots;
        return slots.size() > SLOT_INDEX ? slots.get(SLOT_INDEX).getItem() : ItemStack.EMPTY;
    }

    public static boolean isWearingPack(Player player) {
        return getPackStack(player).is(QuantX.MATTER_PACK.get());
    }
}
