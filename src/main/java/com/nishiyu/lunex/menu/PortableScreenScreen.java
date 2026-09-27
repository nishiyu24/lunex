package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.client.ClientScreenInteractionManager;
import com.nishiyu.lunex.client.ClientScreenManager;
import com.nishiyu.lunex.client.renderer.ScreenAnimator;
import com.nishiyu.lunex.client.renderer.ScreenRenderCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PortableScreenScreen extends AbstractContainerScreen<PortableScreenMenu> {

    public static final int VIRTUAL_WIDTH = 768;
    public static final int VIRTUAL_HEIGHT = 1024;

    private final String ip;
    private EditBox activeInput;
    private String activeInputId = null;

    private float zoom = 1.0f;
    private float panX = 0.0f;
    private float panY = 0.0f;

    private boolean hasSentResize = false;

    public PortableScreenScreen(PortableScreenMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.ip = menu.getIp();
        this.imageWidth = 0;
        this.imageHeight = 0;
        System.out.println("[Lunex Debug] [Client] 💻 GUI Opened with ID: '" + this.ip + "'");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        if (!hasSentResize) {
            ClientScreenManager.recomputeLayout(this.ip, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            hasSentResize = true;
        }

        this.activeInput = new EditBox(this.font, 0, 0, 100, 20, Component.empty());
        this.activeInput.setVisible(false);
        this.activeInput.setMaxLength(256);
        this.activeInput.setResponder(text -> {
            if (this.activeInputId != null && this.activeInput.isVisible()) {
                ClientScreenInteractionManager.updateInputLocally(this.ip, this.activeInputId, text);
            }
        });
        this.addRenderableWidget(this.activeInput);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        ClientScreenInteractionManager.ensureSynced(this.ip);

        List<ScreenBlockEntity.UIElement> elements = ClientScreenManager.getElements(this.ip);
        if (elements == null) elements = new ArrayList<>();

        float logicPixelW = VIRTUAL_WIDTH;
        float logicPixelH = VIRTUAL_HEIGHT;

        float maxPanX = Math.max(0.0f, logicPixelW - (logicPixelW / this.zoom));
        float maxPanY = Math.max(0.0f, logicPixelH - (logicPixelH / this.zoom));
        this.panX = Mth.clamp(this.panX, 0.0f, maxPanX);
        this.panY = Mth.clamp(this.panY, 0.0f, maxPanY);

        float baseScale = (float) Math.min((this.width * 0.95f) / logicPixelW, (this.height * 0.95f) / logicPixelH);
        float drawW = logicPixelW * baseScale;
        float drawH = logicPixelH * baseScale;
        float drawX = (this.width - drawW) / 2.0f;
        float drawY = (this.height - drawH) / 2.0f;

        guiGraphics.fill((int) drawX - 4, (int) drawY - 4, (int) (drawX + drawW) + 4, (int) (drawY + drawH) + 4, 0xFF333333);
        guiGraphics.fill((int) drawX - 2, (int) drawY - 2, (int) (drawX + drawW) + 2, (int) (drawY + drawH) + 2, 0xFF000000);

        guiGraphics.enableScissor((int) drawX, (int) drawY, (int) (drawX + drawW), (int) (drawY + drawH));

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(drawX, drawY, 0);

        float finalScale = baseScale * this.zoom;
        pose.scale(finalScale, finalScale, 1.0f);
        pose.translate(-this.panX, -this.panY, 0);

        double[] pixels = getLogicPixels(mouseX, mouseY);
        String hoveredId = "";
        if (pixels != null) {
            hoveredId = ClientScreenInteractionManager.handleHover(this.ip, pixels[0], pixels[1]);
        } else {
            hoveredId = ClientScreenInteractionManager.handleHover(this.ip, -1, -1);
        }

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        int logicWidthBlocks = (int) Math.ceil((float) VIRTUAL_WIDTH / ScreenBlockEntity.RESOLUTION);
        int renderHeightBlocks = (int) Math.ceil((float) VIRTUAL_HEIGHT / ScreenBlockEntity.RESOLUTION);

        ScreenRenderCore.renderElements(
                elements, logicWidthBlocks, renderHeightBlocks,
                pose, bufferSource, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                true, true, true, 0.02f, 0.01f, hoveredId, 0x00000000, false,
                Minecraft.getInstance().level, true, true, this.ip,
                null,
                null,
                0 // ★ 追加: renderMode 引数
        );

        bufferSource.endBatch();
        pose.popPose();
        guiGraphics.disableScissor();

        updateInputBoxPosition(drawX, drawY, drawW, drawH, logicPixelW, logicPixelH);
    }

    private double[] getLogicPixels(double mouseX, double mouseY) {
        float logicPixelW = VIRTUAL_WIDTH;
        float logicPixelH = VIRTUAL_HEIGHT;
        float baseScale = (float) Math.min((this.width * 0.95f) / logicPixelW, (this.height * 0.95f) / logicPixelH);
        float drawW = logicPixelW * baseScale;
        float drawH = logicPixelH * baseScale;
        float drawX = (this.width - drawW) / 2.0f;
        float drawY = (this.height - drawH) / 2.0f;

        if (mouseX >= drawX && mouseX <= drawX + drawW && mouseY >= drawY && mouseY <= drawY + drawH) {
            double rx = (mouseX - drawX) / drawW;
            double ry = (mouseY - drawY) / drawH;
            double pixelX = this.panX + rx * (logicPixelW / this.zoom);
            double pixelY = this.panY + ry * (logicPixelH / this.zoom);
            return new double[]{pixelX, pixelY};
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            if (this.activeInput.isVisible()) submitInput();
            return super.mouseClicked(mouseX, mouseY, button);
        }

        double[] pixels = getLogicPixels(mouseX, mouseY);
        if (pixels != null) {
            ScreenBlockEntity.UIElement clickedEl = ClientScreenInteractionManager.handleClick(this.ip, pixels[0], pixels[1]);
            if (clickedEl != null && "input".equals(clickedEl.type())) {
                activateInputBox(clickedEl);
                return true;
            }
            if (this.activeInput.isVisible()) submitInput();
            return true;
        }

        if (this.activeInput.isVisible()) submitInput();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1 || button == 2) {
            float baseScale = (float) Math.min((this.width * 0.95f) / (float) VIRTUAL_WIDTH, (this.height * 0.95f) / (float) VIRTUAL_HEIGHT);
            float finalScale = baseScale * this.zoom;

            this.panX = (float) (this.panX - (dragX / finalScale));
            this.panY = (float) (this.panY - (dragY / finalScale));
            return true;
        }

        double[] pixels = getLogicPixels(mouseX, mouseY);
        if (pixels != null) {
            if (ClientScreenInteractionManager.handleDrag(this.ip, pixels[0], pixels[1])) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ClientScreenInteractionManager.handleRelease(this.ip);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hasControlDown()) {
            float oldZoom = this.zoom;
            float zoomStep = 0.2f * this.zoom;
            if (scrollY > 0) this.zoom += zoomStep;
            else if (scrollY < 0) this.zoom -= zoomStep;
            this.zoom = Mth.clamp(this.zoom, 1.0f, 10.0f);

            if (oldZoom != this.zoom) {
                double[] logicPx = getLogicPixels(mouseX, mouseY);
                if (logicPx != null) {
                    float logicPixelW = VIRTUAL_WIDTH;
                    float logicPixelH = VIRTUAL_HEIGHT;
                    float baseScale = (float) Math.min((this.width * 0.95f) / logicPixelW, (this.height * 0.95f) / logicPixelH);
                    float drawW = logicPixelW * baseScale;
                    float drawH = logicPixelH * baseScale;
                    float drawX = (this.width - drawW) / 2.0f;
                    float drawY = (this.height - drawH) / 2.0f;

                    double mouseRelX = (mouseX - drawX) / drawW;
                    double mouseRelY = (mouseY - drawY) / drawH;

                    this.panX = (float) (logicPx[0] - mouseRelX * (logicPixelW / this.zoom));
                    this.panY = (float) (logicPx[1] - mouseRelY * (logicPixelH / this.zoom));
                }
            }
            return true;
        }

        boolean handledByContainer = false;
        double[] pixels = getLogicPixels(mouseX, mouseY);
        if (pixels != null) {
            if (hasShiftDown()) {
                handledByContainer = ClientScreenInteractionManager.handleScroll(this.ip, pixels[0], pixels[1], scrollY, 0);
            } else {
                handledByContainer = ClientScreenInteractionManager.handleScroll(this.ip, pixels[0], pixels[1], 0, scrollY);
            }
        }

        if (!handledByContainer) {
            float logicPixelW = VIRTUAL_WIDTH;
            float logicPixelH = VIRTUAL_HEIGHT;
            float maxPanX = Math.max(0.0f, logicPixelW - (logicPixelW / this.zoom));
            float maxPanY = Math.max(0.0f, logicPixelH - (logicPixelH / this.zoom));

            if (hasShiftDown() && maxPanX > 0) {
                this.panX -= (float) (scrollY * 30.0f / this.zoom);
                this.panX = Mth.clamp(this.panX, 0.0f, maxPanX);
                return true;
            } else if (!hasShiftDown() && maxPanY > 0) {
                this.panY -= (float) (scrollY * 30.0f / this.zoom);
                this.panY = Mth.clamp(this.panY, 0.0f, maxPanY);
                return true;
            }
        } else {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.activeInput.isVisible() && (keyCode == 257 || keyCode == 335)) {
            submitInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        if (this.activeInput != null && this.activeInput.isVisible()) submitInput();
        super.removed();
    }

    private void submitInput() {
        if (this.activeInputId != null) {
            ClientScreenInteractionManager.submitInput(this.ip, this.activeInputId, this.activeInput.getValue());
            this.activeInput.setVisible(false);
            this.activeInputId = null;
            this.setFocused(null);
            ScreenRenderCore.activeInputId = "";
        }
    }

    private void activateInputBox(ScreenBlockEntity.UIElement clickedEl) {
        this.activeInputId = clickedEl.id();
        ScreenRenderCore.activeInputId = clickedEl.id();
        this.activeInput.setValue(clickedEl.text() == null ? "" : clickedEl.text());
        this.activeInput.setBordered(false);
        this.activeInput.setTextColor(0x00000000);
        this.activeInput.setVisible(true);
        this.setFocused(this.activeInput);
        this.activeInput.setFocused(true);
    }

    private void updateInputBoxPosition(float drawX, float drawY, float drawW, float drawH, float logicPixelW, float logicPixelH) {
        if (this.activeInput.isVisible() && this.activeInputId != null) {
            ScreenBlockEntity.UIElement clickedEl = ClientScreenManager.getElementById(this.ip, this.activeInputId);
            if (clickedEl != null) {
                float baseScale = (float) Math.min((this.width * 0.95f) / logicPixelW, (this.height * 0.95f) / logicPixelH);
                float finalScale = baseScale * this.zoom;
                ScreenAnimator.AnimatedValues anim = ScreenAnimator.getCurrentValues(this.ip, clickedEl);

                float logicX = clickedEl.x() + anim.tx() - this.panX;
                float logicY = clickedEl.y() + anim.ty() - this.panY;

                this.activeInput.setPosition((int) (drawX + logicX * finalScale), (int) (drawY + logicY * finalScale));
                this.activeInput.setSize((int) (clickedEl.width() * anim.sx() * finalScale), (int) (clickedEl.height() * anim.sy() * finalScale));
            }
        }
    }
}