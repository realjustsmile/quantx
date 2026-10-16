package ru.justsmile.quantx;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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
import ru.justsmile.quantx.menu.MaterializerMenu;
import ru.justsmile.quantx.menu.MaterializerScreen;

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

    public QuantX(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        ITEMS.register(modBusGroup);
        BLOCKS.register(modBusGroup);
        BLOCK_ENTITIES.register(modBusGroup);
        MENUS.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::clientSetup);
        BusGroup.DEFAULT.register(MethodHandles.lookup(), this);
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
    }
}