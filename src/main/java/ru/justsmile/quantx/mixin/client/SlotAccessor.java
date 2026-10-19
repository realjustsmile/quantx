package ru.justsmile.quantx.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к координатам слота — нужен, чтобы переставлять SlotWrapper
 * креативного инвентаря в ячейку панели ранца.
 */
@Mixin(Slot.class)
public interface SlotAccessor {

    @Mutable
    @Accessor("x")
    void quantx$setX(int x);

    @Mutable
    @Accessor("y")
    void quantx$setY(int y);
}
