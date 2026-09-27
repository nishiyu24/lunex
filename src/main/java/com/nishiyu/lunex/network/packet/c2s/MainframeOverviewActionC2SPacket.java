package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

                if (be instanceof DatabaseBlockEntity db) {
                    if ("set_priority".equals(packet.action())) {
                        try {
                            int priority = Integer.parseInt(packet.payload());
                            if (priority < 1 || priority > 10) priority = 1;
                            db.getPersistentData().putInt("Priority", priority);
                            db.setChanged();
                            level.sendBlockUpdated(db.getBlockPos(), db.getBlockState(), db.getBlockState(), 3);
                        } catch (NumberFormatException ignored) {}
                    }
                } else if (be instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity machine) {
                    if ("set_mainframe_tag".equals(packet.action())) {
                        machine.persistentData.putString("MainframeNetworkTag", packet.payload());
                        machine.setChanged();
                        level.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), 3);
                        com.nishiyu.lunex.mcnet.MCNetUtil.triggerNetworkUpdate(level, machine.getBlockPos());
                    }
                } else if (be instanceof ScreenBlockEntity screen) {
                    if ("set_screen_mode".equals(packet.action())) {
                        screen.getPersistentData().putString("DisplayMode", packet.payload());
                        screen.setChanged();
                        level.sendBlockUpdated(screen.getBlockPos(), screen.getBlockState(), screen.getBlockState(), 3);
                    } else if ("set_screen_filter".equals(packet.action())) {
                        screen.getPersistentData().putString("ScreenFilter", packet.payload());
                        screen.setChanged();
                        level.sendBlockUpdated(screen.getBlockPos(), screen.getBlockState(), screen.getBlockState(), 3);
                    }
                } else if (be instanceof ProbeBlockEntity probe) {
                    switch (packet.action()) {
                        case "toggle_active":
                            probe.isDetected = !probe.isDetected;
                            probe.setChanged();

                            BlockState state = probe.getBlockState();
                            // ★修正: 方向プロパティの上書きを排除し、ACTIVEだけを切り替える
                            if (state.hasProperty(com.nishiyu.lunex.block.ProbeBlock.ACTIVE)) {
                                state = state.setValue(com.nishiyu.lunex.block.ProbeBlock.ACTIVE, probe.isDetected);
                                level.setBlock(probe.getBlockPos(), state, 3);
                            }
                            level.sendBlockUpdated(probe.getBlockPos(), probe.getBlockState(), probe.getBlockState(), 3);
                            break;
                        case "toggle_mode":
                            String currentMode = probe.getPersistentData().getString("IOMode");
                            probe.getPersistentData().putString("IOMode", "OUT".equals(currentMode) ? "IN" : "OUT");
                            probe.setChanged();
                            level.sendBlockUpdated(probe.getBlockPos(), probe.getBlockState(), probe.getBlockState(), 3);
                            break;
                        case "set_nbt_filter":
                            probe.getPersistentData().putString("NBTFilter", packet.payload());
                            probe.setChanged();
                            level.sendBlockUpdated(probe.getBlockPos(), probe.getBlockState(), probe.getBlockState(), 3);
                            break;
                    }
                }
            }
        });
    }
}