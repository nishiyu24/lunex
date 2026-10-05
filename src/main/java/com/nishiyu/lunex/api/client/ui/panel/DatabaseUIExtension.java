package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class DatabaseUIExtension extends AbstractRightPanel {

    public DatabaseUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.maxScroll = 80;

        int currentPriority = be.getPersistentData().getInt("Priority");
        if (currentPriority < 1 || currentPriority > 10) currentPriority = 1;

        Button priorityBtn = Button.builder(Component.literal("Priority: " + currentPriority), btn -> {
            int current = be.getPersistentData().getInt("Priority");
            int next = (current < 1 || current >= 10) ? 1 : current + 1;
            be.getPersistentData().putInt("Priority", next);
            btn.setMessage(Component.literal("Priority: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_priority", String.valueOf(next)));
        }).bounds(0, 0, 130, 20).build();

        addWidget(priorityBtn, 5, 120);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        Component displayName = MainframeUIRegistry.getDisplayBlockName(be);

        graphics.drawString(font, "Target: " + displayName.getString(), startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        if (be instanceof DatabaseBlockEntity db && db.getLevel() != null && db.getMasterPos() != null) {
            Level level = db.getLevel();
            BlockEntity masterBe = level.getBlockEntity(db.getMasterPos());
            if (masterBe instanceof SimpleMachineBlockEntity master) {
                // ★修正: getCore() 経由でリソース情報にアクセス
                long maxItem = master.getCore().resourceCapacities.getOrDefault("item", 0L);
                long usedItem = master.getCore().resourceUsages.getOrDefault("item", 0L);
                graphics.drawString(font, "Items: " + usedItem, startX + 5, startY + 50, 0x00E5FF);
                graphics.drawString(font, "Max:   " + maxItem, startX + 5, startY + 65, 0x00E5FF);
            }
        }
    }
}