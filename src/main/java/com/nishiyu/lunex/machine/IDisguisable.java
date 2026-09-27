package com.nishiyu.lunex.machine;

import net.minecraft.world.level.block.state.BlockState;

public interface IDisguisable {
    /** 現在の偽装用のBlockStateを取得します */
    BlockState getDisguiseState();

    /** 偽装用のBlockStateをセットします */
    void setDisguiseState(BlockState state);

    /**
     * 偽装状態が変化した際のブロックの更新処理（IS_DISGUISEDの切り替え、setChanged、syncなど）を行います。
     * @param isDisguised 偽装するかどうか
     */
    void applyDisguiseState(boolean isDisguised);
}