package com.nishiyu.lunex.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nishiyu.lunex.block.ScreenBlock;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.client.ClientScreenInteractionManager;
import com.nishiyu.lunex.client.ClientScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity> {

    public ScreenRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(ScreenBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return new AABB(pos).inflate(be.screenWidth + 1, be.screenHeight + 1, be.screenWidth + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull ScreenBlockEntity be) {
        return true;
    }

    @Override
    public void render(ScreenBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.isMaster) return;

        ClientMediaManager.linkScreenToSpeakers(be.getBlockPos(), be.linkedSpeakers);

        String sessionId = be.getSessionId();

        ClientScreenInteractionManager.ensureSynced(sessionId);

        List<ScreenBlockEntity.UIElement> elementsToRender = new ArrayList<>(ClientScreenManager.getElements(sessionId));

        if (elementsToRender.isEmpty() && be.lodColor == 0x00000000) return;

        Minecraft mc = Minecraft.getInstance();
        double camX = mc.gameRenderer.getMainCamera().getPosition().x;
        double camY = mc.gameRenderer.getMainCamera().getPosition().y;
        double camZ = mc.gameRenderer.getMainCamera().getPosition().z;
        double dx = be.getBlockPos().getX() + 0.5 - camX;
        double dy = be.getBlockPos().getY() + 0.5 - camY;
        double dz = be.getBlockPos().getZ() + 0.5 - camZ;
        double distSqr = dx * dx + dy * dy + dz * dz;

        boolean isLodMode = distSqr > 4096.0;
        boolean renderItems = distSqr < 1024.0, renderText = distSqr < 256.0, renderRounded = distSqr < 256.0;
        float zStep = distSqr > 1024.0 ? 0.25f : (distSqr > 256.0 ? 0.1f : 0.02f);
        float subZOffset = zStep * 0.5f;

        Direction facing = be.getBlockState().getValue(ScreenBlock.FACING);
        String hoveredId = ClientScreenInteractionManager.getHoveredId(sessionId);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, -0.5, 0.501);

        float scale = 1.0f / (float) ScreenBlockEntity.RESOLUTION;
        poseStack.scale(scale, -scale, scale);
        poseStack.translate(0, -ScreenBlockEntity.RESOLUTION * be.screenHeight, 0);

        ScreenRenderCore.renderElements(
                elementsToRender, be.screenWidth, be.screenHeight,
                poseStack, buffer, packedLight, packedOverlay,
                renderItems, renderText, renderRounded,
                zStep, subZOffset, hoveredId, be.lodColor, isLodMode,
                be.getLevel(), true, false, sessionId,
                be.getBlockPos(),
                null,
                0
        );

        poseStack.popPose();
    }
}