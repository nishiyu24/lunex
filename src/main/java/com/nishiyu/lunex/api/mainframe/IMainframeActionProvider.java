package com.nishiyu.lunex.api.mainframe;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * サーバー側でメインフレームのアクションパケットを処理するプロバイダー。
 * UI拡張(クライアント側)と対になるサーバー側の処理を定義します。
 */
public interface IMainframeActionProvider<T extends BlockEntity> {
    /**
     * @param action クライアントから送信されたアクション名
     * @param payload ペイロードデータ
     * @param be 対象のBlockEntity
     * @param level ワールド
     * @return 処理が完了し、以後の汎用処理をスキップする場合は true
     */
    boolean handleAction(String action, String payload, T be, Level level);
}