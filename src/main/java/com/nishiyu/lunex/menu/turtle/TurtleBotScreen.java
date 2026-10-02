package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class TurtleBotScreen extends AbstractContainerScreen<TurtleBotMenu> {

    // ※適切なテクスチャサイズのものに変更してください。今回は汎用的なサイズを仮指定。
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/generic_54.png");
    private Button toggleButton;

    public TurtleBotScreen(TurtleBotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 184;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 5;

        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        this.toggleButton = Button.builder(Component.literal("▶"), btn -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", this.menu.getPos().asLong());
            if (this.menu.isRunning()) {
                tag.putString("command", "stop");
                tag.putString("arg", "");
            } else {
                String programName = this.menu.getBlockEntity().programName;
                tag.putString("command", "boot");
                tag.putString("arg", programName != null ? programName : "");
            }
            PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "machine_command", tag));
        }).bounds(leftPos + 130, topPos + 14, 16, 18).build();

        this.addRenderableWidget(this.toggleButton);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.menu.isRunning()) {
            this.toggleButton.setMessage(Component.literal("■"));
        } else {
            this.toggleButton.setMessage(Component.literal("▶"));
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        // 背景
        guiGraphics.blit(TEXTURE, i, j, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        // 4x4 のスロット枠を自力で描画 (TEXTUREが対応していない場合)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                guiGraphics.fill(i + 51 + (col * 18), j + 16 + (row * 18), i + 51 + (col * 18) + 18, j + 16 + (row * 18) + 18, 0xFF8b8b8b);
                guiGraphics.fill(i + 52 + (col * 18), j + 17 + (row * 18), i + 51 + (col * 18) + 17, j + 16 + (row * 18) + 17, 0xFF373737);
                guiGraphics.fill(i + 52 + (col * 18), j + 17 + (row * 18), i + 52 + (col * 18) + 16, j + 17 + (row * 18) + 16, 0xFF8b8b8b);
            }
        }

        // エネルギーバー
        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        int barX = i + 8;
        int barY = j + 80;
        int barWidth = 160;
        int barHeight = 6;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF550000);

        int progressWidth = maxEnergy > 0 ? (int) ((float) energy / maxEnergy * (barWidth - 2)) : 0;
        if (progressWidth > 0) {
            guiGraphics.fill(barX + 1, barY + 1, barX + 1 + progressWidth, barY + barHeight - 1, 0xFFFF7700);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        String programName = this.menu.getBlockEntity().programName;
        if (programName == null || programName.isEmpty()) programName = "No Program";
        guiGraphics.drawString(this.font, "Boot: " + programName, 8, 22, 4210752, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        int barX = i + 8;
        int barY = j + 80;
        int barWidth = 160;
        int barHeight = 6;

        if (mouseX >= barX && mouseX < barX + barWidth && mouseY >= barY && mouseY < barY + barHeight) {
            guiGraphics.renderTooltip(this.font, Component.literal("Energy: " + this.menu.getEnergy() + " / " + this.menu.getMaxEnergy()), mouseX, mouseY);
        }
    }
}