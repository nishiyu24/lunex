package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class MainframeBottomTabRegistry {

    private static final List<BottomTabEntry> GLOBAL_TABS = new ArrayList<>();
    private static final Map<Block, List<Supplier<IdeScreenFramework.IBottomTab>>> BLOCK_TABS = new HashMap<>();

    public static void registerGlobal(Supplier<IdeScreenFramework.IBottomTab> tabSupplier, BiPredicate<SimpleMachineBlockEntity, Level> condition) {
        GLOBAL_TABS.add(new BottomTabEntry(tabSupplier, condition));
    }

    public static void registerBlockTab(Block block, Supplier<IdeScreenFramework.IBottomTab> tabSupplier) {
        BLOCK_TABS.computeIfAbsent(block, k -> new ArrayList<>()).add(tabSupplier);
    }

    public static List<IdeScreenFramework.IBottomTab> getTabsFor(SimpleMachineBlockEntity master, Level level) {
        List<IdeScreenFramework.IBottomTab> tabs = new ArrayList<>();

        for (BottomTabEntry entry : GLOBAL_TABS) {
            if (entry.condition.test(master, level)) {
                tabs.add(entry.tabSupplier.get());
            }
        }

        Set<Class<?>> addedClasses = new HashSet<>();
        for (BlockPos p : master.mainframeParts) {
            BlockState state = level.getBlockState(p);
            Block block = state.getBlock();

            if (BLOCK_TABS.containsKey(block)) {
                for (Supplier<IdeScreenFramework.IBottomTab> supplier : BLOCK_TABS.get(block)) {
                    IdeScreenFramework.IBottomTab tabInstance = supplier.get();
                    if (tabInstance != null && addedClasses.add(tabInstance.getClass())) {
                        tabs.add(tabInstance);
                    }
                }
            }
        }
        return tabs;
    }

    private record BottomTabEntry(Supplier<IdeScreenFramework.IBottomTab> tabSupplier, BiPredicate<SimpleMachineBlockEntity, Level> condition) {}
}