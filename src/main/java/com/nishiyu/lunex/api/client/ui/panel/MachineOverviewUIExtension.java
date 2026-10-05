package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MachineOverviewUIExtension extends AbstractRightPanel {

    public MachineOverviewUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.maxScroll = 120;
    }

    @Override
    protected String getTitle() {
        return "System Overview";
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        if (!(be instanceof SimpleMachineBlockEntity master)) return;

        // ★修正: getCore() 経由で NBT データを取得
        String tag = master.getCore().persistentData.getString("MainframeNetworkTag");
        String networkName = tag.isEmpty() ? "Unnamed Network" : tag;

        graphics.drawString(font, "Network: " + networkName, startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Core Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        int currentY = startY + 50;
        graphics.drawString(font, "System Status:", startX + 5, currentY, 0x00E5FF);

        currentY += 23;
        graphics.drawString(font, "Structure", startX + 5, currentY, 0xFFAAAAAA);
        // パーツ構成データはそのまま
        graphics.drawString(font, "Nodes: " + master.mainframeParts.size(), startX + 5, currentY + 12, 0xFFD4D4D4);
        graphics.drawString(font, "CPUs:  " + master.mainframeMachines, startX + 5, currentY + 24, 0xFFD4D4D4);

        currentY += 35;
        // ★修正: getCore() 経由でリソース情報を取得
        long maxItem = master.getCore().resourceCapacities.getOrDefault("item", 0L);
        long usedItem = master.getCore().resourceUsages.getOrDefault("item", 0L);
        graphics.drawString(font, "Storage", startX + 5, currentY, 0xFFAAAAAA);
        graphics.drawString(font, usedItem + " / " + maxItem, startX + 5, currentY + 12, 0xFFD4D4D4);

        currentY += 23;
        // ★修正: getCore() 経由でエネルギー情報を取得
        long maxEnergy = master.getCore().resourceCapacities.getOrDefault("energy", 0L);
        long usedEnergy = master.getCore().resourceUsages.getOrDefault("energy", 0L);
        graphics.drawString(font, "Energy", startX + 5, currentY, 0xFFAAAAAA);
        graphics.drawString(font, usedEnergy + " / " + maxEnergy, startX + 5, currentY + 12, 0xFF4EC9B0);
    }
}