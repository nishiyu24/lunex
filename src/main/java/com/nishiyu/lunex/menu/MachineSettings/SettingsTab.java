package com.nishiyu.lunex.menu.MachineSettings;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.client.renderer.blocks.ChunkLoadRenderer;
import com.nishiyu.lunex.machine.IMachineContext;
import com.nishiyu.lunex.menu.utiles.IMachineTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;

public class SettingsTab implements IMachineTab {
    private final MachineSettingsScreen screen;
    private int leftPos, topPos;

    private EditBox labelInput;
    private String labelSetButtonText = "Set";
    private int labelSetTick = 0;

    // Copy用に最後のエラーを保持
    private String currentLastError = "";

    public SettingsTab(MachineSettingsScreen screen) {
        this.screen = screen;
    }

    private int getDistanceUpgradeLevel(IMachineContext ctx) {
        if (ctx.isBlock() && ctx.getPos() != null) {
            if (Minecraft.getInstance().level != null &&
                    Minecraft.getInstance().level.getBlockEntity(ctx.getPos()) instanceof AdvancedMachineBlockEntity be) {
                return be.getDistanceUpgradeLevel();
            }
        }
        return 0;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
        IMachineContext ctx = this.screen.getMachineContext();

        if (this.labelInput == null) {
            this.labelInput = new EditBox(this.screen.getFont(), leftPos + 18, topPos + 52, 100, 10, Component.literal("Machine Label"));
            this.labelInput.setMaxLength(30);
            this.labelInput.setBordered(false);
            this.labelInput.setTextColor(GuiRenderUtils.COLOR_TEXT_PRIMARY);
        } else {
            this.labelInput.setX(leftPos + 18);
            this.labelInput.setY(topPos + 52);
        }
        this.labelInput.setValue(ctx.getMachineLabel() != null ? ctx.getMachineLabel() : "");

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

        // Machine Label
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Machine Label:", x, this.topPos + 35, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        guiGraphics.fill(x - 1, this.topPos + 49, x + 105, this.topPos + 65, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(x, this.topPos + 50, x + 104, this.topPos + 64, GuiRenderUtils.COLOR_BG_MAIN);

        if (this.labelInput != null) {
            if (this.labelInput.getValue().isEmpty() && !this.labelInput.isFocused()) {
                GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Label name...", x + 3, this.topPos + 52, 0xFF666666, 1.0f);
            }
            this.labelInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int btnColor1 = this.labelSetButtonText.equals("OK") ? GuiRenderUtils.COLOR_ITEM_SELECTED : GuiRenderUtils.COLOR_BTN_BG;
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 110, this.topPos + 49, 35, 16, this.labelSetButtonText, false, 1.0f, btnColor1, false);
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 150, this.topPos + 49, 45, 16, "Clear", false, 1.0f, GuiRenderUtils.COLOR_BTN_BG, false);

        IMachineContext ctx = this.screen.getMachineContext();

        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Last Error Log:", x, this.topPos + 75, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

        // ★修正: MOD独自の persistentData に直接アクセスしてエラーを取得
        String lastError = "No recent errors.";
        if (ctx.isBlock() && ctx.getPos() != null && Minecraft.getInstance().level != null) {
            BlockEntity be = Minecraft.getInstance().level.getBlockEntity(ctx.getPos());
            if (be instanceof AdvancedMachineBlockEntity pme) {
                if (pme.persistentData.contains("LastError")) {
                    lastError = pme.persistentData.getString("LastError");
                }
            } else if (be instanceof com.nishiyu.lunex.blockentity.RouterBlockEntity router) {
                if (router.persistentData.contains("LastError")) {
                    lastError = router.persistentData.getString("LastError");
                }
            }
        }
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

        // ★修正: 横に Clear と Copy ボタンを配置
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 215, this.topPos + 72, 35, 12, "Clear", false, 0.8f, GuiRenderUtils.COLOR_BTN_BG, false);
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 255, this.topPos + 72, 35, 12, "Copy", false, 0.8f, GuiRenderUtils.COLOR_ITEM_SELECTED, false);


        // System Toggles
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "System Toggles:", x, this.topPos + 120, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        GuiRenderUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x, this.topPos + 134, 55, 16, "Private", ctx.isPrivateMode(), false);
        GuiRenderUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 60, this.topPos + 134, 65, 16, "Wake(RS)", ctx.isWakeOnRedstone(), false);
        GuiRenderUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x + 130, this.topPos + 134, 75, 16, "Debug Log", ctx.isDebugChat(), false);

        // Admin
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Admin:", x, this.topPos + 160, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, x, this.topPos + 172, 65, 16, "Wipe Mem", false, 1.0f, GuiRenderUtils.COLOR_BTN_DANGER, false);

        int distanceLevel = getDistanceUpgradeLevel(ctx);
        int currentX = x + 70;
        if (distanceLevel > 0) {
            boolean isShowingChunk = ChunkLoadRenderer.isRendering(ctx.getPos());
            GuiRenderUtils.drawToggleButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, currentX, this.topPos + 172, 85, 16, "Show Chunk", isShowingChunk, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos + 15;
        if (button == 0) {
            // Label Buttons
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 110, this.topPos + 49, 35, 16)) {
                String lbl = this.labelInput.getValue().trim();
                this.screen.sendCommand("label", "set " + lbl);
                this.screen.getMachineContext().setMachineLabel(lbl);
                this.labelSetButtonText = "OK";
                this.labelSetTick = 20;
                return true;
            }
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 150, this.topPos + 49, 45, 16)) {
                this.screen.sendCommand("label", "clear");
                this.screen.getMachineContext().setMachineLabel("");
                this.labelInput.setValue("");
                return true;
            }

            IMachineContext ctx = this.screen.getMachineContext();

            // ★追加: Clear Error ボタン
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 215, this.topPos + 72, 35, 12)) {
                this.screen.sendCommand("clear_error", "");

                // 即座にローカル表示を消して反応を良くする
                if (ctx.isBlock() && ctx.getPos() != null && Minecraft.getInstance().level != null) {
                    BlockEntity be = Minecraft.getInstance().level.getBlockEntity(ctx.getPos());
                    if (be instanceof AdvancedMachineBlockEntity pme) {
                        pme.persistentData.remove("LastError");
                    } else if (be instanceof com.nishiyu.lunex.blockentity.RouterBlockEntity router) {
                        router.persistentData.remove("LastError");
                    }
                }
                this.currentLastError = "No recent errors.";
                return true;
            }

            // ★追加: Copy ボタン
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 255, this.topPos + 72, 35, 12)) {
                if (this.currentLastError != null && !this.currentLastError.isEmpty() && !this.currentLastError.equals("No recent errors.")) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.currentLastError);
                }
                return true;
            }

            // Toggles
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x, this.topPos + 134, 55, 16)) {
                boolean val = !ctx.isPrivateMode();
                this.screen.sendCommand("toggle_private", String.valueOf(val));
                ctx.setPrivateMode(val);
                return true;
            }
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 60, this.topPos + 134, 65, 16)) {
                boolean val = !ctx.isWakeOnRedstone();
                this.screen.sendCommand("toggle_wake", String.valueOf(val));
                ctx.setWakeOnRedstone(val);
                return true;
            }
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 130, this.topPos + 134, 75, 16)) {
                boolean val = !ctx.isDebugChat();
                this.screen.sendCommand("toggle_debug", String.valueOf(val));
                ctx.setDebugChat(val);
                return true;
            }

            // Wipe Memory
            if (GuiRenderUtils.isHovered(mouseX, mouseY, x, this.topPos + 172, 65, 16)) {
                this.screen.sendCommand("wipe_memory", "");
                return true;
            }

            int distanceLevel = getDistanceUpgradeLevel(ctx);
            if (distanceLevel > 0) {
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 70, this.topPos + 172, 85, 16)) {
                    ChunkPos center = new ChunkPos(Objects.requireNonNull(ctx.getPos()));
                    int radius = distanceLevel == 1 ? 0 : (distanceLevel == 2 ? 1 : 2);
                    ChunkLoadRenderer.toggleMachine(ctx.getPos(), center, radius);
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