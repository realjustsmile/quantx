package ru.justsmile.quantx.block;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.justsmile.quantx.QuantX;
import ru.justsmile.quantx.matter.MatterValues;
import ru.justsmile.quantx.trigger.QuantXTriggers;

/**
 * Блок-материализатор. Хранит материю как числа (микро-единицы)
 * и инвентарь из 2 слотов (вход/выход).
 */
public class MaterializerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    /** Режимы работы материализатора. */
    public enum Mode {
        /** Изучение предмета — запись рецепта в knownRecipes. */
        SCAN,
        /** Разбор предмета в серую материю с потерями. */
        DEMATERIALIZE,
        /** Сборка изученного предмета из материи. */
        MATERIALIZE
    }

    // Длительности операций в тиках.
    public static final int SCAN_TICKS = 100;
    public static final int DEMATERIALIZE_TICKS = 40;
    public static final int MATERIALIZE_TICKS = 60;

    // ===== Инвентарь =====
    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // В выходной слот нельзя класть ничего вручную
            return slot != SLOT_OUTPUT;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64 * 16;
        }

        @Override
        protected int getStackLimit(int slot, @NotNull ItemStack stack) {
            // Расширенные стеки: слот вмещает 16 максимальных стаков предмета.
            return stack.getMaxStackSize() * 16;
        }
    };

    /**
     * Представление инвентаря для автоматики (воронки, трубы других модов).
     * Вставка — только во входной слот, извлечение — только из выходного.
     * Направление стороны не учитываем: у машины нет ориентации.
     */
    private final IItemHandler automationHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != SLOT_INPUT) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUTPUT) return ItemStack.EMPTY;
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };

    // ===== Материя (микро-единицы, единая) =====
    private long matterMicro = 0L;

    /**
     * «Призрачный» слот: показывает иконку выбранного рецепта в GUI.
     * Не является настоящим инвентарём — только отображение.
     * Синхронизируется бесплатно, т.к. стеки слотов шлются клиенту автоматически.
     */
    private final ItemStackHandler displayInventory = new ItemStackHandler(1);

    // ===== Состояние машины =====
    private Mode mode = Mode.SCAN;
    private int progress = 0;
    private int maxProgress = SCAN_TICKS;

    /** Машина работает только когда running=true. Новый блок стоит остановленным. */
    private boolean running = false;

    /**
     * Последний взаимодействовавший игрок — для ачивок.
     * Не сохраняется: сессионное значение, достаточное для триггеров.
     */
    @Nullable
    private UUID lastUser = null;

    /** Изученные рецепты: идентификатор предмета -> стоимость в микро-серых. */
    private final Map<Identifier, Long> knownRecipes = new HashMap<>();

    /** Выбранный рецепт для сборки (null — ничего не выбрано). */
    @Nullable
    private Identifier selectedRecipe = null;

    public MaterializerBlockEntity(BlockPos pos, BlockState state) {
        super(QuantX.MATERIALIZER_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /**
     * Хендлер для автоматики (ForgeCapabilities.ITEM_HANDLER).
     * Вставка только во вход, извлечение только из выхода.
     */
    public IItemHandler getAutomationHandler() {
        return automationHandler;
    }

    /** Запомнить игрока, чтобы ачивки улетали именно ему. */
    public void setLastUser(@Nullable UUID user) {
        this.lastUser = user;
    }

    /**
     * Выстрелить триггер достижения последнему пользователю машины,
     * если он сейчас на сервере. Молча пропускаем, если игрока нет.
     */
    private void fireTrigger(QuantXTriggers.SimpleTrigger trigger) {
        if (level == null || level.getServer() == null || lastUser == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(lastUser);
        if (player != null) {
            trigger.fire(player);
        }
    }

    /** Инвентарь-призрак с иконкой выбранного рецепта. */
    public ItemStackHandler getDisplayInventory() {
        return displayInventory;
    }

    // ===== Режим и прогресс =====

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        // При смене режима прогресс сбрасывается и машина останавливается —
        // запускать новую операцию нужно кнопкой «Старт».
        this.progress = 0;
        this.maxProgress = ticksForMode(mode);
        this.running = false;
        setChanged();
    }

    /** Длительность операции для режима. */
    public static int ticksForMode(Mode mode) {
        return switch (mode) {
            case SCAN -> SCAN_TICKS;
            case DEMATERIALIZE -> DEMATERIALIZE_TICKS;
            case MATERIALIZE -> MATERIALIZE_TICKS;
        };
    }

    public boolean isRunning() {
        return running;
    }

    /** Переключить работу (Старт/Стоп из GUI). При остановке прогресс сбрасывается. */
    public void toggleRunning() {
        running = !running;
        if (!running) {
            progress = 0;
        }
        setChanged();
    }

    /**
     * Стоимость выбранного рецепта в микро-серых (0 — не выбран/неизвестен).
     * Цена всегда живая из MatterValues: значения в knownRecipes могли
     * устареть (старые версии таблицы, баги) — не доверяем им.
     */
    public long getSelectedCostMicro() {
        if (selectedRecipe == null || !isRecipeKnown(selectedRecipe)) return 0L;
        Item item = BuiltInRegistries.ITEM.getValue(selectedRecipe);
        return item == null ? 0L : MatterValues.getCostMicro(item);
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    /** Переключить режим по кругу: SCAN -> DEMATERIALIZE -> MATERIALIZE -> SCAN. */
    public void cycleMode() {
        Mode[] modes = Mode.values();
        setMode(modes[(mode.ordinal() + 1) % modes.length]);
        // Вошли в сборку без выбранного рецепта — берём первый изученный.
        if (mode == Mode.MATERIALIZE && selectedRecipe == null && !knownRecipes.isEmpty()) {
            cycleRecipe(1);
        }
    }

    // ===== Изученные рецепты =====

    /** Запомнить предмет и его стоимость (в микро-серых). */
    public void learnRecipe(Identifier itemId, long costMicro) {
        knownRecipes.put(itemId, costMicro);
        // Первый изученный рецепт сразу выбираем — призрачный слот покажет предмет.
        if (selectedRecipe == null) {
            setSelectedRecipe(itemId);
        }
        setChanged();
    }

    public boolean isRecipeKnown(Identifier itemId) {
        return knownRecipes.containsKey(itemId);
    }

    /** Только для чтения — изменять рецепты через learnRecipe. */
    public Map<Identifier, Long> getKnownRecipes() {
        return Collections.unmodifiableMap(knownRecipes);
    }

    // ===== Выбранный рецепт (для MATERIALIZE) =====

    @Nullable
    public Identifier getSelectedRecipe() {
        return selectedRecipe;
    }

    public void setSelectedRecipe(@Nullable Identifier id) {
        if (java.util.Objects.equals(this.selectedRecipe, id)) return;
        this.selectedRecipe = id;
        // Смена рецепта — останавливаем машину и сбрасываем прогресс.
        this.progress = 0;
        this.running = false;
        updateDisplaySlot();
        setChanged();
    }

    /**
     * Листает выбранный рецепт по списку изученных (в алфавитном порядке id).
     * direction: +1 — следующий, -1 — предыдущий. По кругу.
     */
    public void cycleRecipe(int direction) {
        if (knownRecipes.isEmpty()) {
            setSelectedRecipe(null);
            return;
        }
        List<Identifier> ids = new ArrayList<>(knownRecipes.keySet());
        ids.sort(Identifier::compareTo);
        int index = (selectedRecipe == null) ? -1 : ids.indexOf(selectedRecipe);
        int next = Math.floorMod(index + direction, ids.size());
        setSelectedRecipe(ids.get(next));
    }

    /** Обновляет иконку призрачного слота под текущий рецепт. */
    private void updateDisplaySlot() {
        if (selectedRecipe == null) {
            displayInventory.setStackInSlot(0, ItemStack.EMPTY);
            return;
        }
        Item item = BuiltInRegistries.ITEM.getValue(selectedRecipe);
        ItemStack stack = (item == null) ? ItemStack.EMPTY : new ItemStack(item);
        displayInventory.setStackInSlot(0, stack);
    }

    /**
     * Полная остановка машины: running=false и прогресс в ноль.
     * Вызывается, когда действие режима выполнить нельзя
     * (пустой вход, нет рецепта/материи, выход не принимает).
     */
    private void stop() {
        running = false;
        progress = 0;
        setChanged();
    }

    // ===== Тик =====

    /**
     * Вызывается каждый игровой тик тикером блока.
     * Работаем только на сервере; клиентская сторона ничего не считает.
     */
    public static void tick(Level level, BlockPos pos, BlockState state,
                            MaterializerBlockEntity be) {
        if (level.isClientSide()) return;
        // Ленивый индекс рецептов для ценообразования — строится один раз.
        if (level.getServer() != null) {
            MatterValues.init(level.getServer().getRecipeManager());
        }
        // Синк «работает» в BlockState — клиент по нему спавнит частицы.
        if (state.getValue(MaterializerBlock.ACTIVE) != be.running) {
            level.setBlock(pos, state.setValue(MaterializerBlock.ACTIVE, be.running), 3);
        }
        if (!be.running) return;
        switch (be.mode) {
            case SCAN -> be.tickScan(level, pos, state);
            case DEMATERIALIZE -> be.tickDematerialize(level, pos, state);
            case MATERIALIZE -> be.tickMaterialize(level, pos, state);
        }
    }

    /**
     * Этап 6в: изучить предмет во входном слоте, записать в knownRecipes.
     * Сканирование уничтожает 1 предмет, но не даёт материи — только рецепт.
     * Если предмет уже изучен — ждём, пока его заберут (ничего не делаем).
     */
    private void tickScan(Level level, BlockPos pos, BlockState state) {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) {
            stop();
            return;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(input.getItem());
        if (isRecipeKnown(itemId)) {
            // Уже изучен — повторно не уничтожаем. Стоп, пока не заберут.
            stop();
            return;
        }

        progress++;
        if (progress >= maxProgress) {
            progress = 0;
            learnRecipe(itemId, MatterValues.getCostMicro(input.getItem()));
            inventory.extractItem(SLOT_INPUT, 1, false);
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            fireTrigger(QuantXTriggers.SCANNED.get());
            setChanged();
        }
    }

    /**
     * Разобрать предмет из входного слота, добавить материю.
     * Выход = 75% от стоимости (25% теряется при разборе).
     */
    private void tickDematerialize(Level level, BlockPos pos, BlockState state) {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) {
            stop();
            return;
        }

        progress++;
        if (progress >= maxProgress) {
            progress = 0;
            long yield = MatterValues.getDematerializeYieldMicro(input.getItem());
            inventory.extractItem(SLOT_INPUT, 1, false);
            addMatter(yield);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.4f, 1.2f);
            fireTrigger(QuantXTriggers.DEMATERIALIZED.get());
            setChanged();
        }
    }

    /**
     * Этап 6г: собрать выбранный изученный предмет из материи в выходной слот.
     * Работает только если: рецепт выбран и изучен, хватает материи,
     * выходной слот может принять предмет.
     */
    private void tickMaterialize(Level level, BlockPos pos, BlockState state) {
        if (selectedRecipe == null || !isRecipeKnown(selectedRecipe)) {
            stop();
            return;
        }

        Item item = BuiltInRegistries.ITEM.getValue(selectedRecipe);
        if (item == null) {
            stop();
            return;
        }

        long cost = getSelectedCostMicro();
        if (matterMicro < cost) {
            // Не хватает материи — останавливаемся.
            stop();
            return;
        }

        // Проверяем, что выходной слот сможет принять предмет.
        // insertItem нельзя: isItemValid запрещает класть в выход (анти-воронка).
        // Поэтому работаем напрямую: setStackInSlot/grow обходят проверку.
        ItemStack result = new ItemStack(item);
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        boolean fits = out.isEmpty()
                || (ItemStack.isSameItemSameComponents(out, result)
                        && out.getCount() + result.getCount() <= out.getMaxStackSize() * 16);
        if (!fits) {
            // Выход не принимает предмет — останавливаемся.
            stop();
            return;
        }

        progress++;
        if (progress >= maxProgress) {
            progress = 0;
            consumeMatter(cost);
            if (out.isEmpty()) {
                inventory.setStackInSlot(SLOT_OUTPUT, result);
            } else {
                out.grow(result.getCount());
            }
            level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL,
                    SoundSource.BLOCKS, 0.7f, 1.3f);
            fireTrigger(QuantXTriggers.MATERIALIZED.get());
            setChanged();
        }
    }

    // ===== Материя =====

    public long getMatterMicro() { return matterMicro; }

    /** Добавить материю (в микро-единицах). */
    public void addMatter(long micro) {
        if (micro <= 0) return;
        matterMicro += micro;
        setChanged();
    }

    /** Списать материю при сборке. false — не хватает. */
    public boolean consumeMatter(long costMicro) {
        if (costMicro <= 0) return true;
        if (matterMicro < costMicro) return false;
        matterMicro -= costMicro;
        setChanged();
        return true;
    }

    // ===== Сохранение =====

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        output.putLong("MatterMicro", matterMicro);
        // Масштаб 2: 1 единица материи = 10 микро (был 1_000_000).
        output.putInt("MatterScale", 2);

        output.putInt("Mode", mode.ordinal());
        output.putInt("Progress", progress);
        output.putInt("MaxProgress", maxProgress);
        output.putBoolean("Running", running);

        // Изученные рецепты: ключ — строка вида "minecraft:iron_ingot", значение — long.
        CompoundTag recipesTag = new CompoundTag();
        for (Map.Entry<Identifier, Long> entry : knownRecipes.entrySet()) {
            recipesTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        output.store("KnownRecipes", CompoundTag.CODEC, recipesTag);

        if (selectedRecipe != null) {
            output.store("SelectedRecipe", Codec.STRING, selectedRecipe.toString());
        }

        // Инвентарь сохраняем как CompoundTag через HolderLookup.Provider.
        HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
        if (registries != null) {
            CompoundTag invTag = inventory.serializeNBT(registries);
            output.store("Inventory", CompoundTag.CODEC, invTag);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        // Единая материя; миграция старых тиров в эквивалент (x10/x100).
        long storedMatter = input.getLongOr("MatterMicro",
                input.getLongOr("GrayMicro", 0L)
                        + input.getLongOr("BlueMicro", 0L) * 10
                        + input.getLongOr("GoldMicro", 0L) * 100);
        // Миграция масштаба: старые сейвы (без MatterScale) хранили микро
        // при 1 ед. = 1_000_000 мк; теперь 1 ед. = 10 мк → делим на 100_000.
        if (input.getIntOr("MatterScale", 1) < 2) {
            storedMatter /= 100_000L;
        }
        matterMicro = storedMatter;

        int modeOrdinal = input.getIntOr("Mode", 0);
        Mode[] modes = Mode.values();
        mode = (modeOrdinal >= 0 && modeOrdinal < modes.length)
                ? modes[modeOrdinal]
                : Mode.SCAN;
        progress = input.getIntOr("Progress", 0);
        maxProgress = input.getIntOr("MaxProgress", ticksForMode(mode));
        running = input.getBooleanOr("Running", false);

        knownRecipes.clear();
        input.read("KnownRecipes", CompoundTag.CODEC).ifPresent(recipesTag -> {
            for (String key : recipesTag.keySet()) {
                Identifier id = Identifier.tryParse(key);
                if (id == null) continue;
                recipesTag.getLong(key).ifPresent(cost -> knownRecipes.put(id, cost));
            }
        });

        selectedRecipe = input.read("SelectedRecipe", Codec.STRING)
                .map(Identifier::tryParse)
                .orElse(null);
        updateDisplaySlot();

        HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
        if (registries != null) {
            input.read("Inventory", CompoundTag.CODEC).ifPresent(invTag ->
                    inventory.deserializeNBT(registries, invTag));
        }
    }

    // ===== MenuProvider =====

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantx.materializer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ru.justsmile.quantx.menu.MaterializerMenu(windowId, playerInv, this);
    }
}