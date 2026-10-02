package com.nishiyu.lunex.menu.turtle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class TurtleGuiUtils {
    public static final int COLOR_BG_MAIN = 0xFF1E1E2E;
    public static final int COLOR_BG_SIDEBAR = 0xFF11111B;
    public static final int COLOR_BG_HEADER = 0xFF181825;
    public static final int COLOR_BORDER = 0xFF45475A;
    public static final int COLOR_BTN_BG = 0xFF313244;
    public static final int COLOR_BTN_HOVER = 0xFF585B70;
    public static final int COLOR_BTN_DANGER = 0xFF772222;
    public static final int COLOR_BTN_WARNING = 0xFF885522;
    public static final int COLOR_ITEM_SELECTED = 0xFF89B4FA;
    public static final int COLOR_ITEM_HOVER = 0xFF313244;
    public static final int COLOR_TEXT_PRIMARY = 0xFFCDD6F4;
    public static final int COLOR_TEXT_MUTED = 0xFFA6ADC8;
    public static final int COLOR_TEXT_DARK = 0xFF11111B;

    public static void drawScaledString(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    public static void drawCustomButton(GuiGraphics guiGraphics, Font font, double mouseX, double mouseY, int x, int y, int width, int height, String text, boolean disabled, float textScale, int baseColor, boolean isModalOrContextOpen) {
        boolean isHovered = !disabled && !isModalOrContextOpen && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int hoverColor = (baseColor & 0x00FFFFFF) + 0x222222;
        if (hoverColor > 0xFFFFFF) hoverColor = 0xFFFFFF;
        hoverColor = 0xFF000000 | hoverColor;

        int bgColor = disabled ? 0xFF222233 : (isHovered ? hoverColor : baseColor);
        int textColor = disabled ? 0xFF555566 : ((baseColor == COLOR_ITEM_SELECTED || baseColor == COLOR_BTN_DANGER || baseColor == COLOR_BTN_WARNING) ? COLOR_TEXT_DARK : COLOR_TEXT_PRIMARY);

        guiGraphics.fill(x, y, x + width, y + height, COLOR_BORDER);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, bgColor);

        int textWidth = (int) (font.width(text) * textScale);
        drawScaledString(guiGraphics, font, text, x + (width - textWidth) / 2, y + (height - (int) (8 * textScale)) / 2, textColor, textScale);
    }

    public static void drawToggleButton(GuiGraphics guiGraphics, Font font, double mouseX, double mouseY, int x, int y, int w, int h, String text, boolean isActive, boolean isModalOrContextOpen) {
        boolean isHovered = !isModalOrContextOpen && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bgColor = isActive ? COLOR_ITEM_SELECTED : (isHovered ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        int textColor = isActive ? COLOR_TEXT_DARK : COLOR_TEXT_PRIMARY;

        guiGraphics.fill(x, y, x + w, y + h, COLOR_BORDER);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        int textWidth = (int) (font.width(text) * 0.8f);
        drawScaledString(guiGraphics, font, text, x + (w - textWidth) / 2, y + (h - 6) / 2, textColor, 0.8f);
    }

    public static boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}