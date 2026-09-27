package com.nishiyu.lunex.menu.utiles;

import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.client.ClientScreenInteractionManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class InvisibleInputScreen extends Screen {

    private final String sessionId;
    private final ScreenBlockEntity.UIElement targetElement;
    private final BlockPos blockPos;
    private EditBox hiddenEditBox;

    public InvisibleInputScreen(String sessionId, ScreenBlockEntity.UIElement targetElement, BlockPos blockPos) {
        super(Component.literal("Hidden Input"));
        this.sessionId = sessionId;
        this.targetElement = targetElement;
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        super.init();

        hiddenEditBox = new EditBox(this.font, -100, -100, 10, 10, Component.empty());
        hiddenEditBox.setMaxLength(256);
        hiddenEditBox.setValue(targetElement.text() != null ? targetElement.text() : "");

        hiddenEditBox.setFocused(true);
        this.setFocused(hiddenEditBox);

        hiddenEditBox.setResponder(newText -> {
            ClientScreenInteractionManager.updateInputLocally(sessionId, targetElement.id(), newText);
        });

        this.addRenderableWidget(hiddenEditBox);
    }

    @Override
    public void removed() {
        ClientScreenInteractionManager.submitInput(sessionId, targetElement.id(), hiddenEditBox.getValue());
        super.removed();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }
}