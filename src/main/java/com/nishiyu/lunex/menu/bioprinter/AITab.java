package com.nishiyu.lunex.menu.bioprinter;

import com.nishiyu.lunex.entity.CustomBehaviorRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AITab extends AbstractPrinterTab {
    private int scrollIndex = 0;
    private boolean isDraggingScrollbar = false;

    public AITab(BioPrinterScreen screen, BioPrinterMenu menu) {
        super(screen, menu);
    }

    private List<CustomBehaviorRegistry.BehaviorDef> getVisibleBehaviors() {
        List<CustomBehaviorRegistry.BehaviorDef> list = new ArrayList<>();
        CustomBehaviorRegistry.BehaviorDef luaControl = null;
        int unlockedBehaviors = this.menu.data.get(12);

        for (CustomBehaviorRegistry.BehaviorDef def : CustomBehaviorRegistry.BEHAVIORS) {
            if ((unlockedBehaviors & (1 << def.id())) != 0) {
                if (def.id() == 19) luaControl = def;
                else list.add(def);
            }
        }
        if (luaControl != null) list.add(0, luaControl);
        return list;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int leftPos, int topPos) {
        guiGraphics.fill(leftPos + 10, topPos + 30, leftPos + 265, topPos + 213, 0xFF11111B);
        guiGraphics.renderOutline(leftPos + 10, topPos + 30, 255, 183, 0xFF45475A);

        int startX = leftPos + 15;
        int startY = topPos + 75;
        int scrollBarX = leftPos + 252;
        int scrollBarH = visibleRows * rowHeight;

        List<CustomBehaviorRegistry.BehaviorDef> list = getVisibleBehaviors();
        List<Integer> selectedBehaviors = this.menu.blockEntity.getSelectedBehaviors();
        boolean isMechSelected = selectedBehaviors.contains(19);

        // ★ ハードコードを取り除き、Translatableのキーを使用
        guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.AI_BEHAVIORS).getString(), leftPos + 15, topPos + 40, 0xFFF38BA8, false);
        guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.AI_SELECTED_COUNT, selectedBehaviors.size()).getString(), leftPos + 15, topPos + 55, 0xFFA6ADC8, false);

        int maxScroll = Math.max(0, list.size() - visibleRows);
        if (maxScroll > 0) {
            guiGraphics.fill(scrollBarX, startY, scrollBarX + 6, startY + scrollBarH, 0xFF313244);
            int thumbH = Math.max(10, scrollBarH * visibleRows / list.size());
            int thumbY = startY + (int) ((scrollBarH - thumbH) * ((float) this.scrollIndex / maxScroll));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 6, thumbY + thumbH, 0xFFA6ADC8);
        }

        CustomBehaviorRegistry.BehaviorDef hoveredDef = null;

        for (int j = 0; j < list.size(); j++) {
            if (j >= this.scrollIndex && j < this.scrollIndex + visibleRows) {
                CustomBehaviorRegistry.BehaviorDef def = list.get(j);
                boolean isSpecial = (def.id() == 19);
                boolean isOn = selectedBehaviors.contains(def.id());
                int order = isOn ? selectedBehaviors.indexOf(def.id()) + 1 : 0;
                boolean canToggle = isOn || (isSpecial ? true : (!isMechSelected && selectedBehaviors.size() < 3));

                int displayRow = j - this.scrollIndex;
                int rowY = startY + displayRow * rowHeight;

                String displayName = Component.translatable("behavior.lunex." + def.key() + ".name").getString();
                drawAiRow(guiGraphics, mouseX, mouseY, startX, rowY, listWidth, rowHeight, displayName, order, isOn, canToggle, isSpecial);

                if (screen.isHovered(mouseX, mouseY, startX, rowY, listWidth, rowHeight)) {
                    hoveredDef = def;
                }
            }
        }

        if (hoveredDef != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("behavior.lunex." + hoveredDef.key() + ".name").withStyle(net.minecraft.ChatFormatting.GOLD));
            tooltip.add(Component.translatable("behavior.lunex." + hoveredDef.key() + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
            guiGraphics.renderComponentTooltip(screen.getFont(), tooltip, mouseX, mouseY);
        }
    }

    private void drawAiRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int w, int h, String text, int order, boolean isChecked, boolean canToggle, boolean isSpecial) {
        boolean hover = canToggle && screen.isHovered(mouseX, mouseY, x, y, w, h);
        int bgColor = hover ? 0xFF313244 : (isSpecial ? 0xFF181825 : 0xFF1E1E2E);
        guiGraphics.fill(x, y, x + w, y + h, bgColor);

        int outlineColor = isSpecial ? 0xFF89B4FA : 0xFF45475A;
        guiGraphics.renderOutline(x, y, w, h, outlineColor);

        int textColor = canToggle ? (isSpecial ? 0xFF89B4FA : 0xFFCDD6F4) : 0xFF585B70;
        guiGraphics.drawString(screen.getFont(), text, x + 10, y + (h - 8) / 2, textColor, false);

        int boxSize = 10;
        int boxX = x + w - 18;
        int boxY = y + (h - boxSize) / 2;
        guiGraphics.renderOutline(boxX, boxY, boxSize, boxSize, outlineColor);

        if (isChecked) {
            if (isSpecial) {
                guiGraphics.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, 0xFF89B4FA);
            } else {
                guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, 0xFF313244);
                guiGraphics.drawString(screen.getFont(), String.valueOf(order), boxX + 2, boxY + 1, 0xFFA6E3A1, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int leftPos, int topPos) {
        if (button == 0) {
            int scrollBarX = leftPos + 252;
            int scrollBarY = topPos + 75;
            int scrollBarH = visibleRows * rowHeight;
            int startX = leftPos + 15;
            int startY = topPos + 75;

            List<CustomBehaviorRegistry.BehaviorDef> list = getVisibleBehaviors();
            int maxScroll = Math.max(0, list.size() - visibleRows);

            if (maxScroll > 0 && screen.isHovered(mouseX, mouseY, scrollBarX, scrollBarY, 6, scrollBarH)) {
                this.isDraggingScrollbar = true;
                updateScrollIndexFromMouse(mouseY, topPos);
                return true;
            }

            List<Integer> selectedBehaviors = this.menu.blockEntity.getSelectedBehaviors();
            boolean isMechSelected = selectedBehaviors.contains(19);

            for (int j = 0; j < list.size(); j++) {
                if (j >= this.scrollIndex && j < this.scrollIndex + visibleRows) {
                    CustomBehaviorRegistry.BehaviorDef def = list.get(j);
                    boolean isOn = selectedBehaviors.contains(def.id());
                    boolean canToggle = isOn || (def.id() == 19 ? true : (!isMechSelected && selectedBehaviors.size() < 3));

                    int displayRow = j - this.scrollIndex;
                    int bx = startX;
                    int by = startY + displayRow * rowHeight;

                    if (canToggle && screen.isHovered(mouseX, mouseY, bx, by, listWidth, rowHeight)) {
                        screen.handleButtonClick(100 + def.id());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<CustomBehaviorRegistry.BehaviorDef> list = getVisibleBehaviors();
        int maxScroll = Math.max(0, list.size() - visibleRows);
        if (maxScroll > 0) {
            if (scrollY > 0) this.scrollIndex = Math.max(0, this.scrollIndex - 1);
            else if (scrollY < 0) this.scrollIndex = Math.min(maxScroll, this.scrollIndex + 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int leftPos, int topPos) {
        if (this.isDraggingScrollbar) {
            updateScrollIndexFromMouse(mouseY, topPos);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.isDraggingScrollbar = false;
        return false;
    }

    private void updateScrollIndexFromMouse(double mouseY, int topPos) {
        int scrollBarY = topPos + 75;
        int scrollBarH = visibleRows * rowHeight;
        float ratio = (float) (mouseY - scrollBarY) / scrollBarH;
        ratio = Math.clamp(ratio, 0.0f, 1.0f);
        int maxScroll = Math.max(0, getVisibleBehaviors().size() - visibleRows);
        if (maxScroll > 0) this.scrollIndex = Math.round(ratio * maxScroll);
    }
}