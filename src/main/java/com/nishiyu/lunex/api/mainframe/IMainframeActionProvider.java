// IMainframeActionProvider.java
package com.nishiyu.lunex.api.mainframe;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface IMainframeActionProvider<T extends BlockEntity> {
    /**
     * @param action クライアントから送信されたアクション名
     * @param payload ペイロードデータ
     * @param be 対象のBlockEntity
     * @param level ワールド
     * @param player 操作したプレイヤー (追加)
     * @return 処理が完了し、以後の汎用処理をスキップする場合は true
     */
    boolean handleAction(String action, String payload, T be, Level level, ServerPlayer player);
}