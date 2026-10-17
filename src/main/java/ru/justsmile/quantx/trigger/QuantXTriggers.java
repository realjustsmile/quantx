package ru.justsmile.quantx.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.justsmile.quantx.QuantX;

/**
 * Кастомные триггеры достижений QuantX.
 * Каждый триггер «безусловный»: срабатывает на действие машины,
 * условий в JSON не нужно (conditions: {}).
 */
public final class QuantXTriggers {

    /**
     * DeferredRegister на ванильный реестр trigger_type.
     * Нельзя дёргать CriteriaTriggers.register в commonSetup — к этому
     * моменту реестр уже заморожен. Регистрация идёт через RegisterEvent.
     */
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, QuantX.MODID);

    /** Изучен рецепт (завершилось сканирование). */
    public static final RegistryObject<SimpleTrigger> SCANNED =
            TRIGGERS.register("scanned", SimpleTrigger::new);

    /** Завершён разбор предмета в материю. */
    public static final RegistryObject<SimpleTrigger> DEMATERIALIZED =
            TRIGGERS.register("dematerialized", SimpleTrigger::new);

    /** Завершена сборка предмета из материи. */
    public static final RegistryObject<SimpleTrigger> MATERIALIZED =
            TRIGGERS.register("materialized", SimpleTrigger::new);

    private QuantXTriggers() {
    }

    /**
     * Простой триггер без параметров: один record-инстанс с пустым player.
     * Кодек — unit: в advancement JSON достаточно "conditions": {}.
     */
    public static class SimpleTrigger extends SimpleCriterionTrigger<SimpleTrigger.Instance> {

        private static final Codec<Instance> CODEC =
                MapCodec.unitCodec(new Instance(Optional.empty()));

        @Override
        public Codec<Instance> codec() {
            return CODEC;
        }

        /** Выстрелить триггер игроку (только сервер). */
        public void fire(ServerPlayer player) {
            trigger(player, instance -> true);
        }

        public record Instance(Optional<Holder<LootItemCondition>> player)
                implements SimpleCriterionTrigger.SimpleInstance {
        }
    }
}
