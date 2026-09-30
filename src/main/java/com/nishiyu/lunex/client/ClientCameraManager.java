package com.nishiyu.lunex.client;

import com.nishiyu.lunex.Lunex;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@EventBusSubscriber(modid = Lunex.MODID, value = Dist.CLIENT)
public class ClientCameraManager {

    private static boolean isViewingCamera = false;
    private static Entity originalCamera = null;
    private static Entity currentCameraEntity = null;

    /**
     * 監視カメラの視点へ切り替える
     * @param cameraEntity カメラの位置に存在するダミーエンティティ（ArmorStandなど）
     */
    public static void startViewingCamera(Entity cameraEntity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && cameraEntity != null) {
            isViewingCamera = true;
            originalCamera = mc.getCameraEntity();
            currentCameraEntity = cameraEntity;

            mc.setCameraEntity(cameraEntity);
            mc.player.displayClientMessage(Component.literal("📹 監視カメラへ接続しました。ESCキーで切断します。"), true);
        }
    }

    /**
     * 監視カメラの視点を解除し、プレイヤーに戻る
     */
    public static void stopViewingCamera() {
        if (!isViewingCamera) return;

        Minecraft mc = Minecraft.getInstance();
        if (originalCamera != null && originalCamera.isAlive()) {
            mc.setCameraEntity(originalCamera);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }

        isViewingCamera = false;
        currentCameraEntity = null;
        originalCamera = null;

        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("🔌 監視カメラから切断しました。"), true);
        }
    }

    // =========================================
    // イベントハンドラ
    // =========================================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!isViewingCamera) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stopViewingCamera();
            return;
        }

        // カメラエンティティが破壊・消滅した場合は強制的に視点を戻す
        if (currentCameraEntity != null && currentCameraEntity.isRemoved()) {
            stopViewingCamera();
            return;
        }

        // 監視中は移動やアイテム使用のキー入力をすべて無効化する
        for (KeyMapping keyMapping : mc.options.keyMappings) {
            if (keyMapping == mc.options.keyUp || keyMapping == mc.options.keyDown ||
                    keyMapping == mc.options.keyLeft || keyMapping == mc.options.keyRight ||
                    keyMapping == mc.options.keyJump || keyMapping == mc.options.keyShift ||
                    keyMapping == mc.options.keySprint || keyMapping == mc.options.keyInventory ||
                    keyMapping == mc.options.keyUse || keyMapping == mc.options.keyAttack) {
                while (keyMapping.consumeClick()) {}
                keyMapping.setDown(false);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (isViewingCamera) {
            // ESCキーなどでメニューが開こうとした瞬間にキャンセルし、カメラを切断する
            stopViewingCamera();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isViewingCamera || Minecraft.getInstance().options.hideGui) return;

        var guiGraphics = event.getGuiGraphics();
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        // 監視カメラ特有のシネマティックな上下の黒帯
        int barHeight = height / 8;
        guiGraphics.fill(0, 0, width, barHeight, 0xFF000000);
        guiGraphics.fill(0, height - barHeight, width, height, 0xFF000000);

        // 現在時刻の取得
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));

        // 点滅するRECマーク
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (blink) {
            guiGraphics.drawString(Minecraft.getInstance().font, "● REC", 10, 10, 0xFFFF0000, true);
        }

        // CCTV風のテキスト描画
        guiGraphics.drawString(Minecraft.getInstance().font, "CAMERA-01 [LIVE]", 10, 25, 0xFFFFFFFF, true);
        guiGraphics.drawString(Minecraft.getInstance().font, timeStr, width - Minecraft.getInstance().font.width(timeStr) - 10, 10, 0xFFFFFFFF, true);

        guiGraphics.drawString(Minecraft.getInstance().font, "[ESC] Disconnect", 10, height - 15, 0xFFAAAAAA, true);
    }
}