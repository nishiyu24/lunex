package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SimpleMachineUIExtension extends AbstractRightPanel {

    public SimpleMachineUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.maxScroll = 120;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        Component displayName = MainframeUIRegistry.getDisplayBlockName(be);

        graphics.drawString(font, "Target: " + displayName.getString(), startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        if (!(be instanceof SimpleMachineBlockEntity master)) return;

        int currentY = startY + 50;
        graphics.drawString(font, "System Status:", startX + 5, currentY, 0x00E5FF);

        currentY += 20;
        graphics.drawString(font, "Network ID", startX + 5, currentY, 0xFFAAAAAA);
        String tag = master.persistentData.getString("MainframeNetworkTag");
        graphics.drawString(font, tag.isEmpty() ? "Unnamed Network" : tag, startX + 5, currentY + 12, 0xFFD4D4D4);

        currentY += 23;
        graphics.drawString(font, "Structure", startX + 5, currentY, 0xFFAAAAAA);
        graphics.drawString(font, "Nodes: " + master.mainframeParts.size(), startX + 5, currentY + 12, 0xFFD4D4D4);
        graphics.drawString(font, "CPUs:  " + master.mainframeMachines, startX + 5, currentY + 24, 0xFFD4D4D4);

        currentY += 35;
        long maxItem = master.resourceCapacities.getOrDefault("item", 0L);
        long usedItem = master.resourceUsages.getOrDefault("item", 0L);
        graphics.drawString(font, "Storage", startX + 5, currentY, 0xFFAAAAAA);
        graphics.drawString(font, usedItem + " / " + maxItem, startX + 5, currentY + 12, 0xFFD4D4D4);

        currentY += 23;
        long maxEnergy = master.resourceCapacities.getOrDefault("energy", 0L);
        long usedEnergy = master.resourceUsages.getOrDefault("energy", 0L);
        graphics.drawString(font, "Energy", startX + 5, currentY, 0xFFAAAAAA);
        graphics.drawString(font, usedEnergy + " / " + maxEnergy, startX + 5, currentY + 12, 0xFF4EC9B0);
    }
}