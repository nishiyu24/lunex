package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class TurtleSettingsTab implements ITurtleTab {
    private final TurtleSettingsScreen screen;
    private int leftPos, topPos;
    private EditBox labelInput;
    private String labelSetButtonText = "Set";
    private int labelSetTick = 0;
    private String currentLastError = "";

    public TurtleSettingsTab(TurtleSettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
        TurtleBotBlockEntity be = this.screen.getMenu().getBlockEntity();

        if (this.labelInput == null) {
            this.labelInput = new EditBox(this.screen.getFont(), leftPos + 18, topPos + 52, 100, 10, Component.literal("Machine Label"));
            this.labelInput.setMaxLength(30);
            this.labelInput.setBordered(false);
            this.labelInput.setTextColor(TurtleGuiUtils.COLOR_TEXT_PRIMARY);
        } else {
            this.labelInput.setX(leftPos + 18);
            this.labelInput.setY(topPos + 52);
        }
        this.labelInput.setValue(be.getMachineLabel() != null ? be.getMachineLabel() : "");
        this.screen.addWidgetToScreen(this.labelInput);
        this.labelInput.setVisible(true);
    }

    @Override
    public void tick() {
        if (this.labelSetTick > 0) {
            this.labelSetTick--;
            if (this.labelSetTick == 0) this.labelSetButtonText = "Set";
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        TurtleBotBlockEntity be = this.screen.getMenu().getBlockEntity();
        int x = this.leftPos + 15;

        // Machine Label
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Machine Label:", x, this.topPos + 35, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        guiGraphics.fill(x - 1, this.topPos + 49, x + 105, this.topPos + 65, TurtleGuiUtils.COLOR_BORDER);
        guiGraphics.fill(x, this.topPos + 50, x + 104, this.topPos + 64, TurtleGuiUtils.COLOR_BG_MAIN);

        if (this.labelInput != null) {
            if (this.labelInput.getValue().isEmpty() && !this.labelInput.isFocused()) {
                TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Label name...", x + 3, this.topPos + 52, 0xFF666666, 1.0f);
            }
            this.labelInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int btnColor1 = this.labelSetButtonText.equals("OK") ? TurtleGuiUtils.COLOR_ITEM_SELECTED : TurtleGuiUtils.COLOR_BTN_BG;
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 110, this.topPos + 49, 35, 16, this.labelSetButtonText, false, 1.0f, btnColor1, false);
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 150, this.topPos + 49, 45, 16, "Clear", false, 1.0f, TurtleGuiUtils.COLOR_BTN_BG, false);

        // Last Error Log
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Last Error Log:", x, this.topPos + 75, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        String lastError = be.persistentData.contains("LastError") ? be.persistentData.getString("LastError") : "No recent errors.";
        this.currentLastError = lastError;

        guiGraphics.fill(x - 1, this.topPos + 86, x + 295, this.topPos + 115, TurtleGuiUtils.COLOR_BORDER);
        guiGraphics.fill(x, this.topPos + 87, x + 294, this.topPos + 114, TurtleGuiUtils.COLOR_BG_SIDEBAR);

        String[] errorLines = lastError.split("\n");
        int errY = this.topPos + 89;
        for (int i = 0; i < errorLines.length; i++) {
            if (i > 2) break;
            TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), errorLines[i], x + 4, errY, 0xFFFF8888, 0.8f);
            errY += 8;
        }

        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 215, this.topPos + 72, 35, 12, "Clear", false, 0.8f, TurtleGuiUtils.COLOR_BTN_BG, false);
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 255, this.topPos + 72, 35, 12, "Copy", false, 0.8f, TurtleGuiUtils.COLOR_ITEM_SELECTED, false);

        // System Toggles
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "System Toggles:", x, this.topPos + 120, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        TurtleGuiUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x, this.topPos + 134, 55, 16, "Private", be.isPrivateMode, false);
        TurtleGuiUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 60, this.topPos + 134, 65, 16, "Wake(RS)", be.wakeOnRedstone, false);
        TurtleGuiUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 130, this.topPos + 134, 75, 16, "Debug Log", be.debugChat, false);

        // Admin
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Admin:", x, this.topPos + 160, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x, this.topPos + 172, 65, 16, "Wipe Mem", false, 1.0f, TurtleGuiUtils.COLOR_BTN_DANGER, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos + 15;
        if (button == 0) {
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x + 110, this.topPos + 49, 35, 16)) {
                String lbl = this.labelInput.getValue().trim();
                this.screen.sendCommand("label", "set " + lbl);
                this.screen.getMenu().getBlockEntity().setMachineLabel(lbl);
                this.labelSetButtonText = "OK";
                this.labelSetTick = 20;
                return true;
            }
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x + 150, this.topPos + 49, 45, 16)) {
                this.screen.sendCommand("label", "clear");
                this.screen.getMenu().getBlockEntity().setMachineLabel("");
                this.labelInput.setValue("");
                return true;
            }

            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x + 215, this.topPos + 72, 35, 12)) {
                this.screen.sendCommand("clear_error", "");
                this.screen.getMenu().getBlockEntity().persistentData.remove("LastError");
                this.currentLastError = "No recent errors.";
                return true;
            }

            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x + 255, this.topPos + 72, 35, 12)) {
                if (!this.currentLastError.equals("No recent errors.")) {
                    net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(this.currentLastError);
                }
                return true;
            }

            TurtleBotBlockEntity be = this.screen.getMenu().getBlockEntity();
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x, this.topPos + 134, 55, 16)) {
                boolean val = !be.isPrivateMode;
                this.screen.sendCommand("toggle_private", String.valueOf(val));
                be.isPrivateMode = val;
                return true;
            }
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x + 60, this.topPos + 134, 65, 16)) {
                boolean val = !be.wakeOnRedstone;
                this.screen.sendCommand("toggle_wake", String.valueOf(val));
                be.wakeOnRedstone = val;
                return true;
            }
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x + 130, this.topPos + 134, 75, 16)) {
                boolean val = !be.debugChat;
                this.screen.sendCommand("toggle_debug", String.valueOf(val));
                be.debugChat = val;
                return true;
            }
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, x, this.topPos + 172, 65, 16)) {
                this.screen.sendCommand("wipe_memory", "");
                return true;
            }
            if (this.labelInput != null && this.labelInput.mouseClicked(mouseX, mouseY, button)) {
                this.screen.setScreenFocused(this.labelInput);
                return true;
            } else {
                this.screen.setScreenFocused(null);
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    @Override
    public void onClose() { if (this.labelInput != null) this.labelInput.setVisible(false); }
}