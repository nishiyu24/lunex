package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.api.MainframeComponentData;
import com.nishiyu.lunex.api.MainframeComponentRegistry;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainframeUIRegistry {
    private static final List<IMainframeUIExtensionProvider> PROVIDERS = new ArrayList<>();
    private static final Map<String, IMainframeUIExtension<?>> UI_CACHE = new ConcurrentHashMap<>();

    public interface ILeftPanelProvider {
        IdeScreenFramework.ILeftPanel create(BlockEntity masterBe);
    }
    public interface IRightPanelProvider {
        IdeScreenFramework.IRightPanel create(BlockPos selectedPos, BlockEntity be, IMainframeUIExtension<?> extension);
    }

    private static ILeftPanelProvider leftPanelProvider = null;
    private static IRightPanelProvider rightPanelProvider = null;

    public static void setLeftPanelProvider(ILeftPanelProvider provider) { leftPanelProvider = provider; }
    public static void setRightPanelProvider(IRightPanelProvider provider) { rightPanelProvider = provider; }

    public static IdeScreenFramework.ILeftPanel createLeftPanel(BlockEntity masterBe) {
        return leftPanelProvider != null ? leftPanelProvider.create(masterBe) : null;
    }

    public static IdeScreenFramework.IRightPanel createRightPanel(BlockPos selectedPos, BlockEntity be, IMainframeUIExtension<?> extension) {
        return rightPanelProvider != null ? rightPanelProvider.create(selectedPos, be, extension) : null;
    }

    public static void registerProvider(IMainframeUIExtensionProvider provider) {
        PROVIDERS.add(provider);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> IMainframeUIExtension<T> get(T be) {
        if (be == null) return null;

        // ★追加: アダプターブロックの場合はラップされている元のブロックを参照する
        Block targetBlock = be.getBlockState().getBlock();
        if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
            targetBlock = adapter.getOriginalState().getBlock();
        }

        // ビルダーで addBlockTab に登録された UI 拡張クラスを動的に解決
        MainframeComponentData data = MainframeComponentRegistry.get(targetBlock);
        if (data != null && !data.getBlockTabs().isEmpty()) {
            for (String className : data.getBlockTabs()) {
                IMainframeUIExtension<?> extension = UI_CACHE.computeIfAbsent(className, cls -> {
                    try {
                        Class<?> clazz = Class.forName(cls);
                        return (IMainframeUIExtension<?>) clazz.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        System.err.println("[Lunex] Failed to load Block Tab UI Extension: " + cls);
                        return null;
                    }
                });
                if (extension != null) return (IMainframeUIExtension<T>) extension;
            }
        }

        for (IMainframeUIExtensionProvider provider : PROVIDERS) {
            IMainframeUIExtension<?> extension = provider.getExtension(be);
            if (extension != null) {
                return (IMainframeUIExtension<T>) extension;
            }
        }
        return null;
    }
}