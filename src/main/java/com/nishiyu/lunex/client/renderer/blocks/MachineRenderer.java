package com.nishiyu.lunex.client.renderer.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.AdvancedMachineBlock;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MachineRenderer implements BlockEntityRenderer<AdvancedMachineBlockEntity> {

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AdvancedMachineBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.disguiseState != null) {
            Player player = Minecraft.getInstance().player;
            boolean hasPaintball = player != null && (player.getMainHandItem().is(Lunex.PAINTBALL.get()) || player.getOffhandItem().is(Lunex.PAINTBALL.get()));

            BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

            if (hasPaintball) {
                BlockState originalState = blockEntity.getBlockState().setValue(AdvancedMachineBlock.IS_DISGUISED, false);
                blockRenderer.renderSingleBlock(originalState, poseStack, bufferSource, packedLight, packedOverlay, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            } else {
                BlockState state = blockEntity.disguiseState;
                net.minecraft.client.resources.model.BakedModel model = blockRenderer.getBlockModel(state);
                RenderType renderType = RenderType.cutout();
                VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

                blockRenderer.getModelRenderer().tesselateBlock(
                        Objects.requireNonNull(blockEntity.getLevel()),
                        model,
                        state,
                        blockEntity.getBlockPos(),
                        poseStack,
                        vertexConsumer,
                        false, // ▼ 修正
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