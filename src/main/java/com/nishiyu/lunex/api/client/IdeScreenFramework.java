package com.nishiyu.lunex.api.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class IdeScreenFramework {

    private int splitterColor = 0xFF6A6A6A;
    private int center3dBackgroundColor = 0x44000000;

    private ILeftPanel leftPanel = null;
    private IRightPanel rightPanel = null;
    private final List<IBottomTab> bottomTabs = new ArrayList<>();
    private int activeBottomTabIndex = 0;

    public int leftWidth = 220;
    public int rightWidth = 240;
    public int bottomHeight = 140;
    public boolean showLeftPanel = true;

    private int draggingSplitter = 0;
    private static final int SPLITTER_HITBOX = 4;

    public IdeScreenFramework setSplitterColor(int argb) { this.splitterColor = argb; return this; }
    public IdeScreenFramework setCenter3dBackground(int argb) { this.center3dBackgroundColor = argb; return this; }
    public IdeScreenFramework setLeftPanel(ILeftPanel panel) { this.leftPanel = panel; return this; }
    public IdeScreenFramework setRightPanel(IRightPanel panel) { this.rightPanel = panel; return this; }

    public ILeftPanel getLeftPanel() { return this.leftPanel; }
    public IRightPanel getRightPanel() { return this.rightPanel; }

    public IdeScreenFramework addBottomTab(IBottomTab tab) {
        this.bottomTabs.add(tab);
        return this;
    }

    public void clearBottomTabs() {
        this.bottomTabs.clear();
        this.activeBottomTabIndex = 0;
    }

    public IBottomTab getActiveBottomTab() {
        if (bottomTabs.isEmpty() || activeBottomTabIndex < 0 || activeBottomTabIndex >= bottomTabs.size()) {
            return null;
        }
        return bottomTabs.get(activeBottomTabIndex);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int topHeight) {
        int activeLeftWidth = showLeftPanel ? this.leftWidth : 0;
        int bottomY = screenHeight - this.bottomHeight;

        graphics.fill(activeLeftWidth, topHeight, screenWidth - this.rightWidth, bottomY, this.center3dBackgroundColor);

        if (this.leftPanel != null && showLeftPanel) {
            this.leftPanel.render(graphics, 0, topHeight, this.leftWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, partialTick);
        }

        if (this.rightPanel != null) {
            this.rightPanel.render(graphics, screenWidth - this.rightWidth, topHeight, this.rightWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, partialTick);
        }

        graphics.fill(0, bottomY, screenWidth, screenHeight, 0xFF252526);
        if (!bottomTabs.isEmpty() && this.bottomHeight > 20) {
            drawBottomTabHeaders(graphics, screenWidth, bottomY, mouseX, mouseY);
            IBottomTab activeTab = bottomTabs.get(activeBottomTabIndex);
            activeTab.render(graphics, 0, bottomY + 20, screenWidth, this.bottomHeight - 20, mouseX, mouseY, partialTick);
        }

        drawSplitters(graphics, screenWidth, screenHeight, activeLeftWidth, this.rightWidth, topHeight, this.bottomHeight, mouseX, mouseY);
    }

    private void drawBottomTabHeaders(GuiGraphics graphics, int screenWidth, int bottomY, int mouseX, int mouseY) {
        int tabX = 10;
        int tabHeight = 20;
        for (int i = 0; i < bottomTabs.size(); i++) {
            IBottomTab tab = bottomTabs.get(i);
            int tabWidth = Minecraft.getInstance().font.width(tab.getTitle()) + 20;
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= bottomY && mouseY <= bottomY + tabHeight;
            boolean isActive = (i == activeBottomTabIndex);

            int color = isActive ? 0xFF3E3E42 : (isHovered ? 0xFF35353A : 0xFF252526);
            graphics.fill(tabX, bottomY, tabX + tabWidth, bottomY + tabHeight, color);
            if (isActive) graphics.fill(tabX, bottomY, tabX + tabWidth, bottomY + 1, 0xFF007ACC);

            graphics.drawString(Minecraft.getInstance().font, tab.getTitle(), tabX + 10, bottomY + 6, isActive ? 0xFFFFFFFF : 0xFFAAAAAA);
            tabX += tabWidth + 2;
        }
        graphics.fill(0, bottomY + tabHeight - 1, screenWidth, bottomY + tabHeight, this.splitterColor);
    }

    private void drawSplitters(GuiGraphics graphics, int width, int height, int left, int right, int top, int bottom, int mouseX, int mouseY) {
        graphics.fill(0, top, width, top + 1, this.splitterColor);
        int colorBottom = (draggingSplitter == 3 || Math.abs(mouseY - (height - bottom)) <= SPLITTER_HITBOX) ? 0xFF007ACC : this.splitterColor;
        graphics.fill(0, height - bottom, width, height - bottom + 1, colorBottom);

        if (showLeftPanel) {
            // ★ 修正: Y座標がボトムタブより上にある時のみハイライト判定を行う
            boolean hoverLeft = Math.abs(mouseX - left) <= SPLITTER_HITBOX && mouseY >= top && mouseY <= (height - bottom);
            int colorLeft = (draggingSplitter == 1 || hoverLeft) ? 0xFF007ACC : this.splitterColor;
            graphics.fill(left - 1, top, left, height - bottom, colorLeft);
        }

        // ★ 修正: 同様に右スプリッターのハイライト判定もY座標を制限
        boolean hoverRight = Math.abs(mouseX - (width - right)) <= SPLITTER_HITBOX && mouseY >= top && mouseY <= (height - bottom);
        int colorRight = (draggingSplitter == 2 || hoverRight) ? 0xFF007ACC : this.splitterColor;
        graphics.fill(width - right, top, width - right + 1, height - bottom, colorRight);
    }

    public static void drawEditorPanelBackground(GuiGraphics guiGraphics, int x, int y, int width, int height, String title, boolean isLeft) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2D2D30);
        guiGraphics.fill(x, y, x + width, y + 20, 0xFF3E3E42);
        guiGraphics.fill(x, y + 20, x + width, y + 21, 0xFF6A6A6A);
        guiGraphics.drawString(Minecraft.getInstance().font, title, x + 8, y + 6, 0xFFFFFFFF);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight, int topHeight) {
        int activeLeftWidth = showLeftPanel ? this.leftWidth : 0;
        int bottomY = screenHeight - this.bottomHeight;

        // ボトムスプリッターの判定
        if (Math.abs(mouseY - bottomY) <= SPLITTER_HITBOX) { draggingSplitter = 3; return true; }

        // ★ 修正: 左右のスプリッターの当たり判定を「Y座標がボトムタブより上にある場合のみ」に制限
        if (mouseY >= topHeight && mouseY <= bottomY) {
            if (showLeftPanel && Math.abs(mouseX - activeLeftWidth) <= SPLITTER_HITBOX) { draggingSplitter = 1; return true; }
            if (Math.abs(mouseX - (screenWidth - this.rightWidth)) <= SPLITTER_HITBOX) { draggingSplitter = 2; return true; }
        }

        if (mouseY >= bottomY) {
            if (mouseY <= bottomY + 20) {
                int tabX = 10;
                for (int i = 0; i < bottomTabs.size(); i++) {
                    int tabWidth = Minecraft.getInstance().font.width(bottomTabs.get(i).getTitle()) + 20;
                    if (mouseX >= tabX && mouseX <= tabX + tabWidth) {
                        activeBottomTabIndex = i;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    tabX += tabWidth + 2;
                }
            } else if (!bottomTabs.isEmpty()) {
                bottomTabs.get(activeBottomTabIndex).mouseClicked(0, bottomY + 20, screenWidth, this.bottomHeight - 20, mouseX, mouseY, button);
            }
            return true;
        }

        if (this.leftPanel != null && showLeftPanel && mouseX <= activeLeftWidth && mouseY >= topHeight && mouseY <= bottomY) {
            if (this.leftPanel.mouseClicked(0, topHeight, activeLeftWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, button)) return true;
        }
        if (this.rightPanel != null && mouseX >= screenWidth - this.rightWidth && mouseY >= topHeight && mouseY <= bottomY) {
            if (this.rightPanel.mouseClicked(screenWidth - this.rightWidth, topHeight, this.rightWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, button)) return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (draggingSplitter == 1) {
            this.leftWidth = Mth.clamp((int) mouseX, 100, screenWidth / 2 - 50);
            return true;
        } else if (draggingSplitter == 2) {
            this.rightWidth = Mth.clamp(screenWidth - (int) mouseX, 150, screenWidth / 2 - 50);
            return true;
        } else if (draggingSplitter == 3) {
            this.bottomHeight = Mth.clamp(screenHeight - (int) mouseY, 60, screenHeight - 100);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.draggingSplitter = 0;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int screenWidth, int screenHeight, int topHeight) {
        int activeLeftWidth = showLeftPanel ? this.leftWidth : 0;
        int bottomY = screenHeight - this.bottomHeight;

        if (mouseY > bottomY) {
            if (!bottomTabs.isEmpty()) {
                bottomTabs.get(activeBottomTabIndex).mouseScrolled(0, bottomY + 20, screenWidth, this.bottomHeight - 20, mouseX, mouseY, scrollX, scrollY);
            }
            return true;
        }
        if (showLeftPanel && mouseX <= activeLeftWidth && mouseY >= topHeight && mouseY <= bottomY) {
            if (this.leftPanel != null) {
                this.leftPanel.mouseScrolled(0, topHeight, activeLeftWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, scrollX, scrollY);
            }
            return true;
        }
        if (mouseX >= screenWidth - this.rightWidth && mouseY >= topHeight && mouseY <= bottomY) {
            if (this.rightPanel != null) {
                this.rightPanel.mouseScrolled(screenWidth - this.rightWidth, topHeight, this.rightWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, scrollX, scrollY);
            }
            return true;
        }
        return false;
    }

    public interface ILeftPanel {
        void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
        default boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    }

    public interface IRightPanel {
        void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
        int getScrollY();
        default boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    }

    public interface IBottomTab {
        String getTitle();
        void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
        default boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

        default int getPlayerInventoryX() { return 9999; }
        default int getPlayerInventoryY(int screenHeight, int bottomHeight) { return 9999; }
    }
}