package com.nishiyu.lunex.network.packet.s2c;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.client.ClientScreenManager;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsScreen;
import com.nishiyu.lunex.menu.MachineSettings.MachineSettingsScreen;
import com.nishiyu.lunex.program.client.ClientScriptManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AppMessageS2CPacket(String sessionId, String action, CompoundTag payload) implements CustomPacketPayload {
    public static final Type<AppMessageS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "app_msg_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AppMessageS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AppMessageS2CPacket::sessionId,
            ByteBufCodecs.STRING_UTF8, AppMessageS2CPacket::action,
            ByteBufCodecs.COMPOUND_TAG, AppMessageS2CPacket::payload,
            AppMessageS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if ("dom_sync".equals(action)) {
                ClientScreenManager.handleSyncPacket(sessionId, payload);
            } else if ("fetch_response".equals(action)) {
                ClientScriptManager.handleFetchResponse(payload.getString("requestId"), payload.getString("json"));
            } else if ("server_event".equals(action)) {
                ClientScriptManager.handleServerEvent(sessionId, payload.getString("eventName"), payload.getString("json"));
            } else if ("file_content".equals(action)) {
                String programName = payload.getString("programName");
                String content = payload.getString("content");

                // ★フロントから読み込めない問題への対策
                // もしパケットにファイル名が含まれていなかった場合、WebSocket側が待機している名前を強制補完します
                if (programName == null || programName.isEmpty()) {
                    programName = com.nishiyu.lunex.network.LocalWebSocketServer.currentLoadedProgramName;
                }

                com.nishiyu.lunex.network.LocalWebSocketServer.handleProgramResponse(programName, content);

            } else if ("file_list".equals(action)) {
                java.util.List<String> list = new java.util.ArrayList<>();
                net.minecraft.nbt.ListTag nbtList = payload.getList("files", net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < nbtList.size(); i++) {
                    list.add(nbtList.getString(i));
                }

                if (net.minecraft.client.Minecraft.getInstance().screen instanceof BioEntitySettingsScreen bioScreen) {
                    bioScreen.receiveItemFiles(list);
                } else if (net.minecraft.client.Minecraft.getInstance().screen instanceof MachineSettingsScreen machineScreen) {
                    machineScreen.receiveItemFiles(list);
                }

            } else if ("bio_info_sync".equals(action)) {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof BioEntitySettingsScreen bioScreen) {
                    bioScreen.receiveBioInfo(payload);
                }
            }
        });
    }
}