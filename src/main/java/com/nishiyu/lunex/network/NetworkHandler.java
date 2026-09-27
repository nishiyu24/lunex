package com.nishiyu.lunex.network;

import com.nishiyu.lunex.network.packet.c2s.*;
import com.nishiyu.lunex.network.packet.s2c.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.0");

        registrar.playToServer(SubscribeC2SPacket.TYPE, SubscribeC2SPacket.STREAM_CODEC, SubscribeC2SPacket::handle);
        registrar.playToServer(AppMessageC2SPacket.TYPE, AppMessageC2SPacket.STREAM_CODEC, AppMessageC2SPacket::handle);
        registrar.playToServer(ProbeUpdateC2SPacket.TYPE, ProbeUpdateC2SPacket.STREAM_CODEC, ProbeUpdateC2SPacket::handle);
        registrar.playToServer(AssembleMachineC2SPacket.TYPE, AssembleMachineC2SPacket.STREAM_CODEC, AssembleMachineC2SPacket::handle);
        registrar.playToServer(SimpleMachineActionC2SPacket.TYPE, SimpleMachineActionC2SPacket.STREAM_CODEC, SimpleMachineActionC2SPacket::handle);
        registrar.playToServer(MainframeOverviewActionC2SPacket.TYPE, MainframeOverviewActionC2SPacket.STREAM_CODEC, MainframeOverviewActionC2SPacket::handle);

        registrar.playToClient(ErrorToastS2CPacket.TYPE, ErrorToastS2CPacket.STREAM_CODEC, ErrorToastS2CPacket::handle);
        registrar.playToClient(AppMessageS2CPacket.TYPE, AppMessageS2CPacket.STREAM_CODEC, AppMessageS2CPacket::handle);
        registrar.playToClient(FileContentS2CPacket.TYPE, FileContentS2CPacket.STREAM_CODEC, FileContentS2CPacket::handle);
        registrar.playToClient(PubSubUpdateS2CPacket.TYPE, PubSubUpdateS2CPacket.STREAM_CODEC, PubSubUpdateS2CPacket::handle);
        registrar.playToClient(ItemFileListS2CPacket.TYPE, ItemFileListS2CPacket.STREAM_CODEC, ItemFileListS2CPacket::handle);
    }
}