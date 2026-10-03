package com.nishiyu.lunex.api.mainframe;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * メインフレームの内部機能拡張（IMainframeExtension）を管理する公開レジストリ。
 */
public class MainframeExtensionRegistry {
    // マルチスレッドでの安全な登録を保証
    private static final Map<ResourceLocation, Supplier<IMainframeExtension>> FACTORIES = new ConcurrentHashMap<>();

    /**
     * アドオン初期化時に、独自の拡張モジュールを登録します。
     */
    public static void register(ResourceLocation id, Supplier<IMainframeExtension> factory) {
        FACTORIES.put(id, factory);
    }

    /**
     * マスターノード生成時に、登録されたすべての拡張を実体化してマップとして返します。
     * （LUNEXコアのSimpleMachineBlockEntity内で自動的に呼ばれます）
     */
    public static Map<ResourceLocation, IMainframeExtension> createAllInstances() {
        Map<ResourceLocation, IMainframeExtension> instances = new ConcurrentHashMap<>();
        for (Map.Entry<ResourceLocation, Supplier<IMainframeExtension>> entry : FACTORIES.entrySet()) {
            instances.put(entry.getKey(), entry.getValue().get());
        }
        return instances;
    }
}