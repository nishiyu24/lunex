package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nishiyu.lunex.Lunex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/printer.png");

    public PrinterScreen(PrinterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        // 背景画像の描画
        guiGraphics.blit(TEXTURE, i, j, 0, 0, this.imageWidth, this.imageHeight);

        // ==========================================
        // 1. エネルギーバーの描画 (画面左端の縦バー)
        // ==========================================
        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        int barX = i + 12;
        int barY = j + 20;
        int barWidth = 8;
        int barHeight = 45;

        // 背景（黒枠と暗い赤）
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF550000);

        // エネルギー残量に応じた塗りつぶし
        if (maxEnergy > 0 && energy > 0) {
            int fillHeight = (int) ((float) energy / maxEnergy * barHeight);
            int startY = barY + barHeight - fillHeight;

            guiGraphics.fill(barX, startY, barX + barWidth, barY + barHeight, 0xFFCC3300);
            guiGraphics.fill(barX, startY, barX + barWidth - 2, barY + barHeight, 0xFFFF7700);
            guiGraphics.fill(barX, startY, barX + barWidth - 4, barY + barHeight, 0xFFFFB955);
        }

        // 目盛り線
        for (int k = 1; k < 5; k++) {
            int lineY = barY + (k * 9);
            guiGraphics.fill(barX, lineY, barX + barWidth, lineY + 1, 0xAA000000);
        }

        // ==========================================
        // 2. 進行度ゲージの描画 (矢印の下の細いバー)
        // ==========================================
        int progress = this.menu.getProgress();
        int maxProgress = this.menu.getMaxProgress();

        int progX = i + 98;
        int progY = j + 48;
        int progWidth = 22;
        int progHeight = 16; // 矢印の高さ

        // 矢印のすぐ下に細いゲージの背景を描画
        guiGraphics.fill(progX, progY + progHeight, progX + progWidth, progY + progHeight + 4, 0xFF000000);
        guiGraphics.fill(progX + 1, progY + progHeight + 1, progX + progWidth - 1, progY + progHeight + 3, 0xFF333333);

        // 進行度に応じた塗りつぶし (緑色)
        if (maxProgress > 0 && progress > 0) {
            int fillWidth = (int) ((float) progress / maxProgress * (progWidth - 2));
            guiGraphics.fill(progX + 1, progY + progHeight + 1, progX + 1 + fillWidth, progY + progHeight + 3, 0xFF00FF00);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        // ツールチップ: エネルギー
        int barX = i + 12;
        int barY = j + 20;
        int barWidth = 8;
        int barHeight = 45;
        if (mouseX >= barX && mouseX < barX + barWidth && mouseY >= barY && mouseY < barY + barHeight) {
            int energy = this.menu.getEnergy();
            int maxEnergy = this.menu.getMaxEnergy();
            guiGraphics.renderTooltip(this.font, Component.translatable("tooltip.lunex.energy", energy, maxEnergy), mouseX, mouseY);
        }

        // ツールチップ: 進行度
        int progX = i + 98;
        int progY = j + 48;
        int progWidth = 22;
        int progHeight = 20; // 矢印と追加したゲージ部分を含む高さ
        if (mouseX >= progX && mouseX < progX + progWidth && mouseY >= progY && mouseY < progY + progHeight) {
            int progress = this.menu.getProgress();
            int maxProgress = this.menu.getMaxProgress();
            if (maxProgress > 0) {
                int percent = (int) ((float) progress / maxProgress * 100);
                guiGraphics.renderTooltip(this.font, Component.literal(percent + "%"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 96 + 2, 4210752, false);
    }
}