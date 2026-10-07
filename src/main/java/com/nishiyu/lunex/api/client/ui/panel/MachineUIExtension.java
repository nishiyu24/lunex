package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class MachineUIExtension extends AbstractRightPanel {

    private final SimpleMachineBlockEntity machineEntity;
    private Button toggleButton;

    public MachineUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.machineEntity = (SimpleMachineBlockEntity) be;
        this.maxScroll = 120; // 描画コンテンツに合わせたスクロール幅

        this.initWidgets();
    }

    @Override
    protected String getTitle() {
        return "Machine Overview";
    }

    private void initWidgets() {
        this.toggleButton = Button.builder(Component.literal("Loading..."), btn -> {
                    boolean currentlyRunning = machineEntity.getCore() != null &&
                            machineEntity.getCore().vm != null &&
                            machineEntity.getCore().vm.isRunning;

                    String action = currentlyRunning ? "stop_vm" : "start_vm";

                    // DatabaseUIExtensionに倣い、汎用Actionパケットを送信
                    PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, action, ""));

                    // クライアント側での即時反映
                    if (machineEntity.getCore() != null && machineEntity.getCore().vm != null) {
                        machineEntity.getCore().vm.isRunning = !currentlyRunning;
                        updateButtonState();
                    }
                })
                .bounds(0, 0, 130, 20)
                .build();

        // 描画位置を DatabaseUIExtension に合わせて調整 (startX/startY を加味して render される)
        this.addWidget(this.toggleButton, 10, 80);
        this.updateButtonState();
    }

    private void updateButtonState() {
        if (machineEntity.getCore() == null || machineEntity.getCore().vm == null) {
            this.toggleButton.active = false;
            this.toggleButton.setMessage(Component.literal("VM Unavailable"));
            return;
        }

        this.toggleButton.active = true;
        if (machineEntity.getCore().vm.isRunning) {
            this.toggleButton.setMessage(Component.literal("Stop VM"));
        } else {
            this.toggleButton.setMessage(Component.literal("Start VM"));
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        // ボタン状態の更新を毎フレーム行う
        this.updateButtonState();

        var font = Minecraft.getInstance().font;

        graphics.drawString(font, "Target: Mainframe Core", startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        String label = machineEntity.getMachineLabel();
        if (label == null || label.isEmpty()) label = "Unnamed Machine";
        graphics.drawString(font, "Label: " + label, startX + 10, startY + 40, 0xFFFFFFFF);

        String programName = machineEntity.getProgramName();
        if (programName == null || programName.isEmpty()) programName = "None";
        graphics.drawString(font, "Program: " + programName, startX + 10, startY + 55, 0xFFFFFFFF);

        boolean isRunning = machineEntity.getCore() != null &&
                machineEntity.getCore().vm != null &&
                machineEntity.getCore().vm.isRunning;
        graphics.drawString(font, "Status: " + (isRunning ? "§aRUNNING" : "§cSTOPPED"), startX + 10, startY + 70, 0xFFFFFF);
    }
}