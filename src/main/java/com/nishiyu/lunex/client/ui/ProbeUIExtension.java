package com.nishiyu.lunex.client.ui;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public class ProbeUIExtension implements IMainframeUIExtension<ProbeBlockEntity> {

    @Override
    public int getPanelHeight(ProbeBlockEntity be) {
        return 145;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, ProbeBlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        Button activeBtn = Button.builder(Component.literal("Active: " + be.isDetected), btn -> {
            be.isDetected = !be.isDetected;
            btn.setMessage(Component.literal("Active: " + be.isDetected));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_active", ""));
        }).bounds(panelX + 5, textY + 15, 130, 20).build();
        addWidget.accept(activeBtn);

        String mode = be.getPersistentData().getString("IOMode");
        if (mode.isEmpty()) mode = "IN";
        Button ioBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
            String current = be.getPersistentData().getString("IOMode");
            String next = "OUT".equals(current) ? "IN" : "OUT";
            if (current.isEmpty()) next = "OUT";
            be.getPersistentData().putString("IOMode", next);
            btn.setMessage(Component.literal("Mode: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_mode", ""));
        }).bounds(panelX + 5, textY + 40, 130, 20).build();
        addWidget.accept(ioBtn);

        String filter = be.getPersistentData().getString("NBTFilter");
        EditBox nbtBox = new EditBox(screen.getMinecraft().font, panelX + 5, textY + 75, 130, 16, Component.literal("NBT Filter"));
        nbtBox.setValue(filter);
        nbtBox.setMaxLength(256);
        nbtBox.setResponder(val -> {
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_nbt_filter", val));
        });
        addWidget.accept(nbtBox);
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, ProbeBlockEntity be, int panelX, int textY) {
        guiGraphics.drawString(font, "Probe Configuration:", panelX + 5, textY, 0x00E5FF);
        guiGraphics.drawString(font, "Filter:", panelX + 5, textY + 65, 0xFFFFFF);
    }

    @Override
    public boolean handleAction(String action, String value, ProbeBlockEntity probe, Level level) {
        // C2SPacket から委譲されてサーバー側で実行される
        switch (action) {
            case "toggle_active":
                probe.isDetected = !probe.isDetected;
                probe.setChanged();

                BlockState state = probe.getBlockState();
                if (state.hasProperty(com.nishiyu.lunex.block.ProbeBlock.ACTIVE)) {
                    state = state.setValue(com.nishiyu.lunex.block.ProbeBlock.ACTIVE, probe.isDetected);
                    level.setBlock(probe.getBlockPos(), state, 3);
                }
                level.sendBlockUpdated(probe.getBlockPos(), probe.getBlockState(), probe.getBlockState(), 3);
                return true;

            case "toggle_mode":
                String currentMode = probe.getPersistentData().getString("IOMode");
                probe.getPersistentData().putString("IOMode", "OUT".equals(currentMode) ? "IN" : "OUT");
                probe.setChanged();
                level.sendBlockUpdated(probe.getBlockPos(), probe.getBlockState(), probe.getBlockState(), 3);
                return true;

            case "set_nbt_filter":
                probe.getPersistentData().putString("NBTFilter", value);
                probe.setChanged();
                level.sendBlockUpdated(probe.getBlockPos(), probe.getBlockState(), probe.getBlockState(), 3);
                return true;
        }
        return false;
    }
}