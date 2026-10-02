package com.nishiyu.lunex.menu.BioEntity;

import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.menu.utiles.GuiRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class BioSettingsTab implements IMachineTab {
    private final BioEntitySettingsScreen screen;
    private int leftPos, topPos;

    private EditBox labelInput;
    private String labelSetButtonText = "Set";
    private int labelSetTick = 0;

    private String currentLastError = "";

    public BioSettingsTab(BioEntitySettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;

        // ★タブを開くたびに最新の設定をサーバーへリクエストする
        this.screen.sendCommand("request_bio_info", "");

        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        String currentLabel = (mob != null && mob.hasCustomName()) ? mob.getCustomName().getString() : "";

        if (this.labelInput == null) {
            this.labelInput = new EditBox(this.screen.getFont(), leftPos + 18, topPos + 52, 100, 10, Component.literal("Mob Label"));
            this.labelInput.setMaxLength(30);
            this.labelInput.setBordered(false);
            this.labelInput.setTextColor(GuiRenderUtils.COLOR_TEXT_PRIMARY);
        } else {
            this.labelInput.setX(leftPos + 18);
            this.labelInput.setY(topPos + 52);
        }
        this.labelInput.setValue(currentLabel);
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
        int x = this.leftPos + 15;
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        boolean isMechanical = (mob != null && mob.behaviors.contains("mechanical"));

        // 1. Mob Label
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Mob Name (Label):", x, this.topPos + 35, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        guiGraphics.fill(x - 1, this.topPos + 49, x + 105, this.topPos + 65, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(x, this.topPos + 50, x + 104, this.topPos + 64, GuiRenderUtils.COLOR_BG_MAIN);

        if (this.labelInput != null) {
            if (this.labelInput.getValue().isEmpty() && !this.labelInput.isFocused()) {
                GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Name...", x + 3, this.topPos + 52, 0xFF666666, 1.0f);
            }
            this.labelInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int btnColor1 = this.labelSetButtonText.equals("OK") ? GuiRenderUtils.COLOR_ITEM_SELECTED : GuiRenderUtils.COLOR_BTN_BG;
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 110, this.topPos + 49, 35, 16, this.labelSetButtonText, false, 1.0f, btnColor1, false);
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 150, this.topPos + 49, 45, 16, "Clear", false, 1.0f, GuiRenderUtils.COLOR_BTN_BG, false);

        // 2. Error Log & Toggles (VMを持つ場合のみ)
        if (isMechanical) {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Last Error Log:", x, this.topPos + 75, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

            // ★変更: Screenインスタンスが保持しているエラーを取得
            String lastError = this.screen.lastError;
            this.currentLastError = lastError;

            guiGraphics.fill(x - 1, this.topPos + 86, x + 295, this.topPos + 115, GuiRenderUtils.COLOR_BORDER);
            guiGraphics.fill(x, this.topPos + 87, x + 294, this.topPos + 114, GuiRenderUtils.COLOR_BG_SIDEBAR);

            String[] errorLines = lastError.split("\n");
            int errY = this.topPos + 89;
            for (int i = 0; i < errorLines.length; i++) {
                if (i > 2) break;
                GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), errorLines[i], x + 4, errY, 0xFFFF8888, 0.8f);
                errY += 8;
            }

            GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 215, this.topPos + 72, 35, 12, "Clear", false, 0.8f, GuiRenderUtils.COLOR_BTN_BG, false);
            GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 255, this.topPos + 72, 35, 12, "Copy", false, 0.8f, GuiRenderUtils.COLOR_ITEM_SELECTED, false);

            // System Toggles
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "System Toggles:", x, this.topPos + 120, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

            // ★変更: Screenインスタンスが保持している設定値を取得
            boolean isPrivate = this.screen.isPrivateMode;
            boolean isDebug = this.screen.isDebugLog;

            GuiRenderUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x, this.topPos + 134, 55, 16, "Private", isPrivate, false);
            GuiRenderUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 60, this.topPos + 134, 75, 16, "Debug Log", isDebug, false);

            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Admin:", x, this.topPos + 160, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
            GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x, this.topPos + 172, 65, 16, "Wipe Mem", false, 1.0f, GuiRenderUtils.COLOR_BTN_DANGER, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos + 15;
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        boolean isMechanical = (mob != null && mob.behaviors.contains("mechanical"));

        if (button == 0) {
            // Label Set
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 110, this.topPos + 49, 35, 16)) {
                String lbl = this.labelInput.getValue().trim();
                this.screen.sendCommand("label", "set " + lbl);
                if (mob != null) mob.setCustomName(Component.literal(lbl));
                this.labelSetButtonText = "OK";
                this.labelSetTick = 20;
                return true;
            }
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 150, this.topPos + 49, 45, 16)) {
                this.screen.sendCommand("label", "clear");
                if (mob != null) mob.setCustomName(null);
                this.labelInput.setValue("");
                return true;
            }

            if (isMechanical) {
                // Clear Error
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 215, this.topPos + 72, 35, 12)) {
                    this.screen.sendCommand("clear_error", "");
                    this.screen.lastError = "No recent errors.";
                    this.currentLastError = "No recent errors.";
                    return true;
                }

                // Copy Error
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 255, this.topPos + 72, 35, 12)) {
                    if (this.currentLastError != null && !this.currentLastError.isEmpty() && !this.currentLastError.equals("No recent errors.")) {
                        if (Minecraft.getInstance().keyboardHandler != null) {
                            Minecraft.getInstance().keyboardHandler.setClipboard(this.currentLastError);
                        }
                    }
                    return true;
                }

                // Toggles
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x, this.topPos + 134, 55, 16)) {
                    this.screen.sendCommand("toggle_private", "");
                    this.screen.isPrivateMode = !this.screen.isPrivateMode;
                    return true;
                }
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 60, this.topPos + 134, 75, 16)) {
                    this.screen.sendCommand("toggle_debug", "");
                    this.screen.isDebugLog = !this.screen.isDebugLog;
                    return true;
                }

                // Wipe Memory
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x, this.topPos + 172, 65, 16)) {
                    this.screen.sendCommand("wipe_memory", "");
                    return true;
                }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public void onClose() {
        if (this.labelInput != null) this.labelInput.setVisible(false);
    }
}