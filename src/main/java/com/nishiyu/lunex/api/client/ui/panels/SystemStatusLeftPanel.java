package com.nishiyu.lunex.api.client.ui.panels;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;

public class SystemStatusLeftPanel implements IdeScreenFramework.ILeftPanel {

    private final BlockEntity masterBe;
    private int scrollY = 0;

    public SystemStatusLeftPanel(BlockEntity masterBe) {
        this.masterBe = masterBe;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        IdeScreenFramework.drawEditorPanelBackground(graphics, x, y, width, height, "System Status", true);

        int contentY = y + 21;

        graphics.enableScissor(x, contentY, x + width, y + height);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, -this.scrollY, 0);

        if (masterBe instanceof SimpleMachineBlockEntity master) {
            int textX = x + 10;
            int textY = contentY + 10;

            graphics.drawString(Minecraft.getInstance().font, "Network ID", textX, textY, 0xFFAAAAAA);
            String tag = master.persistentData.getString("MainframeNetworkTag");
            graphics.drawString(Minecraft.getInstance().font, tag.isEmpty() ? "Unnamed Network" : tag, textX, textY + 12, 0xFFD4D4D4);

            graphics.drawString(Minecraft.getInstance().font, "Structure", textX, textY + 35, 0xFFAAAAAA);
            graphics.drawString(Minecraft.getInstance().font, "Nodes: " + master.mainframeParts.size(), textX, textY + 47, 0xFFD4D4D4);
            graphics.drawString(Minecraft.getInstance().font, "CPUs:  " + master.mainframeMachines, textX, textY + 59, 0xFFD4D4D4);

            long maxItem = master.resourceCapacities.getOrDefault("item", 0L);
            long usedItem = master.resourceUsages.getOrDefault("item", 0L);
            graphics.drawString(Minecraft.getInstance().font, "Storage", textX, textY + 82, 0xFFAAAAAA);
            graphics.drawString(Minecraft.getInstance().font, usedItem + " / " + maxItem, textX, textY + 94, 0xFFD4D4D4);

            long maxEnergy = master.resourceCapacities.getOrDefault("energy", 0L);
            long usedEnergy = master.resourceUsages.getOrDefault("energy", 0L);
            graphics.drawString(Minecraft.getInstance().font, "Energy", textX, textY + 117, 0xFFAAAAAA);
            graphics.drawString(Minecraft.getInstance().font, usedEnergy + " / " + maxEnergy, textX, textY + 129, 0xFF4EC9B0);
        }

        pose.popPose();
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY = Mth.clamp(this.scrollY - (int)(scrollY * 15), 0, 100);
        return true;
    }
}