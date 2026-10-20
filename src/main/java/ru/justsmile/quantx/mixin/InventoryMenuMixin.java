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
import ru.justsmile.quantx.menu.GogglesContainer;
import ru.justsmile.quantx.menu.MatterPackContainer;

/**
 * Добавляет слоты снаряжения мода в ванильное меню инвентаря —
 * панель слева от рамки: очки сверху (-29, 4), ранец под ними
 * (-29, 25). Верх панели на одном уровне с верхом интерфейса.
 * Работает на обеих сторонах: ванильная синхронизация слотов
 * сама передаёт содержимое на клиент.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    private static final Identifier EMPTY_PACK_ICON =
            Identifier.fromNamespaceAndPath(QuantX.MODID, "container/slot/matter_pack");
    private static final Identifier EMPTY_GOGGLES_ICON =
            Identifier.fromNamespaceAndPath(QuantX.MODID, "container/slot/matter_goggles");

    protected InventoryMenuMixin(@Nullable MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void quantx$addMatterPackSlot(Inventory inventory, boolean active,
                                          Player owner, CallbackInfo ci) {
        this.addSlot(new Slot(new MatterPackContainer(owner), 0, -29, 25) {
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
                return EMPTY_PACK_ICON;
            }
        });
        this.addSlot(new Slot(new GogglesContainer(owner), 0, -29, 4) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem()
                        instanceof ru.justsmile.quantx.item.MatterGogglesItem;
            }

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
                return EMPTY_GOGGLES_ICON;
            }
        });
    }

    /**
     * Shift-клик по ранцу/очкам в инвентаре — автоматически надевает
     * предмет в свой слот, как ванильная броня. Клики по самим слотам
     * вкладки обрабатывает ванильная ветка (индексы попадают в общий
     * диапазон).
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void quantx$packShiftClick(Player player, int index,
                                       CallbackInfoReturnable<ItemStack> cir) {
        if (index == MatterPackContainer.SLOT_INDEX
                || index == GogglesContainer.SLOT_INDEX
                || index < 0 || index >= this.slots.size()
                || this.slots.size() <= GogglesContainer.SLOT_INDEX) {
            return;
        }
        ItemStack stack = this.slots.get(index).getItem();
        if (stack.isEmpty()) return;

        if (stack.getItem()
                    instanceof ru.justsmile.quantx.item.MatterPackItem
                && !this.slots.get(MatterPackContainer.SLOT_INDEX).hasItem()
                && this.moveItemStackTo(stack, MatterPackContainer.SLOT_INDEX,
                        MatterPackContainer.SLOT_INDEX + 1, false)) {
            cir.setReturnValue(ItemStack.EMPTY);
        } else if (stack.getItem()
                    instanceof ru.justsmile.quantx.item.MatterGogglesItem
                && !this.slots.get(GogglesContainer.SLOT_INDEX).hasItem()
                && this.moveItemStackTo(stack, GogglesContainer.SLOT_INDEX,
                        GogglesContainer.SLOT_INDEX + 1, false)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
