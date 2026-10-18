package ru.justsmile.quantx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Стартовый справочник QuantX: книга с иллюстрированными рецептами
 * мода (GuideBookItem → GuideScreen). Выдаётся игроку один раз при
 * первом входе в мир — флаг в persistentData переживает смерти
 * и перезаходы.
 */
public final class QuantXGuideBook {

    private static final String GIVEN_TAG = "quantxGuideBookGiven";

    private QuantXGuideBook() {
    }

    /** Отдать книгу игроку, если он ещё не получал её в этом мире. */
    public static void giveIfFirstLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var data = player.getPersistentData();
        if (data.getBooleanOr(GIVEN_TAG, false)) return;
        data.putBoolean(GIVEN_TAG, true);

        // addItem сам выкладывает стек в инвентарь или роняет его у ног,
        // если места нет, и шлёт клиенту обновление контейнера.
        player.addItem(new ItemStack(QuantX.GUIDE_BOOK.get()));
    }
}
