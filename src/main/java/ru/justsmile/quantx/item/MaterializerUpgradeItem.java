package ru.justsmile.quantx.item;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.block.MaterializerBlock;
import ru.justsmile.quantx.block.MaterializerBlockEntity;

/**
 * Улучшение материализатора: ПКМ по установленному блоку нужного тира
 * заменяет его на следующий. BlockEntity сохраняется — изученные
 * рецепты, материя и инвентарь остаются на месте.
 *
 * upgrade_2: Материализатор I → II. upgrade_3: Материализатор II → III.
 */
public class MaterializerUpgradeItem extends Item {

    /** Тир, который получится после применения (2 или 3). */
    private final int targetTier;

    public MaterializerUpgradeItem(Properties properties, int targetTier) {
        super(properties);
        this.targetTier = targetTier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MaterializerBlock block)
                || block.getTier() != targetTier - 1) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // Тип BE общий для всех тиров → при замене блока ванильный
        // LevelChunk сохраняет существующий BE вместе с NBT.
        Block target = targetTier == 2
                ? QuantX.MATERIALIZER_2.get()
                : QuantX.MATERIALIZER_3.get();
        BlockState upgraded = target.defaultBlockState()
                .setValue(MaterializerBlock.FACING, state.getValue(MaterializerBlock.FACING))
                .setValue(MaterializerBlock.ACTIVE, state.getValue(MaterializerBlock.ACTIVE));
        level.setBlock(pos, upgraded, 3);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MaterializerBlockEntity) {
            level.sendBlockUpdated(pos, upgraded, upgraded, 3);
        }
        context.getItemInHand().shrink(1);
        level.playSound(null, pos, SoundEvents.ANVIL_USE,
                SoundSource.BLOCKS, 1.0f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable("item.quantx.materializer_upgrade.desc"));
    }
}
