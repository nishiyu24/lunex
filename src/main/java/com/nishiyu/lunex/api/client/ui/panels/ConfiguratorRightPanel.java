package com.nishiyu.lunex.api.client.ui.panels;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.api.client.IdeScreenFramework;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;

public class ConfiguratorRightPanel implements IdeScreenFramework.IRightPanel {

    private final BlockPos pos;
    private final BlockEntity be;
    private final IMainframeUIExtension<BlockEntity> extension;
    private int scrollY = 0;

    public ConfiguratorRightPanel(BlockPos pos, BlockEntity be, IMainframeUIExtension<BlockEntity> extension) {
        this.pos = pos;
        this.be = be;
        this.extension = extension;
    }

    @Override
    public int getScrollY() {
        return this.scrollY;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        IdeScreenFramework.drawEditorPanelBackground(graphics, x, y, width, height, "Configurator", false);

        int contentY = y + 21;
        int contentHeight = height - 21;

        graphics.enableScissor(x, contentY, x + width, y + height);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, -this.scrollY, 0);

        if (be == null || pos == null) {
            graphics.drawString(Minecraft.getInstance().font, "No node selected.", x + 10, contentY + 10, 0xFF888888);
        } else {
            BlockState state = be.getBlockState();
            String name = state.getBlock().getName().getString();

            graphics.drawString(Minecraft.getInstance().font, "Target: " + name, x + 10, contentY + 10, 0xFFD4D4D4);
            graphics.drawString(Minecraft.getInstance().font, "Pos: " + pos.toShortString(), x + 10, contentY + 22, 0xFF4EC9B0);

            if (extension != null) {
                extension.renderDetails(graphics, Minecraft.getInstance().font, pos, be, x, contentY + 50);
            }
        }

        pose.popPose();
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = 200;
        this.scrollY = Mth.clamp(this.scrollY - (int)(scrollY * 15), 0, maxScroll);
        return true;
    }
}