package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RedPowerAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public RedPowerAPI() {}
    public RedPowerAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return MainframeConstants.API_RS; }

    @Override
    public String getRequiredFeature() { return MainframeConstants.FEATURE_PROBE; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new RedPowerAPI(vm);
    }

    @LuaFunction(
            value = "このデバイスから特定の面に対して、レッドストーン信号（0〜15）を出力します。",
            args = {"str:target", "num:power"},
            rets = {},
            isAsync = true
    )
    public void setRedstoneOutput(String targetStr, int power) {
        vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return null;

            RSTargetInfo info = resolveOutputTarget(targetStr);
            if (info != null && info.probe != null) {
                int clampedPower = Math.max(0, Math.min(15, power));
                Level level = machine.getLevel();

                if (info.direction != null) {
                    info.probe.redstoneOutputs.put(info.direction, clampedPower);
                } else {
                    for (Direction d : Direction.values()) {
                        info.probe.redstoneOutputs.put(d, clampedPower);
                    }
                }

                info.probe.sync();
                if (level != null) level.updateNeighborsAt(info.probe.getBlockPos(), info.probe.getBlockState().getBlock());
            }
            return null;
        });
    }

    @LuaFunction(
            value = "このデバイスが受けているレッドストーン信号の強さ（0〜15）を取得します。",
            args = {"str:target"},
            rets = {"num:power"},
            isAsync = false
    )
    public int getAnalogRedstoneInput(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null || machine.getLevel() == null || targetStr == null) return 0;
            Level level = machine.getLevel();

            if (targetStr.contains(":")) {
                String[] parts = targetStr.split(":", 2);
                BlockPos basePos = machine.resolveDevice(parts[0]);
                if (basePos != null) {
                    Direction dir = parseDirection(parts[1]);
                    if (dir != null) {
                        return level.getSignal(basePos.relative(dir), dir);
                    }
                }
            }

            BlockPos targetPos = machine.resolveDevice(targetStr);
            if (targetPos != null) {
                return level.getBestNeighborSignal(targetPos);
            }

            return 0;
        });
    }

    @LuaFunction(
            value = "このデバイスがレッドストーン信号を受けているか（ON/OFF）を真偽値で取得します。",
            args = {"str:target"},
            rets = {"bool:isOn"},
            isAsync = false
    )
    public boolean getRedstoneInput(String targetStr) {
        return getAnalogRedstoneInput(targetStr) > 0;
    }

    private RSTargetInfo resolveOutputTarget(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || machine.getLevel() == null || targetStr == null) return null;
        Level level = machine.getLevel();

        if (targetStr.contains(":")) {
            String[] parts = targetStr.split(":", 2);
            BlockPos basePos = machine.resolveDevice(parts[0]);
            if (basePos != null) {
                Direction dir = parseDirection(parts[1]);
                if (dir != null) {
                    BlockEntity be = level.getBlockEntity(basePos);
                    if (be instanceof ProbeBlockEntity probe) {
                        return new RSTargetInfo(probe, dir);
                    }
                }
            }
            return null;
        }

        BlockPos resolvedPos = machine.resolveDevice(targetStr);
        if (resolvedPos != null) {
            BlockEntity be = level.getBlockEntity(resolvedPos);
            if (be instanceof ProbeBlockEntity probe) {
                return new RSTargetInfo(probe, null);
            }
        }

        return null;
    }

    private Direction parseDirection(String str) {
        if (str == null) return null;
        return switch (str.toLowerCase()) {
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    private record RSTargetInfo(ProbeBlockEntity probe, Direction direction) {}
}