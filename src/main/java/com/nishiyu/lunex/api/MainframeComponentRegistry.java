package com.nishiyu.lunex.api;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.LuaAPIRegistry;
import com.nishiyu.lunex.api.mainframe.MainframeExtensionRegistry;
import com.nishiyu.lunex.api.mainframe.action.*;
import com.nishiyu.lunex.api.mainframe.extension.FurnaceExtension; // ★追加
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainframeComponentRegistry {
    private static final Map<Block, MainframeComponentData> REGISTRY = new ConcurrentHashMap<>();

    public static void register(Block block, MainframeComponentData data) {
        REGISTRY.put(block, data);
        data.getExtensions().forEach(MainframeExtensionRegistry::register);
        data.getLuaAPIs().forEach(LuaAPIRegistry::register);
    }

    public static MainframeComponentData get(Block block) { return REGISTRY.get(block); }
    public static boolean isRegistered(Block block) { return REGISTRY.containsKey(block); }
    public static Map<Block, MainframeComponentData> getAll() { return REGISTRY; }

    public static void registerCoreComponents() {

        register(Lunex.ROUTER_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(1)
                .addFeature(MainframeConstants.FEATURE_ROUTER)
                .addApi(MainframeConstants.API_NET)
                .addApi(MainframeConstants.API_ROUTER)
                .addApi(MainframeConstants.API_STORAGE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        register(Lunex.DATABASE_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(16)
                .addFeature(MainframeConstants.FEATURE_DATABASE)
                .addApi(MainframeConstants.API_DATABASE)
                .addApi(MainframeConstants.API_FS)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addResourceCapacity("item", 3000)
                .setActionProvider(new DatabaseActionProvider())
                .build());

        register(Lunex.SPEAKER_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(8)
                .addFeature(MainframeConstants.FEATURE_SPEAKER)
                .addApi(MainframeConstants.API_SPEAKER)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        register(Lunex.PROBE_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(32)
                .addFeature(MainframeConstants.FEATURE_PROBE)
                .addFeature("IO_PORT")
                .addApi(MainframeConstants.API_RS)
                .addApi(MainframeConstants.API_INVENTORY)
                .addApi("net")
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .setActionProvider(new ProbeActionProvider())
                .build());

        register(Lunex.SCREEN_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addFeature(MainframeConstants.FEATURE_SCREEN)
                .addApi(MainframeConstants.API_SCREEN)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .setActionProvider(new ScreenActionProvider())
                .build());

        register(Blocks.CRAFTER, MainframeComponentData.builder()
                .maxCount(4)
                .addFeature("lunex:crafter")
                .addApi("crafter")
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .setActionProvider(new CrafterActionProvider())
                .addExtension(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "crafter"), com.nishiyu.lunex.api.mainframe.extension.CrafterExtension::new)
                .build());


        register(Blocks.FURNACE, MainframeComponentData.builder()
                .maxCount(4)
                .addFeature("lunex:furnace")
                .addApi("furnace")
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .setActionProvider(new FurnaceActionProvider())
                .addExtension(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "furnace"), FurnaceExtension::new)
                .build());

        register(Lunex.MACHINE_FRAME.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addFeature("CPU_CORE")
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .build());

        register(Lunex.SIMPLE_MACHINE.get(), MainframeComponentData.builder()
                .maxCount(10)
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .addResourceCapacity("energy", 10000)
                .build());

        register(Lunex.MAINFRAME_ADAPTER_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .build());
    }
}