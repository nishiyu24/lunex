package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.api.client.*;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

public class MainframeOverviewScreen extends AbstractContainerScreen<MainframeOverviewMenu> {

    private float renderScale = 30F;
    private boolean scaleInitialized = false;
    private float yaw = 45f, pitch = 30f;
    private boolean isDraggingView = false;
    private boolean isExploded = false;
    private float currentExplodeOffset = 0.0f;
    private BlockPos selectedPos = null;

    private record ScrolledWidget(AbstractWidget widget, int initialY) {}
    private final List<ScrolledWidget> dynamicWidgets = new ArrayList<>();

    private final IdeScreenFramework uiFramework;
    private static final int TOP_BAR_HEIGHT = 22;

    public MainframeOverviewScreen(MainframeOverviewMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 0; this.imageHeight = 0;
        this.uiFramework = new IdeScreenFramework();
    }

    private int getVpX() { return uiFramework.showLeftPanel ? uiFramework.leftWidth : 0; }
    private int getVpY() { return TOP_BAR_HEIGHT; }
    private int getVpWidth() {
        int w = this.width - uiFramework.rightWidth;
        if (uiFramework.showLeftPanel) w -= uiFramework.leftWidth;
        return w;
    }
    private int getVpHeight() { return this.height - TOP_BAR_HEIGHT - uiFramework.bottomHeight; }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 9999; this.titleLabelY = 9999;
        this.inventoryLabelX = 9999; this.inventoryLabelY = 9999;

        if (!scaleInitialized) {
            calculateInitialScale();
            scaleInitialized = true;
        }

        BlockEntity masterBe = this.menu.getLevel().getBlockEntity(this.menu.getMasterPos());
        this.uiFramework.setLeftPanel(MainframeUIRegistry.createLeftPanel(masterBe));
        this.uiFramework.clearBottomTabs();

        if (masterBe instanceof SimpleMachineBlockEntity master) {
            List<IdeScreenFramework.IBottomTab> dynamicTabs = MainframeBottomTabRegistry.getTabsFor(master, this.menu.getLevel());
            for (IdeScreenFramework.IBottomTab tab : dynamicTabs) {
                this.uiFramework.addBottomTab(tab);
            }
        }
        rebuildUI();
        updateSlotPositions();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        currentExplodeOffset += ((isExploded ? 1.0f : 0.0f) - currentExplodeOffset) * 0.2f;

        if (this.uiFramework != null && this.uiFramework.getRightPanel() != null) {
            int scrollY = this.uiFramework.getRightPanel().getScrollY();
            int panelTopY = TOP_BAR_HEIGHT + 21;
            int panelBottomY = this.height - uiFramework.bottomHeight;

            for (ScrolledWidget sw : dynamicWidgets) {
                int newY = sw.initialY - scrollY;
                sw.widget.setY(newY);
                // ★ 右パネルからはみ出たウィジェットは active だけでなく visible もオフにする
                boolean inBounds = (newY >= panelTopY && newY + sw.widget.getHeight() <= panelBottomY);
                sw.widget.active = inBounds;
                sw.widget.visible = inBounds;
            }
        }
    }

    private void calculateInitialScale() {
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return;
        float cx = 0, cy = 0, cz = 0, maxDist = 1.0f;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();
        for (BlockPos p : parts) {
            maxDist = Math.max(maxDist, Math.abs(p.getX() - cx));
            maxDist = Math.max(maxDist, Math.abs(p.getY() - cy));
            maxDist = Math.max(maxDist, Math.abs(p.getZ() - cz));
        }
        this.renderScale = Math.clamp(Math.min(Math.max(100, getVpWidth()), Math.max(100, getVpHeight())) / (maxDist * 2.5f), 5f, 50f);
    }

    private List<BlockPos> getParts() {
        List<BlockPos> parts = new ArrayList<>();
        Level level = this.menu.getLevel();
        BlockEntity masterBe = level.getBlockEntity(this.menu.getMasterPos());
        if (masterBe instanceof SimpleMachineBlockEntity master && !master.mainframeParts.isEmpty()) {
            parts.addAll(master.mainframeParts);
        }
        return parts;
    }

    public void rebuildUI() {
        for (ScrolledWidget sw : dynamicWidgets) this.removeWidget(sw.widget);
        dynamicWidgets.clear();

        this.uiFramework.showLeftPanel = (this.width >= 800 || this.selectedPos == null);

        BlockEntity be = this.selectedPos != null ? this.menu.getLevel().getBlockEntity(this.selectedPos) : null;
        IMainframeUIExtension<BlockEntity> extension = (be != null) ? (IMainframeUIExtension<BlockEntity>) MainframeUIRegistry.get(be) : null;

        this.uiFramework.setRightPanel(MainframeUIRegistry.createRightPanel(this.selectedPos, be, extension));

        if (extension != null && be != null) {
            int panelX = this.width - uiFramework.rightWidth;
            int textY = TOP_BAR_HEIGHT + 70;
            extension.buildWidgets(this, this.selectedPos, be, panelX, textY, widget -> {
                this.addRenderableWidget(widget);
                dynamicWidgets.add(new ScrolledWidget(widget, widget.getY()));
            });
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ★ 1. 分解トグルボタン (最前面)
        int btnX = getVpX() + getVpWidth() - 30;
        int btnY = getVpY() + 10;
        if (mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= btnY && mouseY <= btnY + 20) {
            if (button == 0) {
                isExploded = !isExploded;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // ★ 2. パネル上の動的ウィジェット (Zオーダー高め)
        for (ScrolledWidget sw : this.dynamicWidgets) {
            if (sw.widget.active && sw.widget.visible && sw.widget.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(sw.widget);
                if (button == 0) this.isDraggingView = false;
                return true;
            }
        }

        this.setFocused(null);

        // ★ 3. UIフレームワーク領域 (タブ・パネル領域ならイベントをキャッチしてここで打ち切る)
        if (uiFramework.mouseClicked(mouseX, mouseY, button, this.width, this.height, TOP_BAR_HEIGHT)) {
            return true;
        }

        // ★ 4. 背景 (3Dビューの操作とブロックピッキング)
        if (button == 0) this.isDraggingView = true;
        if (button == 0 || button == 1) {
            BlockPos hitPos = pickBlock(mouseX, mouseY);
            if (hitPos == null && isMouseInViewport(mouseX, mouseY)) return true;
            if (hitPos != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectBlock(hitPos);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseInViewport(double mouseX, double mouseY) {
        return mouseX >= getVpX() && mouseX <= getVpX() + getVpWidth() && mouseY >= getVpY() && mouseY <= getVpY() + getVpHeight();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.uiFramework.mouseReleased();
        if (button == 0) this.isDraggingView = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (uiFramework.mouseDragged(mouseX, mouseY, this.width, this.height)) {
            rebuildUI();
            return true;
        }
        if (this.isDraggingView && button == 0 && isMouseInViewport(mouseX, mouseY)) {
            this.yaw += (float) dragX;
            this.pitch = Math.clamp(this.pitch + (float) dragY, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (uiFramework.mouseScrolled(mouseX, mouseY, scrollX, scrollY, this.width, this.height, TOP_BAR_HEIGHT)) return true;
        if (isMouseInViewport(mouseX, mouseY)) {
            this.renderScale = Math.clamp(this.renderScale + (float) (scrollY * 2.0f), 5f, 100f);
            return true;
        }
        return false;
    }

    private void selectBlock(BlockPos pos) {
        this.selectedPos = pos;
        BlockEntity be = this.menu.getLevel().getBlockEntity(pos);
        if (be instanceof ScreenBlockEntity screenBe && screenBe.masterPos != null) {
            this.selectedPos = screenBe.masterPos;
        }
        rebuildUI();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        guiGraphics.enableScissor(getVpX(), getVpY(), getVpX() + getVpWidth(), getVpY() + getVpHeight());
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(getVpX() + (getVpWidth() / 2.0f), getVpY() + (getVpHeight() / 2.0f), 200);
        poseStack.scale(renderScale, -renderScale, renderScale);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)));

        BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
        for (BlockPos p : parts) {
            BlockState state = this.menu.getLevel().getBlockState(p);
            if (state.isAir()) continue;

            BlockEntity be = this.menu.getLevel().getBlockEntity(p);
            if (be instanceof MainframeAdapterBlockEntity adapter) {
                BlockState original = adapter.getOriginalState();
                if (original != null) {
                    state = original;
                }
            }

            float dx = p.getX() - cx;
            float dy = p.getY() - cy;
            float dz = p.getZ() - cz;

            float renderX = dx - 0.5f + (dx * currentExplodeOffset * 0.8f);
            float renderY = dy - 0.5f + (dy * currentExplodeOffset * 0.8f);
            float renderZ = dz - 0.5f + (dz * currentExplodeOffset * 0.8f);

            poseStack.pushPose();
            poseStack.translate(renderX, renderY, renderZ);
            renderer.renderSingleBlock(state, poseStack, guiGraphics.bufferSource(), 15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            guiGraphics.flush();

            if (p.equals(this.selectedPos)) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                VertexConsumer highlight = guiGraphics.bufferSource().getBuffer(RenderType.gui());
                for (Direction dir : Direction.values()) {
                    drawFaceHighlightColor(poseStack, highlight, dir, 50, 255, 50, 100);
                }
                guiGraphics.flush();
                RenderSystem.disableBlend();
            }
            poseStack.popPose();
        }
        poseStack.popPose();

        int btnX = getVpX() + getVpWidth() - 30;
        int btnY = getVpY() + 10;
        boolean isHovered = mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= btnY && mouseY <= btnY + 20;

        guiGraphics.fill(btnX, btnY, btnX + 20, btnY + 20, isHovered ? 0xFF555555 : 0xFF333333);
        guiGraphics.renderOutline(btnX, btnY, 20, 20, 0xFF6A6A6A);

        int color = isExploded ? 0xFF00E5FF : 0xFFFFFFFF;
        if (isExploded) {
            guiGraphics.fill(btnX + 4, btnY + 4, btnX + 8, btnY + 8, color);
            guiGraphics.fill(btnX + 12, btnY + 4, btnX + 16, btnY + 8, color);
            guiGraphics.fill(btnX + 4, btnY + 12, btnX + 8, btnY + 16, color);
            guiGraphics.fill(btnX + 12, btnY + 12, btnX + 16, btnY + 16, color);
        } else {
            guiGraphics.fill(btnX + 6, btnY + 6, btnX + 14, btnY + 14, color);
        }

        guiGraphics.disableScissor();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    private void updateSlotPositions() {
        IdeScreenFramework.IBottomTab activeTab = uiFramework.getActiveBottomTab();
        if (activeTab != null) {
            this.leftPos = activeTab.getPlayerInventoryX();
            this.topPos = activeTab.getPlayerInventoryY(this.height, uiFramework.bottomHeight);
        } else {
            this.leftPos = 9999;
            this.topPos = 9999;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, TOP_BAR_HEIGHT, 0xFF3C3C3C);
        guiGraphics.drawString(this.font, "LUNEX MAINFRAME EDITOR", 10, 6, 0xFFD4D4D4);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);

        uiFramework.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height, TOP_BAR_HEIGHT);

        updateSlotPositions();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int btnX = getVpX() + getVpWidth() - 30;
        int btnY = getVpY() + 10;
        if (mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= btnY && mouseY <= btnY + 20) {
            guiGraphics.renderTooltip(this.font, Component.literal(isExploded ? "Collapse View" : "Explode View"), mouseX, mouseY);
        }
    }

    private BlockPos pickBlock(double mouseX, double mouseY) {
        if (!isMouseInViewport(mouseX, mouseY)) return null;
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return null;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        BlockPos hitPos = null;
        float maxZ = -Float.MAX_VALUE;

        for (BlockPos p : parts) {
            Matrix4f matrix = new Matrix4f();
            matrix.translate(getVpX() + (getVpWidth() / 2.0f), getVpY() + (getVpHeight() / 2.0f), 200);
            matrix.scale(renderScale, -renderScale, renderScale);
            Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
            matrix.rotate(rotation);

            float dx = p.getX() - cx;
            float dy = p.getY() - cy;
            float dz = p.getZ() - cz;
            float renderX = dx - 0.5f + (dx * currentExplodeOffset * 0.8f);
            float renderY = dy - 0.5f + (dy * currentExplodeOffset * 0.8f);
            float renderZ = dz - 0.5f + (dz * currentExplodeOffset * 0.8f);

            matrix.translate(renderX, renderY, renderZ);

            for (Direction dir : Direction.values()) {
                Vector3f[] corners = getFaceCorners(dir);
                Vector3f[] projected = new Vector3f[4];
                float avgZ = 0;
                for (int i = 0; i < 4; i++) {
                    Vector4f vec = new Vector4f(corners[i].x, corners[i].y, corners[i].z, 1.0f);
                    matrix.transform(vec);
                    projected[i] = new Vector3f(vec.x, vec.y, vec.z);
                    avgZ += vec.z;
                }
                avgZ /= 4f;

                if (isPointInQuad(mouseX, mouseY, projected) && avgZ > maxZ) {
                    maxZ = avgZ;
                    hitPos = p;
                }
            }
        }
        return hitPos;
    }

    private void drawFaceHighlightColor(PoseStack poseStack, VertexConsumer buffer, Direction face, int r, int g, int b, int a) {
        Matrix4f pose = poseStack.last().pose();
        float offset = 0.005f;
        switch (face) {
            case NORTH -> { addVertex(buffer, pose, 1, 1, -offset, r, g, b, a); addVertex(buffer, pose, 1, 0, -offset, r, g, b, a); addVertex(buffer, pose, 0, 0, -offset, r, g, b, a); addVertex(buffer, pose, 0, 1, -offset, r, g, b, a); }
            case SOUTH -> { addVertex(buffer, pose, 0, 1, 1 + offset, r, g, b, a); addVertex(buffer, pose, 0, 0, 1 + offset, r, g, b, a); addVertex(buffer, pose, 1, 0, 1 + offset, r, g, b, a); addVertex(buffer, pose, 1, 1, 1 + offset, r, g, b, a); }
            case WEST -> { addVertex(buffer, pose, -offset, 1, 0, r, g, b, a); addVertex(buffer, pose, -offset, 0, 0, r, g, b, a); addVertex(buffer, pose, -offset, 0, 1, r, g, b, a); addVertex(buffer, pose, -offset, 1, 1, r, g, b, a); }
            case EAST -> { addVertex(buffer, pose, 1 + offset, 1, 1, r, g, b, a); addVertex(buffer, pose, 1 + offset, 0, 1, r, g, b, a); addVertex(buffer, pose, 1 + offset, 0, 0, r, g, b, a); addVertex(buffer, pose, 1 + offset, 1, 0, r, g, b, a); }
            case UP -> { addVertex(buffer, pose, 0, 1 + offset, 0, r, g, b, a); addVertex(buffer, pose, 0, 1 + offset, 1, r, g, b, a); addVertex(buffer, pose, 1, 1 + offset, 1, r, g, b, a); addVertex(buffer, pose, 1, 1 + offset, 0, r, g, b, a); }
            case DOWN -> { addVertex(buffer, pose, 1, -offset, 0, r, g, b, a); addVertex(buffer, pose, 1, -offset, 1, r, g, b, a); addVertex(buffer, pose, 0, -offset, 1, r, g, b, a); addVertex(buffer, pose, 0, -offset, 0, r, g, b, a); }
        }
    }

    private void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int r, int g, int b, int a) {
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }

    private Vector3f[] getFaceCorners(Direction dir) {
        return switch (dir) {
            case UP -> new Vector3f[]{new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, 1, 1), new Vector3f(0, 1, 1)};
            case DOWN -> new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(1, 0, 1), new Vector3f(1, 0, 0)};
            case NORTH -> new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, 0, 0)};
            case SOUTH -> new Vector3f[]{new Vector3f(1, 0, 1), new Vector3f(1, 1, 1), new Vector3f(0, 1, 1), new Vector3f(0, 0, 1)};
            case WEST -> new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 1), new Vector3f(0, 1, 0)};
            case EAST -> new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(1, 1, 1), new Vector3f(1, 0, 1)};
        };
    }

    private boolean isPointInQuad(double px, double py, Vector3f[] corners) {
        boolean hasPos = false, hasNeg = false;
        for (int i = 0; i < 4; i++) {
            Vector3f p1 = corners[i], p2 = corners[(i + 1) % 4];
            double cross = (px - p1.x) * (p2.y - p1.y) - (py - p1.y) * (p2.x - p1.x);
            if (cross > 0.001) hasPos = true;
            if (cross < -0.001) hasNeg = true;
        }
        return !(hasPos && hasNeg);
    }
}