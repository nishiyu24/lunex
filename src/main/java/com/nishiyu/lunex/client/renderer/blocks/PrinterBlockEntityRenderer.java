package com.nishiyu.lunex.client.renderer.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nishiyu.lunex.block.PrinterBlock;
import com.nishiyu.lunex.blockentity.PrinterBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {
    private final ItemRenderer itemRenderer;

    public PrinterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PrinterBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // プリンターの現在の向きを取得
        Direction direction = blockEntity.getBlockState().getValue(PrinterBlock.FACING);

        // 青い台座の高さ
        float height = 0.58f;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = blockEntity.itemHandler.getStackInSlot(i);

            if (!stack.isEmpty()) {
                poseStack.pushPose();

                // ブロックの中心へ移動
                poseStack.translate(0.5, height, 0.5);

                // ★修正箇所：+ 180.0f を追加してアイテムの前後を反転させる
                float rotation = -direction.toYRot() + 180.0f;
                poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

                // 3つのアイテムが重ならないよう、X軸（左右）にずらす
                float offsetX = (i - 1) * 0.25f;
                poseStack.translate(offsetX, 0, 0);

                // アイテムを台座の上に寝かせる（90度倒す）
                poseStack.mulPose(Axis.XP.rotationDegrees(90));

                // アイテムの表示サイズ
                poseStack.scale(0.4f, 0.4f, 0.4f);

                // 実際のアイテム描画処理
                this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 0);

                poseStack.popPose();
            }
        }
    }
}