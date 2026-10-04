package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;

public class MainframeUIRegistry {
    private static final Map<Block, IMainframeUIExtension> UI_MAP = new HashMap<>();

    public interface ILeftPanelProvider {
        IdeScreenFramework.ILeftPanel create(BlockEntity masterBe);
    }
    public interface IRightPanelProvider {
        IdeScreenFramework.IRightPanel create(BlockPos selectedPos, BlockEntity be, IMainframeUIExtension extension);
    }

    private static ILeftPanelProvider leftPanelProvider = null;
    private static IRightPanelProvider rightPanelProvider = null;

    public static void setLeftPanelProvider(ILeftPanelProvider provider) { leftPanelProvider = provider; }
    public static void setRightPanelProvider(IRightPanelProvider provider) { rightPanelProvider = provider; }

    public static IdeScreenFramework.ILeftPanel createLeftPanel(BlockEntity masterBe) {
        return leftPanelProvider != null ? leftPanelProvider.create(masterBe) : null;
    }

    public static IdeScreenFramework.IRightPanel createRightPanel(BlockPos selectedPos, BlockEntity be, IMainframeUIExtension extension) {
        return rightPanelProvider != null ? rightPanelProvider.create(selectedPos, be, extension) : null;
    }

    public static void register(Block block, IMainframeUIExtension extension) {
        UI_MAP.put(block, extension);
    }

    public static IMainframeUIExtension get(BlockEntity be) {
        if (be == null) return null;

        Block targetBlock = be.getBlockState().getBlock();
        if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
            targetBlock = adapter.getOriginalState().getBlock();
        }

        return UI_MAP.get(targetBlock);
    }

    // ★追加: アダプターを考慮して正しいブロック名（Component）を返すユーティリティ
    public static Component getDisplayBlockName(BlockEntity be) {
        if (be == null) return Component.empty();

        if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
            return adapter.getOriginalState().getBlock().getName();
        }
        return be.getBlockState().getBlock().getName();
    }
}