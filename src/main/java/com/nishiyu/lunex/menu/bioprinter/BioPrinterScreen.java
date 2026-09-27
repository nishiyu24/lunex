package com.nishiyu.lunex.menu.bioprinter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BioPrinterScreen extends AbstractContainerScreen<BioPrinterMenu> {

    private final AbstractPrinterTab[] tabs;
    private int currentTab = 0;

    public BioPrinterScreen(BioPrinterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 320;
        this.imageHeight = 220;

        // ★ タブの登録順を Mixing -> AI -> Trait に変更
        this.tabs = new AbstractPrinterTab[]{
                new MixingTab(this, menu),
                new AITab(this, menu),
                new TraitTab(this, menu)
        };
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelX = 10000;
        this.titleLabelX = 10000;
    }

    public void handleButtonClick(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    public net.minecraft.client.gui.Font getFont() {
        return this.font;
    }

    public net.minecraft.util.RandomSource getRandom() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.random : net.minecraft.util.RandomSource.create();
    }

    public boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void drawTabBtn(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int w, int h, String text, int baseColor, boolean active) {
        boolean hover = active && isHovered(mouseX, mouseY, x, y, w, h);
        int bgColor = baseColor;
        if (hover) {
            int hoverColor = (baseColor & 0x00FFFFFF) + 0x222222;
            if (hoverColor > 0xFFFFFF) hoverColor = 0xFFFFFF;
            bgColor = 0xFF000000 | hoverColor;
        }
        int textColor = (baseColor == 0xFF89B4FA || baseColor == 0xFFDD5555 || baseColor == 0xFFE0AF68) ? 0xFF11111B : 0xFFCDD6F4;
        guiGraphics.fill(x, y, x + w, y + h, 0xFF45475A);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        guiGraphics.drawString(this.font, text, x + (w - this.font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tabs[currentTab].mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        tabs[currentTab].mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;
        if (tabs[currentTab].mouseDragged(mouseX, mouseY, button, dragX, dragY, leftPos, topPos)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        if (button == 0) {
            if (isHovered(mouseX, mouseY, leftPos + 10, topPos + 10, 60, 16)) {
                this.currentTab = 0;
                this.menu.activeTab = 0;
                return true;
            }
            if (isHovered(mouseX, mouseY, leftPos + 75, topPos + 10, 60, 16)) {
                this.currentTab = 1;
                this.menu.activeTab = 1;
                return true;
            }
            if (isHovered(mouseX, mouseY, leftPos + 140, topPos + 10, 60, 16)) {
                this.currentTab = 2;
                this.menu.activeTab = 2;
                return true;
            }
        }

        if (tabs[currentTab].mouseClicked(mouseX, mouseY, button, leftPos, topPos)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1E1E2E);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF45475A);

        // ★ タブボタンの表記も順番通りに変更
        drawTabBtn(guiGraphics, mouseX, mouseY, x + 10, y + 10, 60, 16, "Mixing", this.currentTab == 0 ? 0xFF89B4FA : 0xFF313244, true);
        drawTabBtn(guiGraphics, mouseX, mouseY, x + 75, y + 10, 60, 16, "AI", this.currentTab == 1 ? 0xFF89B4FA : 0xFF313244, true);
        drawTabBtn(guiGraphics, mouseX, mouseY, x + 140, y + 10, 60, 16, "Traits", this.currentTab == 2 ? 0xFF89B4FA : 0xFF313244, true);

        int energy = this.menu.data.get(0);
        int currentTotal = this.menu.data.get(1);
        int maxMaterials = this.menu.data.get(2);
        int barY = y + 30;
        int barHeight = 183;
        int progX = x + 275;

        guiGraphics.fill(progX, barY, progX + 15, barY + barHeight, 0xFF45475A);
        guiGraphics.fill(progX + 1, barY + 1, progX + 14, barY + barHeight - 1, 0xFF11111B);
        int maxBarHeight = barHeight - 2;
        if (currentTotal > 0 && maxMaterials > 0) {
            int totalScaled = (int) (((float) currentTotal / maxMaterials) * maxBarHeight);
            int machinePoints = this.menu.data.get(3);
            int animalPoints = this.menu.data.get(4);
            int monsterPoints = this.menu.data.get(5);
            float ratio = (float) totalScaled / currentTotal;
            int monH = Math.round(monsterPoints * ratio);
            int aniH = Math.round(animalPoints * ratio);
            int macH = Math.round(machinePoints * ratio);
            int othH = totalScaled - (monH + aniH + macH);
            int currentY = barY + barHeight - 1;
            if (monH > 0) {
                guiGraphics.fill(progX + 1, currentY - monH, progX + 14, currentY, 0xFFF38BA8);
                currentY -= monH;
            }
            if (aniH > 0) {
                guiGraphics.fill(progX + 1, currentY - aniH, progX + 14, currentY, 0xFFA6E3A1);
                currentY -= aniH;
            }
            if (macH > 0) {
                guiGraphics.fill(progX + 1, currentY - macH, progX + 14, currentY, 0xFF89DCEB);
                currentY -= macH;
            }
            if (othH > 0) guiGraphics.fill(progX + 1, currentY - othH, progX + 14, currentY, 0xFFA6ADC8);
        }

        int feX = x + 295;
        guiGraphics.fill(feX, barY, feX + 15, barY + barHeight, 0xFF45475A);
        guiGraphics.fill(feX + 1, barY + 1, feX + 14, barY + barHeight - 1, 0xFF11111B);
        int scaledFe = (int) (((float) energy / 100000) * maxBarHeight);
        if (scaledFe > 0)
            guiGraphics.fill(feX + 1, barY + maxBarHeight + 1 - scaledFe, feX + 14, barY + barHeight - 1, 0xFFA6E3A1);

        tabs[currentTab].render(guiGraphics, mouseX, mouseY, partialTick, x, y);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int barY = y + 30;
        int barHeight = 183;
        int progX = x + 275;
        int feX = x + 295;

        if (isHovered(mouseX, mouseY, feX, barY, 15, barHeight)) {
            guiGraphics.renderTooltip(this.font, Component.literal("Energy: " + this.menu.data.get(0) + " / 100000 FE"), mouseX, mouseY);
        }

        if (isHovered(mouseX, mouseY, progX, barY, 15, barHeight)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("Materials: " + this.menu.data.get(1) + " / " + this.menu.data.get(2)));
            Map<String, Integer> mats = this.menu.blockEntity.getMaterialCounts();
            if (mats != null && !mats.isEmpty()) {
                tooltip.add(Component.literal(" "));
                for (Map.Entry<String, Integer> entry : mats.entrySet()) {
                    String[] parts = entry.getKey().split(":");
                    tooltip.add(Component.literal("- " + (parts.length > 1 ? parts[1] : parts[0]) + ": " + entry.getValue()).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            } else {
                tooltip.add(Component.literal("Empty").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }
}