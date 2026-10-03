package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeExtension;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MainframeOverviewActionC2SPacket(BlockPos pos, String action, String payload) implements CustomPacketPayload {

    public static final Type<MainframeOverviewActionC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "mainframe_overview_action"));

    public static final StreamCodec<FriendlyByteBuf, MainframeOverviewActionC2SPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MainframeOverviewActionC2SPacket::pos,
            ByteBufCodecs.STRING_UTF8, MainframeOverviewActionC2SPacket::action,
            ByteBufCodecs.STRING_UTF8, MainframeOverviewActionC2SPacket::payload,
            MainframeOverviewActionC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MainframeOverviewActionC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(packet.pos());
                if (be == null) return;

                // 1. UI専用のアクションハンドラがあればそちらに処理を委譲（クライアント側UIから送られた固有のアクション等）
                IMainframeUIExtension<BlockEntity> uiExt = MainframeUIRegistry.get(be);
                if (uiExt != null) {
                    boolean handled = uiExt.handleAction(packet.action(), packet.payload(), be, level);
                    if (handled) return; // UI側で処理が完了した場合は終了
                }

                // 2. SimpleMachine本体のアクション処理
                if (be instanceof SimpleMachineBlockEntity machine) {
                    if ("set_mainframe_tag".equals(packet.action())) {
                        machine.persistentData.putString("MainframeNetworkTag", packet.payload());
                        machine.setChanged();
                        level.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), 3);
                        com.nishiyu.lunex.mcnet.MCNetUtil.triggerNetworkUpdate(level, machine.getBlockPos());
                        return;
                    }

                    // バックエンド用の拡張機能（IMainframeExtension）にアクションを伝播
                    if (machine.isMainframeMaster) {
                        for (IMainframeExtension ext : machine.extensions.values()) {
                            ext.onActionReceived(packet.action(), packet.payload(), machine);
                        }
                    }
                }
            }
        });
    }
}