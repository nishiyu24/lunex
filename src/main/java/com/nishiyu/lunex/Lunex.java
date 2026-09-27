package com.nishiyu.lunex;

import com.mojang.logging.LogUtils;
import com.nishiyu.lunex.block.*;
import com.nishiyu.lunex.blockentity.*;
import com.nishiyu.lunex.datagen.DataGenerators;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.item.ARGlassesItem;
import com.nishiyu.lunex.item.ProgramDiskItem;
import com.nishiyu.lunex.item.UpgradeItem;
import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsMenu;
import com.nishiyu.lunex.menu.MachineSettings.MachineSettingsMenu;
import com.nishiyu.lunex.menu.*;
import com.nishiyu.lunex.menu.bioprinter.BioPrinterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

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

    // =========================================
    // Entity の登録
    // =========================================
    @Translatable(en = "Custom Bio Mob", ja = "カスタムバイオモブ")
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.nishiyu.lunex.entity.CustomBioMobEntity>> CUSTOM_BIO_MOB = ENTITY_TYPES.register("custom_bio_mob",
            () -> net.minecraft.world.entity.EntityType.Builder.of(com.nishiyu.lunex.entity.CustomBioMobEntity::new, net.minecraft.world.entity.MobCategory.CREATURE).sized(0.6F, 1.8F).build("custom_bio_mob"));

    // =========================================
    // Blockの登録
    // =========================================
    @Translatable(en = "Advanced Machine", ja = "アドバンスドマシン")
    public static final DeferredHolder<Block, AdvancedMachineBlock> ADVANCED_MACHINE = BLOCKS.register("advanced_machine",
            () -> new AdvancedMachineBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

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

    @Translatable(en = "Database", ja = "データベース")
    public static final DeferredHolder<Block, com.nishiyu.lunex.block.DatabaseBlock> DATABASE_BLOCK = BLOCKS.register("database_block",
            () -> new com.nishiyu.lunex.block.DatabaseBlock(Block.Properties.of().strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    // =========================================
    // Item の登録
    // =========================================
    @Translatable(en = "Advanced Machine", ja = "アドバンスドマシン")
    public static final DeferredHolder<Item, BlockItem> ADVANCED_MACHINE_ITEM = ITEMS.register("advanced_machine",
            () -> new BlockItem(ADVANCED_MACHINE.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
                    if (tag.contains("MachineLabel") && !tag.getString("MachineLabel").isEmpty()) {
                        tooltipComponents.add(Component.literal("ラベル: " + tag.getString("MachineLabel")).withStyle(ChatFormatting.AQUA));
                    } else {
                        tooltipComponents.add(Component.literal("ラベル: 未設定").withStyle(ChatFormatting.GRAY));
                    }
                }
            });

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

    @Translatable(en = "Database", ja = "データベース")
    public static final DeferredHolder<Item, BlockItem> DATABASE_BLOCK_ITEM = ITEMS.register("database_block", () -> new BlockItem(DATABASE_BLOCK.get(), new Item.Properties()));

    @Translatable(en = "Screwdriver", ja = "ドライバー", descEn = "Used for configuring machines.", descJa = "マシンの設定に使用します。")
    public static final DeferredHolder<Item, WrenchItem> WRENCH = ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Programmer Tablet", ja = "プログラム登録タブレット")
    public static final DeferredHolder<Item, com.nishiyu.lunex.item.TabletItem> PROGRAMMER_TABLET = ITEMS.register("programmer_tablet", () -> new com.nishiyu.lunex.item.TabletItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Machine Cartridge", ja = "マシンカートリッジ")
    public static final DeferredHolder<Item, Item> MACHINE_CARTRIDGE = ITEMS.register("machine_cartridge", () -> new Item(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Paintball", ja = "ペイントボール")
    public static final DeferredHolder<Item, com.nishiyu.lunex.item.PaintballItem> PAINTBALL = ITEMS.register("paintball", () -> new com.nishiyu.lunex.item.PaintballItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Upgrade Base", ja = "アップグレードベース")
    public static final DeferredHolder<Item, Item> UPGRADE_BASE = ITEMS.register("upgrade_base", () -> new Item(new Item.Properties()));

    @Translatable(en = "Program Disk", ja = "プログラムディスク")
    public static final DeferredHolder<Item, ProgramDiskItem> PROGRAM_DISK = ITEMS.register("program_disk", () -> new ProgramDiskItem(new Item.Properties().stacksTo(1)));

    @Translatable(en = "Inactive Enchanted Book", ja = "非活性エンチャント本")
    public static final DeferredHolder<Item, com.nishiyu.lunex.item.InactiveBookItem> INACTIVE_BOOK = ITEMS.register("inactive_book", () -> new com.nishiyu.lunex.item.InactiveBookItem(new Item.Properties().stacksTo(16)));

    @Translatable(en = "Execution Upgrade Mk1", ja = "作業枠アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> EXEC_UPGRADE_MK1 = ITEMS.register("exec_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.EXECUTION, 1));
    @Translatable(en = "Execution Upgrade Mk2", ja = "作業枠アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> EXEC_UPGRADE_MK2 = ITEMS.register("exec_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.EXECUTION, 2));
    @Translatable(en = "Execution Upgrade Mk3", ja = "作業枠アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> EXEC_UPGRADE_MK3 = ITEMS.register("exec_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.EXECUTION, 3));

    @Translatable(en = "Storage Upgrade Mk1", ja = "補完枠アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> STORAGE_UPGRADE_MK1 = ITEMS.register("storage_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.STORAGE, 1));
    @Translatable(en = "Storage Upgrade Mk2", ja = "補完枠アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> STORAGE_UPGRADE_MK2 = ITEMS.register("storage_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.STORAGE, 2));
    @Translatable(en = "Storage Upgrade Mk3", ja = "補完枠アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> STORAGE_UPGRADE_MK3 = ITEMS.register("storage_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.STORAGE, 3));

    @Translatable(en = "Speed Upgrade Mk1", ja = "速度アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> SPEED_UPGRADE_MK1 = ITEMS.register("speed_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.SPEED, 1));
    @Translatable(en = "Speed Upgrade Mk2", ja = "速度アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> SPEED_UPGRADE_MK2 = ITEMS.register("speed_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.SPEED, 2));
    @Translatable(en = "Speed Upgrade Mk3", ja = "速度アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> SPEED_UPGRADE_MK3 = ITEMS.register("speed_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.SPEED, 3));

    @Translatable(en = "Efficiency Upgrade Mk1", ja = "燃費アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> EFFICIENCY_UPGRADE_MK1 = ITEMS.register("efficiency_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.EFFICIENCY, 1));
    @Translatable(en = "Efficiency Upgrade Mk2", ja = "燃費アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> EFFICIENCY_UPGRADE_MK2 = ITEMS.register("efficiency_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.EFFICIENCY, 2));
    @Translatable(en = "Efficiency Upgrade Mk3", ja = "燃費アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> EFFICIENCY_UPGRADE_MK3 = ITEMS.register("efficiency_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.EFFICIENCY, 3));

    @Translatable(en = "Capacity Upgrade Mk1", ja = "容量アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> CAPACITY_UPGRADE_MK1 = ITEMS.register("capacity_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.CAPACITY, 1));
    @Translatable(en = "Capacity Upgrade Mk2", ja = "容量アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> CAPACITY_UPGRADE_MK2 = ITEMS.register("capacity_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.CAPACITY, 2));
    @Translatable(en = "Capacity Upgrade Mk3", ja = "容量アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> CAPACITY_UPGRADE_MK3 = ITEMS.register("capacity_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.CAPACITY, 3));

    @Translatable(en = "Generator Upgrade Mk1", ja = "発電アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> GENERATOR_UPGRADE_MK1 = ITEMS.register("generator_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.GENERATOR, 1));
    @Translatable(en = "Generator Upgrade Mk2", ja = "発電アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> GENERATOR_UPGRADE_MK2 = ITEMS.register("generator_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.GENERATOR, 2));
    @Translatable(en = "Generator Upgrade Mk3", ja = "発電アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> GENERATOR_UPGRADE_MK3 = ITEMS.register("generator_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.GENERATOR, 3));

    @Translatable(en = "Distance Upgrade Mk1", ja = "通信距離アップグレード Mk1")
    public static final DeferredHolder<Item, UpgradeItem> DISTANCE_UPGRADE_MK1 = ITEMS.register("distance_upgrade_mk1", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.DISTANCE, 1));
    @Translatable(en = "Distance Upgrade Mk2", ja = "通信距離アップグレード Mk2")
    public static final DeferredHolder<Item, UpgradeItem> DISTANCE_UPGRADE_MK2 = ITEMS.register("distance_upgrade_mk2", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.DISTANCE, 2));
    @Translatable(en = "Distance Upgrade Mk3", ja = "通信距離アップグレード Mk3")
    public static final DeferredHolder<Item, UpgradeItem> DISTANCE_UPGRADE_MK3 = ITEMS.register("distance_upgrade_mk3", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.DISTANCE, 3));
    @Translatable(en = "Distance Upgrade Mk4", ja = "通信距離アップグレード Mk4")
    public static final DeferredHolder<Item, UpgradeItem> DISTANCE_UPGRADE_MK4 = ITEMS.register("distance_upgrade_mk4", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.DISTANCE, 4));

    @Translatable(en = "Command Upgrade", ja = "コマンドアップグレード")
    public static final DeferredHolder<Item, UpgradeItem> COMMAND_UPGRADE = ITEMS.register("command_upgrade", () -> new UpgradeItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC), UpgradeItem.UpgradeType.COMMAND, 1));

    @Translatable(en = "Router Module", ja = "ルーターモジュール")
    public static final DeferredHolder<Item, UpgradeItem> ROUTER_MODULE = ITEMS.register("router_module", () -> new UpgradeItem(new Item.Properties().stacksTo(1), UpgradeItem.UpgradeType.ROUTER, 1));

    @Translatable(en = "Router", ja = "ルーター")
    public static final DeferredHolder<Item, BlockItem> ROUTER_BLOCK_ITEM = ITEMS.register("router_block", () -> new BlockItem(ROUTER_BLOCK.get(), new Item.Properties()));

    // =========================================
    // BlockEntity の登録
    // =========================================
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedMachineBlockEntity>> ADVANCED_MACHINE_BE = BLOCK_ENTITIES.register("advanced_machine", () -> BlockEntityType.Builder.of(AdvancedMachineBlockEntity::new, ADVANCED_MACHINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SimpleMachineBlockEntity>> SIMPLE_MACHINE_BE = BLOCK_ENTITIES.register("simple_machine", () -> BlockEntityType.Builder.of(SimpleMachineBlockEntity::new, SIMPLE_MACHINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurtleBotBlockEntity>> TURTLE_BOT_BE = BLOCK_ENTITIES.register("turtle_bot_block", () -> BlockEntityType.Builder.of(TurtleBotBlockEntity::new, TURTLE_BOT_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.ScreenBlockEntity>> SCREEN_BE = BLOCK_ENTITIES.register("screen_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.ScreenBlockEntity::new, SCREEN_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProbeBlockEntity>> PROBE_BE = BLOCK_ENTITIES.register("probe_block", () -> BlockEntityType.Builder.of(ProbeBlockEntity::new, PROBE_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.SpeakerBlockEntity>> SPEAKER_BE = BLOCK_ENTITIES.register("speaker_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.SpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME_BE = BLOCK_ENTITIES.register("machine_frame", () -> BlockEntityType.Builder.of(MachineFrameBlockEntity::new, MACHINE_FRAME.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouterBlockEntity>> ROUTER_BE = BLOCK_ENTITIES.register("router_block", () -> BlockEntityType.Builder.of(RouterBlockEntity::new, ROUTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrinterBlockEntity>> PRINTER_BE = BLOCK_ENTITIES.register("printer", () -> BlockEntityType.Builder.of(PrinterBlockEntity::new, PRINTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.BioPrinterBlockEntity>> BIO_PRINTER_BE = BLOCK_ENTITIES.register("bio_printer_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.BioPrinterBlockEntity::new, BIO_PRINTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.nishiyu.lunex.blockentity.DatabaseBlockEntity>> DATABASE_BE = BLOCK_ENTITIES.register("database_block", () -> BlockEntityType.Builder.of(com.nishiyu.lunex.blockentity.DatabaseBlockEntity::new, DATABASE_BLOCK.get()).build(null));

    // =========================================
    // Menu (GUI) の登録
    // =========================================
    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedMachineMenu>> ADVANCED_MACHINE_MENU = MENUS.register("advanced_machine", () -> IMenuTypeExtension.create((windowId, inv, data) -> new AdvancedMachineMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<SimpleMachineMenu>> SIMPLE_MACHINE_MENU = MENUS.register("simple_machine", () -> IMenuTypeExtension.create((windowId, inv, data) -> new SimpleMachineMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<UpgradeMenu>> UPGRADE_MENU = MENUS.register("upgrade_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new UpgradeMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineSettingsMenu>> MACHINE_SETTINGS_MENU = MENUS.register("machine_settings", () -> IMenuTypeExtension.create((windowId, inv, data) -> {
        if (data.readableBytes() == 8) {
            return new MachineSettingsMenu(windowId, inv, data.readBlockPos());
        } else {
            data.readBoolean();
            net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(data);
            com.nishiyu.lunex.machine.ItemMachineContext ctx = new com.nishiyu.lunex.machine.ItemMachineContext(stack);
            return new MachineSettingsMenu(windowId, inv, ctx);
        }
    }));
    public static final DeferredHolder<MenuType<?>, MenuType<ProbeMenu>> PROBE_MENU = MENUS.register("probe_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new ProbeMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<RouterDashboardMenu>> ROUTER_DASHBOARD_MENU = MENUS.register("router_dashboard", () -> IMenuTypeExtension.create(RouterDashboardMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.nishiyu.lunex.menu.PortableScreenMenu>> PORTABLE_SCREEN_MENU = MENUS.register("portable_screen", () -> IMenuTypeExtension.create((windowId, inv, data) -> new com.nishiyu.lunex.menu.PortableScreenMenu(windowId, inv, data.readUtf())));
    public static final DeferredHolder<MenuType<?>, MenuType<PrinterMenu>> PRINTER_MENU = MENUS.register("printer", () -> IMenuTypeExtension.create((windowId, inv, data) -> new PrinterMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<BioPrinterMenu>> BIO_PRINTER_MENU = MENUS.register("bio_printer", () -> IMenuTypeExtension.create(BioPrinterMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BioEntitySettingsMenu>> BIO_MOB_SETTINGS_MENU = MENUS.register("bio_mob_settings", () -> IMenuTypeExtension.create(BioEntitySettingsMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.nishiyu.lunex.menu.DatabaseMenu>> DATABASE_MENU = MENUS.register("database_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new com.nishiyu.lunex.menu.DatabaseMenu(windowId, inv, data.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<MainframeOverviewMenu>> MAINFRAME_OVERVIEW_MENU = MENUS.register("mainframe_overview", () -> IMenuTypeExtension.create((windowId, inv, data) -> new MainframeOverviewMenu(windowId, inv, data.readBlockPos())));

    // =========================================
    // CreativeTabの登録
    // =========================================
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LUNEX_TAB = CREATIVE_MODE_TABS.register("lunex_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lunex"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ADVANCED_MACHINE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MACHINE_FRAME_ITEM.get());
                output.accept(ADVANCED_MACHINE_ITEM.get());
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

                output.accept(WRENCH.get());
                output.accept(PROGRAMMER_TABLET.get());
                output.accept(PORTABLE_SCREEN.get());
                output.accept(AR_GLASSES.get());

                output.accept(MACHINE_CARTRIDGE.get());
                output.accept(PAINTBALL.get());
                output.accept(UPGRADE_BASE.get());
                output.accept(PROGRAM_DISK.get());
                output.accept(INACTIVE_BOOK.get());

                output.accept(EXEC_UPGRADE_MK1.get());
                output.accept(EXEC_UPGRADE_MK2.get());
                output.accept(EXEC_UPGRADE_MK3.get());
                output.accept(STORAGE_UPGRADE_MK1.get());
                output.accept(STORAGE_UPGRADE_MK2.get());
                output.accept(STORAGE_UPGRADE_MK3.get());
                output.accept(SPEED_UPGRADE_MK1.get());
                output.accept(SPEED_UPGRADE_MK2.get());
                output.accept(SPEED_UPGRADE_MK3.get());
                output.accept(EFFICIENCY_UPGRADE_MK1.get());
                output.accept(EFFICIENCY_UPGRADE_MK2.get());
                output.accept(EFFICIENCY_UPGRADE_MK3.get());
                output.accept(CAPACITY_UPGRADE_MK1.get());
                output.accept(CAPACITY_UPGRADE_MK2.get());
                output.accept(CAPACITY_UPGRADE_MK3.get());
                output.accept(GENERATOR_UPGRADE_MK1.get());
                output.accept(GENERATOR_UPGRADE_MK2.get());
                output.accept(GENERATOR_UPGRADE_MK3.get());
                output.accept(DISTANCE_UPGRADE_MK1.get());
                output.accept(DISTANCE_UPGRADE_MK2.get());
                output.accept(DISTANCE_UPGRADE_MK3.get());
                output.accept(DISTANCE_UPGRADE_MK4.get());
                output.accept(ROUTER_MODULE.get());
                output.accept(COMMAND_UPGRADE.get());
            }).build());

    // =========================================
    // Modの初期化・イベント登録
    // =========================================
    public Lunex(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerEntityAttributes);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onItemCrafted);
        modEventBus.addListener(com.nishiyu.lunex.network.NetworkHandler::register);
        modEventBus.addListener(DataGenerators::gatherData);
    }

    private void registerEntityAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(CUSTOM_BIO_MOB.get(), com.nishiyu.lunex.entity.CustomBioMobEntity.createBaseAttributes().build());
    }

    private void setup(final FMLCommonSetupEvent event) {
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ADVANCED_MACHINE_BE.get(),
                (be, side) -> be.itemHandler
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TURTLE_BOT_BE.get(),
                (be, side) -> be.itemHandler
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                PRINTER_BE.get(),
                (be, side) -> be.itemHandler
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BIO_PRINTER_BE.get(),
                (be, side) -> be.itemHandler
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                BIO_PRINTER_BE.get(),
                (be, side) -> be.energyStorage
        );
    }

    private void onItemCrafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        net.minecraft.world.item.ItemStack crafted = event.getCrafting();
        if (crafted.getItem() == TURTLE_BOT_BLOCK_ITEM.get()) {
            net.minecraft.world.Container inventory = event.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack slotStack = inventory.getItem(i);
                if (slotStack.getItem() == ADVANCED_MACHINE_ITEM.get()) {
                    net.minecraft.world.item.component.CustomData beData = slotStack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
                    if (beData != null) {
                        net.minecraft.nbt.CompoundTag tag = beData.copyTag();
                        tag.putString("id", Objects.requireNonNull(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(TURTLE_BOT_BE.get())).toString());
                        crafted.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                    }
                    break;
                }
            }
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
    }

    private void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.nishiyu.lunex.network.LocalWebServer.stop();
    }
}