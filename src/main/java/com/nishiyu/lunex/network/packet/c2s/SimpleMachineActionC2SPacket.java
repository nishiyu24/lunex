package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.PrinterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.frame.MainframeScanner;
import com.nishiyu.lunex.machine.frame.CoreMachineVMCache;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SimpleMachineActionC2SPacket(BlockPos pos, String action, String name, String payload) implements CustomPacketPayload {

    public static final Type<SimpleMachineActionC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "simple_machine_action"));

    public static final StreamCodec<FriendlyByteBuf, SimpleMachineActionC2SPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SimpleMachineActionC2SPacket::pos,
            ByteBufCodecs.STRING_UTF8, SimpleMachineActionC2SPacket::action,
            ByteBufCodecs.STRING_UTF8, SimpleMachineActionC2SPacket::name,
            ByteBufCodecs.STRING_UTF8, SimpleMachineActionC2SPacket::payload,
            SimpleMachineActionC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SimpleMachineActionC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(packet.pos());

                if (be instanceof SimpleMachineBlockEntity machineEntity) {
                    switch (packet.action()) {
                        case "execute":
                            if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM simpleVm) {
                                simpleVm.executeString(packet.payload());
                            }
                            break;
                        case "clear":
                            if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM simpleVm) {
                                simpleVm.terminalLog.clear();
                                simpleVm.syncClient();
                            }
                            break;
                        case "wipe":
                            if (machineEntity.getCore().machineId != null) {
                                CoreMachineVMCache.removeVM(machineEntity.getCore().machineId);
                                machineEntity.getCore().vm = CoreMachineVMCache.getOrCreateVM(machineEntity.getCore().machineId, machineEntity);
                                if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM newVm) {
                                    newVm.syncClient();
                                }
                            }
                            break;
                        case "help":
                            if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM simpleVm) {
                                simpleVm.terminalLog.add("§e--- Available Commands ---§r");
                                simpleVm.terminalLog.add("  /help     - Show this help message");
                                simpleVm.terminalLog.add("  /clear    - Clear the screen");
                                simpleVm.terminalLog.add("  /wipe     - Wipe memory and reboot VM");
                                simpleVm.terminalLog.add("  /assemble - Attempt to assemble the mainframe");
                                simpleVm.terminalLog.add("  /exit     - Close the terminal");
                                simpleVm.syncClient();
                            }
                            break;
                        case "assemble":
                            if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM simpleVm) {
                                if (machineEntity.isMainframeMaster) {
                                    simpleVm.terminalLog.add("§cMainframe is already assembled.§r");
                                } else {
                                    boolean success = MainframeScanner.attemptFormMainframe(level, packet.pos(), context.player());
                                    if (!success) {
                                        simpleVm.terminalLog.add("§cFailed to assemble mainframe. Invalid structure.§r");
                                    }
                                }
                                simpleVm.syncClient();
                            }
                            break;
                        case "unknown_cmd":
                            if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM simpleVm) {
                                simpleVm.terminalLog.add("§cUnknown command: " + packet.payload() + "§r");
                                simpleVm.syncClient();
                            }
                            break;
                        case "export":
                            if (machineEntity.getCore().vm instanceof CoreMachineServerLuaVM simpleVm) {
                                List<String> toExport = new ArrayList<>(simpleVm.successfulPrograms);
                                String finalCode = String.join("\n", toExport);

                                if (finalCode.isEmpty()) {
                                    simpleVm.terminalLog.add("§cNo valid program to export.§r");
                                    simpleVm.syncClient();
                                    break;
                                }

                                for (Direction dir : Direction.values()) {
                                    BlockEntity neighbor = level.getBlockEntity(packet.pos().relative(dir));
                                    if (neighbor instanceof PrinterBlockEntity printer) {
                                        if (printer.createDisc(packet.name(), finalCode)) {
                                            simpleVm.terminalLog.add("§a[Success] Program '" + packet.name() + "' exported to disk.§r");
                                            simpleVm.syncClient();
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                    }
                }
            }
        });
    }
}