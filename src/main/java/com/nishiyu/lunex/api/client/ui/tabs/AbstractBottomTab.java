package com.nishiyu.lunex.api.client.ui.tabs;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBottomTab implements IdeScreenFramework.IBottomTab {
    protected int scrollY = 0;
    protected int maxScroll = 1000;

    protected static class WidgetEntry {
        public final AbstractWidget widget;
        public int relX, relY;
        public WidgetEntry(AbstractWidget widget, int relX, int relY) {
            this.widget = widget;
            this.relX = relX;
            this.relY = relY;
        }
    }

    protected final List<WidgetEntry> contentWidgets = new ArrayList<>();
    protected final List<WidgetEntry> overlayWidgets = new ArrayList<>();

    protected void addContentWidget(AbstractWidget widget, int relX, int relY) {
        this.contentWidgets.add(new WidgetEntry(widget, relX, relY));
    }

    protected void addOverlayWidget(AbstractWidget widget, int relX, int relY) {
        this.overlayWidgets.add(new WidgetEntry(widget, relX, relY));
    }

    @Override
    public final void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        int absX = x + IdeScreenFramework.guiOffsetX;
        int absY = y + IdeScreenFramework.guiOffsetY;

        // 1. 背景の描画 (全体クリッピング)
        graphics.enableScissor(absX, absY, absX + width, absY + height);
        renderBackground(graphics, x, y, width, height);
        graphics.flush();
        graphics.disableScissor();

        int headerHeight = getHeaderHeight();
        int contentY = y + headerHeight;
        int contentHeight = height - headerHeight;
        int scrolledY = contentY - this.scrollY;

        // 2. コンテンツの描画 (ヘッダー下のみクリッピング)
        graphics.enableScissor(absX, absY + headerHeight, absX + width, absY + height);
        renderContent(graphics, x, scrolledY, width, contentHeight, mouseX, mouseY, partialTick);

        for (WidgetEntry entry : contentWidgets) {
            AbstractWidget w = entry.widget;
            w.setX(x + entry.relX);
            w.setY(scrolledY + entry.relY);

            boolean inBounds = (w.getY() + w.getHeight() >= contentY) && (w.getY() <= y + height);
            w.visible = inBounds;

            if (inBounds) w.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.flush();
        graphics.disableScissor();

        // 3. オーバーレイの描画 (全体クリッピング)
        graphics.enableScissor(absX, absY, absX + width, absY + height);
        renderOverlay(graphics, x, y, width, height, mouseX, mouseY, partialTick);

        for (WidgetEntry entry : overlayWidgets) {
            AbstractWidget w = entry.widget;
            w.setX(x + entry.relX);
            w.setY(y + entry.relY);
            w.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.flush();
        graphics.disableScissor();

        // 4. ツールチップ等のクリッピング外描画
        renderTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public final boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) {
        // ★修正: クリック時にすべてのウィジェットのフォーカスを一度リセットする
        for (WidgetEntry entry : overlayWidgets) {
            entry.widget.setFocused(false);
        }
        for (WidgetEntry entry : contentWidgets) {
            entry.widget.setFocused(false);
        }

        for (WidgetEntry entry : overlayWidgets) {
            if (entry.widget.mouseClicked(mouseX, mouseY, button)) {
                entry.widget.setFocused(true); // ★修正: クリックされたウィジェットにフォーカスを当てる
                return true;
            }
        }
        if (overlayMouseClicked(x, y, mouseX, mouseY, button)) return true;

        int contentY = y + getHeaderHeight();
        if (mouseX >= x && mouseX <= x + width && mouseY >= contentY && mouseY <= y + height) {
            for (WidgetEntry entry : contentWidgets) {
                if (entry.widget.visible && entry.widget.mouseClicked(mouseX, mouseY, button)) {
                    entry.widget.setFocused(true); // ★修正: クリックされたウィジェットにフォーカスを当てる
                    return true;
                }
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (WidgetEntry entry : overlayWidgets) if (entry.widget.keyPressed(keyCode, scanCode, modifiers)) return true;
        for (WidgetEntry entry : contentWidgets) if (entry.widget.visible && entry.widget.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (WidgetEntry entry : overlayWidgets) if (entry.widget.charTyped(codePoint, modifiers)) return true;
        for (WidgetEntry entry : contentWidgets) if (entry.widget.visible && entry.widget.charTyped(codePoint, modifiers)) return true;
        return false;
    }

    protected void renderBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF1E1E1E);
    }

    protected int getHeaderHeight() { return 0; }

    protected abstract void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick);

    protected boolean contentMouseClicked(int startX, int startY, double mouseX, double mouseY, int button) { return false; }

    protected void renderOverlay(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {}

    protected boolean overlayMouseClicked(int startX, int startY, double mouseX, double mouseY, int button) { return false; }

    // クリッピング（シザー）に影響されないツールチップの描画用メソッド
    protected void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {}
}