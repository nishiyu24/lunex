package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.network.packet.c2s.AssembleMachineC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class UpgradeScreen extends AbstractContainerScreen<UpgradeMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/upgrade_gui.png");

    private Button assembleButton;

    public UpgradeScreen(UpgradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        this.imageWidth = 176;
        this.imageHeight = 151;

        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // ボタンのY座標を微調整 (y + 41: スロットの約3ドット下)
        this.assembleButton = Button.builder(Component.literal("Assemble"), b -> {
            PacketDistributor.sendToServer(new AssembleMachineC2SPacket(this.menu.getPos()));
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.closeContainer();
            }
        }).bounds(x + 63, y + 41, 50, 16).build();

        this.assembleButton.active = false;
        this.addRenderableWidget(this.assembleButton);
    }

    @Override
    public void containerTick() {
        super.containerTick();

        boolean hasUpgrade = false;
        for (int i = 0; i < 7; i++) {
            if (this.menu.getSlot(i).hasItem()) {
                hasUpgrade = true;
                break;
            }
        }

        if (this.assembleButton != null) {
            this.assembleButton.active = hasUpgrade;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }
}