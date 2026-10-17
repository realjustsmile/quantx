package ru.justsmile.quantx;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import ru.justsmile.quantx.block.MaterializerBlock;
import ru.justsmile.quantx.block.MaterializerBlockEntity;
import ru.justsmile.quantx.item.PunchCardItem;
import ru.justsmile.quantx.matter.MatterValues;
import ru.justsmile.quantx.menu.MaterializerMenu;
import ru.justsmile.quantx.menu.MaterializerScreen;
import ru.justsmile.quantx.trigger.QuantXTriggers;

@Mod(QuantX.MODID)
public class QuantX {

    public static final String MODID = "quantx";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, name));
    }

    public static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, name));
    }

    public static final RegistryObject<Block> MATERIALIZER = BLOCKS.register("materializer",
            () -> new MaterializerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .setId(blockKey("materializer"))));
    public static final RegistryObject<Item> MATERIALIZER_ITEM = ITEMS.register("materializer",
            () -> new BlockItem(MATERIALIZER.get(), new Item.Properties().setId(itemKey("materializer"))));

    public static final RegistryObject<BlockEntityType<MaterializerBlockEntity>> MATERIALIZER_BE =
            BLOCK_ENTITIES.register("materializer",
                    () -> new BlockEntityType<>(MaterializerBlockEntity::new,
                            Set.of(MATERIALIZER.get())));

    public static final RegistryObject<MenuType<MaterializerMenu>> MATERIALIZER_MENU =
            MENUS.register("materializer",
                    () -> IForgeMenuType.create(MaterializerMenu::new));

    /** Перфокарта — перенос изученных рецептов. В стаке 1 шт., хранит данные. */
    public static final RegistryObject<Item> PUNCH_CARD = ITEMS.register("punch_card",
            () -> new PunchCardItem(new Item.Properties()
                    .setId(itemKey("punch_card")).stacksTo(1)));

    public QuantX(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        ITEMS.register(modBusGroup);
        BLOCKS.register(modBusGroup);
        BLOCK_ENTITIES.register(modBusGroup);
        MENUS.register(modBusGroup);
        QuantXTriggers.TRIGGERS.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::clientSetup);
        BusGroup.DEFAULT.register(MethodHandles.lookup(), this);
    }

    /**
     * Подключаем ITEM_HANDLER к каждому материализатору:
     * вставка только во вход, извлечение только из выхода.
     */
    @SubscribeEvent
    private static void attachMaterializerCaps(AttachCapabilitiesEvent.BlockEntities event) {
        if (!(event.getObject() instanceof MaterializerBlockEntity be)) return;
        event.addCapability(
                Identifier.fromNamespaceAndPath(MODID, "inventory"),
                // getCapability generic — лямбдой не реализовать, нужен класс.
                new net.minecraftforge.common.capabilities.ICapabilityProvider() {
                    @Override
                    public <T> LazyOptional<T> getCapability(
                            net.minecraftforge.common.capabilities.Capability<T> cap,
                            net.minecraft.core.Direction side) {
                        if (cap == ForgeCapabilities.ITEM_HANDLER) {
                            return LazyOptional.of(be::getAutomationHandler).cast();
                        }
                        return LazyOptional.empty();
                    }
                });
    }

    /** Тултип «Стоимость/Выход материи» на каждом предмете. */
    @SubscribeEvent
    private static void addMatterTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        long cost = MatterValues.getCostMicro(item);
        long yield = MatterValues.getDematerializeYieldMicro(item);
        event.getToolTip().add(Component.translatable("tooltip.quantx.matter_cost",
                MatterValues.formatMicro(cost)));
        event.getToolTip().add(Component.translatable("tooltip.quantx.matter_yield",
                MatterValues.formatMicro(yield)));
    }

    @SubscribeEvent
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("QuantX успешно загружен!");
    }

    @SubscribeEvent
    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(MATERIALIZER_MENU.get(), MaterializerScreen::new);
        });
    }

    @SubscribeEvent
    public void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(MATERIALIZER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PUNCH_CARD);
        }
    }
}