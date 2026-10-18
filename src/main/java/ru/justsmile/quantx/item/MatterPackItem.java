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
import ru.justsmile.quantx.matter.MatterValues;
import ru.justsmile.quantx.menu.MatterPackContainer;

/**
 * Ранец материи — носится в отдельном слоте инвентаря (слева от нагрудника).
 * Увеличивает вместимость бака материальной пушки на 40 000 единиц.
 * Shift+ПКМ ранцем в руке — надеть в свой слот.
 */
public class MatterPackItem extends Item {

    /** Бонус к вместимости пушки (в микро): 40 000 единиц. */
    public static final long BONUS_MICRO = 400_000L;

    public MatterPackItem(Properties properties) {
        super(properties);
    }

    /** Shift+ПКМ — надеть ранец в выделенный слот инвентаря. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        var slots = player.inventoryMenu.slots;
        if (slots.size() <= MatterPackContainer.SLOT_INDEX) {
            return InteractionResult.PASS;
        }
        Slot slot = slots.get(MatterPackContainer.SLOT_INDEX);
        if (!(slot.container instanceof MatterPackContainer)
                || !slot.getItem().isEmpty()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        slot.set(held.copyWithCount(1));
        held.shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable("item.quantx.matter_pack.desc"));
        builder.accept(Component.translatable("item.quantx.matter_pack.bonus",
                MatterValues.formatMicro(BONUS_MICRO)));
    }
}
