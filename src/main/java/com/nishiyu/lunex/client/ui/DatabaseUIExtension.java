package com.nishiyu.lunex.client.ui;

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

import java.util.Locale;
import java.util.function.Consumer;

public class DatabaseUIExtension implements IMainframeUIExtension<DatabaseBlockEntity> {

    @Override
    public int getPanelHeight(DatabaseBlockEntity be) {
        return 140;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, DatabaseBlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
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
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, DatabaseBlockEntity be, int panelX, int textY) {
        Level level = be.getLevel();
        if (level != null && be.getMasterPos() != null) {
            BlockEntity masterBe = level.getBlockEntity(be.getMasterPos());
            if (masterBe instanceof SimpleMachineBlockEntity master) {
                double maxMB = master.mainframeTotalCapacityBytes / 1048576.0;
                double usedMB = master.mainframeUsedItemBytes / 1048576.0;
                guiGraphics.drawString(font, String.format(Locale.US, "Usage: %.2f MB", usedMB), panelX + 5, textY, 0x00E5FF);
                guiGraphics.drawString(font, String.format(Locale.US, "/ %.2f MB", maxMB), panelX + 5, textY + 15, 0x00E5FF);
            }
        }
    }

    @Override
    public boolean handleAction(String action, String value, DatabaseBlockEntity db, Level level) {
        if ("set_priority".equals(action)) {
            try {
                int priority = Integer.parseInt(value);
                if (priority < 1 || priority > 10) priority = 1;
                db.getPersistentData().putInt("Priority", priority);
                db.setChanged();
                level.sendBlockUpdated(db.getBlockPos(), db.getBlockState(), db.getBlockState(), 3);
                return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}