package com.nishiyu.lunex.api.mainframe;

import com.nishiyu.lunex.Lunex;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * メインフレームを構成する各ブロック（コンポーネント）が持つ機能（Feature）と
 * 提供するAPI、および配置可能な場所（Placements）を紐づけるレジストリです。
 */
public class MainframeComponentRegistry {
    private static final Map<Block, MainframeComponentData> REGISTRY = new ConcurrentHashMap<>();

    public static void register(Block block, MainframeComponentData data) {
        REGISTRY.put(block, data);
    }

    public static MainframeComponentData get(Block block) {
        return REGISTRY.get(block);
    }

    public static boolean isRegistered(Block block) {
        return REGISTRY.containsKey(block);
    }

    public static void registerCoreComponents() {

        // ルーター機能: 中 または 面
        register(Lunex.ROUTER_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(1)
                .addFeature(MainframeConstants.FEATURE_ROUTER)
                .addApi(MainframeConstants.API_NET)
                .addApi(MainframeConstants.API_ROUTER)
                .addApi(MainframeConstants.API_STORAGE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        // データベース機能: 中 または 面
        register(Lunex.DATABASE_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(16)
                .addFeature(MainframeConstants.FEATURE_DATABASE)
                .addApi(MainframeConstants.API_DATABASE)
                .addApi(MainframeConstants.API_FS)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        // スピーカー機能: 面のみ
        register(Lunex.SPEAKER_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(8)
                .addFeature(MainframeConstants.FEATURE_SPEAKER)
                .addApi(MainframeConstants.API_SPEAKER)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        // プローブ機能: 面のみ
        register(Lunex.PROBE_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(32)
                .addFeature(MainframeConstants.FEATURE_PROBE)
                .addApi(MainframeConstants.API_RS)
                .addApi(MainframeConstants.API_INVENTORY)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        // スクリーン機能: 面のみ（※上限なし設定を追加）
        register(Lunex.SCREEN_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addFeature(MainframeConstants.FEATURE_SCREEN)
                .addApi(MainframeConstants.API_SCREEN)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .build());

        register(Blocks.CRAFTER, MainframeComponentData.builder()
                .maxCount(4)
                .addFeature("lunex:crafter")
                .addApi("crafter")
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .build());

        // ==========================================
        // ★ 構造用・汎用パーツ
        // ==========================================

        // マシンフレーム (MachineFrame)
        // 制限なし (9999を指定することで最大サイズである9x9x9の総ブロック数729を余裕でカバー)
        register(Lunex.MACHINE_FRAME.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .build());

        // コアマシン (SimpleMachine)
        // ★ ご要望に合わせて最大10個に制限
        register(Lunex.SIMPLE_MACHINE.get(), MainframeComponentData.builder()
                .maxCount(10)
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .build());

        // メインフレームアダプター (他MODブロック用)
        // 制限なし
        register(Lunex.MAINFRAME_ADAPTER_BLOCK.get(), MainframeComponentData.builder()
                .maxCount(9999)
                .addPlacement(MainframeConstants.PLACEMENT_EDGE)
                .addPlacement(MainframeConstants.PLACEMENT_FACE)
                .addPlacement(MainframeConstants.PLACEMENT_INSIDE)
                .build());
    }
}