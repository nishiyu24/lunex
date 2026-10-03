package com.nishiyu.lunex.api.client;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayList;
import java.util.List;

public class MainframeUIRegistry {
    // 複数のプロバイダーを管理
    private static final List<IMainframeUIExtensionProvider> PROVIDERS = new ArrayList<>();

    /**
     * UI拡張プロバイダーを登録します。
     * アドオンや他のMODからも自由に登録できます。
     */
    public static void registerProvider(IMainframeUIExtensionProvider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * BlockEntity に適合する最初のUI拡張を取得します。
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> IMainframeUIExtension<T> get(T be) {
        if (be == null) return null;
        for (IMainframeUIExtensionProvider provider : PROVIDERS) {
            IMainframeUIExtension<?> extension = provider.getExtension(be);
            if (extension != null) {
                return (IMainframeUIExtension<T>) extension;
            }
        }
        return null;
    }
}