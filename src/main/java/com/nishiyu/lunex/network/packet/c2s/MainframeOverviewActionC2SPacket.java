// MainframeOverviewActionC2SPacket.java
package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.api.MainframeComponentData;
import com.nishiyu.lunex.api.MainframeComponentRegistry;
import com.nishiyu.lunex.api.mainframe.IMainframeExtension;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MainframeOverviewActionC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(packet.pos());
                if (be == null) return;

                Block targetBlock = be.getBlockState().getBlock();
                if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                    targetBlock = adapter.getOriginalState().getBlock();
                }

                // 各種プロバイダによる処理の移譲
                MainframeComponentData data = MainframeComponentRegistry.get(targetBlock);
                if (data != null && data.getActionProvider() != null) {
                    @SuppressWarnings("unchecked")
                    IMainframeActionProvider<BlockEntity> provider = (IMainframeActionProvider<BlockEntity>) data.getActionProvider();
                    // 引数に player を渡す
                    if (provider.handleAction(packet.action(), packet.payload(), be, level, player)) {
                        return; // ハンドラで処理が完了した場合はスキップ
                    }
                }

                // 拡張機能(Extension)へのブロードキャスト処理
                if (be instanceof SimpleMachineBlockEntity machine && machine.isMainframeMaster) {
                    for (IMainframeExtension ext : machine.extensions.values()) {
                        ext.onActionReceived(packet.action(), packet.payload(), machine);
                    }
                }
            }
        });
    }
}