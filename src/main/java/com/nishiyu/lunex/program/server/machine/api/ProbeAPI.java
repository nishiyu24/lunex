package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.block.ProbeBlock;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class ProbeAPI {
    private final ServerLuaVM vm;

    public ProbeAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    private ProbeBlockEntity getProbe(String targetStr) {
        if (vm.hardware == null || vm.hardware.getLevel() == null || targetStr == null) return null;
        BlockPos pos = vm.hardware.resolveDevice(targetStr);
        if (pos != null && vm.hardware.getLevel().getBlockEntity(pos) instanceof ProbeBlockEntity probe) {
            return probe;
        }
        return null;
    }

    private Direction parseDirection(String face) {
        if (face == null) return null;
        for (Direction dir : Direction.values()) {
            if (dir.getName().equalsIgnoreCase(face)) return dir;
        }
        return null;
    }

    @LuaFunction(
            value = "指定した面の接続状態を有効/無効に設定します。",
            en = "Enables or disables the connection state of the specified face.",
            args = {"str:face", "bool:enabled"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setFaceEnabled(String targetStr, String face, boolean enabled) {
        return vm.executeInMainThreadSync(() -> {
            ProbeBlockEntity probe = getProbe(targetStr);
            Direction dir = parseDirection(face);
            if (probe == null || dir == null) return false;

            Level level = probe.getLevel();
            BlockPos pos = probe.getBlockPos();
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof ProbeBlock) {
                BooleanProperty prop = ProbeBlock.getPropertyByDirection(dir);
                if (state.getValue(prop) != enabled) {
                    level.setBlock(pos, state.setValue(prop, enabled), 3);
                    probe.notifyNetworkTagChanged();
                    return true;
                }
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定した面の接続状態を取得します。",
            en = "Gets the connection state of the specified face.",
            args = {"str:face"},
            rets = {"bool:enabled"},
            isAsync = false
    )
    public boolean getFaceEnabled(String targetStr, String face) {
        return vm.executeInMainThreadSync(() -> {
            ProbeBlockEntity probe = getProbe(targetStr);
            Direction dir = parseDirection(face);
            if (probe == null || dir == null) return false;

            BlockState state = probe.getLevel().getBlockState(probe.getBlockPos());
            if (state.getBlock() instanceof ProbeBlock) {
                return state.getValue(ProbeBlock.getPropertyByDirection(dir));
            }
            return false;
        }, 0, false);
    }

    @LuaFunction(
            value = "指定した面に対してレッドストーン信号(0~15)を出力します。",
            en = "Outputs a redstone signal (0-15) to the specified face.",
            args = {"str:face", "num:power"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setRedstone(String targetStr, String face, int power) {
        return vm.executeInMainThreadSync(() -> {
            ProbeBlockEntity probe = getProbe(targetStr);
            Direction dir = parseDirection(face);
            if (probe == null || dir == null) return false;

            int clampedPower = Math.clamp(power, 0, 15);
            probe.redstoneOutputs.put(dir, clampedPower);
            probe.setChanged();

            // 周囲のブロックにレッドストーンの更新を通知
            probe.getLevel().updateNeighborsAt(probe.getBlockPos(), probe.getBlockState().getBlock());
            probe.getLevel().updateNeighborsAt(probe.getBlockPos().relative(dir), probe.getBlockState().getBlock());
            return true;
        });
    }

    @LuaFunction(
            value = "指定した面に隣接するブロックからのレッドストーン入力信号(0~15)を取得します。",
            en = "Gets the redstone input signal (0-15) from the block adjacent to the specified face.",
            args = {"str:face"},
            rets = {"num:power"},
            isAsync = false
    )
    public int getRedstone(String targetStr, String face) {
        return vm.executeInMainThreadSync(() -> {
            ProbeBlockEntity probe = getProbe(targetStr);
            Direction dir = parseDirection(face);
            if (probe == null || dir == null) return 0;

            BlockPos targetPos = probe.getBlockPos().relative(dir);
            return probe.getLevel().getSignal(targetPos, dir);
        }, 0, false);
    }

    @LuaFunction(
            value = "指定した面に隣接するブロックのID(名前)を取得します。",
            en = "Gets the ID (name) of the block adjacent to the specified face.",
            args = {"str:face"},
            rets = {"str:blockName"},
            isAsync = false
    )
    public String getBlockName(String targetStr, String face) {
        return vm.executeInMainThreadSync(() -> {
            ProbeBlockEntity probe = getProbe(targetStr);
            Direction dir = parseDirection(face);
            if (probe == null || dir == null) return "minecraft:air";

            BlockPos targetPos = probe.getBlockPos().relative(dir);
            BlockState state = probe.getLevel().getBlockState(targetPos);
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }, 0, false);
    }
}