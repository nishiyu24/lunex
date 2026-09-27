package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.ProbeBlock;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ProbeUpdateC2SPacket(BlockPos pos, Action action, Direction direction, String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProbeUpdateC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "probe_update"));

    public enum Action {
        TOGGLE_VISIBLE,
        TOGGLE_FACE,
        SET_NAME
    }

    public static final StreamCodec<FriendlyByteBuf, ProbeUpdateC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeEnum(packet.action);
                switch (packet.action) {
                    case TOGGLE_FACE -> buf.writeEnum(packet.direction);
                    case SET_NAME -> buf.writeUtf(packet.name);
                    case TOGGLE_VISIBLE -> {} // 追加データなし
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                Action action = buf.readEnum(Action.class);
                Direction dir = action == Action.TOGGLE_FACE ? buf.readEnum(Direction.class) : null;
                String name = action == Action.SET_NAME ? buf.readUtf() : "";
                return new ProbeUpdateC2SPacket(pos, action, dir, name);
            }
    );

    public static void handle(ProbeUpdateC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                BlockPos pos = packet.pos();

                // すべての処理に対してプレイヤーの近くにあるブロックか確認（セキュリティ対策）
                if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
                    return;
                }

                BlockEntity be = level.getBlockEntity(pos);
                switch (packet.action()) {
                    case TOGGLE_VISIBLE -> {
                        if (be instanceof ProbeBlockEntity probe) {
                            probe.isDetected = !probe.isDetected;
                            probe.setChanged();

                            // ★追加・修正: ACTIVEプロパティを更新し、ブロックの同期を行う
                            BlockState state = level.getBlockState(pos);
                            if (state.hasProperty(ProbeBlock.ACTIVE)) {
                                state = state.setValue(ProbeBlock.ACTIVE, probe.isDetected);
                                level.setBlock(pos, state, 3);
                            }
                            level.sendBlockUpdated(pos, state, state, 3);
                        }
                    }
                    case TOGGLE_FACE -> {
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof ProbeBlock) {
                            BooleanProperty prop = ProbeBlock.getPropertyByDirection(packet.direction());
                            level.setBlockAndUpdate(pos, state.setValue(prop, !state.getValue(prop)));
                        }
                    }
                    case SET_NAME -> {
                        if (be instanceof ProbeBlockEntity probe) {
                            probe.setNetworkTag(packet.name());
                            probe.setChanged();

                            // ★修正: probe.sync(); の代わりにブロック更新処理を送信
                            BlockState state = level.getBlockState(pos);
                            level.sendBlockUpdated(pos, state, state, 3);
                        }
                    }
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}