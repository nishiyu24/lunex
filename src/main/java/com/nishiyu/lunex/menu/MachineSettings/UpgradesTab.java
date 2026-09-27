package com.nishiyu.lunex.menu.MachineSettings;

import com.nishiyu.lunex.machine.IMachineContext;
import com.nishiyu.lunex.menu.utiles.IMachineTab;
import net.minecraft.client.gui.GuiGraphics;

public class UpgradesTab implements IMachineTab {
    private final MachineSettingsScreen screen;
    private int leftPos, topPos;

    public UpgradesTab(MachineSettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        IMachineContext ctx = this.screen.getMachineContext();

        int col1 = this.leftPos + 15;
        int col2 = this.leftPos + 175;
        int y1 = this.topPos + 40;
        int y2 = this.topPos + 40;

        // 1. Execution
        int execLv = ctx.getExecUpgradeLevel();
        int execSlots = (execLv >= 3) ? 9 : (execLv == 2) ? 6 : (execLv == 1) ? 3 : 0;
        renderInfoEntry(guiGraphics, mouseX, mouseY, col1, y1, "Execution: " + (execLv > 0 ? "Lv " + execLv : "Base"), "Action Slots: " + execSlots, ctx.isBlock(), execLv > 0);
        y1 += 36;

        // 3. Speed
        int speedLv = ctx.getSpeedUpgradeLevel();
        int reduction = Math.min(speedLv * 30, 95);
        renderInfoEntry(guiGraphics, mouseX, mouseY, col1, y1, "Speed: " + (speedLv > 0 ? "Lv " + speedLv : "Base"), reduction > 0 ? "Action Time " + reduction + "% Reduced" : "Action Time 100% (Normal)", ctx.isBlock(), speedLv > 0);
        y1 += 36;

        // 5. Capacity
        int capLv = ctx.getCapacityUpgradeLevel();
        int maxEnergy = (capLv >= 3) ? 1000000 : (capLv == 2) ? 500000 : (capLv == 1) ? 200000 : 100000;
        renderInfoEntry(guiGraphics, mouseX, mouseY, col1, y1, "Capacity: " + (capLv > 0 ? "Lv " + capLv : "Base"), "Max Energy: " + maxEnergy, ctx.isBlock(), capLv > 0);
        y1 += 36;

        // 7. Distance
        int distLv = ctx.getDistanceUpgradeLevel();
        String distDesc = (distLv >= 4) ? " 5x5 Chunks" :
                (distLv == 3) ? "5x5 Chunks" :
                        (distLv == 2) ? "3x3 Chunks" :
                                (distLv == 1) ? "1x1 Chunk" :
                                        "No Chunk Load";
        renderInfoEntry(guiGraphics, mouseX, mouseY, col1, y1, "Distance: " + (distLv > 0 ? "Lv " + distLv : "Base"), distDesc, ctx.isBlock(), distLv > 0);

        // 2. Storage
        int storageLv = ctx.getStorageUpgradeLevel();
        int storageSlots = (storageLv >= 3) ? 27 : (storageLv == 2) ? 18 : (storageLv == 1) ? 9 : 0;
        renderInfoEntry(guiGraphics, mouseX, mouseY, col2, y2, "Storage: " + (storageLv > 0 ? "Lv " + storageLv : "Base"), "Storage Slots: " + storageSlots, ctx.isBlock(), storageLv > 0);
        y2 += 36;

        // 4. Efficiency
        int effLv = ctx.getEfficiencyUpgradeLevel();
        int effRed = effLv * 20;
        renderInfoEntry(guiGraphics, mouseX, mouseY, col2, y2, "Efficiency: " + (effLv > 0 ? "Lv " + effLv : "Base"), effRed > 0 ? "Energy Cost " + effRed + "% Reduced" : "Energy Cost 100% (Normal)", ctx.isBlock(), effLv > 0);
        y2 += 36;

        // 6. Generator (修正箇所)
        int genLv = ctx.getGeneratorUpgradeLevel();
        int receiveRate = (genLv >= 3) ? 10000 : (genLv == 2) ? 5000 : (genLv == 1) ? 2000 : 1000;
        renderInfoEntry(guiGraphics, mouseX, mouseY, col2, y2, "Generator: " + (genLv > 0 ? "Lv " + genLv : "Base"), "Receive Rate: " + receiveRate + " FE/t", ctx.isBlock(), genLv > 0);
    }

    private void renderInfoEntry(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, String title, String desc, boolean isBlock, boolean hasUpgrade) {
        int titleColor = hasUpgrade ? GuiRenderUtils.COLOR_ITEM_SELECTED : GuiRenderUtils.COLOR_TEXT_PRIMARY;
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), title, x, y, titleColor, 1.0f);
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), desc, x + 5, y + 14, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public void onClose() {
    }
}