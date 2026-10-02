package com.nishiyu.lunex.menu.BioEntity;

import net.minecraft.client.gui.GuiGraphics;

public interface IMachineTab {
    void init(int leftPos, int topPos);

    void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    void tick();

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    void onClose();
}