package com.nishiyu.lunex.api.client;

import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * メインフレームのオーバービュー画面で、ブロックごとの動的なUIを提供するインターフェース。
 * アドオン開発者はこれを実装し、MainframeUIRegistry に登録します。
 */
public interface IMainframeUIExtension {
    /**
     * 描画するUIパネルの全体の高さを指定します。
     */
    int getPanelHeight(BlockEntity be);

    /**
     * ボタンやテキストボックスなどの操作ウィジェットを構築します。
     */
    void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, BlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget);

    /**
     * パネル内に固有のテキストやアイコンを描画します。
     */
    void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, BlockEntity be, int panelX, int textY);

}