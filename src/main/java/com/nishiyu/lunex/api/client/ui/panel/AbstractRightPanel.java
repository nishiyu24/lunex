package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractRightPanel implements IdeScreenFramework.IRightPanel {
    protected final BlockPos pos;
    protected final BlockEntity be;

    protected static class WidgetEntry {
        public final AbstractWidget widget;
        public int relX, relY;
        public WidgetEntry(AbstractWidget widget, int relX, int relY) {
            this.widget = widget;
            this.relX = relX;
            this.relY = relY;
        }
    }
    protected final List<WidgetEntry> widgets = new ArrayList<>();

    protected int scrollY = 0;
    protected int maxScroll = 200;

    public AbstractRightPanel(BlockPos pos, BlockEntity be) {
        this.pos = pos;
        this.be = be;
    }

    protected void addWidget(AbstractWidget widget, int relX, int relY) {
        this.widgets.add(new WidgetEntry(widget, relX, relY));
    }

    @Override
    public int getScrollY() { return this.scrollY; }

    @Override
    public final void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        renderBackgroundAndHeader(graphics, x, y, width, height);

        int headerHeight = getHeaderHeight();
        int contentY = y + headerHeight;
        int contentHeight = height - headerHeight;
        int scrolledY = contentY - this.scrollY;

        int absX = x + IdeScreenFramework.guiOffsetX;
        int absContentY = contentY + IdeScreenFramework.guiOffsetY;
        int absBottomY = y + height + IdeScreenFramework.guiOffsetY;

        graphics.enableScissor(absX, absContentY, absX + width, absBottomY);

        renderContent(graphics, x, scrolledY, width, contentHeight, mouseX, mouseY, partialTick);

        for (WidgetEntry entry : widgets) {
            AbstractWidget w = entry.widget;
            w.setX(x + entry.relX);
            w.setY(scrolledY + entry.relY);

            boolean inBounds = (w.getY() + w.getHeight() >= contentY) && (w.getY() <= y + height);
            w.visible = inBounds;

            if (inBounds) {
                w.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.flush(); // ★追加: シザー解除前に確定させる
        graphics.disableScissor();
    }

    @Override
    public final boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) {
        int contentY = y + getHeaderHeight();
        if (mouseX >= x && mouseX <= x + width && mouseY >= contentY && mouseY <= y + height) {
            for (WidgetEntry entry : widgets) {
                if (entry.widget.visible && entry.widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            int scrolledY = contentY - this.scrollY;
            return contentMouseClicked(x, scrolledY, mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY = Mth.clamp(this.scrollY - (int)(scrollY * 15), 0, this.maxScroll);
        return true;
    }

    protected void renderBackgroundAndHeader(GuiGraphics graphics, int x, int y, int width, int height) {
        IdeScreenFramework.drawEditorPanelBackground(graphics, x, y, width, height, getTitle(), false);
    }

    protected int getHeaderHeight() { return 21; }

    protected String getTitle() { return "Configurator"; }

    protected abstract void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick);

    protected boolean contentMouseClicked(int startX, int startY, double mouseX, double mouseY, int button) { return false; }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (WidgetEntry entry : widgets) {
            if (entry.widget.visible && entry.widget.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (WidgetEntry entry : widgets) {
            if (entry.widget.visible && entry.widget.charTyped(codePoint, modifiers)) return true;
        }
        return false;
    }
}