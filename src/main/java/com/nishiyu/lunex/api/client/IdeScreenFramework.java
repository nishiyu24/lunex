package com.nishiyu.lunex.api.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class IdeScreenFramework {

    public static float currentUiScale = 1.0f;
    public static int guiOffsetX = 0;
    public static int guiOffsetY = 0;

    public static void applyScissor(GuiGraphics graphics, int minX, int minY, int maxX, int maxY) {
        graphics.enableScissor(minX, minY, maxX, maxY);
    }

    private int splitterColor = 0xFF6A6A6A;
    private int center3dBackgroundColor = 0x44000000;

    private IRightPanel rightPanel = null;
    private ICenterPanel customCenterPanel = null;
    private final List<IBottomTab> bottomTabs = new ArrayList<>();
    private int activeBottomTabIndex = 0;

    public int rightWidth = 240;
    public int bottomHeight = 140;

    private int draggingSplitter = 0;
    private static final int SPLITTER_HITBOX = 4;

    public IdeScreenFramework setSplitterColor(int argb) { this.splitterColor = argb; return this; }
    public IdeScreenFramework setCenter3dBackground(int argb) { this.center3dBackgroundColor = argb; return this; }
    public IdeScreenFramework setRightPanel(IRightPanel panel) { this.rightPanel = panel; return this; }

    public void setCustomCenterPanel(ICenterPanel panel) { this.customCenterPanel = panel; }
    public void clearCustomCenterPanel() { this.customCenterPanel = null; }
    public boolean hasCustomCenterPanel() { return this.customCenterPanel != null; }

    public IRightPanel getRightPanel() { return this.rightPanel; }
    public ICenterPanel getCustomCenterPanel() { return this.customCenterPanel; }

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

    public void updateLayoutConstraints(int availableWidth, int availableHeight) {
        int maxSideWidth = Math.max(140, availableWidth / 2);
        int maxBottomHeight = Math.max(80, availableHeight / 2);

        this.rightWidth = Mth.clamp(this.rightWidth, 140, maxSideWidth);
        this.bottomHeight = Mth.clamp(this.bottomHeight, 80, maxBottomHeight);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight, int topHeight) {
        int bottomY = screenHeight - this.bottomHeight;

        int vpX = 0;
        int vpY = topHeight;
        int vpWidth = screenWidth - this.rightWidth - vpX;
        int vpHeight = screenHeight - topHeight - this.bottomHeight;

        graphics.fill(vpX, vpY, vpX + vpWidth, vpY + vpHeight, this.center3dBackgroundColor);

        if (this.customCenterPanel != null) {
            this.customCenterPanel.render(graphics, vpX, vpY, vpWidth, vpHeight, mouseX, mouseY, partialTick);

            int backBtnX = vpX + 10;
            int backBtnY = vpY + 10;
            String text = "◀ Back to 3D View";
            int backBtnW = Minecraft.getInstance().font.width(text) + 10;
            int backBtnH = 16;
            boolean hover = mouseX >= backBtnX && mouseX <= backBtnX + backBtnW && mouseY >= backBtnY && mouseY <= backBtnY + backBtnH;
            graphics.fill(backBtnX, backBtnY, backBtnX + backBtnW, backBtnY + backBtnH, hover ? 0xFF555555 : 0xFF333333);
            graphics.renderOutline(backBtnX, backBtnY, backBtnW, backBtnH, 0xFF6A6A6A);
            graphics.drawString(Minecraft.getInstance().font, text, backBtnX + 5, backBtnY + 4, 0xFFFFFFFF);
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

        drawSplitters(graphics, screenWidth, screenHeight, this.rightWidth, topHeight, this.bottomHeight, mouseX, mouseY);
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

    private void drawSplitters(GuiGraphics graphics, int width, int height, int right, int top, int bottom, int mouseX, int mouseY) {
        graphics.fill(0, top, width, top + 1, this.splitterColor);
        int colorBottom = (draggingSplitter == 3 || Math.abs(mouseY - (height - bottom)) <= SPLITTER_HITBOX) ? 0xFF007ACC : this.splitterColor;
        graphics.fill(0, height - bottom, width, height - bottom + 1, colorBottom);

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
        int bottomY = screenHeight - this.bottomHeight;
        int vpWidth = screenWidth - this.rightWidth;
        int vpHeight = screenHeight - topHeight - this.bottomHeight;

        if (Math.abs(mouseY - bottomY) <= SPLITTER_HITBOX) { draggingSplitter = 3; return true; }
        if (mouseY >= topHeight && mouseY <= bottomY) {
            if (Math.abs(mouseX - (screenWidth - this.rightWidth)) <= SPLITTER_HITBOX) { draggingSplitter = 2; return true; }
        }

        if (this.customCenterPanel != null && button == 0) {
            String text = "◀ Back to 3D View";
            int backBtnW = Minecraft.getInstance().font.width(text) + 10;
            int backBtnH = 16;
            int backBtnX = 10;
            int backBtnY = topHeight + 10;
            if (mouseX >= backBtnX && mouseX <= backBtnX + backBtnW && mouseY >= backBtnY && mouseY <= backBtnY + backBtnH) {
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                clearCustomCenterPanel();
                return true;
            }
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
                return true;
            } else if (!bottomTabs.isEmpty()) {
                return bottomTabs.get(activeBottomTabIndex).mouseClicked(0, bottomY + 20, screenWidth, this.bottomHeight - 20, mouseX, mouseY, button);
            }
            return false;
        }

        if (this.rightPanel != null && mouseX >= screenWidth - this.rightWidth && mouseY >= topHeight && mouseY <= bottomY) {
            if (this.rightPanel.mouseClicked(screenWidth - this.rightWidth, topHeight, this.rightWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, button)) return true;
        }
        if (this.customCenterPanel != null && mouseX >= 0 && mouseX <= vpWidth && mouseY >= topHeight && mouseY <= topHeight + vpHeight) {
            return this.customCenterPanel.mouseClicked(0, topHeight, vpWidth, vpHeight, mouseX, mouseY, button);
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int screenWidth, int screenHeight, int topHeight) {
        if (draggingSplitter == 2) {
            int minVPWidth = Math.max(150, screenWidth / 2);
            this.rightWidth = Mth.clamp(screenWidth - (int) mouseX, 140, screenWidth - minVPWidth);
            return true;
        } else if (draggingSplitter == 3) {
            int minVPHeight = 100;
            this.bottomHeight = Mth.clamp(screenHeight - (int) mouseY, 60, screenHeight - minVPHeight);
            return true;
        }

        int vpX = 0;
        int vpWidth = screenWidth - this.rightWidth - vpX;
        int vpHeight = screenHeight - topHeight - this.bottomHeight;
        if (this.customCenterPanel != null && mouseX >= vpX && mouseX <= vpX + vpWidth && mouseY >= topHeight && mouseY <= topHeight + vpHeight) {
            return this.customCenterPanel.mouseDragged(vpX, topHeight, vpWidth, vpHeight, mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, int screenWidth, int screenHeight, int topHeight) {
        this.draggingSplitter = 0;
        int vpX = 0;
        int vpWidth = screenWidth - this.rightWidth - vpX;
        int vpHeight = screenHeight - topHeight - this.bottomHeight;
        if (this.customCenterPanel != null && mouseX >= vpX && mouseX <= vpX + vpWidth && mouseY >= topHeight && mouseY <= topHeight + vpHeight) {
            return this.customCenterPanel.mouseReleased(vpX, topHeight, vpWidth, vpHeight, mouseX, mouseY, button);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int screenWidth, int screenHeight, int topHeight) {
        int bottomY = screenHeight - this.bottomHeight;
        int vpWidth = screenWidth - this.rightWidth;
        int vpHeight = screenHeight - topHeight - this.bottomHeight;

        if (mouseY > bottomY) {
            if (!bottomTabs.isEmpty()) bottomTabs.get(activeBottomTabIndex).mouseScrolled(0, bottomY + 20, screenWidth, this.bottomHeight - 20, mouseX, mouseY, scrollX, scrollY);
            return true;
        }
        if (mouseX >= screenWidth - this.rightWidth && mouseY >= topHeight && mouseY <= bottomY) {
            if (this.rightPanel != null) this.rightPanel.mouseScrolled(screenWidth - this.rightWidth, topHeight, this.rightWidth, screenHeight - topHeight - this.bottomHeight, mouseX, mouseY, scrollX, scrollY);
            return true;
        }
        if (this.customCenterPanel != null && mouseX >= 0 && mouseX <= vpWidth && mouseY >= topHeight && mouseY <= topHeight + vpHeight) {
            return this.customCenterPanel.mouseScrolled(0, topHeight, vpWidth, vpHeight, mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers, int screenWidth, int screenHeight, int topHeight) {
        if (!bottomTabs.isEmpty() && activeBottomTabIndex >= 0 && activeBottomTabIndex < bottomTabs.size()) {
            return bottomTabs.get(activeBottomTabIndex).keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers, int screenWidth, int screenHeight, int topHeight) {
        if (!bottomTabs.isEmpty() && activeBottomTabIndex >= 0 && activeBottomTabIndex < bottomTabs.size()) {
            return bottomTabs.get(activeBottomTabIndex).charTyped(codePoint, modifiers);
        }
        return false;
    }

    public interface IRightPanel {
        void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
        int getScrollY();
        default boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    }
    public interface ICenterPanel {
        void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
        default boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseReleased(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseDragged(int x, int y, int width, int height, double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
        default boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    }
    public interface IBottomTab {
        String getTitle();
        void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
        default boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) { return false; }
        default boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
        default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
        default boolean charTyped(char codePoint, int modifiers) { return false; }
        default int getPlayerInventoryX() { return 9999; }
        default int getPlayerInventoryY(int screenHeight, int bottomHeight) { return 9999; }
    }
}