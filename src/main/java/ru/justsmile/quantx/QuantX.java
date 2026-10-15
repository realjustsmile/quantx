package ru.justsmile.quantx;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(QuantX.MODID)
public class QuantX {

    public static final String MODID = "quantx";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    // Вспомогательные методы для создания ключей
    public static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, name));
    }

    public static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, name));
    }

    // Наш первый предмет
    public static final RegistryObject<Item> MY_ITEM = ITEMS.register("my_item",
            () -> new Item(new Item.Properties().setId(itemKey("my_item"))));

    // Наш первый блок + его предмет-версия
    public static final RegistryObject<Block> MY_BLOCK = BLOCKS.register("my_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).setId(blockKey("my_block"))));
    public static final RegistryObject<Item> MY_BLOCK_ITEM = ITEMS.register("my_block",
            () -> new BlockItem(MY_BLOCK.get(), new Item.Properties().setId(itemKey("my_block"))));

    public QuantX(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();

        ITEMS.register(modBusGroup);
        BLOCKS.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);
        BusGroup.DEFAULT.register(MethodHandles.lookup(), this);
    }

    @SubscribeEvent
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("QuantX успешно загружен!");
    }

    @SubscribeEvent
    public void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(MY_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(MY_BLOCK_ITEM);
        }
    }
}