package com.nishiyu.lunex.client.renderer.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public class ProbeRenderer implements BlockEntityRenderer<ProbeBlockEntity> {

    public ProbeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ProbeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.disguiseState != null) {
            Player player = Minecraft.getInstance().player;
            boolean hasPaintball = player != null && (player.getMainHandItem().is(Lunex.PAINTBALL.get()) || player.getOffhandItem().is(Lunex.PAINTBALL.get()));

            BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

            if (hasPaintball) {
                // 透視時: noOcclusion() のおかげで packedLight が正常になり、黒くならずに確実に見えます
                BlockState originalState = blockEntity.getBlockState().setValue(com.nishiyu.lunex.block.ProbeBlock.IS_DISGUISED, false);
                blockRenderer.renderSingleBlock(originalState, poseStack, bufferSource, packedLight, packedOverlay, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            } else {
                // 偽装時: tesselateBlock を使いつつ、透明化(カリング)を防ぐために false を指定
                BlockState state = blockEntity.disguiseState;
                net.minecraft.client.resources.model.BakedModel model = blockRenderer.getBlockModel(state);
                RenderType renderType = RenderType.cutout();
                VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

                blockRenderer.getModelRenderer().tesselateBlock(
                        blockEntity.getLevel(),
                        model,
                        state,
                        blockEntity.getBlockPos(),
                        poseStack,
                        vertexConsumer,
                        false, // ▼ 修正: ここを false にすることで透明化バグを完全に防止
                        blockEntity.getLevel().random,
                        state.getSeed(blockEntity.getBlockPos()),
                        packedOverlay,
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                        renderType
                );
            }
        }
    }
}