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
        this.addSlot(new Slot(new MatterPackContainer(owner), 0, -10, 28) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(QuantX.MATTER_PACK.get());
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
}
