package com.nishiyu.lunex.client.renderer.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class TurtleBotRenderer implements BlockEntityRenderer<TurtleBotBlockEntity> {

    public TurtleBotRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TurtleBotBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {

        float progress = 0;
        if (blockEntity.animDurationMs > 0 && blockEntity.clientAnimStartTime > 0) {
            long elapsed = System.currentTimeMillis() - blockEntity.clientAnimStartTime;
            float rawProgress = Math.min(1.0f, (float) elapsed / blockEntity.animDurationMs);
            // Ease-in-out Sine による極めて滑らかなアニメーション補間
            progress = (float) (0.5 - 0.5 * Math.cos(rawProgress * Math.PI));
        }

        poseStack.pushPose();

        float x = blockEntity.renderOffsetX * progress;
        float y = blockEntity.renderOffsetY * progress;
        float z = blockEntity.renderOffsetZ * progress;
        poseStack.translate(x, y, z);

        poseStack.translate(0.5, 0.5, 0.5);
        float rot = blockEntity.renderRotDiff * progress;
        if (rot != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot));
        }
        poseStack.translate(-0.5, -0.5, -0.5);

        BlockState state = blockEntity.getBlockState();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);

        dispatcher.getModelRenderer().renderModel(
                poseStack.last(),
                buffer.getBuffer(RenderType.cutout()),
                state,
                model,
                1.0F, 1.0F, 1.0F,
                packedLight,
                packedOverlay,
                ModelData.EMPTY,
                RenderType.cutout()
        );

        poseStack.popPose();
    }
}