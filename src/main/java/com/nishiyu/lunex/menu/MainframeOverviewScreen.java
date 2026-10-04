package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.api.client.*;
import com.nishiyu.lunex.api.client.ui.main.Mainframe3DView;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MainframeOverviewScreen extends AbstractContainerScreen<MainframeOverviewMenu> {

    // ★変更: currentInstance の代わりに選択中のブロック座標を記憶
    public static BlockPos lastSelectedPos = null;

    private BlockPos selectedPos = null;

    public BlockPos getSelectedPos() {
        return this.selectedPos;
    }

    private record ScrolledWidget(AbstractWidget widget, int initialY) {}
    private final List<ScrolledWidget> dynamicWidgets = new ArrayList<>();

    private final IdeScreenFramework uiFramework;
    private final Mainframe3DView view3d;
    private static final int TOP_BAR_HEIGHT = 22;

    public MainframeOverviewScreen(MainframeOverviewMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        this.imageWidth = 1;
        this.imageHeight = 1;
        this.uiFramework = new IdeScreenFramework();
        this.view3d = new Mainframe3DView(menu.getLevel(), menu.getMasterPos(), () -> this.selectedPos, this::selectBlock);
    }

    public IdeScreenFramework getUiFramework() {
        return this.uiFramework;
    }

    private int getVpX() { return uiFramework.showLeftPanel ? uiFramework.leftWidth : 0; }
    private int getVpY() { return TOP_BAR_HEIGHT; }
    private int getVpWidth() {
        int w = this.width - uiFramework.rightWidth;
        if (uiFramework.showLeftPanel) w -= uiFramework.leftWidth;
        return w;
    }
    private int getVpHeight() { return this.height - TOP_BAR_HEIGHT - uiFramework.bottomHeight; }

    @Override
    protected void init() {
        super.init();

        // ★追加: JEI画面から戻った際などに選択状態を復元する
        if (this.selectedPos == null && lastSelectedPos != null) {
            this.selectedPos = lastSelectedPos;
        }

        this.titleLabelX = 9999; this.titleLabelY = 9999;
        this.inventoryLabelX = 9999; this.inventoryLabelY = 9999;

        this.view3d.initScale(getVpWidth(), getVpHeight());

        BlockEntity masterBe = this.menu.getLevel().getBlockEntity(this.menu.getMasterPos());
        this.uiFramework.setLeftPanel(MainframeUIRegistry.createLeftPanel(masterBe));
        this.uiFramework.clearBottomTabs();

        if (masterBe instanceof SimpleMachineBlockEntity master) {
            List<IdeScreenFramework.IBottomTab> dynamicTabs = MainframeBottomTabRegistry.getTabsFor(master, this.menu.getLevel());
            for (IdeScreenFramework.IBottomTab tab : dynamicTabs) {
                this.uiFramework.addBottomTab(tab);
            }
        }
        rebuildUI();
        updateSlotPositions();
    }

    @Override
    public void removed() {
        super.removed();
        // ★変更: currentInstanceをnullにする処理を削除
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.view3d.tick();

        if (this.uiFramework != null && this.uiFramework.getRightPanel() != null) {
            int scrollY = this.uiFramework.getRightPanel().getScrollY();
            int panelTopY = TOP_BAR_HEIGHT + 21;
            int panelBottomY = this.height - uiFramework.bottomHeight;

            for (ScrolledWidget sw : dynamicWidgets) {
                int newY = sw.initialY - scrollY;
                sw.widget.setY(newY);
                boolean inBounds = (newY >= panelTopY && newY + sw.widget.getHeight() <= panelBottomY);
                sw.widget.active = inBounds;
                sw.widget.visible = inBounds;
            }
        }
    }

    public void rebuildUI() {
        for (ScrolledWidget sw : dynamicWidgets) this.removeWidget(sw.widget);
        dynamicWidgets.clear();

        this.uiFramework.showLeftPanel = (this.width >= 800 || this.selectedPos == null);

        BlockEntity be = this.selectedPos != null ? this.menu.getLevel().getBlockEntity(this.selectedPos) : null;
        IMainframeUIExtension extension = (be != null) ? MainframeUIRegistry.get(be) : null;

        this.uiFramework.setRightPanel(MainframeUIRegistry.createRightPanel(this.selectedPos, be, extension));

        if (extension != null) {
            int panelX = this.width - uiFramework.rightWidth;
            int textY = TOP_BAR_HEIGHT + 70;
            extension.buildWidgets(this, this.selectedPos, be, panelX, textY, widget -> {
                this.addRenderableWidget(widget);
                dynamicWidgets.add(new ScrolledWidget(widget, widget.getY()));
            });
        }
    }

    private void selectBlock(BlockPos pos) {
        this.selectedPos = pos;
        BlockEntity be = this.menu.getLevel().getBlockEntity(pos);
        if (be instanceof ScreenBlockEntity screenBe && screenBe.masterPos != null) {
            this.selectedPos = screenBe.masterPos;
        }

        // ★追加: 選択されたブロックの座標を記憶
        lastSelectedPos = this.selectedPos;

        rebuildUI();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ScrolledWidget sw : this.dynamicWidgets) {
            if (sw.widget.active && sw.widget.visible && sw.widget.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(sw.widget);
                return true;
            }
        }
        this.setFocused(null);

        if (uiFramework.mouseClicked(mouseX, mouseY, button, this.width, this.height, TOP_BAR_HEIGHT)) {
            return true;
        }

        if (!uiFramework.hasCustomCenterPanel() && isMouseInViewport(mouseX, mouseY)) {
            if (this.view3d.mouseClicked(getVpX(), getVpY(), getVpWidth(), getVpHeight(), mouseX, mouseY, button)) {
                return true;
            }
        }

        updateSlotPositions();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (uiFramework.mouseReleased(mouseX, mouseY, button, this.width, this.height, TOP_BAR_HEIGHT)) return true;

        if (!uiFramework.hasCustomCenterPanel()) {
            this.view3d.mouseReleased(button);
        }

        updateSlotPositions();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (uiFramework.mouseDragged(mouseX, mouseY, button, dragX, dragY, this.width, this.height, TOP_BAR_HEIGHT)) {
            rebuildUI();
            return true;
        }

        if (!uiFramework.hasCustomCenterPanel() && isMouseInViewport(mouseX, mouseY)) {
            if (this.view3d.mouseDragged(dragX, dragY, button)) return true;
        }

        updateSlotPositions();
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (uiFramework.mouseScrolled(mouseX, mouseY, scrollX, scrollY, this.width, this.height, TOP_BAR_HEIGHT)) return true;

        if (!uiFramework.hasCustomCenterPanel() && isMouseInViewport(mouseX, mouseY)) {
            if (this.view3d.mouseScrolled(scrollY)) return true;
        }

        updateSlotPositions();
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.uiFramework.keyPressed(keyCode, scanCode, modifiers, this.width, this.height, TOP_BAR_HEIGHT)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.uiFramework.charTyped(codePoint, modifiers, this.width, this.height, TOP_BAR_HEIGHT)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isMouseInViewport(double mouseX, double mouseY) {
        return mouseX >= getVpX() && mouseX <= getVpX() + getVpWidth() && mouseY >= getVpY() && mouseY <= getVpY() + getVpHeight();
    }

    private void updateSlotPositions() {
        IdeScreenFramework.IBottomTab activeTab = uiFramework.getActiveBottomTab();
        if (activeTab != null) {
            this.leftPos = activeTab.getPlayerInventoryX();
            this.topPos = activeTab.getPlayerInventoryY(this.height, uiFramework.bottomHeight);
        } else {
            this.leftPos = 0;
            this.topPos = this.height + 100;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, TOP_BAR_HEIGHT, 0xFF3C3C3C);
        guiGraphics.drawString(this.font, "LUNEX MAINFRAME EDITOR", 10, 6, 0xFFD4D4D4);

        if (!uiFramework.hasCustomCenterPanel()) {
            this.view3d.render(guiGraphics, getVpX(), getVpY(), getVpWidth(), getVpHeight(), mouseX, mouseY);
        }

        uiFramework.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, TOP_BAR_HEIGHT);

        updateSlotPositions();

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.renderTooltip(guiGraphics, mouseX, mouseY);

        if (!uiFramework.hasCustomCenterPanel()) {
            int btnX = getVpX() + getVpWidth() - 30;
            int btnY = getVpY() + 10;
            if (mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= btnY && mouseY <= btnY + 20) {
                guiGraphics.renderTooltip(this.font, Component.literal(view3d.isExploded() ? "Collapse View" : "Explode View"), mouseX, mouseY);
            }
        }

        this.leftPos = -10000;
        this.topPos = -10000;
        this.imageWidth = 1;
        this.imageHeight = 1;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return false;
    }
}