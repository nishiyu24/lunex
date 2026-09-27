package com.nishiyu.lunex.mcnet;

import net.minecraft.world.level.block.entity.BlockEntity;

public interface IMCNetDevice {
    /**
     * ネットワーク上でアクティブかどうかを返します。
     * デフォルトは true ですが、ProbeBlockEntity のように条件がある場合はオーバーライドします。
     */
    default boolean isNetworkActive() {
        return true;
    }

    /**
     * ネットワーク・クライアントへブロックのデータを同期（更新）します。
     * 各BlockEntityで重複していた処理をここに集約しています。
     */
    default void sync() {
        if (this instanceof BlockEntity be) {
            if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
            }
        }
    }
}