package ru.justsmile.quantx.menu;

import net.minecraft.world.inventory.ContainerData;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.block.MaterializerBlockEntity;

/**
 * Синхронизация материи (long) между сервером и клиентом.
 * Материя единая — без тиров.
 *
 * ВАЖНО: ContainerData синхронизируется шортами (16 бит) —
 * int-части long >32767 обрезались и давали числа вида 429494...
 * Поэтому каждый long передаётся четырьмя 16-битными чанками.
 *
 * Ячейки 0-3: matter (4 чанка, от старших бит к младшим).
 * Ячейки 4-7: стоимость выбранного рецепта (4 чанка).
 * Ячейка 8: режим (ordinal). 9: прогресс. 10: макс. прогресс.
 * Ячейка 11: число изученных рецептов. 12: флаг running.
 * Ячейки 13-16: ёмкость бака (4 чанка).
 * Ячейка 17: статус машины (ordinal Status) — строка состояния над GUI.
 */
public class MatterContainerData implements ContainerData {

    public static final int SIZE = 18;

    // Индексы ячеек.
    public static final int IDX_MATTER = 0;
    public static final int IDX_COST = 4;
    public static final int IDX_MODE = 8;
    public static final int IDX_PROGRESS = 9;
    public static final int IDX_MAX_PROGRESS = 10;
    public static final int IDX_KNOWN_COUNT = 11;
    public static final int IDX_RUNNING = 12;
    public static final int IDX_CAPACITY = 13;
    public static final int IDX_STATUS = 17;

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
            long capacity = be.getCapacityMicro();
            return switch (index) {
                case IDX_MATTER -> (int) (matter >>> 48);
                case IDX_MATTER + 1 -> (int) (matter >>> 32);
                case IDX_MATTER + 2 -> (int) (matter >>> 16);
                case IDX_MATTER + 3 -> (int) matter;
                case IDX_COST -> (int) (cost >>> 48);
                case IDX_COST + 1 -> (int) (cost >>> 32);
                case IDX_COST + 2 -> (int) (cost >>> 16);
                case IDX_COST + 3 -> (int) cost;
                case IDX_CAPACITY -> (int) (capacity >>> 48);
                case IDX_CAPACITY + 1 -> (int) (capacity >>> 32);
                case IDX_CAPACITY + 2 -> (int) (capacity >>> 16);
                case IDX_CAPACITY + 3 -> (int) capacity;
                case IDX_MODE -> be.getMode().ordinal();
                case IDX_PROGRESS -> be.getProgress();
                case IDX_MAX_PROGRESS -> be.getMaxProgress();
                case IDX_KNOWN_COUNT -> be.getKnownRecipes().size();
                case IDX_RUNNING -> be.isRunning() ? 1 : 0;
                case IDX_STATUS -> be.getStatus().ordinal();
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

    // ===== Сборка long из четырёх 16-битных чанков =====

    public static long packLong(int c0, int c1, int c2, int c3) {
        return ((long) (c0 & 0xFFFF) << 48)
                | ((long) (c1 & 0xFFFF) << 32)
                | ((long) (c2 & 0xFFFF) << 16)
                | (c3 & 0xFFFFL);
    }
}