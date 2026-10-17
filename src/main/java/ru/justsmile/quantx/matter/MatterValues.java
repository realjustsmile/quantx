package ru.justsmile.quantx.matter;

import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Статическая таблица стоимостей предметов в микро-единицах материи.
 * Материя единая, без тиров: 1 единица = 1_000_000 микро.
 * Блок земли стоит 0.0001 = 100 микро.
 *
 * getCostMicro            — сколько материи нужно, чтобы СОБРАТЬ предмет.
 * getDematerializeYield   — сколько материи ПОЛУЧИМ при разборе (скидка 25%).
 * isKnown                 — есть ли предмет в таблице (иначе дефолтная цена).
 */
public final class MatterValues {

    /** 1 единица материи = 1_000_000 микро. */
    public static final long MICRO_PER_UNIT = 1_000_000L;

    /** Цена по умолчанию для предметов вне таблицы: 0.01 единицы. */
    public static final long DEFAULT_COST_MICRO = 10_000L;

    /** Стоимость предметов в микро-единицах материи. */
    private static final Map<Item, Long> VALUES = Map.of(
            Items.DIRT,           100L,        // 0.0001
            Items.STONE,          200L,        // 0.0002
            Items.IRON_INGOT,     25_000L,     // 0.025
            Items.DIAMOND,        500_000L,    // 0.5
            Items.NETHERITE_INGOT, 5_000_000L, // 5.0
            Items.ELYTRA,         10_000_000L  // 10.0
    );

    private MatterValues() {
    }

    /** Полная стоимость предмета в микро-единицах материи. */
    public static long getCostMicro(Item item) {
        return VALUES.getOrDefault(item, DEFAULT_COST_MICRO);
    }

    /**
     * Выход материи при дематериализации — 75% от стоимости
     * (25% теряется при разборе).
     */
    public static long getDematerializeYieldMicro(Item item) {
        return getCostMicro(item) * 3 / 4;
    }

    /** true, если предмет есть в таблице; false — будет использоваться дефолт. */
    public static boolean isKnown(Item item) {
        return VALUES.containsKey(item);
    }

    /**
     * Форматирует микро-единицы в читаемые единицы материи:
     * 1500000 → "1.500", 100 → "0.000100".
     */
    public static String formatMicro(long micro) {
        double units = micro / (double) MICRO_PER_UNIT;
        if (units >= 1000) return String.format("%.2f", units);
        if (units >= 1) return String.format("%.3f", units);
        return String.format("%.6f", units);
    }
}
