package com.nishiyu.lunex.api.mainframe;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

@FunctionalInterface
public interface IMainframeActionHandler<T extends BlockEntity> {
    /**
     * @param payload クライアントから送信された値
     * @param be 対象のBlockEntity
     * @param level ワールド
     * @return 処理が成功し、以後の汎用処理をスキップする場合は true
     */
    boolean handleAction(String payload, T be, Level level);
}