package com.nishiyu.lunex.api.client.ui.tabs;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public class NetworkStatusBottomTab implements IdeScreenFramework.IBottomTab {

    public NetworkStatusBottomTab() {}

    @Override
    public String getTitle() {
        return "Network Status";
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        graphics.fill(x, y, x + width, y + height, 0xFF1E1E1E);

        graphics.drawString(Minecraft.getInstance().font, "Connected Nodes: Routing active...", x + 15, y + 15, 0xFFCCCCCC);
        graphics.drawString(Minecraft.getInstance().font, "Select a node in 3D view for details.", x + 15, y + 30, 0xFF888888);
    }
}