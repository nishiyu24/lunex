package com.nishiyu.lunex.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.client.ClientScreenManager;
import com.nishiyu.lunex.menu.PortableScreenScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Matrix4f;

import java.util.List;

public class PortableScreenItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.parse("lunex:textures/item/portable_screen.png");

    public PortableScreenItemRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher, net.minecraft.client.model.geom.EntityModelSet entityModelSet) {
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
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        Minecraft mc = Minecraft.getInstance();

        boolean shouldRenderUI = true;

        if (tag.contains("RouterPos") && mc.player != null && mc.level != null) {
            net.minecraft.core.BlockPos routerPos = net.minecraft.core.BlockPos.of(tag.getLong("RouterPos"));
            String routerDim = tag.getString("RouterDim");
            double maxDist = tag.getDouble("RouterRange");
            boolean isSameDim = mc.level.dimension().location().toString().equals(routerDim);

            if (!isSameDim && maxDist != Double.MAX_VALUE) {
                shouldRenderUI = false;
            } else if (isSameDim && maxDist != Double.MAX_VALUE && mc.player.blockPosition().distSqr(routerPos) > (maxDist * maxDist)) {
                shouldRenderUI = false;
            }
        }

        poseStack.pushPose();

        if (mc.player != null && (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
            float pitch = mc.player.getXRot();
            if (Float.isNaN(pitch)) pitch = 0.0f;

            float lookDownAmount = Mth.clamp(pitch / 90.0f, 0.0f, 1.0f);
            float yShift = lookDownAmount * 0.3f;
            float zShift = 0.0f;

            ItemStack mainHand = mc.player.getMainHandItem();
            ItemStack offHand = mc.player.getOffhandItem();

            boolean holdingBoth = mainHand != null && offHand != null &&
                    mainHand.getItem() == stack.getItem() &&
                    offHand.getItem() == stack.getItem();

            if (holdingBoth) {
                if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) zShift = 0.08f;
                else if (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) zShift = -0.08f;
            }
            poseStack.translate(0, yShift, zShift);
        }

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        drawJSONBox(vc, matrix, packedLight, packedOverlay);

        if (shouldRenderUI && tag.contains("IPAddress")) {
            String ip = tag.getString("IPAddress");
            String networkId = tag.contains("NetworkId") ? tag.getUUID("NetworkId").toString() : "global";
            String screenKey = networkId + ":" + ip;

            com.nishiyu.lunex.client.ClientScreenInteractionManager.ensureSynced(screenKey);

            // ★ 修正: メニューを開いていなくてもPortableScreenの解像度に合致させるため、必要なら再計算を行う
            if (ClientScreenManager.getLastRootW(screenKey) != (int) PortableScreenScreen.VIRTUAL_WIDTH ||
                    ClientScreenManager.getLastRootH(screenKey) != (int) PortableScreenScreen.VIRTUAL_HEIGHT) {
                ClientScreenManager.recomputeLayout(screenKey, (int) PortableScreenScreen.VIRTUAL_WIDTH, (int) PortableScreenScreen.VIRTUAL_HEIGHT);
            }

            List<ScreenBlockEntity.UIElement> elements = ClientScreenManager.getElements(screenKey);

            if (elements != null && !elements.isEmpty()) {
                poseStack.pushPose();
                poseStack.translate(0.499f, 0.5f, 5f / 16f);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90f));

                float logicPixelW = PortableScreenScreen.VIRTUAL_WIDTH;
                float scale = 0.375f / logicPixelW;
                poseStack.scale(scale, -scale, scale);

                boolean playAudio = (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

                int logicWidthBlocks = (int) Math.ceil(PortableScreenScreen.VIRTUAL_WIDTH / ScreenBlockEntity.RESOLUTION);
                int logicHeightBlocks = (int) Math.ceil(PortableScreenScreen.VIRTUAL_HEIGHT / ScreenBlockEntity.RESOLUTION);

                ScreenRenderCore.renderElements(
                        elements, logicWidthBlocks, logicHeightBlocks,
                        poseStack, buffer, packedLight, packedOverlay,
                        true, true, true,
                        0.02f, 0.01f,
                        com.nishiyu.lunex.client.ClientScreenInteractionManager.getHoveredId(screenKey),
                        0x00000000, false,
                        mc.level,
                        playAudio, true, screenKey,
                        null,
                        null,
                        0
                );

                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }
}