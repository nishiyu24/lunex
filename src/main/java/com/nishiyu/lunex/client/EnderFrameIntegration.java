package com.nishiyu.lunex.client;

import com.nishiyu.enderframelib.client.ClientScreenInteractionManager;
import com.nishiyu.enderframelib.client.ClientWebManager;
import com.nishiyu.enderframelib.client.binding.ClientPubSubManager;
import com.nishiyu.enderframelib.client.binding.DeclarativeBindingManager;
import com.nishiyu.enderframelib.client.renderer.WebHudRenderer;
import com.nishiyu.enderframelib.client.renderer.WebMediaManager;
import com.nishiyu.enderframelib.core.dom.HtmlNode;
import com.nishiyu.enderframelib.network.EnderFrameNetwork;
import com.nishiyu.enderframelib.network.c2s.SubscribeC2SPacket;
import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.blockentity.SpeakerBlockEntity;
import com.nishiyu.lunex.program.client.ClientScriptManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class EnderFrameIntegration {

    public static void initialize() {
        EnderFrameNetwork.initCommonCallbacks();

        // 1. スクリプトやオーバーライドのコールバック
        ClientWebManager.scriptLoaderCallback = (sessionId, script) -> {
            ClientScriptManager.loadClientScript(sessionId, script);
        };
        ClientWebManager.sessionClearedCallback = (sessionId) -> {
            WebHudRenderer.clearSession(sessionId);
        };
        ClientWebManager.applyOverridesCallback = (sessionId) -> {
            ClientScreenInteractionManager.applyLocalOverrides(sessionId);
        };

        // クライアントLuaイベントの発火連携
        ClientScreenInteractionManager.clientEventCallback = (sessionId, elementId, eventName, actionName, value) -> {
            ClientScriptManager.triggerClientEvent(sessionId, actionName, value);
        };

        // ルーターの有効状態＋距離判定を接続
        ClientScreenInteractionManager.itemRangeValidator = (mc, stack) -> {
            if (!ClientScreenInteractionManager.defaultIsItemInRange(mc, stack)) return false;
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();
            if (!tag.contains("RouterPos")) return true;

            BlockPos routerPos = BlockPos.of(tag.getLong("RouterPos"));
            String routerDim = tag.getString("RouterDim");
            boolean isSameDim = mc.level != null && mc.level.dimension().location().toString().equals(routerDim);
            if (isSameDim && mc.level.isLoaded(routerPos)) {
                BlockEntity be = mc.level.getBlockEntity(routerPos);
                if (be instanceof SimpleMachineBlockEntity master) {
                    return master.isMainframeMaster && master.getCore() != null && master.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER);
                }
                return false;
            }
            return true;
        };

        // ARグラス装着時のみHUDを描画するようリゾルバを設定
        WebHudRenderer.activeHudSessionResolver = (mc) -> {
            if (mc.player == null || mc.options.hideGui) return null;
            ItemStack headItem = mc.player.getItemBySlot(EquipmentSlot.HEAD);
            if (headItem.getItem() != Lunex.AR_GLASSES.get()) return null;
            return WebHudRenderer.defaultResolveActiveHudSession(mc);
        };

        // 2. PubSub (サーバーへの購読パケット送信)
        ClientPubSubManager.subscriptionSender = (channel) -> {
            PacketDistributor.sendToServer(new SubscribeC2SPacket(channel));
        };

        // 3. ARグラス HUD/トラッカーの登録フック
        DeclarativeBindingManager.hudCallback = new DeclarativeBindingManager.IHudRegistrationCallback() {
            @Override
            public void registerHudTemplate(String sessionId, String containerId, String origDisplay, HtmlNode templateRoot, List<String> requireNbt) {
                WebHudRenderer.enableHudTemplate(sessionId, containerId, origDisplay, templateRoot, requireNbt);
            }

            @Override
            public void registerTrackerTemplate(String sessionId, HtmlNode templateNode, double radius, double yOffset, String targetType, List<String> requireNbt) {
                WebHudRenderer.setTrackerTemplate(sessionId, templateNode, radius, yOffset, targetType, requireNbt);
            }
        };

        // 4. 動画再生機能のMinecraft連携フック
        WebMediaManager.externalContext = new WebMediaManager.IExternalVideoContext() {
            @Override
            public boolean isYouTubeSupportEnabled() {
                return (Boolean) Config.ENABLE_YOUTUBE_SUPPORT.get();
            }

            @Override
            public List<String> getActiveSessionIdsFromHotbar() {
                Minecraft mc = Minecraft.getInstance();
                List<String> hotbarIps = new ArrayList<>();
                if (mc.player == null) return hotbarIps;

                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    extractSessionId(stack, hotbarIps);
                }
                ItemStack offhand = mc.player.getOffhandItem();
                extractSessionId(offhand, hotbarIps);

                return hotbarIps;
            }

            private void extractSessionId(ItemStack stack, List<String> list) {
                if (!stack.isEmpty() && stack.has(DataComponents.CUSTOM_DATA)) {
                    CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
                    if (tag.contains("IPAddress")) {
                        String ip = tag.getString("IPAddress");
                        String networkId = tag.contains("NetworkId") ? tag.getUUID("NetworkId").toString() : "global";
                        String sessionKey = networkId + ":" + ip;
                        if (!ip.isEmpty() && !list.contains(sessionKey)) list.add(sessionKey);
                    }
                }
            }

            @Override
            public boolean isScreenOrSpeaker(BlockPos pos) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || !mc.level.isLoaded(pos)) return false;
                BlockEntity be = mc.level.getBlockEntity(pos);
                return be instanceof ScreenBlockEntity || be instanceof SpeakerBlockEntity;
            }

            @Override
            public float getSpeakerMaxDistance(BlockPos pos) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (be instanceof SpeakerBlockEntity sbe) {
                        return sbe.getMaxDistance();
                    }
                }
                return 10.0f;
            }
        };
    }
}