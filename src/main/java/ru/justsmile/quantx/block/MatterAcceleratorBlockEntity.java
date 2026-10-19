package ru.justsmile.quantx.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.justsmile.quantx.QuantX;

/**
 * Волоконный ускоритель материи — «помпа» сети. Собственной ёмкости
 * нет: как водяная помпа, он лишь перекачивает поток из источника
 * в приёмник. Без ускорителя материя по сети не движется — волокна
 * это пассивные провода.
 *
 * FACING — сторона ВЫХОДА; оппозитная сторона — ВХОД.
 * Каждый тик помпа:
 *  1) ищет ВСЕ источники за входом — баки или выходы других
 *     ускорителей, сквозь сеть волокон;
 *  2) ищет ВСЕ приёмники за выходом — баки или входы других
 *     ускорителей, тоже сквозь волокна;
 *  3) переносит min(скорость, суммарно_доступно, суммарно_вмещается)
 *     материи, равномерно разбирая из источников и равномерно
 *     разливая по приёмникам — как вода по разветвлённым трубам.
 *
 * Shift+ПКМ разворачивает FACING — вход и выход меняются местами.
 */
public class MatterAcceleratorBlockEntity extends BlockEntity implements MatterConduit {

    /**
     * Скорость перекачки помпы: 4× эталонного сегмента = 200 мк/тик =
     * 4000 мк/с = 400 ед. материи в секунду.
     */
    public static final long RATE_MICRO = QuantumFiberBlockEntity.RATE_MICRO * 4;

    /** Максимум блоков при обходе проводов в поисках концов линии. */
    private static final int WIRE_SCAN_LIMIT = 256;

    /** Глубина рекурсии цепочек помп (помпа → помпа → …). */
    private static final int PUMP_CHAIN_LIMIT = 16;

    /** Последний тик, когда помпа переносила материю (для анимации). */
    private long lastFlowTick = Long.MIN_VALUE;

    public MatterAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATTER_ACCELERATOR_BE.get(), pos, state);
    }

    public Direction getFacing() {
        return getBlockState().getValue(MatterAcceleratorBlock.FACING);
    }

    /** Отметить проход материи — включить анимацию ядра. */
    private void markFlow(long gameTime) {
        if (level == null) return;
        lastFlowTick = gameTime;
        BlockState state = getBlockState();
        if (!state.getValue(MatterAcceleratorBlock.FLOW)) {
            level.setBlock(worldPosition,
                    state.setValue(MatterAcceleratorBlock.FLOW, true), 3);
        }
    }

    // ============================== Тик ==============================

    public static void tick(Level level, BlockPos pos, BlockState state,
                            MatterAcceleratorBlockEntity be) {
        if (level.isClientSide()) return;

        // Клапан перекачивает только когда открыт redstone-сигналом.
        if (isOpen(state)) {
            be.transfer(level.getGameTime(), 0);
        }

        // Анимация потока гаснет, если помпа не перекачивала пару тиков.
        if (state.getValue(MatterAcceleratorBlock.FLOW)
                && level.getGameTime() - be.lastFlowTick > 4) {
            level.setBlock(pos, state.setValue(MatterAcceleratorBlock.FLOW, false), 3);
        }
    }

    /**
     * Открыт ли этот сегмент: ускоритель — всегда, клапан — когда
     * его redstone-режим (с учётом инверсии) пропускает поток.
     */
    public static boolean isOpen(BlockState state) {
        return !(state.getBlock() instanceof MatterValveBlock)
                || state.getValue(MatterValveBlock.POWERED)
                        != state.getValue(MatterValveBlock.INVERTED);
    }

    // ======================= Перекачка (атомарно) =======================

    /**
     * Один рабочий цикл помпы: определить, сколько можно перенести
     * со входа на выход, и перенести. depth — защита от циклов
     * помпа→помпа.
     */
    private void transfer(long gameTime, int depth) {
        if (depth >= PUMP_CHAIN_LIMIT || level == null) return;

        long supply = peekSupply(level, RATE_MICRO, depth);
        if (supply <= 0) return;
        long demand = peekSink(level, RATE_MICRO, depth);
        long amount = Math.min(supply, demand);
        if (amount <= 0) return;

        long taken = takeSupply(level, amount, depth);
        if (taken <= 0) return;
        long delivered = putSink(level, taken, depth);
        long leftover = taken - delivered;
        if (leftover > 0) {
            // Приёмник не вместил всё обещанное — вернуть источнику.
            refundSupply(level, leftover, depth);
        }
        if (delivered > 0) {
            markFlow(gameTime);
        }
    }

    // ===== Вход: поиск и выкачка источника =====

    /** Сколько материи суммарно можно забрать со входа (без записи). */
    private long peekSupply(Level level, long want, int depth) {
        long total = 0L;
        for (Endpoint src : findEndpoints(level, getFacing().getOpposite(), true)) {
            total += switch (src) {
                case TankEnd t -> Math.min(Math.min(want, t.tank().getMatterMicro()),
                        t.tank().getTransferRateMicro());
                case PumpEnd p -> p.pump().provideOutPeek(level, want, depth + 1);
            };
        }
        return total;
    }

    /**
     * Забрать n материи из источников на входе, равномерно по всем.
     * Недобранная доля источника перекатывается к следующим.
     */
    private long takeSupply(Level level, long want, int depth) {
        List<Endpoint> srcs = findEndpoints(level, getFacing().getOpposite(), true);
        long taken = 0L;
        long remaining = want;
        for (int i = 0; i < srcs.size() && remaining > 0; i++) {
            Endpoint src = srcs.get(i);
            long share = remaining / (srcs.size() - i);
            if (share <= 0) break;
            // Скорость источника ограничивает его долю: медленный бак
            // не отдаст быстрее своего рейтинга даже в быструю сеть.
            long pulled = switch (src) {
                case TankEnd t -> t.tank().takeMatter(
                        Math.min(share, t.tank().getTransferRateMicro()));
                case PumpEnd p -> p.pump().provideOut(level, share, depth + 1);
            };
            if (pulled > 0) {
                src.markFlow(level, level.getGameTime(), pulled);
                taken += pulled;
                remaining -= pulled;
            }
        }
        return taken;
    }

    /** Вернуть материю источникам на входе (компенсация недовлива). */
    private void refundSupply(Level level, long micro, int depth) {
        List<Endpoint> srcs = findEndpoints(level, getFacing().getOpposite(), true);
        long remaining = micro;
        for (int i = 0; i < srcs.size() && remaining > 0; i++) {
            Endpoint src = srcs.get(i);
            long share = remaining / (srcs.size() - i);
            if (share <= 0) break;
            remaining -= switch (src) {
                case TankEnd t -> t.tank().addMatter(share);
                case PumpEnd p -> {
                    p.pump().returnIn(level, share, depth + 1);
                    yield share;
                }
            };
        }
    }

    // ===== Выход: поиск и наполнение приёмника =====

    /** Сколько материи суммарно вместят приёмники за выходом (без записи). */
    private long peekSink(Level level, long want, int depth) {
        long total = 0L;
        for (Endpoint sink : findEndpoints(level, getFacing(), false)) {
            total += switch (sink) {
                case TankEnd t -> Math.min(Math.min(want,
                        t.tank().getCapacityMicro() - t.tank().getMatterMicro()),
                        t.tank().getTransferRateMicro());
                case PumpEnd p -> p.pump().forwardThroughPeek(level, want, depth + 1);
            };
        }
        return total;
    }

    /**
     * Влить n материи в приёмники за выходом, равномерно по всем.
     * Непринятая доля приёмника перекатывается к следующим.
     */
    private long putSink(Level level, long micro, int depth) {
        List<Endpoint> sinks = findEndpoints(level, getFacing(), false);
        long put = 0L;
        long remaining = micro;
        for (int i = 0; i < sinks.size() && remaining > 0; i++) {
            Endpoint sink = sinks.get(i);
            long share = remaining / (sinks.size() - i);
            if (share <= 0) break;
            // Скорость приёмника ограничивает его долю — быстрая линия
            // в медленный бак ждёт его рейтинг.
            long accepted = switch (sink) {
                case TankEnd t -> t.tank().addMatter(
                        Math.min(share, t.tank().getTransferRateMicro()));
                case PumpEnd p -> p.pump().forwardThrough(level, share, depth + 1);
            };
            if (accepted > 0) {
                sink.markFlow(level, level.getGameTime(), accepted);
                put += accepted;
                remaining -= accepted;
            }
        }
        return put;
    }

    // ===== Рекурсия помп =====

    /** Пиковая отдача помпы наружу (её выход): сколько она вытащит со входа. */
    private long provideOutPeek(Level level, long want, int depth) {
        return Math.min(want, peekSupply(level, RATE_MICRO, depth));
    }

    /** Отдать материю соседу у своего выхода — берём со входа. */
    private long provideOut(Level level, long want, int depth) {
        return takeSupply(level, Math.min(want, RATE_MICRO), depth);
    }

    /** Пиковая пропускная способность помпы насквозь до её приёмника. */
    private long forwardThroughPeek(Level level, long want, int depth) {
        return Math.min(want, peekSink(level, RATE_MICRO, depth));
    }

    /** Пропустить материю насквозь в приёмник за своим выходом. */
    private long forwardThrough(Level level, long micro, int depth) {
        return putSink(level, Math.min(micro, RATE_MICRO), depth);
    }

    /** Вернуть материю назад в источник на входе этой помпы. */
    private void returnIn(Level level, long micro, int depth) {
        refundSupply(level, micro, depth);
    }

    // ===================== Поиск концов по проводам =====================

    /** Конец линии: бак или соседняя помпа + путь проводов до неё. */
    private sealed interface Endpoint {
        void markFlow(Level level, long gameTime, long amount);
    }

    private record TankEnd(MatterTank tank, List<BlockPos> path)
            implements Endpoint {
        @Override
        public void markFlow(Level level, long gameTime, long amount) {
            markWires(level, path, gameTime, amount);
        }
    }

    private record PumpEnd(MatterAcceleratorBlockEntity pump, List<BlockPos> path)
            implements Endpoint {
        @Override
        public void markFlow(Level level, long gameTime, long amount) {
            markWires(level, path, gameTime, amount);
            pump.markFlow(gameTime);
        }
    }

    private static void markWires(Level level, List<BlockPos> path, long gameTime,
                                  long amount) {
        for (BlockPos p : path) {
            BlockEntity wire = level.getBlockEntity(p);
            if (wire instanceof QuantumFiberBlockEntity fiber) {
                fiber.markFlow(gameTime);
            } else if (wire instanceof MatterFlowMeterBlockEntity meter) {
                meter.markFlow(gameTime, amount);
            }
        }
    }

    /**
     * BFS по сети волокон от стороны face — собрать ВСЕ концы линии:
     * баки и ускорители, смотрящие правильной стороной:
     *  - в режиме source помпа-партнёр должна выводить в точку
     *    контакта (её FACING направлен на блок, из которого пришёл путь);
     *  - в режиме sink помпа-партнёр должна принимать от точки
     *    контакта (её входная сторона смотрит на путь).
     * Волокна — пассивные провода: обходим их в любом направлении.
     */
    private List<Endpoint> findEndpoints(Level level, Direction face, boolean source) {
        List<Endpoint> ends = new ArrayList<>();
        BlockPos pumpPos = worldPosition;
        BlockPos start = pumpPos.relative(face);
        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(pumpPos);
        if (!seen.add(start)) return ends;
        queue.add(start);
        prev.put(start, pumpPos);

        int scanned = 0;
        while (!queue.isEmpty() && scanned < WIRE_SCAN_LIMIT) {
            BlockPos p = queue.poll();
            scanned++;
            BlockEntity be = level.getBlockEntity(p);

            if (be instanceof MatterTank tank) {
                ends.add(new TankEnd(tank, buildPath(prev, p, pumpPos)));
                continue; // конец линии — за бак не ходим
            }
            if (be instanceof MatterAcceleratorBlockEntity acc) {
                BlockPos from = prev.get(p);
                Direction contact = Direction.getNearest(
                        p.getX() - from.getX(),
                        p.getY() - from.getY(),
                        p.getZ() - from.getZ(), null);
                boolean facingUs = contact != null
                        && isOpen(acc.getBlockState())                      // закрытый клапан — тупик
                        && (source
                            ? acc.getFacing() == contact.getOpposite()      // выводит в нас
                            : acc.getFacing() == contact);                  // входом к нам
                if (facingUs) {
                    ends.add(new PumpEnd(acc, buildPath(prev, p, pumpPos)));
                }
                continue; // помпа — конец или тупик, но не провод
            }
            if (be instanceof MatterFlowMeterBlockEntity) {
                // Счётчик проводит только к узлам сети: бак вплотную
                // без волокна — не подключение и не конец линии.
                for (Direction d : Direction.values()) {
                    BlockPos next = p.relative(d);
                    if (level.getBlockEntity(next) instanceof MatterConduit
                            && seen.add(next)) {
                        prev.put(next, p);
                        queue.add(next);
                    }
                }
            } else if (be instanceof MatterConduit) { // волокна — провода
                for (Direction d : Direction.values()) {
                    BlockPos next = p.relative(d);
                    if (seen.add(next)) {
                        prev.put(next, p);
                        queue.add(next);
                    }
                }
            }
            // Прочие блоки — тупики.
        }
        return ends;
    }

    /** Восстановить путь проводов от помпы до найденного конца. */
    private static List<BlockPos> buildPath(Map<BlockPos, BlockPos> prev,
                                            BlockPos end, BlockPos pumpPos) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos p = end; p != null && !p.equals(pumpPos); p = prev.get(p)) {
            path.add(p);
        }
        return path;
    }
}