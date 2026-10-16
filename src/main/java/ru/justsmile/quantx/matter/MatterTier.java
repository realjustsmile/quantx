package ru.justsmile.quantx.matter;

/**
 * Три тира квантовой материи.
 *
 * weightInGray — сколько "серых единиц" стоит 1 единица этого тира.
 * Храним материю в "микро-единицах": 1 единица = 1_000_000 микро.
 * Это позволяет хранить дробные значения (0.0001) без потери точности.
 */
public enum MatterTier {

    GRAY("gray", 1),
    BLUE("blue", 10),
    GOLD("gold", 100);

    /** 1 единица материи = 1_000_000 микро. */
    public static final long MICRO_PER_UNIT = 1_000_000L;

    private final String name;
    private final long weightInGray;

    MatterTier(String name, long weightInGray) {
        this.name = name;
        this.weightInGray = weightInGray;
    }

    public String getName() {
        return name;
    }

    public long getWeightInGray() {
        return weightInGray;
    }

    /** Следующий тир вверх (или null, если это GOLD). */
    public MatterTier nextTier() {
        MatterTier[] values = values();
        int next = ordinal() + 1;
        return next < values.length ? values[next] : null;
    }

    /** Предыдущий тир вниз (или null, если это GRAY). */
    public MatterTier previousTier() {
        int prev = ordinal() - 1;
        return prev >= 0 ? values()[prev] : null;
    }
}