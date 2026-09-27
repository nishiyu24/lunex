package com.nishiyu.lunex.client.renderer.blocks; // パッケージ名は環境に合わせてください

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = "lunex", value = Dist.CLIENT)
public class ChunkLoadRenderer {

    private static final Map<BlockPos, RenderData> activeRenderers = new HashMap<>();

    public static void toggleMachine(BlockPos pos, ChunkPos center, int radius) {
        if (activeRenderers.containsKey(pos)) {
            activeRenderers.remove(pos);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[ChunkLoad] チャンクの表示をOFFにしました"), false);
            }
        } else {
            activeRenderers.put(pos, new RenderData(center, radius));
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[ChunkLoad] チャンクの表示をONにしました"), false);
            }
        }
    }

    public static boolean isRendering(BlockPos pos) {
        return activeRenderers.containsKey(pos);
    }

    // ワールドから退出した際にリストをリセット（他ワールドへの描画引き継ぎを防止）
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        activeRenderers.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (activeRenderers.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 破壊時に消すための最低限の処理（表示ONになっているマシンのブロックが消滅したらリストから除外）
        activeRenderers.entrySet().removeIf(entry -> mc.level.getBlockEntity(entry.getKey()) == null);

        // 上記の除外処理でリストが空になった場合は描画をスキップ
        if (activeRenderers.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        var pose = poseStack.last().pose();
        var bufferSource = mc.renderBuffers().bufferSource();

        // 色と透明度の設定
        float r = 0.0f, g = 1.0f, b = 0.0f, a = 0.1f;

        for (RenderData data : activeRenderers.values()) {
            double minX = (data.center.x - data.radius) * 16;
            double minZ = (data.center.z - data.radius) * 16;
            double maxX = (data.center.x + data.radius) * 16 + 16;
            double maxZ = (data.center.z + data.radius) * 16 + 16;

            // プレイヤーへの追従を廃止し、ワールドの最小高度(底)から最大高度(空)に固定
            double minY = mc.level.getMinBuildHeight();
            double maxY = mc.level.getMaxBuildHeight();

            float rMinX = (float) (minX - cameraPos.x);
            float rMinY = (float) (minY - cameraPos.y);
            float rMinZ = (float) (minZ - cameraPos.z);
            float rMaxX = (float) (maxX - cameraPos.x);
            float rMaxY = (float) (maxY - cameraPos.y);
            float rMaxZ = (float) (maxZ - cameraPos.z);

            // 1. 外側から見える面 (反時計回り)
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugFilledBox());

            buffer.addVertex(pose, rMinX, rMinY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMaxY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMinY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMaxY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMinY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMaxY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMinY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMaxY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMinY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMaxY, rMinZ).setColor(r, g, b, a);

            bufferSource.endBatch(RenderType.debugFilledBox());

            // 2. 内側から見える面 (時計回り)
            buffer = bufferSource.getBuffer(RenderType.debugFilledBox());

            buffer.addVertex(pose, rMinX, rMinY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMaxY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMinY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMaxY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMinY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMaxY, rMaxZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMinY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMaxX, rMaxY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMinY, rMinZ).setColor(r, g, b, a);
            buffer.addVertex(pose, rMinX, rMaxY, rMinZ).setColor(r, g, b, a);

            bufferSource.endBatch(RenderType.debugFilledBox());
        }
    }

    private static class RenderData {
        ChunkPos center;
        int radius;

        RenderData(ChunkPos center, int radius) {
            this.center = center;
            this.radius = radius;
        }
    }
}