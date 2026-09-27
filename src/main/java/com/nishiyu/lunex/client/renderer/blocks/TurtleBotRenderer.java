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

        // 1. アニメーションの進行度（1.0 〜 0.0）を計算
        float progress = 0;
        if (blockEntity.renderOffsetX != 0 || blockEntity.renderOffsetY != 0 || blockEntity.renderOffsetZ != 0 || blockEntity.renderRotDiff != 0) {
            long elapsed = System.currentTimeMillis() - blockEntity.clientAnimStartTime;
            long duration = blockEntity.getAnimationDurationMs();

            if (elapsed < duration) {
                // 【修正】経過時間の割合（0.0 から 1.0 へ増加する）
                progress = (float) elapsed / duration;
            } else {
                // 【修正】アニメーション完了後は、サーバーが物理的にブロックを移動させるまで移動先に留まる
                progress = 1.0f;
            }
        }

        poseStack.pushPose();

        // 2. 移動アニメーション（ズレを適用）
        float x = blockEntity.renderOffsetX * progress;
        float y = blockEntity.renderOffsetY * progress;
        float z = blockEntity.renderOffsetZ * progress;
        poseStack.translate(x, y, z);

        // 3. 回転アニメーション（ブロックの中心を軸にして回す）
        poseStack.translate(0.5, 0.5, 0.5);
        float rot = blockEntity.renderRotDiff * progress;
        if (rot != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot));
        }
        poseStack.translate(-0.5, -0.5, -0.5);

        // 4. ブロックモデルを直接描画する
        BlockState state = blockEntity.getBlockState();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        // モデルデータを取得
        BakedModel model = dispatcher.getBlockModel(state);

        // 無視されないように、モデルデータを強制的に描画
        dispatcher.getModelRenderer().renderModel(
                poseStack.last(),
                buffer.getBuffer(RenderType.cutout()), // 透過テクスチャに対応
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