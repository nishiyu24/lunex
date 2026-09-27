package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class AdvancedMachineScreen extends AbstractContainerScreen<AdvancedMachineMenu> {

    @Translatable(en = "No Program", ja = "プログラムなし")
    public static final String KEY_NO_PROGRAM = "gui.lunex.machine.no_program";
    @Translatable(en = "Boot Program: ", ja = "起動プログラム: ")
    public static final String KEY_BOOT_PROGRAM = "gui.lunex.machine.boot_program";
    @Translatable(en = "Energy: %s / %s", ja = "エネルギー: %s / %s")
    public static final String KEY_ENERGY = "tooltip.lunex.energy";
    @Translatable(en = "Standby", ja = "待機中")
    public static final String KEY_STANDBY = "tooltip.lunex.standby";
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/generic_54.png");
    private Button toggleButton;

    public AdvancedMachineScreen(AdvancedMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
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
                String programName = this.menu.getBlockEntity().getProgramName();
                tag.putString("command", "boot");
                tag.putString("arg", programName != null ? programName : "");
            }
            PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "machine_command", tag));
        }).bounds(leftPos + 152, topPos + 14, 16, 18).build();

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

        guiGraphics.blit(TEXTURE, i, j, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        int energy = this.menu.getEnergy();
        int maxEnergy = this.menu.getMaxEnergy();

        int barX = i + 7;
        int barY = j + 38;
        int barWidth = 162;
        int barHeight = 5;

        guiGraphics.fill(barX + 1, barY, barX + barWidth - 1, barY + 1, 0xFF000000);
        guiGraphics.fill(barX + 1, barY + barHeight - 1, barX + barWidth - 1, barY + barHeight, 0xFF000000);
        guiGraphics.fill(barX, barY + 1, barX + 1, barY + barHeight - 1, 0xFF000000);
        guiGraphics.fill(barX + barWidth - 1, barY + 1, barX + barWidth, barY + barHeight - 1, 0xFF000000);

        guiGraphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF550000);

        int progressWidth = maxEnergy > 0 ? (int) ((float) energy / maxEnergy * (barWidth - 2)) : 0;
        if (progressWidth > 0) {
            guiGraphics.fill(barX + 1, barY + 1, barX + 1 + progressWidth, barY + 2, 0xFFFFB955);
            guiGraphics.fill(barX + 1, barY + 2, barX + 1 + progressWidth, barY + 3, 0xFFFF7700);
            guiGraphics.fill(barX + 1, barY + 3, barX + 1 + progressWidth, barY + 4, 0xFFCC3300);
        }

        for (int k = 1; k < 18; k++) {
            int lineX = barX + (k * 9);
            guiGraphics.fill(lineX, barY + 1, lineX + 1, barY + barHeight - 1, 0xAA000000);
        }

        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                guiGraphics.fill(i + slot.x, j + slot.y, i + slot.x + 16, j + slot.y + 16, 0x80000000);
            }
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        String programName = this.menu.getBlockEntity().getProgramName();
        if (programName == null || programName.isEmpty()) {
            programName = Component.translatable(KEY_NO_PROGRAM).getString();
        }

        String text = Component.translatable(KEY_BOOT_PROGRAM).getString() + programName;
        int maxWidth = 115;

        if (this.font.width(text) > maxWidth) {
            text = this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
        }

        guiGraphics.drawString(this.font, text, 8, 22, 4210752, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        int barX = i + 7;
        int barY = j + 38;
        int barWidth = 162;
        int barHeight = 5;
        if (mouseX >= barX && mouseX < barX + barWidth && mouseY >= barY && mouseY < barY + barHeight) {
            int energy = this.menu.getEnergy();
            int maxEnergy = this.menu.getMaxEnergy();
            guiGraphics.renderTooltip(this.font, Component.translatable(KEY_ENERGY, energy, maxEnergy), mouseX, mouseY);
        }
    }
}