package com.nishiyu.lunex;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.nishiyu.lunex.api.MainframeComponentData;
import com.nishiyu.lunex.api.MainframeComponentRegistry;
import com.nishiyu.lunex.block.*;
import com.nishiyu.lunex.blockentity.*;
import com.nishiyu.lunex.datagen.DataGenerators;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.item.ARGlassesItem;
import com.nishiyu.lunex.item.ProgramDiskItem;
import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.item.InactiveBookItem;
import com.nishiyu.lunex.machine.MainframeCapabilityHandler;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsMenu;
import com.nishiyu.lunex.menu.*;
import com.nishiyu.lunex.menu.bioprinter.BioPrinterMenu;
import com.nishiyu.lunex.menu.turtle.TurtleBotMenu;
import com.nishiyu.lunex.menu.turtle.TurtleSettingsMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

@Mod(Lunex.MODID)
public class Lunex {
    public static final String MODID = "lunex";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    @Translatable(en = "Custom Bio Mob", ja = "カスタムバイオモブ")
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.nishiyu.lunex.entity.CustomBioMobEntity>> CUSTOM_BIO_MOB = ENTITY_TYPES.register("custom_bio_mob",
            () -> net.minecraft.world.entity.EntityType.Builder.of(com.nishiyu.lunex.entity.CustomBioMobEntity::new, net.minecraft.world.entity.MobCategory.CREATURE).sized(0.6F, 1.8F).build("custom_bio_mob"));

    @Translatable(en = "Simple Machine", ja = "シンプルマシン")
    public static final DeferredHolder<Block, SimpleMachineBlock> SIMPLE_MACHINE = BLOCKS.register("simple_machine",
            () -> new SimpleMachineBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "Turtle Bot", ja = "タートルボット")
    public static final DeferredHolder<Block, TurtleBotBlock> TURTLE_BOT_BLOCK = BLOCKS.register("turtle_bot_block",
            () -> new TurtleBotBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "Screen", ja = "スクリーン")
    public static final DeferredHolder<Block, com.nishiyu.lunex.block.ScreenBlock> SCREEN_BLOCK = BLOCKS.register("screen_block",
            () -> new com.nishiyu.lunex.block.ScreenBlock(Block.Properties.of().strength(3.0F).sound(net.minecraft.world.level.block.SoundType.GLASS).noOcclusion()));

    @Translatable(en = "Probe", ja = "プローブ")
    public static final DeferredHolder<Block, ProbeBlock> PROBE_BLOCK = BLOCKS.register("probe_block",
            () -> new ProbeBlock(Block.Properties.of().strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "LAN Cable", ja = "LANケーブル")
    public static final DeferredHolder<Block, LANCableBlock> LAN_CABLE_BLOCK = BLOCKS.register("lan_cable_block",
            () -> new LANCableBlock(Block.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion()));

    @Translatable(en = "Speaker", ja = "スピーカー")
    public static final DeferredHolder<Block, com.nishiyu.lunex.block.SpeakerBlock> SPEAKER_BLOCK = BLOCKS.register("speaker_block",
            () -> new com.nishiyu.lunex.block.SpeakerBlock(Block.Properties.of().strength(3.0F).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion()));

    @Translatable(en = "Machine Frame", ja = "マシンフレーム")
    public static final DeferredHolder<Block, MachineFrameBlock> MACHINE_FRAME = BLOCKS.register("machine_frame",
            () -> new MachineFrameBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "Router", ja = "ルーター")
    public static final DeferredHolder<Block, RouterBlock> ROUTER_BLOCK = BLOCKS.register("router_block",
            () -> new RouterBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "Printer", ja = "プリンター")
    public static final DeferredHolder<Block, PrinterBlock> PRINTER = BLOCKS.register("printer",
            () -> new PrinterBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "Bio Printer", ja = "バイオプリンター")
    public static final DeferredHolder<Block, com.nishiyu.lunex.block.BioPrinterBlock> BIO_PRINTER_BLOCK = BLOCKS.register("bio_printer_block",
            () -> new com.nishiyu.lunex.block.BioPrinterBlock(Block.Properties.of().strength(3.0F).sound(SoundType.METAL).noOcclusion()));

    @Translatable(en = "Camera", ja = "カメラ")
    public static final DeferredHolder<Block, com.nishiyu.lunex.block.CameraBlock> CAMERA_BLOCK = BLOCKS.register("camera_block",
            () -> new com.nishiyu.lunex.block.CameraBlock(Block.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredHolder<Block, com.nishiyu.lunex.block.MainframeAdapterBlock> MAINFRAME_ADAPTER_BLOCK = BLOCKS.register("mainframe_adapter_block",
            () -> new com.nishiyu.lunex.block.MainframeAdapterBlock(Block.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noLootTable()));

    @Translatable(en = "Database", ja = "データベース")
    public static final DeferredHolder<Block, DatabaseBlock> DATABASE_BLOCK = BLOCKS.register("database_block",
            () -> new DatabaseBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    @Translatable(en = "Simple Machine", ja = "シンプルマシン")
    public static final DeferredHolder<Item, BlockItem> SIMPLE_MACHINE_ITEM = ITEMS.register("simple_machine", () -> new BlockItem(SIMPLE_MACHINE.get(), new Item.Properties()));

    @Translatable(en = "Turtle Bot", ja = "タートルボット")
    public static final DeferredHolder<Item, BlockItem> TURTLE_BOT_BLOCK_ITEM = ITEMS.register("turtle_bot_block", () -> new BlockItem(TURTLE_BOT_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Screen", ja = "スクリーン")
    public static final DeferredHolder<Item, BlockItem> SCREEN_BLOCK_ITEM = ITEMS.register("screen_block", () -> new BlockItem(SCREEN_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Probe", ja = "プローブ")
    public static final DeferredHolder<Item, BlockItem> PROBE_BLOCK_ITEM = ITEMS.register("probe_block", () -> new BlockItem(PROBE_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "LAN Cable", ja = "LANケーブル")
    public static final DeferredHolder<Item, BlockItem> LAN_CABLE_BLOCK_ITEM = ITEMS.register("lan_cable_block", () -> new BlockItem(LAN_CABLE_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Speaker", ja = "スピーカー")
    public static final DeferredHolder<Item, BlockItem> SPEAKER_BLOCK_ITEM = ITEMS.register("speaker_block", () -> new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Machine Frame", ja = "マシンフレーム")
    public static final DeferredHolder<Item, BlockItem> MACHINE_FRAME_ITEM = ITEMS.register("machine_frame", () -> new BlockItem(MACHINE_FRAME.get(), new Item.Properties()));

    @Translatable(en = "Portable Screen", ja = "携帯スクリーン")
    public static final DeferredHolder<Item, com.nishiyu.lunex.item.PortableScreenItem> PORTABLE_SCREEN = ITEMS.register("portable_screen", () -> new com.nishiyu.lunex.item.PortableScreenItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "AR Glasses", ja = "ARグラス")
    public static final DeferredHolder<Item, ARGlassesItem> AR_GLASSES = ITEMS.register("ar_glasses", () -> new ARGlassesItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Printer", ja = "プリンター")
    public static final DeferredHolder<Item, BlockItem> PRINTER_ITEM = ITEMS.register("printer", () -> new BlockItem(PRINTER.get(), new Item.Properties()));

    @Translatable(en = "Bio Printer", ja = "バイオプリンター")
    public static final DeferredHolder<Item, BlockItem> BIO_PRINTER_BLOCK_ITEM = ITEMS.register("bio_printer_block", () -> new BlockItem(BIO_PRINTER_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Screwdriver", ja = "ドライバー", descEn = "Used for configuring machines.", descJa = "マシンの設定に使用します。")
    public static final DeferredHolder<Item, WrenchItem> WRENCH = ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Programmer Tablet", ja = "プログラム登録タブレット")
    public static final DeferredHolder<Item, com.nishiyu.lunex.item.TabletItem> PROGRAMMER_TABLET = ITEMS.register("programmer_tablet", () -> new com.nishiyu.lunex.item.TabletItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Program Disk", ja = "プログラムディスク")
    public static final DeferredHolder<Item, ProgramDiskItem> PROGRAM_DISK = ITEMS.register("program_disk", () -> new ProgramDiskItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Router", ja = "ルーター")
    public static final DeferredHolder<Item, BlockItem> ROUTER_BLOCK_ITEM = ITEMS.register("router_block", () -> new BlockItem(ROUTER_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Camera", ja = "カメラ")
    public static final DeferredHolder<Item, BlockItem> CAMERA_BLOCK_ITEM = ITEMS.register("camera_block", () -> new BlockItem(CAMERA_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Inactive Book", ja = "未活性の本")
    public static final DeferredHolder<Item, InactiveBookItem> INACTIVE_BOOK = ITEMS.register("inactive_book", () -> new InactiveBookItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Database", ja = "データベース")
    public static final DeferredHolder<Item, BlockItem> DATABASE_BLOCK_ITEM = ITEMS.register("database_block", () -> new BlockItem(DATABASE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimpleMachineBlockEntity>> SIMPLE_MACHINE_BE = BLOCK_ENTITIES.register("simple_machine", () -> BlockEntityType.Builder.of(SimpleMachineBlockEntity::new, SIMPLE_MACHINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurtleBotBlockEntity>> TURTLE_BOT_BE = BLOCK_ENTITIES.register("turtle_bot_block", () -> BlockEntityType.Builder.of(TurtleBotBlockEntity::new, TURTLE_BOT_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.ScreenBlockEntity>> SCREEN_BE = BLOCK_ENTITIES.register("screen_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.ScreenBlockEntity::new, SCREEN_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProbeBlockEntity>> PROBE_BE = BLOCK_ENTITIES.register("probe_block", () -> BlockEntityType.Builder.of(ProbeBlockEntity::new, PROBE_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.SpeakerBlockEntity>> SPEAKER_BE = BLOCK_ENTITIES.register("speaker_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.SpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME_BE = BLOCK_ENTITIES.register("machine_frame", () -> BlockEntityType.Builder.of(MachineFrameBlockEntity::new, MACHINE_FRAME.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouterBlockEntity>> ROUTER_BE = BLOCK_ENTITIES.register("router_block", () -> BlockEntityType.Builder.of(RouterBlockEntity::new, ROUTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrinterBlockEntity>> PRINTER_BE = BLOCK_ENTITIES.register("printer", () -> BlockEntityType.Builder.of(PrinterBlockEntity::new, PRINTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.BioPrinterBlockEntity>> BIO_PRINTER_BE = BLOCK_ENTITIES.register("bio_printer_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.BioPrinterBlockEntity::new, BIO_PRINTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.CameraBlockEntity>> CAMERA_BE = BLOCK_ENTITIES.register("camera_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.CameraBlockEntity::new, CAMERA_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity>> MAINFRAME_ADAPTER_BE = BLOCK_ENTITIES.register("mainframe_adapter_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity::new, MAINFRAME_ADAPTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DatabaseBlockEntity>> DATABASE_BE = BLOCK_ENTITIES.register("database_block", () -> BlockEntityType.Builder.of(DatabaseBlockEntity::new, DATABASE_BLOCK.get()).build(null));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HAS_CAMERA_ATTACHMENT = ATTACHMENTS.register("has_camera",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    public static final DeferredHolder<MenuType<?>, MenuType<SimpleMachineMenu>> SIMPLE_MACHINE_MENU = MENUS.register("simple_machine", () -> IMenuTypeExtension.create((windowId, inv, data) -> new SimpleMachineMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<ProbeMenu>> PROBE_MENU = MENUS.register("probe_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new ProbeMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<com.nishiyu.lunex.menu.PortableScreenMenu>> PORTABLE_SCREEN_MENU = MENUS.register("portable_screen", () -> IMenuTypeExtension.create((windowId, inv, data) -> new com.nishiyu.lunex.menu.PortableScreenMenu(windowId, inv, data.readUtf())));
    public static final DeferredHolder<MenuType<?>, MenuType<PrinterMenu>> PRINTER_MENU = MENUS.register("printer", () -> IMenuTypeExtension.create((windowId, inv, data) -> new PrinterMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<BioPrinterMenu>> BIO_PRINTER_MENU = MENUS.register("bio_printer", () -> IMenuTypeExtension.create(BioPrinterMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BioEntitySettingsMenu>> BIO_MOB_SETTINGS_MENU = MENUS.register("bio_mob_settings", () -> IMenuTypeExtension.create(BioEntitySettingsMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MainframeOverviewMenu>> MAINFRAME_OVERVIEW_MENU = MENUS.register("mainframe_overview", () -> IMenuTypeExtension.create((windowId, inv, data) -> new MainframeOverviewMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<TurtleBotMenu>> TURTLE_BOT_MENU = MENUS.register("turtle_bot_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new TurtleBotMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<TurtleSettingsMenu>> TURTLE_SETTINGS_MENU = MENUS.register("turtle_settings_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new TurtleSettingsMenu(windowId, inv, data.readBlockPos())));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LUNEX_TAB = CREATIVE_MODE_TABS.register("lunex_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lunex"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SIMPLE_MACHINE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MACHINE_FRAME_ITEM.get());
                output.accept(SIMPLE_MACHINE_ITEM.get());
                output.accept(TURTLE_BOT_BLOCK_ITEM.get());
                output.accept(ROUTER_BLOCK_ITEM.get());
                output.accept(DATABASE_BLOCK_ITEM.get());
                output.accept(PRINTER_ITEM.get());
                output.accept(SCREEN_BLOCK.get());
                output.accept(SPEAKER_BLOCK_ITEM.get());
                output.accept(PROBE_BLOCK_ITEM.get());
                output.accept(LAN_CABLE_BLOCK_ITEM.get());
                output.accept(BIO_PRINTER_BLOCK_ITEM.get());
                output.accept(CAMERA_BLOCK_ITEM.get());

                output.accept(WRENCH.get());
                output.accept(PROGRAMMER_TABLET.get());
                output.accept(PORTABLE_SCREEN.get());
                output.accept(AR_GLASSES.get());

                output.accept(PROGRAM_DISK.get());
                output.accept(INACTIVE_BOOK.get());
            }).build());

    public Lunex(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);

        DATA_COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerEntityAttributes);

        modEventBus.addListener(com.nishiyu.lunex.network.NetworkHandler::register);
        modEventBus.addListener(MainframeCapabilityHandler::registerCapabilities);
        modEventBus.addListener(DataGenerators::gatherData);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void registerEntityAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(CUSTOM_BIO_MOB.get(), com.nishiyu.lunex.entity.CustomBioMobEntity.createBaseAttributes().build());
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(MainframeComponentRegistry::registerCoreComponents);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TURTLE_BOT_BE.get(), (be, side) -> be.getCore() != null ? be.getCore().itemHandler : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TURTLE_BOT_BE.get(), (be, side) -> be.feStorage);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PRINTER_BE.get(), (be, side) -> be.itemHandler);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BIO_PRINTER_BE.get(), (be, side) -> be.itemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BIO_PRINTER_BE.get(), (be, side) -> be.energyStorage);
    }

    private void onServerStarting(ServerStartingEvent event) {
    }

    private void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.nishiyu.lunex.network.LocalWebServer.stop();
    }
}