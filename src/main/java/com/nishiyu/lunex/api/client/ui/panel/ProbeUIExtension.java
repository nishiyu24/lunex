package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class ProbeUIExtension extends AbstractRightPanel {

    public ProbeUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.maxScroll = 170;

        boolean isDetected = be.getPersistentData().getBoolean("IsDetected");
        Button activeBtn = Button.builder(Component.literal("Active: " + isDetected), btn -> {
            boolean next = !be.getPersistentData().getBoolean("IsDetected");
            be.getPersistentData().putBoolean("IsDetected", next);
            btn.setMessage(Component.literal("Active: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_active", ""));
        }).bounds(0, 0, 130, 20).build();
        addWidget(activeBtn, 5, 65);

        String mode = be.getPersistentData().getString("IOMode");
        if (mode.isEmpty()) mode = "IN";
        Button ioBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
            String current = be.getPersistentData().getString("IOMode");
            if (current.isEmpty()) current = "IN";
            String next = "OUT".equals(current) ? "IN" : "OUT";
            be.getPersistentData().putString("IOMode", next);
            btn.setMessage(Component.literal("Mode: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_mode", next));
        }).bounds(0, 0, 130, 20).build();
        addWidget(ioBtn, 5, 90);

        String target = be.getPersistentData().getString("TargetType");
        if (target.isEmpty()) target = "ALL";
        Button targetBtn = Button.builder(Component.literal("Target: " + target), btn -> {
            String current = be.getPersistentData().getString("TargetType");
            String next = ("ALL".equals(current) || current.isEmpty()) ? "ITEM" : ("ITEM".equals(current) ? "ENERGY" : "ALL");
            be.getPersistentData().putString("TargetType", next);
            btn.setMessage(Component.literal("Target: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_target", next));
        }).bounds(0, 0, 130, 20).build();
        addWidget(targetBtn, 5, 115);

        String filter = be.getPersistentData().getString("NBTFilter");
        EditBox nbtBox = new EditBox(Minecraft.getInstance().font, 0, 0, 130, 16, Component.literal("NBT Filter"));
        nbtBox.setValue(filter);
        nbtBox.setMaxLength(256);
        nbtBox.setResponder(val -> {
            if (!val.equals(be.getPersistentData().getString("NBTFilter"))) {
                be.getPersistentData().putString("NBTFilter", val);
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_nbt_filter", val));
            }
        });
        addWidget(nbtBox, 5, 150);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        Component displayName = MainframeUIRegistry.getDisplayBlockName(be);

        graphics.drawString(font, "Target: " + displayName.getString(), startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        graphics.drawString(font, "Probe Configuration:", startX + 5, startY + 50, 0x00E5FF);
        graphics.drawString(font, "Filter (Items Only):", startX + 5, startY + 140, 0xFFFFFF);
    }
}