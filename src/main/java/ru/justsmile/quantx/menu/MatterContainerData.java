package ru.justsmile.quantx.menu;

import net.minecraft.world.inventory.ContainerData;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.block.MaterializerBlockEntity;

/**
 * Синхронизация материи (long) между сервером и клиентом.
 * Материя единая — без тиров. long разбивается на 2 int.
 *
 * Ячейки 0-1: matterHigh, matterLow.
 * Ячейки 2-5: режим (ordinal), прогресс, макс. прогресс, число изученных рецептов.
 * Ячейки 6-7: стоимость выбранного рецепта (long, два int).
 * Ячейка 8: флаг running (1 — работает, 0 — стоп).
 */
public class MatterContainerData implements ContainerData {

    public static final int SIZE = 9;

    // Индексы ячеек.
    public static final int IDX_MODE = 2;
    public static final int IDX_PROGRESS = 3;
    public static final int IDX_MAX_PROGRESS = 4;
    public static final int IDX_KNOWN_COUNT = 5;
    public static final int IDX_COST_HIGH = 6;
    public static final int IDX_COST_LOW = 7;
    public static final int IDX_RUNNING = 8;

    @Nullable
    private final MaterializerBlockEntity be;
    private final int[] clientBuffer = new int[SIZE];

    public MatterContainerData(@Nullable MaterializerBlockEntity be) {
        this.be = be;
    }

    @Override
    public int get(int index) {
        if (be != null) {
            long matter = be.getMatterMicro();
            long cost = be.getSelectedCostMicro();
            return switch (index) {
                case 0 -> (int) (matter >>> 32);
                case 1 -> (int) matter;
                case IDX_MODE -> be.getMode().ordinal();
                case IDX_PROGRESS -> be.getProgress();
                case IDX_MAX_PROGRESS -> be.getMaxProgress();
                case IDX_KNOWN_COUNT -> be.getKnownRecipes().size();
                case IDX_COST_HIGH -> (int) (cost >>> 32);
                case IDX_COST_LOW -> (int) cost;
                case IDX_RUNNING -> be.isRunning() ? 1 : 0;
                default -> 0;
            };
        } else {
            return clientBuffer[index];
        }
    }

    @Override
    public void set(int index, int value) {
        // Сеттер вызывается только на клиенте — заполняем локальный буфер.
        if (be == null && index >= 0 && index < SIZE) {
            clientBuffer[index] = value;
        }
    }

    @Override
    public int getCount() {
        return SIZE;
    }

    // ===== Утилиты для сборки long из двух int =====

    public static long packLong(int high, int low) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }
}