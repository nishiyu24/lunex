package com.nishiyu.lunex.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.ScreenBlock;
import com.nishiyu.lunex.block.SimpleMachineBlock;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.data.ModelData;

public class MachineFrameRenderer<T extends BlockEntity & IMainframePart> implements BlockEntityRenderer<T> {

    private static final ResourceLocation TEX_BASE_SIDE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "block/base_machine_side");
    private static final ResourceLocation TEX_ADVANCED_FRONT = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "block/advanced_machine_front");
    private static final ResourceLocation TEX_BORDER = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "block/machine_border");

    public MachineFrameRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockState state = blockEntity.getBlockState();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        // 中身ブロックの描画（擬態・アダプター用）
        BlockState innerState = null;
        if (blockEntity instanceof ProbeBlockEntity probe && probe.isDetected) {
            innerState = probe.disguiseState;
        } else if (blockEntity instanceof MainframeAdapterBlockEntity adapter) {
            innerState = adapter.getOriginalState();
        }

        if (innerState != null && !innerState.isAir()) {
            poseStack.pushPose();
            BakedModel innerModel = dispatcher.getBlockModel(innerState);
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(), buffer.getBuffer(RenderType.cutout()), innerState, innerModel, 1.0f, 1.0f, 1.0f, packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout()
            );
            poseStack.popPose();
            if (blockEntity instanceof ProbeBlockEntity) return;
        }

        // ★修正: masterPos等のBlockEntity内部データへの依存を完全に排除し、
        // 確実なBlockStateの「assembled」プロパティのみで合体判定を行うように戻しました
        boolean isAssembled = getBooleanProperty(state, "assembled");

        poseStack.pushPose();
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.cutout());
        PoseStack.Pose poseEntry = poseStack.last();

        Direction screenExcludeDir = null;
        if (state.getBlock() instanceof ScreenBlock) {
            screenExcludeDir = state.getValue(ScreenBlock.FACING);
        }

        TextureAtlasSprite borderSprite = getSprite(TEX_BORDER);
        TextureAtlasSprite baseSprite = getSprite(TEX_BASE_SIDE);
        TextureAtlasSprite advancedSprite = getSprite(TEX_ADVANCED_FRONT);

        float e = 2.0f / 16.0f; // 枠線の幅

        // オフセット（重なり防止）
        float baseOffset = 0.0001f;
        float borderOffset = 0.0002f;

        for (Direction dir : Direction.values()) {
            if (dir == screenExcludeDir) continue;

            BlockPos neighborPos = blockEntity.getBlockPos().relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            boolean isExposed = !Block.isShapeFullBlock(neighborState.getShape(level, neighborPos));

            if (isExposed) {
                int faceLight = LevelRenderer.getLightColor(level, neighborPos);
                float shade = getShade(dir);

                // 1. ベーステクスチャの描画
                if (isAssembled) {
                    TextureAtlasSprite spriteToUse = baseSprite;
                    if (blockEntity instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && state.hasProperty(SimpleMachineBlock.FACING) && dir == state.getValue(SimpleMachineBlock.FACING)) {
                        spriteToUse = advancedSprite;
                    }
                    drawQuadUV(vertexConsumer, poseEntry, dir, 0, 0, 1, 1, 0, 0, 1, 1, spriteToUse, faceLight, packedOverlay, baseOffset, shade);
                }

                // 2. ボーダー（枠線）の描画
                if (isAssembled) {
                    boolean hasUp = isConnectedFace(level, blockEntity.getBlockPos(), getLocalUp(dir));
                    boolean hasDown = isConnectedFace(level, blockEntity.getBlockPos(), getLocalDown(dir));
                    boolean hasLeft = isConnectedFace(level, blockEntity.getBlockPos(), getLocalLeft(dir));
                    boolean hasRight = isConnectedFace(level, blockEntity.getBlockPos(), getLocalRight(dir));

                    // 四辺の描画 (角を含まない辺の中心部分)
                    if (!hasUp) drawQuadUV(vertexConsumer, poseEntry, dir, e, 1-e, 1-e, 1, e, 0, 1-e, e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    if (!hasDown) drawQuadUV(vertexConsumer, poseEntry, dir, e, 0, 1-e, e, e, 1-e, 1-e, 1, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    if (!hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, e, e, 1-e, 0, e, e, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    if (!hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, e, 1, 1-e, 1-e, e, 1, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);

                    // 四隅の描画 (隣接状況に応じてUV座標を適切に切り出す)
                    if (!hasUp && !hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, 1-e, e, 1, 0, 0, e, e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (!hasUp && hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, 1-e, e, 1, e, 0, 1-e, e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (hasUp && !hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, 1-e, e, 1, 0, e, e, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);

                    if (!hasUp && !hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, 1-e, 1, 1, 1-e, 0, 1, e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (!hasUp && hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, 1-e, 1, 1, e, 0, 1-e, e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (hasUp && !hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, 1-e, 1, 1, 1-e, e, 1, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);

                    if (!hasDown && !hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, 0, e, e, 0, 1-e, e, 1, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (!hasDown && hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, 0, e, e, e, 1-e, 1-e, 1, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (hasDown && !hasLeft) drawQuadUV(vertexConsumer, poseEntry, dir, 0, 0, e, e, 0, e, e, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);

                    if (!hasDown && !hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, 0, 1, e, 1-e, 1-e, 1, 1, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (!hasDown && hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, 0, 1, e, e, 1-e, 1-e, 1, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    else if (hasDown && !hasRight) drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, 0, 1, e, 1-e, e, 1, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);

                } else {
                    // 未合体時は四辺を描画
                    drawQuadUV(vertexConsumer, poseEntry, dir, 0, 1-e, 1, 1, 0, 0, 1, e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    drawQuadUV(vertexConsumer, poseEntry, dir, 0, 0, 1, e, 0, 1-e, 1, 1, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    drawQuadUV(vertexConsumer, poseEntry, dir, 0, e, e, 1-e, 0, e, e, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                    drawQuadUV(vertexConsumer, poseEntry, dir, 1-e, e, 1, 1-e, 1-e, e, 1, 1-e, borderSprite, faceLight, packedOverlay, borderOffset, shade);
                }
            }
        }
        poseStack.popPose();
    }

    /**
     * ★修正: 隣のブロック判定からもBlockEntity(masterPos)の非同期データを参照するのをやめ、
     * 即座に反映されるBlockState(assembled)のみで判定します。
     */
    private boolean isConnectedFace(Level level, BlockPos myPos, Direction neighborDir) {
        BlockPos neighborPos = myPos.relative(neighborDir);
        BlockState neighborState = level.getBlockState(neighborPos);
        return getBooleanProperty(neighborState, "assembled");
    }

    private boolean getBooleanProperty(BlockState state, String name) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(name) && prop instanceof BooleanProperty boolProp) {
                return state.getValue(boolProp);
            }
        }
        return false;
    }

    private TextureAtlasSprite getSprite(ResourceLocation location) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(location);
    }

    private float getShade(Direction dir) {
        return switch (dir) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    private void drawQuadUV(VertexConsumer consumer, PoseStack.Pose poseEntry, Direction dir,
                            float lx0, float ly0, float lx1, float ly1,
                            float u0_logic, float v0_logic, float u1_logic, float v1_logic,
                            TextureAtlasSprite sprite, int light, int overlay, float offset, float shade) {
        float nx = dir.getStepX();
        float ny = dir.getStepY();
        float nz = dir.getStepZ();

        float atlas_u0 = sprite.getU0() + u0_logic * (sprite.getU1() - sprite.getU0());
        float atlas_u1 = sprite.getU0() + u1_logic * (sprite.getU1() - sprite.getU0());
        float atlas_v0 = sprite.getV0() + v0_logic * (sprite.getV1() - sprite.getV0());
        float atlas_v1 = sprite.getV0() + v1_logic * (sprite.getV1() - sprite.getV0());

        float[] p0 = transform(dir, lx0, ly1, offset);
        float[] p1 = transform(dir, lx0, ly0, offset);
        float[] p2 = transform(dir, lx1, ly0, offset);
        float[] p3 = transform(dir, lx1, ly1, offset);

        int color = (int) (255 * shade);

        addVertex(consumer, poseEntry, p0[0], p0[1], p0[2], atlas_u0, atlas_v0, nx, ny, nz, light, overlay, color);
        addVertex(consumer, poseEntry, p1[0], p1[1], p1[2], atlas_u0, atlas_v1, nx, ny, nz, light, overlay, color);
        addVertex(consumer, poseEntry, p2[0], p2[1], p2[2], atlas_u1, atlas_v1, nx, ny, nz, light, overlay, color);
        addVertex(consumer, poseEntry, p3[0], p3[1], p3[2], atlas_u1, atlas_v0, nx, ny, nz, light, overlay, color);
    }

    private float[] transform(Direction dir, float lx, float ly, float offset) {
        return switch(dir) {
            case NORTH -> new float[]{1 - lx, ly, -offset};
            case SOUTH -> new float[]{lx, ly, 1 + offset};
            case WEST -> new float[]{-offset, ly, lx};
            case EAST -> new float[]{1 + offset, ly, 1 - lx};
            case UP -> new float[]{lx, 1 + offset, 1 - ly};
            case DOWN -> new float[]{lx, -offset, ly};
        };
    }

    private void addVertex(VertexConsumer consumer, PoseStack.Pose poseEntry, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light, int overlay, int color) {
        consumer.addVertex(poseEntry.pose(), x, y, z)
                .setColor(color, color, color, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(poseEntry, nx, ny, nz);
    }

    private Direction getLocalUp(Direction dir) {
        return switch (dir) {
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
        };
    }
    private Direction getLocalDown(Direction dir) {
        return switch (dir) {
            case NORTH, SOUTH, WEST, EAST -> Direction.DOWN;
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
        };
    }
    private Direction getLocalLeft(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case EAST -> Direction.SOUTH;
            case UP, DOWN -> Direction.WEST;
        };
    }
    private Direction getLocalRight(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
            case UP, DOWN -> Direction.EAST;
        };
    }
}