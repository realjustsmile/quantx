package ru.justsmile.quantx.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ru.justsmile.quantx.client.GuideScreen;

/**
 * Справочник QuantX — книга с рецептами мода.
 * ПКМ — открывает экран с иллюстрированными крафтами.
 */
public class GuideBookItem extends Item {

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // GuideScreen — клиентский класс; isClientSide гарантирует,
            // что на сервере он никогда не загрузится.
            GuideScreen.open();
        }
        return InteractionResult.SUCCESS;
    }
}
