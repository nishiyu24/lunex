package com.nishiyu.lunex.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.nishiyu.lunex.server.ServerPubSubManager;

import java.util.List;

public record SubscribeC2SPacket(
        String channel,
        boolean needsHud,
        boolean needsTracker,
        double radius,
        String targetType,
        List<String> hudNbtPaths,
        List<String> trackerNbtPaths
) implements CustomPacketPayload {

    public static final Type<SubscribeC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lunex", "subscribe_pubsub"));

    // ★修正: ラムダ式で引数の順序 (buf, packet) を明示して型推論エラーを解決
    public static final StreamCodec<FriendlyByteBuf, SubscribeC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            SubscribeC2SPacket::new
    );

    public SubscribeC2SPacket(String channel) {
        this(channel, false, false, 0.0, "", List.of(), List.of());
    }

    public SubscribeC2SPacket(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readUtf(),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(FriendlyByteBuf::readUtf)
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(channel());
        buf.writeBoolean(needsHud());
        buf.writeBoolean(needsTracker());
        buf.writeDouble(radius());
        buf.writeUtf(targetType());
        buf.writeCollection(hudNbtPaths(), FriendlyByteBuf::writeUtf);
        buf.writeCollection(trackerNbtPaths(), FriendlyByteBuf::writeUtf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                if (channel() != null && !channel().isEmpty()) {
                    ServerPubSubManager.subscribe(serverPlayer, channel());
                }
                if (needsHud() || needsTracker()) {
                    ServerPubSubManager.updateArSubscription(serverPlayer, needsHud(), needsTracker(), radius(), targetType(), hudNbtPaths(), trackerNbtPaths());
                }
            }
        });
    }
}