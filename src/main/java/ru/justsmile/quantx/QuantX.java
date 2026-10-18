package ru.justsmile.quantx;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import ru.justsmile.quantx.block.MaterializerBlock;
import ru.justsmile.quantx.block.MaterializerBlockEntity;
import ru.justsmile.quantx.block.MatterAcceleratorBlock;
import ru.justsmile.quantx.block.MatterAcceleratorBlockEntity;
import ru.justsmile.quantx.block.MatterFlowMeterBlock;
import ru.justsmile.quantx.block.MatterFlowMeterBlockEntity;
import ru.justsmile.quantx.block.MatterStorageBlock;
import ru.justsmile.quantx.block.MatterValveBlock;
import ru.justsmile.quantx.block.MatterStorageBlockEntity;
import ru.justsmile.quantx.block.QuantumFiberBlock;
import ru.justsmile.quantx.block.QuantumFiberBlockEntity;
import ru.justsmile.quantx.client.QuantXClient;
import ru.justsmile.quantx.item.GuideBookItem;
import ru.justsmile.quantx.item.MatterGunItem;
import ru.justsmile.quantx.item.MatterPackItem;
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

    /** Хранилище материи — пассивный бак, расширяет ёмкость материализатора. */
    public static final RegistryObject<Block> MATTER_STORAGE = BLOCKS.register("matter_storage",
            () -> new MatterStorageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .setId(blockKey("matter_storage"))));
    public static final RegistryObject<Item> MATTER_STORAGE_ITEM = ITEMS.register("matter_storage",
            () -> new BlockItem(MATTER_STORAGE.get(), new Item.Properties().setId(itemKey("matter_storage"))));
    public static final RegistryObject<BlockEntityType<MatterStorageBlockEntity>> MATTER_STORAGE_BE =
            BLOCK_ENTITIES.register("matter_storage",
                    () -> new BlockEntityType<>(MatterStorageBlockEntity::new,
                            Set.of(MATTER_STORAGE.get())));

    /** Квантовое волокно — тонкая трубка передачи материи. */
    public static final RegistryObject<Block> QUANTUM_FIBER = BLOCKS.register("quantum_fiber",
            () -> new QuantumFiberBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .setId(blockKey("quantum_fiber")).noOcclusion()));
    public static final RegistryObject<Item> QUANTUM_FIBER_ITEM = ITEMS.register("quantum_fiber",
            () -> new BlockItem(QUANTUM_FIBER.get(), new Item.Properties().setId(itemKey("quantum_fiber"))));
    public static final RegistryObject<BlockEntityType<QuantumFiberBlockEntity>> QUANTUM_FIBER_BE =
            BLOCK_ENTITIES.register("quantum_fiber",
                    () -> new BlockEntityType<>(QuantumFiberBlockEntity::new,
                            Set.of(QUANTUM_FIBER.get())));

    /** Ускоритель материи волоконный — принудительно перетягивает материю со входа на выход. */
    public static final RegistryObject<Block> MATTER_ACCELERATOR = BLOCKS.register("matter_accelerator",
            () -> new MatterAcceleratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .setId(blockKey("matter_accelerator")).noOcclusion()));
    public static final RegistryObject<Item> MATTER_ACCELERATOR_ITEM = ITEMS.register("matter_accelerator",
            () -> new BlockItem(MATTER_ACCELERATOR.get(), new Item.Properties().setId(itemKey("matter_accelerator"))));
    /** Волоконный клапан — помпа, качающая только по redstone-сигналу. */
    public static final RegistryObject<Block> MATTER_VALVE = BLOCKS.register("matter_valve",
            () -> new MatterValveBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .setId(blockKey("matter_valve")).noOcclusion()));
    public static final RegistryObject<Item> MATTER_VALVE_ITEM = ITEMS.register("matter_valve",
            () -> new BlockItem(MATTER_VALVE.get(), new Item.Properties().setId(itemKey("matter_valve"))));

    /** Тип BE общий: клапан — это ускоритель с redstone-гейтингом. */
    public static final RegistryObject<BlockEntityType<MatterAcceleratorBlockEntity>> MATTER_ACCELERATOR_BE =
            BLOCK_ENTITIES.register("matter_accelerator",
                    () -> new BlockEntityType<>(MatterAcceleratorBlockEntity::new,
                            Set.of(MATTER_ACCELERATOR.get(), MATTER_VALVE.get())));

    /** Счётчик потока — пассивный узел сети с компараторным выходом скорости. */
    public static final RegistryObject<Block> MATTER_FLOW_METER = BLOCKS.register("matter_flow_meter",
            () -> new MatterFlowMeterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .setId(blockKey("matter_flow_meter")).noOcclusion()));
    public static final RegistryObject<Item> MATTER_FLOW_METER_ITEM = ITEMS.register("matter_flow_meter",
            () -> new BlockItem(MATTER_FLOW_METER.get(), new Item.Properties().setId(itemKey("matter_flow_meter"))));
    public static final RegistryObject<BlockEntityType<MatterFlowMeterBlockEntity>> MATTER_FLOW_METER_BE =
            BLOCK_ENTITIES.register("matter_flow_meter",
                    () -> new BlockEntityType<>(MatterFlowMeterBlockEntity::new,
                            Set.of(MATTER_FLOW_METER.get())));

    /** Перфокарта — перенос изученных рецептов. В стаке 1 шт., хранит данные. */
    public static final RegistryObject<Item> PUNCH_CARD = ITEMS.register("punch_card",
            () -> new PunchCardItem(new Item.Properties()
                    .setId(itemKey("punch_card")).stacksTo(1)));

    /** Материальная пушка — ручной дематериализатор. Хранит материю, сливает в машину. */
    public static final RegistryObject<Item> MATTER_GUN = ITEMS.register("matter_gun",
            () -> new MatterGunItem(new Item.Properties()
                    .setId(itemKey("matter_gun")).stacksTo(1)
                    // Без замедления при удержании ПКМ (дефолт: speed 0.2, no sprint).
                    .component(DataComponents.USE_EFFECTS, new UseEffects(true, true, 1.0F))));

    // ===== Гранитные компоненты — основа крафтов мода =====

    /** Гранитная пыль: дробится из гранита 1→2, база для пластин. */
    public static final RegistryObject<Item> GRANITE_DUST = ITEMS.register("granite_dust",
            () -> new Item(new Item.Properties().setId(itemKey("granite_dust"))));

    /** Гранитная пластина: 2×2 пыли (= 2 гранита) → 1 пластина. */
    public static final RegistryObject<Item> GRANITE_PLATE = ITEMS.register("granite_plate",
            () -> new Item(new Item.Properties().setId(itemKey("granite_plate"))));

    /** Гранитная рама: 8 пластин + железный слиток → корпус машины. */
    public static final RegistryObject<Item> GRANITE_FRAME = ITEMS.register("granite_frame",
            () -> new Item(new Item.Properties().setId(itemKey("granite_frame"))));

    /** Ранец материи — носится в своём слоте инвентаря, расширяет бак пушки. */
    public static final RegistryObject<Item> MATTER_PACK = ITEMS.register("matter_pack",
            () -> new MatterPackItem(new Item.Properties()
                    .setId(itemKey("matter_pack")).stacksTo(1)));

    /** Справочник QuantX — книга с иллюстрированными рецептами мода. */
    public static final RegistryObject<Item> GUIDE_BOOK = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties()
                    .setId(itemKey("guide_book")).stacksTo(1)));

    /** Собственная вкладка мода в креативе. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> QUANTX_TAB = CREATIVE_TABS.register("quantx",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.quantx"))
                    .icon(() -> new ItemStack(MATTER_GUN.get()))
                    .displayItems((params, output) -> {
                        output.accept(MATERIALIZER_ITEM.get());
                        output.accept(PUNCH_CARD.get());
                        output.accept(MATTER_GUN.get());
                        output.accept(MATTER_PACK.get());
                        output.accept(GUIDE_BOOK.get());
                        output.accept(MATTER_STORAGE.get());
                        output.accept(QUANTUM_FIBER.get());
                        output.accept(MATTER_ACCELERATOR.get());
                        output.accept(MATTER_VALVE.get());
                        output.accept(MATTER_FLOW_METER.get());
                        output.accept(GRANITE_DUST.get());
                        output.accept(GRANITE_PLATE.get());
                        output.accept(GRANITE_FRAME.get());
                    })
                    .build());

    public QuantX(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        ITEMS.register(modBusGroup);
        BLOCKS.register(modBusGroup);
        BLOCK_ENTITIES.register(modBusGroup);
        MENUS.register(modBusGroup);
        QuantXTriggers.TRIGGERS.register(modBusGroup);
        CREATIVE_TABS.register(modBusGroup);

        // HUD-слои регистрируем уже сейчас: AddGuiOverlayLayersEvent отстреливает
        // при создании Hud внутри Minecraft.<init> — раньше FMLClientSetupEvent.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            QuantXClient.registerOverlays();
        }

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
        // getEntity() == null при стартовом заполнении поиска — индекс будет построен позже.
        if (event.getEntity() != null
                && event.getEntity().level().recipeAccess() instanceof net.minecraft.world.item.crafting.RecipeManager rm) {
            MatterValues.init(rm);
        }
        Item item = event.getItemStack().getItem();
        long cost = MatterValues.getCostMicro(item);
        long yield = MatterValues.getDematerializeYieldMicro(item);
        event.getToolTip().add(Component.translatable("tooltip.quantx.matter_cost",
                MatterValues.formatMicro(cost)));
        event.getToolTip().add(Component.translatable("tooltip.quantx.matter_demat",
                MatterValues.formatMicro(yield)));
    }

    /** Стартовая книга-гайд с рецептами — один раз на мир. */
    @SubscribeEvent
    private static void giveGuideBook(PlayerEvent.PlayerLoggedInEvent event) {
        QuantXGuideBook.giveIfFirstLogin(event);
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
            event.accept(MATTER_STORAGE_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(QUANTUM_FIBER_ITEM);
            event.accept(MATTER_ACCELERATOR_ITEM);
            event.accept(MATTER_VALVE_ITEM);
            event.accept(MATTER_FLOW_METER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PUNCH_CARD);
            event.accept(MATTER_GUN);
            event.accept(MATTER_PACK);
            event.accept(GUIDE_BOOK);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(GRANITE_DUST);
            event.accept(GRANITE_PLATE);
            event.accept(GRANITE_FRAME);
        }
    }
}