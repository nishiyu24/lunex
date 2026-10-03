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
import net.minecraft.world.item.ItemStack;
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

                // 1. 対象ブロックに対応する MainframeComponentData を取得
                Block targetBlock = be.getBlockState().getBlock();
                if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                    targetBlock = adapter.getOriginalState().getBlock();
                }

                // 2. 登録されているアクションプロバイダを呼び出す
                MainframeComponentData data = MainframeComponentRegistry.get(targetBlock);
                if (data != null && data.getActionProvider() != null) {
                    @SuppressWarnings("unchecked")
                    IMainframeActionProvider<BlockEntity> provider = (IMainframeActionProvider<BlockEntity>) data.getActionProvider();
                    if (provider.handleAction(packet.action(), packet.payload(), be, level)) {
                        return; // ハンドラで処理が完了した場合はスキップ
                    }
                }

                // 3. メインフレームのコア機能 (SimpleMachineBlockEntity) に対する標準パケット処理
                if (be instanceof SimpleMachineBlockEntity machine) {

                    if ("storage_click".equals(packet.action())) {
                        String[] parts = packet.payload().split(":");
                        if (parts.length == 2) {
                            try {
                                int index = Integer.parseInt(parts[0]);
                                int button = Integer.parseInt(parts[1]);
                                ItemStack carried = player.containerMenu.getCarried();
                                com.nishiyu.lunex.machine.MainframeItemHandler handler = machine.mainframeStorage;

                                if (index >= 0 && index < handler.getStacks().size()) {
                                    ItemStack target = handler.getStacks().get(index);
                                    if (carried.isEmpty()) {
                                        int extractAmount = (button == 0) ? target.getMaxStackSize() : (target.getCount() + 1) / 2;
                                        ItemStack extracted = handler.extractItem(index, extractAmount, false);
                                        player.containerMenu.setCarried(extracted);
                                    } else {
                                        if (ItemStack.isSameItemSameComponents(carried, target)) {
                                            int insertAmount = (button == 0) ? carried.getCount() : 1;
                                            ItemStack toInsert = carried.copyWithCount(insertAmount);
                                            ItemStack remainder = handler.insertItem(0, toInsert, false);
                                            carried.shrink(insertAmount - remainder.getCount());
                                            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                                        }
                                    }
                                } else {
                                    if (!carried.isEmpty()) {
                                        int insertAmount = (button == 0) ? carried.getCount() : 1;
                                        ItemStack toInsert = carried.copyWithCount(insertAmount);
                                        ItemStack remainder = handler.insertItem(0, toInsert, false);
                                        carried.shrink(insertAmount - remainder.getCount());
                                        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                                    }
                                }
                                player.containerMenu.broadcastChanges();
                                machine.setChanged();
                            } catch (Exception ignored) {}
                        }
                    }

                    if ("set_mainframe_tag".equals(packet.action())) {
                        machine.persistentData.putString("MainframeNetworkTag", packet.payload());
                        machine.setChanged();
                        level.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), 3);
                        com.nishiyu.lunex.mcnet.MCNetUtil.triggerNetworkUpdate(level, machine.getBlockPos());
                    }

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