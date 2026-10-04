package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

public class TurtleDashboardTab implements ITurtleTab {
    private final TurtleSettingsScreen screen;
    private int leftPos, topPos;

    public TurtleDashboardTab(TurtleSettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        TurtleBotBlockEntity be = this.screen.getMenu().getBlockEntity();
        if (be == null) return;

        int col1 = this.leftPos + 15;
        int col2 = this.leftPos + 180;
        int y = this.topPos + 40;

        BlockPos pos = be.getBlockPos();
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Machine ID: #" + Math.abs(pos.hashCode() % 10000), col1, y, TurtleGuiUtils.COLOR_TEXT_PRIMARY, 1.0f);
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Pos: X=" + pos.getX() + " Y=" + pos.getY() + " Z=" + pos.getZ(), col1, y + 16, TurtleGuiUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String lanIp = be.getCore().persistentData.contains("IPAddress") ? be.getCore().persistentData.getString("IPAddress") : "Not Set";
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "LAN IP: " + lanIp, col1, y + 32, TurtleGuiUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String status = this.screen.getMenu().isRunning() ? "§aRunning§r" : "§cStopped§r";
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Status: " + status, col1, y + 52, TurtleGuiUtils.COLOR_TEXT_PRIMARY, 1.0f);
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Energy: " + be.getCore().energy, col1, y + 68, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);

        String programName = (be.getCore().programName == null || be.getCore().programName.isEmpty()) ? "None" : be.getCore().programName;
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Boot Program: " + programName, col2, y, TurtleGuiUtils.COLOR_TEXT_PRIMARY, 1.0f);

        int btnY = this.topPos + 180;
        if (this.screen.getMenu().isRunning()) {
            TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1, btnY, 55, 16, "■ Stop", false, 1.0f, TurtleGuiUtils.COLOR_BTN_WARNING, false);
        } else {
            TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1, btnY, 55, 16, "▶ Start", false, 1.0f, TurtleGuiUtils.COLOR_ITEM_SELECTED, false);
        }
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1 + 65, btnY, 55, 16, "Reboot", false, 1.0f, TurtleGuiUtils.COLOR_BTN_WARNING, false);
    }

    @Override
    public void tick() {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int col1 = this.leftPos + 15;
            int btnY = this.topPos + 180;
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, col1, btnY, 55, 16)) {
                if (this.screen.getMenu().isRunning()) {
                    this.screen.sendCommand("stop", "");
                } else {
                    String pName = this.screen.getMenu().getBlockEntity() != null ? this.screen.getMenu().getBlockEntity().getCore().programName : "";
                    this.screen.sendCommand("boot", pName != null ? pName : "");
                }
                return true;
            }
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, col1 + 65, btnY, 55, 16)) {
                this.screen.sendCommand("reboot", "");
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    @Override
    public void onClose() {}
}