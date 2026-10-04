package com.nishiyu.lunex.api.client.ui.extensions;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public class ProbeUIExtension implements IMainframeUIExtension {

    @Override
    public int getPanelHeight(BlockEntity be) {
        return 170;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, BlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        boolean isDetected = be.getPersistentData().getBoolean("IsDetected");

        Button activeBtn = Button.builder(Component.literal("Active: " + isDetected), btn -> {
            boolean next = !be.getPersistentData().getBoolean("IsDetected");
            be.getPersistentData().putBoolean("IsDetected", next);
            btn.setMessage(Component.literal("Active: " + next));
            // toggle_active は boolean の反転だけなので payload は空のままでもOK（ActionProvider側で反転させる）
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_active", ""));
        }).bounds(panelX + 5, textY + 15, 130, 20).build();
        addWidget.accept(activeBtn);

        String mode = be.getPersistentData().getString("IOMode");
        if (mode.isEmpty()) mode = "IN";
        Button ioBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
            String current = be.getPersistentData().getString("IOMode");
            if (current.isEmpty()) current = "IN"; // 初期値対応
            String next = "OUT".equals(current) ? "IN" : "OUT";
            be.getPersistentData().putString("IOMode", next);
            btn.setMessage(Component.literal("Mode: " + next));
            // payload に「次のモード」を含めて送信するように統一
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_mode", next));
        }).bounds(panelX + 5, textY + 40, 130, 20).build();
        addWidget.accept(ioBtn);

        String target = be.getPersistentData().getString("TargetType");
        if (target.isEmpty()) target = "ALL";
        Button targetBtn = Button.builder(Component.literal("Target: " + target), btn -> {
            String current = be.getPersistentData().getString("TargetType");
            String next = ("ALL".equals(current) || current.isEmpty()) ? "ITEM" : ("ITEM".equals(current) ? "ENERGY" : "ALL");
            be.getPersistentData().putString("TargetType", next);
            btn.setMessage(Component.literal("Target: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_target", next));
        }).bounds(panelX + 5, textY + 65, 130, 20).build();
        addWidget.accept(targetBtn);

        String filter = be.getPersistentData().getString("NBTFilter");
        EditBox nbtBox = new EditBox(screen.getMinecraft().font, panelX + 5, textY + 100, 130, 16, Component.literal("NBT Filter"));

        // setValue を呼ぶと Responder が反応してしまうことがあるため、先に値をセットする
        nbtBox.setValue(filter);
        nbtBox.setMaxLength(256);

        nbtBox.setResponder(val -> {
            // ★修正: 実際のデータと異なる（ユーザーが編集した）場合のみ処理を行う
            if (!val.equals(be.getPersistentData().getString("NBTFilter"))) {
                // UI再描画時に文字が消えないよう、クライアント側のデータも即座に書き換えておく
                be.getPersistentData().putString("NBTFilter", val);
                // サーバーへ変更パケットを送信
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_nbt_filter", val));
            }
        });
        addWidget.accept(nbtBox);
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, BlockEntity be, int panelX, int textY) {
        guiGraphics.drawString(font, "Probe Configuration:", panelX + 5, textY, 0x00E5FF);
        guiGraphics.drawString(font, "Filter (Items Only):", panelX + 5, textY + 90, 0xFFFFFF);
    }
}