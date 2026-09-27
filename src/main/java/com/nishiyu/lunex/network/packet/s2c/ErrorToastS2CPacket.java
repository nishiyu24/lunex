package com.nishiyu.lunex.network.packet.s2c;

import com.nishiyu.lunex.client.ClientToastHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.codec.ByteBufCodecs;

public record ErrorToastS2CPacket(String errorMessage) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ErrorToastS2CPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("lunex", "error_toast"));

    public static final StreamCodec<FriendlyByteBuf, ErrorToastS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ErrorToastS2CPacket::errorMessage,
            ErrorToastS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ErrorToastS2CPacket data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientToastHelper.showErrorToast(data.errorMessage());
        });
    }
}