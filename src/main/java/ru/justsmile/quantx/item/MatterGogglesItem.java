package ru.justsmile.quantx.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import ru.justsmile.quantx.menu.GogglesContainer;

/**
 * Очки материалиста — линзы, открывающие взгляду потоки материи.
 * Носятся в своём слоте вкладки снаряжения (над ранцем) либо в слоте
 * головы как броня. Пока очки надеты, интерфейс показывает материю:
 * стоимость/выход предметов в тултипах, запас пушки и ранца, выход
 * блока под прицелом и уровень баков.
 *
 * Shift+ПКМ — надеть в свой слот; обычный ПКМ — на голову (Equippable).
 */
public class MatterGogglesItem extends Item {

    public MatterGogglesItem(Properties properties) {
        super(properties);
    }

    /** Shift+ПКМ — надеть очки в выделенный слот вкладки снаряжения. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        var slots = player.inventoryMenu.slots;
        if (slots.size() <= GogglesContainer.SLOT_INDEX) {
            return InteractionResult.PASS;
        }
        Slot slot = slots.get(GogglesContainer.SLOT_INDEX);
        if (!(slot.container instanceof GogglesContainer)
                || !slot.getItem().isEmpty()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        slot.set(held.copyWithCount(1));
        held.shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable("item.quantx.matter_goggles.desc"));
    }
}
