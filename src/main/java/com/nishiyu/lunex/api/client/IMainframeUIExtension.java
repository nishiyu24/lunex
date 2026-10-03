package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * メインフレームのオーバービュー画面で、ブロックごとの動的なUIを提供するインターフェース。
 */
public interface IMainframeUIExtension<T extends BlockEntity> {

    /**
     * ボタンやテキストボックスなどの操作ウィジェットを構築します。
     */
    void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, T be, int panelX, int textY, Consumer<AbstractWidget> addWidget);

    /**
     * パネル内に固有のテキストやアイコンを描画します。
     */
    void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, T be, int panelX, int textY);

    /**
     * 描画するUIパネルの高さを指定します。
     */
    int getPanelHeight(T be);

    /**
     * サーバー側で、クライアントから送信された操作パケットを処理します。
     * @return 処理が成功し、以後の汎用処理をスキップする場合は true を返します。
     */
    default boolean handleAction(String action, String value, T be, Level level) {
        return false;
    }
}