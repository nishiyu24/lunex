package com.nishiyu.lunex.api.client.ui.extensions;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

// ★変更: 中継ぎのIAdapterModuleUIを廃止し、直接IMainframeUIExtensionを実装
public class CrafterUIExtension implements IMainframeUIExtension<MainframeAdapterBlockEntity> {

    @Override
    public int getPanelHeight(MainframeAdapterBlockEntity be) {
        return 145;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, MainframeAdapterBlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        boolean isActive = be.getPersistentData().getBoolean("AutoCraftActive");
        Button activeBtn = Button.builder(Component.literal("Auto: " + (isActive ? "ON" : "OFF")), btn -> {
            boolean next = !be.getPersistentData().getBoolean("AutoCraftActive");
            be.getPersistentData().putBoolean("AutoCraftActive", next);
            btn.setMessage(Component.literal("Auto: " + (next ? "ON" : "OFF")));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_autocraft", ""));
        }).bounds(panelX + 5, textY + 15, 130, 20).build();
        addWidget.accept(activeBtn);

        String mode = be.getPersistentData().getString("CraftMode");
        if (mode.isEmpty()) mode = "PULSE";
        Button modeBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
            String current = be.getPersistentData().getString("CraftMode");
            String nextMode = "CONTINUOUS".equals(current) ? "PULSE" : "CONTINUOUS";
            be.getPersistentData().putString("CraftMode", nextMode);
            btn.setMessage(Component.literal("Mode: " + nextMode));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_craftmode", nextMode));
        }).bounds(panelX + 5, textY + 40, 130, 20).build();
        addWidget.accept(modeBtn);
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, MainframeAdapterBlockEntity be, int panelX, int textY) {
        guiGraphics.drawString(font, "Crafter Settings:", panelX + 5, textY, 0x00E5FF);
        boolean isActive = be.getPersistentData().getBoolean("AutoCraftActive");
        guiGraphics.drawString(font, "Status: " + (isActive ? "Running" : "Standby"), panelX + 5, textY + 70, isActive ? 0x55FF55 : 0xAAAAAA);
    }
}