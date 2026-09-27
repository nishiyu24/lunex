package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class DatabaseScreen extends AbstractContainerScreen<DatabaseMenu> {

    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/blank_gui.png");

    public DatabaseScreen(DatabaseMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // ★ EditBox(タグ入力欄)の初期化を削除
    }

    @Override
    public void onClose() {
        // ★ パケット送信処理を削除
        super.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

        int itemBytes = this.menu.getItemBytes();
        int programBytes = this.menu.getProgramBytes();
        int fluidBytes = this.menu.getFluidBytes(); // ★追加: 液体容量
        int maxBytes = this.menu.getMaxBytes();
        int totalBytes = itemBytes + programBytes + fluidBytes;

        // --- 積み上げ式容量バーの描画 ---
        int barX = relX + 18;
        int barY = relY + 60; // 少し上に移動
        int barWidth = 140;
        int barHeight = 10;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000); // 背景(黒)

        int itemBarWidth = (int) ((double) itemBytes / maxBytes * barWidth);
        if (itemBytes > 0 && itemBarWidth == 0) itemBarWidth = 1;
        if (itemBarWidth > 0) {
            guiGraphics.fill(barX, barY, barX + itemBarWidth, barY + barHeight, 0xFF0055FF); // Items(青)
        }

        int fluidBarWidth = (int) ((double) fluidBytes / maxBytes * barWidth);
        if (fluidBytes > 0 && fluidBarWidth == 0) fluidBarWidth = 1;
        if (fluidBarWidth > 0) {
            guiGraphics.fill(barX + itemBarWidth, barY, barX + itemBarWidth + fluidBarWidth, barY + barHeight, 0xFFFFAA00); // Fluids(オレンジ)
        }

        int programBarWidth = (int) ((double) programBytes / maxBytes * barWidth);
        if (programBytes > 0 && programBarWidth == 0) programBarWidth = 1;
        if (programBarWidth > 0) {
            guiGraphics.fill(barX + itemBarWidth + fluidBarWidth, barY, barX + itemBarWidth + fluidBarWidth + programBarWidth, barY + barHeight, 0xFF55FF55); // Programs(緑)
        }

        // ★ Network Tag の描画を削除

        // 容量のテキスト描画
        double usedMB = totalBytes / 1048576.0;
        if (totalBytes > 0 && usedMB < 0.01) {
            usedMB = 0.01;
        }
        double maxMB = maxBytes / 1048576.0;
        String usageText = String.format("Storage: %.2f MB / %.2f MB", usedMB, maxMB);

        guiGraphics.drawString(this.font, usageText, relX + 18, barY - 12, 0x404040, false);

        // 凡例(Legend)の描画
        guiGraphics.fill(relX + 18, barY + 16, relX + 26, barY + 24, 0xFF0055FF);
        guiGraphics.drawString(this.font, "Items", relX + 30, barY + 16, 0x404040, false);

        guiGraphics.fill(relX + 65, barY + 16, relX + 73, barY + 24, 0xFFFFAA00);
        guiGraphics.drawString(this.font, "Fluids", relX + 77, barY + 16, 0x404040, false);

        guiGraphics.fill(relX + 115, barY + 16, relX + 123, barY + 24, 0xFF55FF55);
        guiGraphics.drawString(this.font, "Programs", relX + 127, barY + 16, 0x404040, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        int itemBytes = this.menu.getItemBytes();
        int fluidBytes = this.menu.getFluidBytes();
        int programBytes = this.menu.getProgramBytes();
        int maxBytes = this.menu.getMaxBytes();

        int barX = relX + 18;
        int barY = relY + 60;
        int barWidth = 140;
        int barHeight = 10;

        int itemBarWidth = (int) ((double) itemBytes / maxBytes * barWidth);
        if (itemBytes > 0 && itemBarWidth == 0) itemBarWidth = 1;

        int fluidBarWidth = (int) ((double) fluidBytes / maxBytes * barWidth);
        if (fluidBytes > 0 && fluidBarWidth == 0) fluidBarWidth = 1;

        int programBarWidth = (int) ((double) programBytes / maxBytes * barWidth);
        if (programBytes > 0 && programBarWidth == 0) programBarWidth = 1;

        // マウスホバー時のツールチップ描画
        if (mouseY >= barY && mouseY <= barY + barHeight) {
            // アイテムバー(青色)にホバー
            if (mouseX >= barX && mouseX < barX + itemBarWidth && itemBytes > 0) {
                guiGraphics.renderTooltip(this.font, Component.literal("Items: " + this.menu.getTotalItemCount()), mouseX, mouseY);
            }
            // 液体バー(オレンジ色)にホバー
            else if (mouseX >= barX + itemBarWidth && mouseX < barX + itemBarWidth + fluidBarWidth && fluidBytes > 0) {
                guiGraphics.renderTooltip(this.font, Component.literal("Fluids Data"), mouseX, mouseY); // ※後で流体総数を表示可能
            }
            // プログラムバー(緑色)にホバー
            else if (mouseX >= barX + itemBarWidth + fluidBarWidth && mouseX < barX + itemBarWidth + fluidBarWidth + programBarWidth && programBytes > 0) {
                guiGraphics.renderTooltip(this.font, Component.literal("Programs: " + this.menu.getProgramCount()), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleWidth = this.font.width(this.title);
        guiGraphics.drawString(this.font, this.title, (this.imageWidth - titleWidth) / 2, 8, 4210752, false);
    }

}