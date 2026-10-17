package ru.justsmile.quantx.item;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.block.MaterializerBlockEntity;
import ru.justsmile.quantx.matter.MatterValues;

/**
 * Перфокарта — перенос изученных рецептов между материализаторами.
 *
 * Пустая карта + Shift+ПКМ по материализатору → записывает его рецепты.
 * Записанная карта + Shift+ПКМ по другому материализатору → загружает
 * рецепты и деградирует в случайную часть своего крафта (одноразовая).
 */
public class PunchCardItem extends Item {

    /** Части крафта, в одну из которых деградирует использованная карта. */
    private static final Item[] DEGRADE_PARTS = {
            Items.PAPER, Items.COPPER_INGOT, Items.REDSTONE
    };

    public PunchCardItem(Properties properties) {
        super(properties);
    }

    /** Записанные рецепты: CUSTOM_DATA -> { "Recipes": {"minecraft:dirt": 100, ...} } */
    @Nullable
    private static CompoundTag readRecipes(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return data.copyTag().getCompound("Recipes").orElse(null);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Только sneak-клик и только по материализатору.
        if (!context.isSecondaryUseActive()) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (!(be instanceof MaterializerBlockEntity materializer)) return InteractionResult.PASS;

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null) {
            materializer.setLastUser(player.getUUID());
        }

        CompoundTag stored = readRecipes(stack);
        if (stored == null) {
            return writeCard(context, level, materializer, stack, player);
        }
        return readCard(context, level, materializer, stored, player);
    }

    /** Пустая карта: копируем knownRecipes блока в CUSTOM_DATA. */
    private InteractionResult writeCard(UseOnContext context, Level level,
                                        MaterializerBlockEntity materializer,
                                        ItemStack stack, Player player) {
        Map<Identifier, Long> known = materializer.getKnownRecipes();
        if (known.isEmpty()) {
            if (player != null) {
                player.sendSystemMessage(
                        Component.translatable("item.quantx.punch_card.nothing_to_write"));
            }
            level.playSound(null, context.getClickedPos(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.BLOCKS, 0.8f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        CompoundTag recipes = new CompoundTag();
        for (Map.Entry<Identifier, Long> entry : known.entrySet()) {
            recipes.putLong(entry.getKey().toString(), entry.getValue());
        }
        CompoundTag root = new CompoundTag();
        root.put("Recipes", recipes);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        // Записанная карта светится как зачарованная.
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    "item.quantx.punch_card.written", known.size()));
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Записанная карта: учим рецепты и деградируем в часть крафта. */
    private InteractionResult readCard(UseOnContext context, Level level,
                                       MaterializerBlockEntity materializer,
                                       CompoundTag recipes, Player player) {
        int learned = 0;
        for (String key : recipes.keySet()) {
            Identifier id = Identifier.tryParse(key);
            if (id == null) continue;
            Optional<Long> cost = recipes.getLong(key);
            if (cost.isEmpty() || materializer.isRecipeKnown(id)) continue;
            // Цену на карте не доверяем — берём живую из таблицы
            // (на карте могла остаться устаревшая/багнутая стоимость).
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR) continue;
            materializer.learnRecipe(id, MatterValues.getCostMicro(item));
            learned++;
        }

        level.playSound(null, context.getClickedPos(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.BLOCKS, 1.0f, 1.0f);

        // Одноразовость: карта деградирует в случайную часть своего крафта.
        if (player != null) {
            Item part = DEGRADE_PARTS[level.getRandom().nextInt(DEGRADE_PARTS.length)];
            player.setItemInHand(context.getHand(), new ItemStack(part));
            level.playSound(null, context.getClickedPos(),
                    SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
            player.sendSystemMessage(Component.translatable(
                    "item.quantx.punch_card.transferred", learned,
                    Component.translatable(part.getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable("item.quantx.punch_card.desc"));
        CompoundTag recipes = readRecipes(stack);
        if (recipes != null) {
            builder.accept(Component.translatable(
                    "item.quantx.punch_card.recipes", recipes.size()));
        } else {
            builder.accept(Component.translatable("item.quantx.punch_card.empty"));
        }
    }
}
