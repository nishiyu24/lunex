package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.api.MainframeComponentData;
import com.nishiyu.lunex.api.MainframeComponentRegistry;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class MainframeBottomTabRegistry {

    private static final List<BottomTabEntry> REGISTRY = new ArrayList<>();
    // コンストラクタをキャッシュ（画面を開くたびに新しいインスタンスを生成するため）
    private static final Map<String, java.lang.reflect.Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    /**
     * 条件付きのグローバルなボトムタブを登録します（SystemStorageなど）。
     */
    public static void registerGlobal(Supplier<IdeScreenFramework.IBottomTab> tabSupplier, BiPredicate<SimpleMachineBlockEntity, Level> condition) {
        REGISTRY.add(new BottomTabEntry(tabSupplier, condition));
    }

    /**
     * 現在のメインフレーム構成に合致するすべてのボトムタブを取得します。
     */
    public static List<IdeScreenFramework.IBottomTab> getTabsFor(SimpleMachineBlockEntity master, Level level) {
        List<IdeScreenFramework.IBottomTab> tabs = new ArrayList<>();

        // 1. 条件付きのグローバルタブ
        for (BottomTabEntry entry : REGISTRY) {
            if (entry.condition.test(master, level)) {
                tabs.add(entry.tabSupplier.get());
            }
        }

        // 2. ブロックごとに設定されたアドオン追加タブ (重複を避ける)
        Set<String> addedClasses = new HashSet<>();
        for (BlockPos p : master.mainframeParts) {
            BlockState state = level.getBlockState(p);
            MainframeComponentData data = MainframeComponentRegistry.get(state.getBlock());

            if (data != null && !data.getBottomTabs().isEmpty()) {
                for (String className : data.getBottomTabs()) {
                    if (addedClasses.add(className)) { // 初回出現時のみ
                        IdeScreenFramework.IBottomTab tab = createTabInstance(className);
                        if (tab != null) {
                            tabs.add(tab);
                        }
                    }
                }
            }
        }
        return tabs;
    }

    private static IdeScreenFramework.IBottomTab createTabInstance(String className) {
        try {
            java.lang.reflect.Constructor<?> ctor = CONSTRUCTOR_CACHE.computeIfAbsent(className, cls -> {
                try {
                    return Class.forName(cls).getDeclaredConstructor();
                } catch (Exception e) {
                    System.err.println("[Lunex] Failed to find constructor for Bottom Tab: " + cls);
                    return null;
                }
            });
            if (ctor != null) {
                return (IdeScreenFramework.IBottomTab) ctor.newInstance();
            }
        } catch (Exception e) {
            System.err.println("[Lunex] Failed to instantiate Bottom Tab: " + className);
        }
        return null;
    }

    private record BottomTabEntry(Supplier<IdeScreenFramework.IBottomTab> tabSupplier, BiPredicate<SimpleMachineBlockEntity, Level> condition) {}
}