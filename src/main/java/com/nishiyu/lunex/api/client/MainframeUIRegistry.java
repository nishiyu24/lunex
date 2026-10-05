// MainframeUIRegistry.java
package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.api.client.ui.panel.AbstractRightPanel;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity; // ★追加
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState; // ★追加

import java.util.HashMap;
import java.util.Map;

public class MainframeUIRegistry {

    public interface PanelFactory {
        AbstractRightPanel create(BlockPos pos, BlockEntity be);
    }

    private static final Map<Block, PanelFactory> REGISTRY = new HashMap<>();

    public static void register(Block block, PanelFactory factory) {
        REGISTRY.put(block, factory);
    }

    public static AbstractRightPanel createRightPanel(BlockPos pos, BlockEntity be) {
        if (be == null) return null;

        Block block = be.getBlockState().getBlock();

        // ★修正: Adapterの場合は包まれている本来のブロックを取得する
        if (be instanceof MainframeAdapterBlockEntity adapter) {
            BlockState original = adapter.getOriginalState();
            if (original != null) {
                block = original.getBlock();
            }
        }

        PanelFactory factory = REGISTRY.get(block);
        if (factory != null) {
            return factory.create(pos, be);
        }
        return null;
    }

    public static Component getDisplayBlockName(BlockEntity be) {
        if (be == null) return Component.empty();

        // ★名前の表示もAdapterの中身を反映させる
        if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
            return adapter.getOriginalState().getBlock().getName();
        }

        return be.getBlockState().getBlock().getName();
    }
}