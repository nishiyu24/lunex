package com.nishiyu.lunex.mcnet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface IMCNetBlock {

    /**
     * ブロックが設置されたときのネットワーク更新処理
     */
    default void updateNetworkOnPlace(BlockState state, Level level, BlockPos pos, BlockState oldState) {
        if (!state.is(oldState.getBlock())) {
            MCNetUtil.triggerNetworkUpdate(level, pos);
        }
    }

    /**
     * ブロックが破壊・撤去されたときのネットワーク更新処理
     */
    default void updateNetworkOnRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (!state.is(newState.getBlock())) {
            MCNetUtil.triggerNetworkUpdate(level, pos);
        }
    }
}