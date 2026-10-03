package com.nishiyu.lunex.api;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.LuaAPIRegistry;
import com.nishiyu.lunex.api.mainframe.MainframeExtensionRegistry;
import com.nishiyu.lunex.api.mainframe.action.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainframeComponentRegistry {
    private static final Map<Block, MainframeComponentData> REGISTRY = new ConcurrentHashMap<>();

    public static void register(Block block, MainframeComponentData data) {
        REGISTRY.put(block, data);

        // ★ 自動横流し登録: マシン内のバックエンド処理
        data.getExtensions().forEach(MainframeExtensionRegistry::register);

        // ★ 自動横流し登録: Lua API
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
                .addBottomTab("com.nishiyu.lunex.api.client.ui.tabs.NetworkStatusBottomTab")
                .build());

        register(Lunex.DATABASE_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(16)
                .addFeature(MainframeConstants.FEATURE_DATABASE)
                .addApi(MainframeConstants.API_DATABASE)
                .addApi(MainframeConstants.API_FS)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addResourceCapacity("item", 1000)
                .addBlockTab("com.nishiyu.lunex.api.client.ui.extensions.DatabaseUIExtension")
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
                .addApi(MainframeConstants.API_RS)
                .addApi(MainframeConstants.API_INVENTORY)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addBlockTab("com.nishiyu.lunex.api.client.ui.extensions.ProbeUIExtension")
                .setActionProvider(new ProbeActionProvider())
                .build());

        register(Lunex.SCREEN_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addFeature(MainframeConstants.FEATURE_SCREEN)
                .addApi(MainframeConstants.API_SCREEN)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addBlockTab("com.nishiyu.lunex.api.client.ui.extensions.ScreenUIExtension")
                .setActionProvider(new ScreenActionProvider())
                .build());

        register(Blocks.CRAFTER, MainframeComponentData.builder()
                // 1. 基本情報
                .maxCount(4)
                .addFeature("lunex:crafter")
                .addApi("crafter")
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                // 2. クライアント側: UI描画拡張
                .addBlockTab("com.nishiyu.lunex.api.client.ui.extensions.CrafterUIExtension")
                // 3. サーバー側: パケットアクション処理
                .setActionProvider(new CrafterActionProvider())
                // 4. サーバー側: マシン内の処理
                .addExtension(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "crafter"), com.nishiyu.lunex.api.mainframe.extension.CrafterExtension::new)
                .build());

        register(Lunex.MACHINE_FRAME.get(), MainframeComponentData.builder()
                .maxCount(9999)
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