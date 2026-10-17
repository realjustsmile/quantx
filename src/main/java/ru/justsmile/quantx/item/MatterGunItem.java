package ru.justsmile.quantx.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.justsmile.quantx.block.MaterializerBlockEntity;
import ru.justsmile.quantx.matter.MatterValues;

/**
 * Материальная пушка — ручной дематериализатор.
 *
 * ПКМ по блоку (удержание) — уничтожает блок со скоростью подходящего
 * алмазного инструмента; блок не дропается, а превращается в материю
 * и складывается во внутренний бак пушки (yield = стоимость разбора).
 *
 * Shift+ПКМ по материализатору — сливает всю накопленную материю в него.
 * ЛКМ — обычный удар (по мобам), как у любого предмета.
 */
public class MatterGunItem extends Item {

    /** Вместимость внутреннего бака: 10 000 единиц материи (в микро). */
    public static final long CAPACITY_MICRO = 100_000L;

    private static final String MATTER_KEY = "Matter";

    /** Скорость копания алмазного инструмента. */
    private static final float DIAMOND_SPEED = 8.0F;

    /** Дистанция копания (как рука/инструмент). */
    private static final double REACH = 4.5;

    /** Прогресс удержания ПКМ по игроку. Не храним в NBT — слишком частая запись. */
    private record Dig(BlockPos pos, float progress) {}
    private static final Map<UUID, Dig> DIGGING = new ConcurrentHashMap<>();

    public MatterGunItem(Properties properties) {
        super(properties);
    }

    // ===== Алмазная скорость копания =====
    // Tool-компонент не используем: Named HolderSet ломается при регистрации
    // (теги ещё не забиндены). Вместо него — прямые оверрайды с ленивой
    // проверкой тегов в момент копания.

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isMineableFast(state) ? DIAMOND_SPEED : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return !state.requiresCorrectToolForDrops() || isMineableFast(state);
    }

    private static boolean isMineableFast(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_HOE)
                || state.is(BlockTags.SWORD_EFFICIENT);
    }

    // ===== Хранение материи в предмете =====

    public static long getMatterMicro(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0L;
        return data.copyTag().getLongOr(MATTER_KEY, 0L);
    }

    private static void setMatterMicro(ItemStack stack, long micro) {
        CompoundTag root = new CompoundTag();
        root.putLong(MATTER_KEY, micro);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    // ===== ПКМ: ломание / передача =====

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        BlockEntity be = level.getBlockEntity(pos);
        boolean sneaking = player != null && player.isShiftKeyDown();

        // Shift+ПКМ по материализатору — слить бак пушки в машину.
        if (be instanceof MaterializerBlockEntity materializer && sneaking) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            long stored = getMatterMicro(stack);
            if (stored <= 0) {
                player.sendSystemMessage(
                        Component.translatable("item.quantx.matter_gun.empty"));
                return InteractionResult.SUCCESS;
            }
            materializer.addMatter(stored);
            setMatterMicro(stack, 0L);
            player.sendSystemMessage(Component.translatable(
                    "item.quantx.matter_gun.transferred",
                    MatterValues.formatMicro(stored)));
            level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL,
                    SoundSource.BLOCKS, 1.0f, 1.2f);
            return InteractionResult.SUCCESS;
        }

        // ПКМ по блоку — начать дематериализацию (удержание).
        // Обычный ПКМ по интерактивному блоку сюда не доходит: блок сам
        // обрабатывает клик (GUI, кнопки). Shift+ПКМ обходит это и ломает.
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            return InteractionResult.PASS;
        }
        if (player != null) {
            player.startUsingItem(context.getHand());
            if (!level.isClientSide()) {
                DIGGING.put(player.getUUID(), new Dig(pos, 0.0f));
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS
                : InteractionResult.CONSUME;
    }

    /** Удержание ПКМ: копаем цель под прицелом со скоростью алмазного инструмента. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack,
                          int remainingUseDuration) {
        if (level.isClientSide() || !(entity instanceof Player player)) return;

        BlockPos target = raycastPos(player);
        if (target == null) {
            stopDig(player);
            return;
        }
        BlockState state = level.getBlockState(target);
        if (state.isAir() || state.getDestroySpeed(level, target) < 0) {
            stopDig(player);
            return;
        }

        Dig dig = DIGGING.get(player.getUUID());
        if (dig == null || !target.equals(dig.pos())) {
            dig = new Dig(target, 0.0f);
        }

        // Прогресс за тик — ванильная формула (учитывает наш Tool-компонент:
        // mineable-блоки идут на скорости 8, прочее — на 1).
        float perTick = player.getAbilities().instabuild ? 1.0f
                : state.getDestroyProgress(player, level, target);
        if (perTick <= 0) {
            stopDig(player);
            return;
        }

        float progress = dig.progress() + perTick;
        if (progress < 1.0f) {
            DIGGING.put(player.getUUID(), new Dig(target, progress));
            return;
        }

        // Блок разобран: без дропа, его ценность уходит в бак пушки.
        level.destroyBlock(target, false);
        long stored = getMatterMicro(stack);
        long space = CAPACITY_MICRO - stored;
        if (space > 0) {
            long yield = MatterValues.getDematerializeYieldMicro(state.getBlock().asItem());
            setMatterMicro(stack, stored + Math.min(yield, space));
        } else {
            player.sendSystemMessage(
                    Component.translatable("item.quantx.matter_gun.full"));
        }

        // Не отпускаем кнопку — целимся в следующий блок и продолжаем.
        BlockPos next = raycastPos(player);
        DIGGING.put(player.getUUID(), new Dig(next, 0.0f));
    }

    private static BlockPos raycastPos(Player player) {
        HitResult hit = player.pick(REACH, 1.0f, false);
        return hit instanceof BlockHitResult bhr ? bhr.getBlockPos() : null;
    }

    private static void stopDig(Player player) {
        DIGGING.remove(player.getUUID());
        player.stopUsingItem();
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity,
                                int timeCharged) {
        DIGGING.remove(entity.getUUID());
        return super.releaseUsing(stack, level, entity, timeCharged);
    }

    /** Компоненты стека меняются на лету (материя) — использование не прерывать. */
    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }

    // ===== Режим использования =====

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    // ===== Полоска бака под иконкой =====

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getMatterMicro(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) (13L * getMatterMicro(stack) / CAPACITY_MICRO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x55DDCC;
    }

    // ===== Тултип =====

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable("item.quantx.matter_gun.desc"));
        builder.accept(Component.translatable("item.quantx.matter_gun.matter",
                MatterValues.formatMicro(getMatterMicro(stack)),
                MatterValues.formatMicro(CAPACITY_MICRO)));
    }
}
