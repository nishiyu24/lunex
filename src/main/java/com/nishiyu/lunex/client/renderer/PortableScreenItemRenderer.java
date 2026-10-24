package com.nishiyu.lunex.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.enderframelib.client.renderer.WebItemRendererHelper;
import com.nishiyu.lunex.menu.PortableScreenScreen;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public class PortableScreenItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.parse("lunex:textures/item/portable_screen.png");

    public PortableScreenItemRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher, EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    private void drawQuad(VertexConsumer vc, Matrix4f matrix,
                          float x1, float y1, float z1, float u1, float v1,
                          float x2, float y2, float z2, float u2, float v2,
                          float x3, float y3, float z3, float u3, float v3,
                          float x4, float y4, float z4, float u4, float v4,
                          int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(matrix, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1 / 16f, v1 / 16f).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(matrix, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2 / 16f, v2 / 16f).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(matrix, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3 / 16f, v3 / 16f).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(matrix, x4, y4, z4).setColor(255, 255, 255, 255).setUv(u4 / 16f, v4 / 16f).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }

    private void drawJSONBox(VertexConsumer vc, Matrix4f matrix, int light, int overlay) {
        float minX = 8 / 16f, maxX = 9 / 16f;
        float minY = 0 / 16f, maxY = 8 / 16f;
        float minZ = 5 / 16f, maxZ = 11 / 16f;

        drawQuad(vc, matrix, minX, minY, minZ, 12, 8, minX, minY, maxZ, 6, 8, minX, maxY, maxZ, 6, 0, minX, maxY, minZ, 12, 0, light, overlay, -1, 0, 0);
        drawQuad(vc, matrix, maxX, minY, maxZ, 6, 8, maxX, minY, minZ, 0, 8, maxX, maxY, minZ, 0, 0, maxX, maxY, maxZ, 6, 0, light, overlay, 1, 0, 0);
        drawQuad(vc, matrix, maxX, minY, minZ, 1, 16, minX, minY, minZ, 0, 16, minX, maxY, minZ, 0, 8, maxX, maxY, minZ, 1, 8, light, overlay, 0, 0, -1);
        drawQuad(vc, matrix, minX, minY, maxZ, 2, 16, maxX, minY, maxZ, 1, 16, maxX, maxY, maxZ, 1, 8, minX, maxY, maxZ, 2, 8, light, overlay, 0, 0, 1);
        drawQuad(vc, matrix, minX, maxY, maxZ, 3, 14, maxX, maxY, maxZ, 2, 14, maxX, maxY, minZ, 2, 8, minX, maxY, minZ, 3, 8, light, overlay, 0, 1, 0);
        drawQuad(vc, matrix, minX, minY, minZ, 4, 8, maxX, minY, minZ, 3, 8, maxX, minY, maxZ, 3, 14, minX, minY, maxZ, 4, 14, light, overlay, 0, -1, 0);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        WebItemRendererHelper.applyFirstPersonHandShift(stack, displayContext, poseStack);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        drawJSONBox(vc, matrix, packedLight, packedOverlay);

        WebItemRendererHelper.renderWebOnItem(
                stack, displayContext,
                poseStack, buffer, packedLight, packedOverlay,
                (int) PortableScreenScreen.VIRTUAL_WIDTH, (int) PortableScreenScreen.VIRTUAL_HEIGHT,
                0.499f, 0.5f, 5f / 16f,
                -90f, 0.375f
        );

        poseStack.popPose();
    }
}