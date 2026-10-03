package com.nishiyu.lunex.client.ui;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.api.client.IMainframeUIExtensionProvider;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 汎用的なアダプターUI拡張。
 * 内部に登録された各「モジュール（IAdapterModuleUI）」に処理を委譲し、
 * ラップされているブロック（originalState）に応じた動的なUIを提供します。
 */
public class AdapterUIExtension implements IMainframeUIExtension<MainframeAdapterBlockEntity>, IMainframeUIExtensionProvider {

    // アダプター内で処理を分岐させるためのサブモジュール用インターフェース
    public interface IAdapterModuleUI {
        boolean canHandle(BlockState originalState);
        int getPanelHeight(MainframeAdapterBlockEntity be);
        void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, MainframeAdapterBlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget);
        void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, MainframeAdapterBlockEntity be, int panelX, int textY);
        boolean handleAction(String action, String value, MainframeAdapterBlockEntity be, Level level);
    }

    private static final List<IAdapterModuleUI> MODULES = new ArrayList<>();

    // 外部のアドオン等からモジュールを登録するためのメソッド
    public static void registerModule(IAdapterModuleUI module) {
        MODULES.add(module);
    }

    private IAdapterModuleUI getActiveModule(MainframeAdapterBlockEntity be) {
        BlockState state = be.getOriginalState();
        if (state == null) return null;
        for (IAdapterModuleUI module : MODULES) {
            if (module.canHandle(state)) {
                return module;
            }
        }
        return null;
    }

    @Override
    public IMainframeUIExtension<?> getExtension(BlockEntity be) {
        if (be instanceof MainframeAdapterBlockEntity adapter) {
            if (getActiveModule(adapter) != null) {
                return this;
            }
        }
        return null;
    }

    @Override
    public int getPanelHeight(MainframeAdapterBlockEntity be) {
        IAdapterModuleUI module = getActiveModule(be);
        return module != null ? module.getPanelHeight(be) : 160;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, MainframeAdapterBlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        IAdapterModuleUI module = getActiveModule(be);
        if (module != null) {
            module.buildWidgets(screen, pos, be, panelX, textY, addWidget);
        }
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, MainframeAdapterBlockEntity be, int panelX, int textY) {
        IAdapterModuleUI module = getActiveModule(be);
        if (module != null) {
            module.renderDetails(guiGraphics, font, pos, be, panelX, textY);
        } else {
            guiGraphics.drawString(font, "Adapter Device:", panelX + 5, textY, 0x00E5FF);
        }
    }

    @Override
    public boolean handleAction(String action, String value, MainframeAdapterBlockEntity be, Level level) {
        IAdapterModuleUI module = getActiveModule(be);
        if (module != null) {
            return module.handleAction(action, value, be, level);
        }
        return false;
    }
}