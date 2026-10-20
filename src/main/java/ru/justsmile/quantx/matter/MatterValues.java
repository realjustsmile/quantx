package ru.justsmile.quantx.matter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Система ценообразования материи для всех предметов.
 *
 * Цена сборки предмета вычисляется алгоритмически:
 *   1. Якоря сырья и дропов (BASE) — «тяжесть добычи».
 *   2. Теги (LOGS, LEAVES, FLOWERS, ...) — массовые базовые категории.
 *   3. Рецепты: цена = минимум по рецептам от (сумма ингредиентов * 1.2 / выход).
 *      Альтернативы внутри ингредиента берутся по самому дешёвому варианту.
 *   4. Фолбэк по редкости предмета, если рецептов и якоря нет.
 *
 * Выход при разборе = цена сборки / 3 (якорь: земля 3 ед. сборка, 1 ед. разбор).
 *
 * Расчёт ленивый и мемоизируется; индекс рецептов строится один раз по
 * RecipeManager (серверный или клиентский — оба синхронизированы).
 */
public final class MatterValues {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 1 единица материи = 10 микро. Микро — внутренняя точность расчётов;
     * наружу (GUI, тултипы, компаратор) идут только целые единицы:
     * земля разбор = 1, земля сборка = 3.
     */
    public static final long MICRO_PER_UNIT = 10L;

    /** Наценка за сборку: цена крафта = ингредиенты * 6/5 / количество. */
    private static final int CRAFT_MARKUP_NUM = 6;
    private static final int CRAFT_MARKUP_DEN = 5;

    /** Разбор возвращает треть стоимости (два броска к одному). */
    private static final int YIELD_DIVISOR = 3;

    // ===== Якоря сырья: цена СБОРКИ в микро-единицах =====

    private static final Map<Item, Long> BASE = Map.ofEntries(
            // --- Грунт, камень, базовые блоки (~30 мк) ---
            Map.entry(Items.DIRT, 30L),
            Map.entry(Items.DIRT_PATH, 60L),
            Map.entry(Items.PODZOL, 90L),
            Map.entry(Items.MYCELIUM, 200L),
            Map.entry(Items.COARSE_DIRT, 60L),
            Map.entry(Items.GRASS_BLOCK, 60L),
            Map.entry(Items.MOSS_BLOCK, 80L),
            Map.entry(Items.MUD, 45L),
            Map.entry(Items.MANGROVE_ROOTS, 60L),
            Map.entry(Items.GRAVEL, 30L),
            Map.entry(Items.FLINT, 60L),
            Map.entry(Items.CLAY_BALL, 30L),
            Map.entry(Items.SNOWBALL, 30L),
            Map.entry(Items.ICE, 60L),
            Map.entry(Items.BLUE_ICE, 500L),
            Map.entry(Items.PACKED_ICE, 150L),
            Map.entry(Items.SOUL_SAND, 150L),
            Map.entry(Items.SOUL_SOIL, 150L),
            Map.entry(Items.NETHERRACK, 30L),
            Map.entry(Items.BLACKSTONE, 45L),
            Map.entry(Items.BASALT, 45L),
            Map.entry(Items.END_STONE, 60L),
            Map.entry(Items.COBBLESTONE, 30L),
            Map.entry(Items.STONE, 45L),
            Map.entry(Items.ANDESITE, 45L),
            Map.entry(Items.DIORITE, 45L),
            Map.entry(Items.GRANITE, 45L),
            Map.entry(Items.DEEPSLATE, 45L),
            Map.entry(Items.COBBLED_DEEPSLATE, 45L),
            Map.entry(Items.TUFF, 45L),
            Map.entry(Items.CALCITE, 200L),
            Map.entry(Items.DRIPSTONE_BLOCK, 150L),
            Map.entry(Items.POINTED_DRIPSTONE, 60L),
            Map.entry(Items.OBSIDIAN, 300L),
            Map.entry(Items.CRYING_OBSIDIAN, 2_000L),
            Map.entry(Items.MAGMA_BLOCK, 200L),
            Map.entry(Items.SEA_LANTERN, 8_000L),

            // --- Дерево и растения ---
            Map.entry(Items.BONE, 200L),
            Map.entry(Items.STRING, 150L),
            Map.entry(Items.LEATHER, 400L),
            Map.entry(Items.RABBIT_HIDE, 250L),
            Map.entry(Items.FEATHER, 120L),
            Map.entry(Items.EGG, 120L),
            Map.entry(Items.MILK_BUCKET, 1_000L),
            Map.entry(Items.SUGAR_CANE, 80L),
            Map.entry(Items.CACTUS, 60L),
            Map.entry(Items.BAMBOO, 40L),
            Map.entry(Items.KELP, 40L),
            Map.entry(Items.COCOA_BEANS, 150L),
            Map.entry(Items.WHEAT, 80L),
            Map.entry(Items.WHEAT_SEEDS, 15L),
            Map.entry(Items.CARROT, 100L),
            Map.entry(Items.POTATO, 100L),
            Map.entry(Items.POISONOUS_POTATO, 300L),
            Map.entry(Items.BEETROOT, 120L),
            Map.entry(Items.BEETROOT_SEEDS, 15L),
            Map.entry(Items.MELON_SLICE, 40L),
            Map.entry(Items.PUMPKIN, 200L),
            Map.entry(Items.PUMPKIN_SEEDS, 40L),
            Map.entry(Items.MELON_SEEDS, 40L),
            Map.entry(Items.APPLE, 400L),
            Map.entry(Items.BROWN_MUSHROOM, 60L),
            Map.entry(Items.RED_MUSHROOM, 60L),
            Map.entry(Items.CHORUS_FRUIT, 800L),
            Map.entry(Items.SWEET_BERRIES, 40L),
            Map.entry(Items.GLOW_BERRIES, 300L),
            Map.entry(Items.COD, 300L),
            Map.entry(Items.SALMON, 350L),
            Map.entry(Items.TROPICAL_FISH, 800L),
            Map.entry(Items.PUFFERFISH, 2_000L),
            Map.entry(Items.TURTLE_EGG, 3_000L),
            Map.entry(Items.SNIFFER_EGG, 50_000L),
            Map.entry(Items.TORCHFLOWER_SEEDS, 5_000L),
            Map.entry(Items.PITCHER_POD, 5_000L),
            Map.entry(Items.PINK_PETALS, 40L),
            Map.entry(Items.LILY_PAD, 100L),
            Map.entry(Items.VINE, 80L),
            Map.entry(Items.GLOW_LICHEN, 80L),
            Map.entry(Items.HANGING_ROOTS, 80L),
            Map.entry(Items.SPORE_BLOSSOM, 300L),
            Map.entry(Items.AZALEA, 60L),
            Map.entry(Items.FLOWERING_AZALEA, 100L),
            Map.entry(Items.MANGROVE_PROPAGULE, 100L),
            Map.entry(Items.CHERRY_SAPLING, 60L),

            // --- Цветы (item-тегов цветов в 26.3 нет — якоря вручную) ---
            Map.entry(Items.DANDELION, 80L),
            Map.entry(Items.POPPY, 80L),
            Map.entry(Items.BLUE_ORCHID, 80L),
            Map.entry(Items.ALLIUM, 80L),
            Map.entry(Items.AZURE_BLUET, 80L),
            Map.entry(Items.RED_TULIP, 80L),
            Map.entry(Items.ORANGE_TULIP, 80L),
            Map.entry(Items.WHITE_TULIP, 80L),
            Map.entry(Items.PINK_TULIP, 80L),
            Map.entry(Items.OXEYE_DAISY, 80L),
            Map.entry(Items.CORNFLOWER, 80L),
            Map.entry(Items.LILY_OF_THE_VALLEY, 80L),
            Map.entry(Items.TORCHFLOWER, 5_000L),
            Map.entry(Items.CLOSED_EYEBLOSSOM, 300L),
            Map.entry(Items.OPEN_EYEBLOSSOM, 300L),
            Map.entry(Items.WILDFLOWERS, 100L),
            Map.entry(Items.SUNFLOWER, 200L),
            Map.entry(Items.LILAC, 200L),
            Map.entry(Items.ROSE_BUSH, 200L),
            Map.entry(Items.PEONY, 200L),
            Map.entry(Items.PITCHER_PLANT, 5_000L),
            Map.entry(Items.WITHER_ROSE, 2_000L),

            // --- Уголь, руды, самородки, слитки ---
            Map.entry(Items.COAL, 500L),
            Map.entry(Items.CHARCOAL, 500L),
            Map.entry(Items.RAW_IRON, 3_000L),
            Map.entry(Items.RAW_GOLD, 6_000L),
            Map.entry(Items.RAW_COPPER, 1_200L),
            Map.entry(Items.IRON_INGOT, 4_000L),
            Map.entry(Items.GOLD_INGOT, 8_000L),
            Map.entry(Items.COPPER_INGOT, 1_500L),
            Map.entry(Items.LAPIS_LAZULI, 3_000L),
            Map.entry(Items.REDSTONE, 1_200L),
            Map.entry(Items.QUARTZ, 2_500L),
            Map.entry(Items.AMETHYST_SHARD, 1_500L),
            Map.entry(Items.ECHO_SHARD, 50_000L),
            Map.entry(Items.DIAMOND, 150_000L),
            Map.entry(Items.EMERALD, 200_000L),
            Map.entry(Items.ANCIENT_DEBRIS, 800_000L),
            Map.entry(Items.NETHERITE_SCRAP, 900_000L),
            // Якорь пользователя: незеритовый слиток = 4000 ед. материи.
            Map.entry(Items.NETHERITE_INGOT, 40_000L),
            Map.entry(Items.PRISMARINE_SHARD, 2_000L),
            Map.entry(Items.PRISMARINE_CRYSTALS, 2_000L),
            Map.entry(Items.NAUTILUS_SHELL, 50_000L),
            Map.entry(Items.HEART_OF_THE_SEA, 1_000_000L),

            // --- Моб-дропы ---
            Map.entry(Items.ROTTEN_FLESH, 60L),
            Map.entry(Items.GUNPOWDER, 800L),
            Map.entry(Items.SPIDER_EYE, 1_000L),
            Map.entry(Items.ENDER_PEARL, 8_000L),
            Map.entry(Items.BLAZE_ROD, 10_000L),
            Map.entry(Items.GHAST_TEAR, 15_000L),
            Map.entry(Items.SLIME_BALL, 600L),
            Map.entry(Items.MAGMA_CREAM, 2_500L),
            Map.entry(Items.INK_SAC, 150L),
            Map.entry(Items.GLOW_INK_SAC, 800L),
            Map.entry(Items.TURTLE_SCUTE, 3_000L),
            Map.entry(Items.ARMADILLO_SCUTE, 1_000L),
            Map.entry(Items.PHANTOM_MEMBRANE, 6_000L),
            Map.entry(Items.SHULKER_SHELL, 30_000L),
            Map.entry(Items.NETHER_WART, 400L),
            Map.entry(Items.GLOWSTONE_DUST, 500L),
            Map.entry(Items.HONEYCOMB, 400L),
            Map.entry(Items.BREEZE_ROD, 8_000L),
            Map.entry(Items.WIND_CHARGE, 2_000L),
            Map.entry(Items.DRAGON_BREATH, 20_000L),
            Map.entry(Items.SKELETON_SKULL, 10_000L),
            Map.entry(Items.ZOMBIE_HEAD, 10_000L),
            Map.entry(Items.CREEPER_HEAD, 10_000L),
            Map.entry(Items.PIGLIN_HEAD, 10_000L),
            Map.entry(Items.WITHER_SKELETON_SKULL, 50_000L),
            Map.entry(Items.DRAGON_HEAD, 150_000L),
            Map.entry(Items.TRIAL_KEY, 10_000L),
            Map.entry(Items.OMINOUS_TRIAL_KEY, 20_000L),
            Map.entry(Items.OMINOUS_BOTTLE, 20_000L),

            // --- Редкие находки ---
            Map.entry(Items.SADDLE, 200_000L),
            Map.entry(Items.NAME_TAG, 50_000L),
            Map.entry(Items.LEATHER_HORSE_ARMOR, 80_000L),
            Map.entry(Items.IRON_HORSE_ARMOR, 150_000L),
            Map.entry(Items.GOLDEN_HORSE_ARMOR, 300_000L),
            Map.entry(Items.DIAMOND_HORSE_ARMOR, 600_000L),
            Map.entry(Items.TRIDENT, 500_000L),
            Map.entry(Items.TOTEM_OF_UNDYING, 500_000L),
            Map.entry(Items.SPONGE, 300_000L),
            Map.entry(Items.HEAVY_CORE, 2_000_000L),
            Map.entry(Items.NETHER_STAR, 3_000_000L),
            Map.entry(Items.ELYTRA, 10_000_000L),
            Map.entry(Items.DRAGON_EGG, 50_000_000L),
            Map.entry(Items.DISC_FRAGMENT_5, 100_000L),
            Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 5_000_000L),

            // ===== Руды (блоки под шёлковым касанием) =====
            // Цена = дроп руды + наценка за silk-touch добычу.
            Map.entry(Items.COAL_ORE, 800L),
            Map.entry(Items.DEEPSLATE_COAL_ORE, 900L),
            Map.entry(Items.IRON_ORE, 3_200L),
            Map.entry(Items.DEEPSLATE_IRON_ORE, 3_500L),
            Map.entry(Items.GOLD_ORE, 6_300L),
            Map.entry(Items.DEEPSLATE_GOLD_ORE, 6_600L),
            Map.entry(Items.NETHER_GOLD_ORE, 4_000L),
            Map.entry(Items.COPPER_ORE, 1_500L),
            Map.entry(Items.DEEPSLATE_COPPER_ORE, 1_600L),
            Map.entry(Items.LAPIS_ORE, 5_000L),
            Map.entry(Items.DEEPSLATE_LAPIS_ORE, 5_500L),
            Map.entry(Items.REDSTONE_ORE, 2_000L),
            Map.entry(Items.DEEPSLATE_REDSTONE_ORE, 2_200L),
            Map.entry(Items.DIAMOND_ORE, 155_000L),
            Map.entry(Items.DEEPSLATE_DIAMOND_ORE, 165_000L),
            Map.entry(Items.EMERALD_ORE, 210_000L),
            Map.entry(Items.DEEPSLATE_EMERALD_ORE, 220_000L),
            Map.entry(Items.NETHER_QUARTZ_ORE, 3_000L),
            Map.entry(Items.GILDED_BLACKSTONE, 10_000L),

            // ===== Блоки хранения — ровно 9 (или 4) единиц материала =====
            // Якоря, а не рецепты: упаковка не должна иметь наценку крафта.
            Map.entry(Items.IRON_BLOCK, 36_000L),
            Map.entry(Items.GOLD_BLOCK, 72_000L),
            Map.entry(Items.COPPER_BLOCK.weathering().unaffected(), 13_500L),
            Map.entry(Items.DIAMOND_BLOCK, 1_350_000L),
            Map.entry(Items.EMERALD_BLOCK, 1_800_000L),
            Map.entry(Items.NETHERITE_BLOCK, 360_000L),
            Map.entry(Items.LAPIS_BLOCK, 27_000L),
            Map.entry(Items.REDSTONE_BLOCK, 10_800L),
            Map.entry(Items.COAL_BLOCK, 4_500L),
            Map.entry(Items.RAW_IRON_BLOCK, 27_000L),
            Map.entry(Items.RAW_GOLD_BLOCK, 54_000L),
            Map.entry(Items.RAW_COPPER_BLOCK, 10_800L),
            Map.entry(Items.QUARTZ_BLOCK, 10_000L),
            Map.entry(Items.AMETHYST_BLOCK, 6_000L),
            Map.entry(Items.BUDDING_AMETHYST, 80_000L),
            Map.entry(Items.SLIME_BLOCK, 5_400L),
            Map.entry(Items.HONEYCOMB_BLOCK, 1_600L),
            Map.entry(Items.MELON, 400L),
            Map.entry(Items.NETHER_WART_BLOCK, 3_600L),
            Map.entry(Items.WARPED_WART_BLOCK, 3_600L),

            // ===== Окисление меди — только «природные» стадии без рецепта =====
            // (срезанные/восковые/ступени берут цену из рецептов по базе)
            Map.entry(Items.COPPER_BLOCK.weathering().exposed(), 14_000L),
            Map.entry(Items.COPPER_BLOCK.weathering().weathered(), 14_500L),
            Map.entry(Items.COPPER_BLOCK.weathering().oxidized(), 15_500L),

            // ===== Бетон — застывает в воде, рецептов нет (16 цветов) =====
            Map.entry(Items.CONCRETE.white(), 80L),
            Map.entry(Items.CONCRETE.orange(), 80L),
            Map.entry(Items.CONCRETE.magenta(), 80L),
            Map.entry(Items.CONCRETE.lightBlue(), 80L),
            Map.entry(Items.CONCRETE.yellow(), 80L),
            Map.entry(Items.CONCRETE.lime(), 80L),
            Map.entry(Items.CONCRETE.pink(), 80L),
            Map.entry(Items.CONCRETE.gray(), 80L),
            Map.entry(Items.CONCRETE.lightGray(), 80L),
            Map.entry(Items.CONCRETE.cyan(), 80L),
            Map.entry(Items.CONCRETE.purple(), 80L),
            Map.entry(Items.CONCRETE.blue(), 80L),
            Map.entry(Items.CONCRETE.brown(), 80L),
            Map.entry(Items.CONCRETE.green(), 80L),
            Map.entry(Items.CONCRETE.red(), 80L),
            Map.entry(Items.CONCRETE.black(), 80L),

            // ===== Кораллы — океан, без рецептов =====
            Map.entry(Items.TUBE_CORAL_BLOCK, 500L),
            Map.entry(Items.BRAIN_CORAL_BLOCK, 500L),
            Map.entry(Items.BUBBLE_CORAL_BLOCK, 500L),
            Map.entry(Items.FIRE_CORAL_BLOCK, 500L),
            Map.entry(Items.HORN_CORAL_BLOCK, 500L),
            Map.entry(Items.DEAD_TUBE_CORAL_BLOCK, 150L),
            Map.entry(Items.DEAD_BRAIN_CORAL_BLOCK, 150L),
            Map.entry(Items.DEAD_BUBBLE_CORAL_BLOCK, 150L),
            Map.entry(Items.DEAD_FIRE_CORAL_BLOCK, 150L),
            Map.entry(Items.DEAD_HORN_CORAL_BLOCK, 150L),
            Map.entry(Items.TUBE_CORAL, 300L),
            Map.entry(Items.BRAIN_CORAL, 300L),
            Map.entry(Items.BUBBLE_CORAL, 300L),
            Map.entry(Items.FIRE_CORAL, 300L),
            Map.entry(Items.HORN_CORAL, 300L),
            Map.entry(Items.TUBE_CORAL_FAN, 300L),
            Map.entry(Items.BRAIN_CORAL_FAN, 300L),
            Map.entry(Items.BUBBLE_CORAL_FAN, 300L),
            Map.entry(Items.FIRE_CORAL_FAN, 300L),
            Map.entry(Items.HORN_CORAL_FAN, 300L),
            Map.entry(Items.DEAD_TUBE_CORAL, 100L),
            Map.entry(Items.DEAD_BRAIN_CORAL, 100L),
            Map.entry(Items.DEAD_BUBBLE_CORAL, 100L),
            Map.entry(Items.DEAD_FIRE_CORAL, 100L),
            Map.entry(Items.DEAD_HORN_CORAL, 100L),
            Map.entry(Items.DEAD_TUBE_CORAL_FAN, 100L),
            Map.entry(Items.DEAD_BRAIN_CORAL_FAN, 100L),
            Map.entry(Items.DEAD_BUBBLE_CORAL_FAN, 100L),
            Map.entry(Items.DEAD_FIRE_CORAL_FAN, 100L),
            Map.entry(Items.DEAD_HORN_CORAL_FAN, 100L),
            Map.entry(Items.SEAGRASS, 40L),
            Map.entry(Items.SEA_PICKLE, 200L),

            // ===== Растительность и блоки Незера =====
            Map.entry(Items.CRIMSON_NYLIUM, 100L),
            Map.entry(Items.WARPED_NYLIUM, 100L),
            Map.entry(Items.CRIMSON_FUNGUS, 200L),
            Map.entry(Items.WARPED_FUNGUS, 200L),
            Map.entry(Items.CRIMSON_ROOTS, 80L),
            Map.entry(Items.WARPED_ROOTS, 80L),
            Map.entry(Items.NETHER_SPROUTS, 60L),
            Map.entry(Items.WEEPING_VINES, 100L),
            Map.entry(Items.TWISTING_VINES, 100L),
            Map.entry(Items.SHROOMLIGHT, 500L),

            // ===== Энд =====
            Map.entry(Items.CHORUS_PLANT, 400L),
            Map.entry(Items.CHORUS_FLOWER, 800L),
            Map.entry(Items.END_PORTAL_FRAME, 2_000_000L),

            // ===== Скалк и Глубокая тьма =====
            Map.entry(Items.SCULK, 500L),
            Map.entry(Items.SCULK_VEIN, 100L),
            Map.entry(Items.SCULK_SENSOR, 50_000L),
            Map.entry(Items.SCULK_SHRIEKER, 200_000L),
            Map.entry(Items.SCULK_CATALYST, 300_000L),
            Map.entry(Items.REINFORCED_DEEPSLATE, 1_000_000L),

            // ===== Заражённые блоки (сильверфиши) =====
            Map.entry(Items.INFESTED_STONE, 150L),
            Map.entry(Items.INFESTED_COBBLESTONE, 150L),
            Map.entry(Items.INFESTED_STONE_BRICKS, 150L),
            Map.entry(Items.INFESTED_MOSSY_STONE_BRICKS, 150L),
            Map.entry(Items.INFESTED_CRACKED_STONE_BRICKS, 150L),
            Map.entry(Items.INFESTED_CHISELED_STONE_BRICKS, 150L),
            Map.entry(Items.INFESTED_DEEPSLATE, 150L),

            // ===== Мелкая растительность/декор без рецептов =====
            Map.entry(Items.SHORT_GRASS, 20L),
            Map.entry(Items.TALL_GRASS, 40L),
            Map.entry(Items.FERN, 20L),
            Map.entry(Items.LARGE_FERN, 40L),
            Map.entry(Items.DEAD_BUSH, 30L),
            Map.entry(Items.BUSH, 60L),
            Map.entry(Items.DRY_SHORT_GRASS, 30L),
            Map.entry(Items.DRY_TALL_GRASS, 40L),
            Map.entry(Items.FIREFLY_BUSH, 100L),
            Map.entry(Items.LEAF_LITTER, 60L),
            Map.entry(Items.CACTUS_FLOWER, 120L),
            Map.entry(Items.PALE_MOSS_BLOCK, 80L),
            Map.entry(Items.PALE_MOSS_CARPET, 40L),
            Map.entry(Items.PALE_HANGING_MOSS, 60L),
            Map.entry(Items.SMALL_DRIPLEAF, 150L),
            Map.entry(Items.BIG_DRIPLEAF, 300L),
            Map.entry(Items.BROWN_MUSHROOM_BLOCK, 300L),
            Map.entry(Items.RED_MUSHROOM_BLOCK, 300L),
            Map.entry(Items.MUSHROOM_STEM, 200L),
            Map.entry(Items.ROOTED_DIRT, 60L),
            Map.entry(Items.FARMLAND, 45L),
            Map.entry(Items.SNOW, 15L),

            // ===== Плёнки и жаб'ячий декор =====
            Map.entry(Items.FROGSPAWN, 20_000L),
            Map.entry(Items.PEARLESCENT_FROGLIGHT, 100_000L),
            Map.entry(Items.OCHRE_FROGLIGHT, 100_000L),
            Map.entry(Items.VERDANT_FROGLIGHT, 100_000L),
            Map.entry(Items.CREAKING_HEART, 300_000L),
            Map.entry(Items.DRIED_GHAST, 50_000L),

            // ===== Вёдра (вода/лава бесконечны — дёшево; мобы — редкость) =====
            Map.entry(Items.WATER_BUCKET, 300L),
            Map.entry(Items.LAVA_BUCKET, 800L),
            Map.entry(Items.POWDER_SNOW_BUCKET, 500L),
            Map.entry(Items.COD_BUCKET, 2_500L),
            Map.entry(Items.SALMON_BUCKET, 3_000L),
            Map.entry(Items.PUFFERFISH_BUCKET, 3_500L),
            Map.entry(Items.TROPICAL_FISH_BUCKET, 3_000L),
            Map.entry(Items.AXOLOTL_BUCKET, 15_000L),
            Map.entry(Items.TADPOLE_BUCKET, 8_000L),

            // ===== Музыкальные пластинки =====
            // Пластинки криперов ~40k; редкие дропы/лут — дороже.
            Map.entry(Items.MUSIC_DISC_13, 40_000L),
            Map.entry(Items.MUSIC_DISC_CAT, 40_000L),
            Map.entry(Items.MUSIC_DISC_BLOCKS, 40_000L),
            Map.entry(Items.MUSIC_DISC_CHIRP, 40_000L),
            Map.entry(Items.MUSIC_DISC_FAR, 40_000L),
            Map.entry(Items.MUSIC_DISC_MALL, 40_000L),
            Map.entry(Items.MUSIC_DISC_MELLOHI, 40_000L),
            Map.entry(Items.MUSIC_DISC_STAL, 40_000L),
            Map.entry(Items.MUSIC_DISC_STRAD, 40_000L),
            Map.entry(Items.MUSIC_DISC_WARD, 40_000L),
            Map.entry(Items.MUSIC_DISC_11, 40_000L),
            Map.entry(Items.MUSIC_DISC_WAIT, 40_000L),
            Map.entry(Items.MUSIC_DISC_5, 300_000L),
            Map.entry(Items.MUSIC_DISC_PIGSTEP, 250_000L),
            Map.entry(Items.MUSIC_DISC_OTHERSIDE, 200_000L),
            Map.entry(Items.MUSIC_DISC_RELIC, 150_000L),
            Map.entry(Items.MUSIC_DISC_CREATOR, 80_000L),
            Map.entry(Items.MUSIC_DISC_CREATOR_MUSIC_BOX, 80_000L),
            Map.entry(Items.MUSIC_DISC_PRECIPICE, 100_000L),
            Map.entry(Items.MUSIC_DISC_TEARS, 150_000L),
            Map.entry(Items.MUSIC_DISC_LAVA_CHICKEN, 100_000L),

            // ===== Шаблоны ковки =====
            // Апгрейд незерита — бастион, дублируется 7 алмазами.
            Map.entry(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 500_000L),
            // Отделки брони: лут структур ~100k, самые редкие дороже.
            Map.entry(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 100_000L),
            Map.entry(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 200_000L),
            Map.entry(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 150_000L),
            Map.entry(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 150_000L),

            // ===== Узоры флагов =====
            Map.entry(Items.FLOWER_BANNER_PATTERN, 50_000L),
            Map.entry(Items.CREEPER_BANNER_PATTERN, 80_000L),
            Map.entry(Items.SKULL_BANNER_PATTERN, 120_000L),
            Map.entry(Items.MOJANG_BANNER_PATTERN, 400_000L),
            Map.entry(Items.GLOBE_BANNER_PATTERN, 80_000L),
            Map.entry(Items.PIGLIN_BANNER_PATTERN, 100_000L),
            Map.entry(Items.FLOW_BANNER_PATTERN, 120_000L),
            Map.entry(Items.GUSTER_BANNER_PATTERN, 120_000L),
            Map.entry(Items.FIELD_MASONED_BANNER_PATTERN, 20_000L),
            Map.entry(Items.BORDURE_INDENTED_BANNER_PATTERN, 20_000L),

            // ===== Цепная броня — снята с крафта, лут/торговля =====
            Map.entry(Items.CHAINMAIL_HELMET, 20_000L),
            Map.entry(Items.CHAINMAIL_CHESTPLATE, 50_000L),
            Map.entry(Items.CHAINMAIL_LEGGINGS, 40_000L),
            Map.entry(Items.CHAINMAIL_BOOTS, 15_000L),

            // ===== Прочие предметы без рецептов =====
            Map.entry(Items.BELL, 200_000L),
            Map.entry(Items.GOAT_HORN, 30_000L),
            Map.entry(Items.EXPERIENCE_BOTTLE, 20_000L),
            Map.entry(Items.WRITTEN_BOOK, 5_000L),
            Map.entry(Items.RABBIT_FOOT, 3_000L)
    );

    /**
     * Тег-якоря: массовые категории без индивидуальных записей.
     * Порядок важен — берётся первый подходящий тег.
     */
    private static final List<Map.Entry<TagKey<Item>, Long>> TAG_BASE = List.of(
            Map.entry(ItemTags.LOGS, 150L),
            Map.entry(ItemTags.LEAVES, 15L),
            Map.entry(ItemTags.SAPLINGS, 60L),
            Map.entry(ItemTags.SAND, 30L),
            Map.entry(ItemTags.DIRT, 30L),
            Map.entry(ItemTags.TERRACOTTA, 60L),
            Map.entry(ItemTags.WOOL, 300L),
            Map.entry(ItemTags.STONE_CRAFTING_MATERIALS, 30L),
            Map.entry(ItemTags.WOODEN_SLABS, 20L),
            Map.entry(ItemTags.WOODEN_STAIRS, 40L),
            Map.entry(ItemTags.WOODEN_FENCES, 60L),
            Map.entry(ItemTags.WOODEN_TRAPDOORS, 60L),
            Map.entry(ItemTags.WOODEN_DOORS, 60L),
            Map.entry(ItemTags.WOODEN_BUTTONS, 10L),
            Map.entry(ItemTags.WOODEN_PRESSURE_PLATES, 40L),
            Map.entry(ItemTags.FENCE_GATES, 80L),
            Map.entry(ItemTags.SIGNS, 100L),
            Map.entry(ItemTags.HANGING_SIGNS, 120L),
            Map.entry(ItemTags.BOATS, 1_000L),
            Map.entry(ItemTags.CHEST_BOATS, 1_200L),
            Map.entry(ItemTags.RAILS, 2_000L),
            Map.entry(ItemTags.DECORATED_POT_SHERDS, 5_000L)
    );

    /** Фолбэк по редкости, если нет ни якоря, ни рецепта. */
    private static long rarityFallback(Item item) {
        return switch (item.getDefaultInstance().getRarity()) {
            case UNCOMMON -> 20_000L;
            case RARE -> 300_000L;
            case EPIC -> 5_000_000L;
            default -> 1_000L;
        };
    }

    // ===== Динамический расчёт =====

    /** Индекс рецептов по результирующему предмету: item -> [рецепт, размер выхода]. */
    private record RecipeEntry(Recipe<?> recipe, int resultCount) {}
    private static volatile Map<Item, List<RecipeEntry>> recipesByResult = Map.of();
    private static final Object INIT_LOCK = new Object();

    /** Мемоизированные цены (якоря копируются внутрь при расчёте). */
    private static final Map<Item, Long> values = new ConcurrentHashMap<>();

    private MatterValues() {
    }

    /**
     * Построить индекс рецептов один раз. Безопасно дёргать каждый тик —
     * после первого вызова это дешёвая проверка.
     */
    public static void init(RecipeManager recipeManager) {
        if (recipeManager == null || !recipesByResult.isEmpty()) return;
        synchronized (INIT_LOCK) {
            if (!recipesByResult.isEmpty()) return;
            Map<Item, List<RecipeEntry>> index = new HashMap<>();
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                Recipe<?> recipe = holder.value();
                List<RecipeDisplay> displays = recipe.display();
                if (displays.isEmpty()) continue;
                try {
                    ItemStack result = displays.get(0).result()
                            .resolveForFirstStack(ContextMap.EMPTY);
                    if (result.isEmpty()) continue;
                    index.computeIfAbsent(result.getItem(), k -> new ArrayList<>())
                            .add(new RecipeEntry(recipe, Math.max(1, result.getCount())));
                } catch (Exception ignored) {
                    // Экзотичный дисплей без контекста — пропускаем рецепт.
                }
            }
            recipesByResult = index;
            // Всё, что закешировалось до построения индекса — fallback-мусор.
            // Обязательно сбрасываем, иначе предметы навсегда застрянут на
            // фолбэк-ценах (или на значениях, вычисленных без рецептов).
            values.clear();
            LOGGER.info("[QuantX] recipe index built: {} result-items", index.size());
        }
    }

    /** Отладочный разбор цены предмета: якорь/тег/рецепты с ценами ингредиентов. */
    public static String debugCost(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
        Long base = BASE.get(item);
        if (base != null) return sb.append(" BASE=").append(base).toString();
        for (Map.Entry<TagKey<Item>, Long> tag : TAG_BASE) {
            if (item.getDefaultInstance().is(tag.getKey())) {
                return sb.append(" TAG=").append(tag.getValue()).toString();
            }
        }
        List<RecipeEntry> recipes = recipesByResult.get(item);
        if (recipes == null || recipes.isEmpty()) {
            return sb.append(" NO_RECIPES fallback=").append(rarityFallback(item)).toString();
        }
        for (RecipeEntry e : recipes) {
            sb.append("\n  recipe out=").append(e.resultCount());
            for (Ingredient ing : e.recipe().placementInfo().ingredients()) {
                if (ing == null || ing.isEmpty()) { sb.append(" [empty]"); continue; }
                long min = Long.MAX_VALUE;
                String bestName = "?";
                for (var it = ing.items().iterator(); it.hasNext(); ) {
                    Item v = it.next().value();
                    long c = getCostMicro(v);
                    if (c < min) { min = c; bestName = String.valueOf(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(v)); }
                }
                sb.append(" [").append(bestName).append('=').append(min).append(']');
            }
            sb.append(" => ").append(recipeCost(e, new HashSet<>()));
        }
        return sb.append("\n  => cost=").append(getCostMicro(item)).toString();
    }

    /** Полная стоимость предмета в микро-единицах материи (сборка). */
    public static long getCostMicro(Item item) {
        Long cached = values.get(item);
        if (cached != null) return cached;
        long cost = computeCost(item, new HashSet<>());
        values.put(item, cost);
        return cost;
    }

    /**
     * Выход материи при дематериализации — треть стоимости сборки.
     * Земля: 30 мк сборка → 10 мк разбор.
     */
    public static long getDematerializeYieldMicro(Item item) {
        return Math.max(1L, getCostMicro(item) / YIELD_DIVISOR);
    }

    /** Есть ли осмысленная цена (якорь, тег или рецепт), а не фолбэк по редкости. */
    public static boolean isKnown(Item item) {
        if (BASE.containsKey(item)) return true;
        for (Map.Entry<TagKey<Item>, Long> tag : TAG_BASE) {
            if (item.getDefaultInstance().is(tag.getKey())) return true;
        }
        return recipesByResult.containsKey(item);
    }

    /** Рекурсивный расчёт: якорь → тег → рецепты → фолбэк. */
    private static long computeCost(Item item, Set<Item> visiting) {
        // 1. Якорь сырья.
        Long base = BASE.get(item);
        if (base != null) return base;

        // 2. Тег-якорь.
        for (Map.Entry<TagKey<Item>, Long> tag : TAG_BASE) {
            if (item.getDefaultInstance().is(tag.getKey())) return tag.getValue();
        }

        // 3. Защита от циклов (блок ⇄ 9 предметов и т.п.).
        if (!visiting.add(item)) {
            return rarityFallback(item);
        }
        try {
            List<RecipeEntry> recipes = recipesByResult.get(item);
            if (recipes != null && !recipes.isEmpty()) {
                long best = Long.MAX_VALUE;
                for (RecipeEntry entry : recipes) {
                    long recipeCost = recipeCost(entry, visiting);
                    if (recipeCost < best) best = recipeCost;
                }
                if (best != Long.MAX_VALUE) return Math.max(1L, best);
            }

            // 4. Фолбэк по редкости.
            return rarityFallback(item);
        } finally {
            visiting.remove(item);
        }
    }

    /** Цена одного рецепта: сумма лучших вариантов ингредиентов * наценка / выход. */
    private static long recipeCost(RecipeEntry entry, Set<Item> visiting) {
        List<Ingredient> ingredients = entry.recipe().placementInfo().ingredients();
        if (ingredients.isEmpty()) return Long.MAX_VALUE;

        long sum = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            long min = Long.MAX_VALUE;
            var stream = ingredient.items();
            for (var iter = stream.iterator(); iter.hasNext(); ) {
                Item variant = iter.next().value();
                long c;
                Long cached = values.get(variant);
                if (cached != null) {
                    c = cached;
                } else {
                    c = computeCost(variant, visiting);
                    values.put(variant, c);
                }
                if (c < min) min = c;
            }
            if (min == Long.MAX_VALUE) return Long.MAX_VALUE;
            sum += min;
            if (sum < 0) return Long.MAX_VALUE; // переполнение — страховка
        }

        return sum * CRAFT_MARKUP_NUM / CRAFT_MARKUP_DEN / entry.resultCount();
    }

    /**
     * То же, что {@link #formatMicro}, но БЕЗ пробелов-разделителей
     * разрядов. Для строки бака «Материя: xxx / xxx».
     */
    public static String formatMicroPlain(long micro) {
        long units = (micro + MICRO_PER_UNIT / 2) / MICRO_PER_UNIT;
        return Long.toString(units);
    }

    /**
     * Форматирует микро-единицы в ЦЕЛЫЕ единицы материи (округление
     * до ближайшего), разряды группируются пробелами.
     * 30 → "3", 15 → "2", 4295294000 → "429 529 400".
     */
    public static String formatMicro(long micro) {
        long units = (micro + MICRO_PER_UNIT / 2) / MICRO_PER_UNIT;
        String s = Long.toString(units);
        if (s.length() <= 4) return s;
        StringBuilder grouped = new StringBuilder(s.length() + 4);
        int first = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && i % 3 == first) grouped.append(' ');
            grouped.append(s.charAt(i));
        }
        return grouped.toString();
    }
}
