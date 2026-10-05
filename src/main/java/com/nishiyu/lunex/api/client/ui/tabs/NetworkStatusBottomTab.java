package com.nishiyu.lunex.api.client.ui.tabs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public class NetworkStatusBottomTab extends AbstractBottomTab {

    public NetworkStatusBottomTab() {}

    @Override
    public String getTitle() {
        return "Network Status";
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(Minecraft.getInstance().font, "Connected Nodes: Routing active...", startX + 15, startY + 15, 0xFFCCCCCC);
        graphics.drawString(Minecraft.getInstance().font, "Select a node in 3D view for details.", startX + 15, startY + 30, 0xFF888888);
    }
}