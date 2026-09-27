package com.nishiyu.lunex.menu.MachineSettings;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.machine.IMachineContext;
import com.nishiyu.lunex.menu.utiles.IMachineTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;

public class DashboardTab implements IMachineTab {
    private final MachineSettingsScreen screen;
    private int leftPos, topPos;

    public DashboardTab(MachineSettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    private int getRs(AdvancedMachineBlockEntity be, Direction dir) {
        return be.redstoneOutputs.getOrDefault(dir, 0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        IMachineContext ctx = this.screen.getMachineContext();

        int col1 = this.leftPos + 15;
        int col2 = this.leftPos + 180;
        int y = this.topPos + 40;

        // --- Column 1 ---
        if (ctx.isBlock() && ctx.getPos() != null) {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Machine ID: #" + Math.abs(ctx.getPos().hashCode() % 10000), col1, y, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Pos: X=" + ctx.getPos().getX() + " Y=" + ctx.getPos().getY() + " Z=" + ctx.getPos().getZ(), col1, y + 16, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
        } else {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Device Type: Portable Item", col1, y, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Mode: Mobile Terminal", col1, y + 16, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
        }

        String lanIp = "未設定";
        if (ctx instanceof AdvancedMachineBlockEntity pme) {
            if (pme.persistentData.contains("IPAddress")) lanIp = pme.persistentData.getString("IPAddress");
        }
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "LAN IP: " + lanIp, col1, y + 32, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String status = ctx.isBlock() ? (this.screen.getMenu().isRunning() ? "§aRunning§r" : "§cStopped§r") : "§aReady (Tool)§r";
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Status: " + status, col1, y + 52, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Energy: " + ctx.getEnergy(), col1, y + 68, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

        // --- Column 2 ---
        String programName = (ctx.getProgramName() == null || ctx.getProgramName().isEmpty()) ? "None" : ctx.getProgramName();
        // ★統一: Boot Program の表記に統一
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Boot Program: " + programName, col2, y, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        if (ctx.isBlock() && ctx instanceof AdvancedMachineBlockEntity be) {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Redstone I/O: Active", col2, y + 20, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
            String rs1 = String.format("F:%d  B:%d  L:%d", getRs(be, Direction.NORTH), getRs(be, Direction.SOUTH), getRs(be, Direction.WEST));
            String rs2 = String.format("R:%d  U:%d  D:%d", getRs(be, Direction.EAST), getRs(be, Direction.UP), getRs(be, Direction.DOWN));
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), rs1, col2 + 5, y + 36, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), rs2, col2 + 5, y + 52, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        } else {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Redstone I/O: N/A", col2, y + 20, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        }

        // Buttons
        int btnY = this.topPos + 180;
        if (this.screen.getMenu().isRunning()) {
            GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1, btnY, 55, 16, "■ Stop", false, 1.0f, GuiRenderUtils.COLOR_BTN_WARNING, false);
        } else {
            GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1, btnY, 55, 16, "▶ Start", false, 1.0f, GuiRenderUtils.COLOR_ITEM_SELECTED, false);
        }
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1 + 65, btnY, 55, 16, "Reboot", false, 1.0f, GuiRenderUtils.COLOR_BTN_WARNING, false);
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int col1 = this.leftPos + 15;
            int btnY = this.topPos + 180;

            if (GuiRenderUtils.isHovered(mouseX, mouseY, col1, btnY, 55, 16)) {
                if (this.screen.getMenu().isRunning()) {
                    this.screen.sendCommand("stop", "");
                } else {
                    String pName = this.screen.getMachineContext().getProgramName();
                    this.screen.sendCommand("boot", pName != null ? pName : "");
                }
                return true;
            }
            if (GuiRenderUtils.isHovered(mouseX, mouseY, col1 + 65, btnY, 55, 16)) {
                this.screen.sendCommand("reboot", "");
                return true;
            }
        }
        return false;
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