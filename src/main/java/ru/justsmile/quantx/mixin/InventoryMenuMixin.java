package ru.justsmile.quantx.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.menu.MatterPackContainer;

/**
 * Добавляет слот ранца материи в ванильное меню инвентаря —
 * слева от слота нагрудника (броня на x=8, нагрудник y=26 → x=-10, y=26).
 * Работает на обеих сторонах: ванильная синхронизация слотов
 * сама передаёт содержимое на клиент.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    private static final Identifier EMPTY_ICON =
            Identifier.fromNamespaceAndPath(QuantX.MODID, "container/slot/matter_pack");

    protected InventoryMenuMixin(@Nullable MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void quantx$addMatterPackSlot(Inventory inventory, boolean active,
                                          Player owner, CallbackInfo ci) {
        this.addSlot(new Slot(new MatterPackContainer(owner), 0, -29, 19) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem()
                        instanceof ru.justsmile.quantx.item.MatterPackItem;
            }

            /**
             * Свёрнутая панель = неактивный слот: не рисуется
             * и не кликается (в т.ч. его SlotWrapper в креативе).
             */
            @Override
            public boolean isActive() {
                return !MatterPackContainer.packSlotCollapsed;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public Identifier getNoItemIcon() {
                return EMPTY_ICON;
            }
        });
    }

    /**
     * Shift-клик по ранцу в инвентаре — автоматически надевает его в слот
     * ранца, как ванильная броня. Слот из быстрого перемещения ранца
     * обрабатывает ванильная ветка (index 46 попадает в общий диапазон).
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void quantx$packShiftClick(Player player, int index,
                                       CallbackInfoReturnable<ItemStack> cir) {
        if (index == MatterPackContainer.SLOT_INDEX
                || index < 0 || index >= this.slots.size()
                || this.slots.size() <= MatterPackContainer.SLOT_INDEX) {
            return;
        }
        ItemStack stack = this.slots.get(index).getItem();
        Slot packSlot = this.slots.get(MatterPackContainer.SLOT_INDEX);
        if (!stack.isEmpty()
                && stack.getItem()
                    instanceof ru.justsmile.quantx.item.MatterPackItem
                && !packSlot.hasItem()
                && this.moveItemStackTo(stack, MatterPackContainer.SLOT_INDEX,
                        MatterPackContainer.SLOT_INDEX + 1, false)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
