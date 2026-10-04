package com.nishiyu.lunex.api.client.ui.extensions;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public class DatabaseUIExtension implements IMainframeUIExtension {

    @Override
    public int getPanelHeight(BlockEntity be) {
        return 140;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, BlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        int currentPriority = be.getPersistentData().getInt("Priority");
        if (currentPriority < 1 || currentPriority > 10) currentPriority = 1;
        int finalPriority = currentPriority;

        Button priorityBtn = Button.builder(Component.literal("Priority: " + finalPriority), btn -> {
            int current = be.getPersistentData().getInt("Priority");
            if (current < 1 || current > 10) current = 1;
            int next = current >= 10 ? 1 : current + 1;
            be.getPersistentData().putInt("Priority", next);
            btn.setMessage(Component.literal("Priority: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_priority", String.valueOf(next)));
        }).bounds(panelX + 5, textY + 70, 130, 20).build();

        addWidget.accept(priorityBtn);
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, BlockEntity be, int panelX, int textY) {
        if (be instanceof DatabaseBlockEntity db) {
            Level level = db.getLevel();
            if (level != null && db.getMasterPos() != null) {
                BlockEntity masterBe = level.getBlockEntity(db.getMasterPos());
                if (masterBe instanceof SimpleMachineBlockEntity master) {
                    long maxItem = master.resourceCapacities.getOrDefault("item", 0L);
                    long usedItem = master.resourceUsages.getOrDefault("item", 0L);
                    guiGraphics.drawString(font, "Items: " + usedItem, panelX + 5, textY, 0x00E5FF);
                    guiGraphics.drawString(font, "Max:   " + maxItem, panelX + 5, textY + 15, 0x00E5FF);
                }
            }
        }
    }
}