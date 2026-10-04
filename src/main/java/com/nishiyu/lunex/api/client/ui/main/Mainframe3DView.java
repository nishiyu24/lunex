package com.nishiyu.lunex.api.client.ui.main;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Mainframe3DView {
    private float renderScale = 30F;
    private boolean scaleInitialized = false;
    private float yaw = 45f, pitch = 30f;
    private boolean isDraggingView = false;
    private boolean isExploded = false;
    private float currentExplodeOffset = 0.0f;

    private final Level level;
    private final BlockPos masterPos;
    private final Supplier<BlockPos> selectedPosSupplier;
    private final Consumer<BlockPos> onBlockSelected;

    public Mainframe3DView(Level level, BlockPos masterPos, Supplier<BlockPos> selectedPosSupplier, Consumer<BlockPos> onBlockSelected) {
        this.level = level;
        this.masterPos = masterPos;
        this.selectedPosSupplier = selectedPosSupplier;
        this.onBlockSelected = onBlockSelected;
    }

    public void tick() {
        currentExplodeOffset += ((isExploded ? 1.0f : 0.0f) - currentExplodeOffset) * 0.2f;
    }

    private List<BlockPos> getParts() {
        List<BlockPos> parts = new ArrayList<>();
        BlockEntity masterBe = level.getBlockEntity(masterPos);
        if (masterBe instanceof SimpleMachineBlockEntity master && !master.mainframeParts.isEmpty()) {
            parts.addAll(master.mainframeParts);
        }
        return parts;
    }

    public void initScale(int vpWidth, int vpHeight) {
        if (scaleInitialized) return;
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
        this.renderScale = Math.clamp(Math.clamp(vpHeight, 100, Math.max(100, vpWidth)) / (maxDist * 2.5f), 5f, 50f);
        this.scaleInitialized = true;
    }

    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        guiGraphics.enableScissor(x, y, x + width, y + height);
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x + (width / 2.0f), y + (height / 2.0f), 200);
        poseStack.scale(renderScale, -renderScale, renderScale);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)));

        BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
        BlockPos selectedPos = selectedPosSupplier.get();

        for (BlockPos p : parts) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;

            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof MainframeAdapterBlockEntity adapter) {
                BlockState original = adapter.getOriginalState();
                if (original != null) state = original;
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

            if (p.equals(selectedPos)) {
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

        // 展開ボタンの描画
        int btnX = x + width - 30;
        int btnY = y + 10;
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

    public boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) {
        int btnX = x + width - 30;
        int btnY = y + 10;
        if (mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= btnY && mouseY <= btnY + 20) {
            if (button == 0) {
                isExploded = !isExploded;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (button == 0) this.isDraggingView = true;
        if (button == 0 || button == 1) {
            BlockPos hitPos = pickBlock(x, y, width, height, mouseX, mouseY);
            if (hitPos == null) return true; // 背景クリックとして消費

            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            onBlockSelected.accept(hitPos);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) this.isDraggingView = false;
        return false;
    }

    public boolean mouseDragged(double dragX, double dragY, int button) {
        if (this.isDraggingView && button == 0) {
            this.yaw += (float) dragX;
            this.pitch = Math.clamp(this.pitch + (float) dragY, -90f, 90f);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double scrollY) {
        this.renderScale = Math.clamp(this.renderScale + (float) (scrollY * 2.0f), 5f, 100f);
        return true;
    }

    public boolean isExploded() {
        return isExploded;
    }

    private BlockPos pickBlock(int x, int y, int width, int height, double mouseX, double mouseY) {
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return null;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        BlockPos hitPos = null;
        float maxZ = -Float.MAX_VALUE;

        for (BlockPos p : parts) {
            Matrix4f matrix = new Matrix4f();
            matrix.translate(x + (width / 2.0f), y + (height / 2.0f), 200);
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