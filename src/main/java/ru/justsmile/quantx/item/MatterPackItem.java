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
 * Материальный ранец — носится в отдельном слоте инвентаря
 * (слева от нагрудника). Увеличивает вместимость бака материальной
 * пушки: I — на 40 000 ед., II — на 400 000 ед., III — на 4 000 000 ед.
 * Shift+ПКМ ранцем в руке — надеть в свой слот.
 */
public class MatterPackItem extends Item {

    /** Бонус к вместимости пушки по тирам (в микро). */
    private static final long[] BONUS_MICRO = {
            400_000L, 4_000_000L, 40_000_000L};

    /** Бонус конкретного ранца (0, если это не MatterPackItem). */
    public static long bonusFor(net.minecraft.world.item.ItemStack stack) {
        return (stack.getItem() instanceof MatterPackItem pack)
                ? pack.bonusMicro()
                : 0L;
    }

    private final int tier;

    public MatterPackItem(Properties properties) {
        this(properties, 1);
    }

    public MatterPackItem(Properties properties, int tier) {
        super(properties);
        this.tier = Math.min(3, Math.max(1, tier));
    }

    public long bonusMicro() {
        return BONUS_MICRO[tier - 1];
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
                MatterValues.formatMicro(bonusMicro())));
    }
}
