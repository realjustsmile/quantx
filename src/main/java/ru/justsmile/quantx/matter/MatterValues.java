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
            Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 5_000_000L)
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
