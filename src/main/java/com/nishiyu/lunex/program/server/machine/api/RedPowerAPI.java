package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RedPowerAPI {
    private final ServerLuaVM vm;

    public RedPowerAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    @LuaFunction(
            value = "このデバイスから特定の面に対して、レッドストーン信号（0〜15）を出力します。",
            en = "Outputs a redstone signal (0-15) from this device to a specific face.",
            args = {"num:power"},
            rets = {},
            isAsync = true
    )
    public void setRedstoneOutput(String targetStr, int power) {
        vm.executeInMainThreadSync(() -> {
            RSTargetInfo info = resolveOutputTarget(targetStr);
            if (info != null) {
                int clampedPower = Math.max(0, Math.min(15, power));
                Level level = vm.hardware.getLevel();

                if (info.isProbe && info.probe != null) {
                    info.probe.redstoneOutputs.put(info.direction, clampedPower);
                    info.probe.sync();
                    level.updateNeighborsAt(info.probe.getBlockPos(), info.probe.getBlockState().getBlock());
                } else if (!info.isProbe) {
                    vm.hardware.redstoneOutputs.put(info.direction, clampedPower);
                    vm.hardware.sync();
                    level.updateNeighborsAt(vm.hardware.getBlockPos(), vm.hardware.getBlockState().getBlock());
                }
            }
            return null;
        });
    }

    @LuaFunction(
            value = "このデバイスが受けているレッドストーン信号の強さ（0〜15）を取得します。",
            en = "Gets the strength (0-15) of the redstone signal received by this device.",
            args = {},
            rets = {"num:power"},
            isAsync = false
    )
    public int getAnalogRedstoneInput(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) return 0;
            return vm.hardware.getLevel().getBestNeighborSignal(targetPos);
        });
    }

    @LuaFunction(
            value = "このデバイスがレッドストーン信号を受けているか（ON/OFF）を真偽値で取得します。",
            en = "Gets a boolean value indicating whether this device is receiving a redstone signal.",
            args = {},
            rets = {"bool:isOn"},
            isAsync = false
    )
    public boolean getRedstoneInput(String targetStr) {
        return getAnalogRedstoneInput(targetStr) > 0;
    }

    private RSTargetInfo resolveOutputTarget(String targetStr) {
        AdvancedMachineBlockEntity machine = vm.hardware;
        if (machine == null || machine.getLevel() == null || targetStr == null) return null;
        Level level = machine.getLevel();

        if (targetStr.contains(":")) {
            String[] parts = targetStr.split(":", 2);
            BlockPos basePos = machine.resolveDevice(parts[0]);
            if (basePos != null) {
                // ★変更: TargetUtilを使用
                Direction dir = TargetUtil.getDirectionRelative(parts[1], level.getBlockState(basePos));
                if (dir != null) {
                    BlockEntity be = level.getBlockEntity(basePos);
                    if (be instanceof ProbeBlockEntity probe) {
                        return new RSTargetInfo(true, probe, dir);
                    }
                }
            }
            return null;
        }

        // ★変更: TargetUtilを使用
        Direction dir = TargetUtil.getDirectionRelative(targetStr, machine.getBlockState());
        if (dir != null) {
            return new RSTargetInfo(false, null, dir);
        }

        return null;
    }

    private record RSTargetInfo(boolean isProbe, ProbeBlockEntity probe, Direction direction) {
    }
}