package com.nishiyu.lunex.network.packet.s2c;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.network.LocalWebSocketServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FileContentS2CPacket(String content) implements CustomPacketPayload {

    public static final Type<FileContentS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "file_content"));

    public static final StreamCodec<FriendlyByteBuf, FileContentS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FileContentS2CPacket::content,
            FileContentS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            String targetProgram = LocalWebSocketServer.currentLoadedProgramName;
            String fileData = this.content();

            LocalWebSocketServer.handleProgramResponse(targetProgram, fileData);

            // ★変更: 毎秒のVM状態監視パケットの場合はログを出さない
            if (targetProgram != null && !targetProgram.startsWith("[STATE]")) {
                Lunex.LOGGER.info("[Packet] File content received and forwarded to Web Editor: " + targetProgram);
            }
        });
    }
}