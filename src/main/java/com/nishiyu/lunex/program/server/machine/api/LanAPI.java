package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.mcnet.MCNetUtil;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LanAPI {
    private final ServerLuaVM vm;

    public LanAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    @LuaFunction(
            value = "このデバイス（指定方向）にネットワーク対応デバイスが存在するか確認します。",
            en = "Checks if a network compatible device exists at this device (specified direction).",
            args = {"str:direction"},
            rets = {"bool:exists"},
            isAsync = false
    )
    public boolean ping(String direction) {
        return vm.executeInMainThreadSync(() -> {
            if (vm.hardware == null) return false;
            Direction dir = TargetUtil.getDirectionRelative(direction, vm.hardware.getBlockState());
            if (dir == null) return false;
            BlockPos pos = vm.hardware.getBlockPos().relative(dir);
            BlockEntity be = vm.hardware.getLevel().getBlockEntity(pos);
            return MCNetUtil.isMCNetDevice(be);
        });
    }

    @LuaFunction(
            value = "このデバイス（指定方向）の隣接マシンに直接メッセージを送信します。",
            en = "Sends a direct message to the adjacent machine at this device (specified direction).",
            args = {"str:direction", "str:message"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean send(String direction, String message) {
        return vm.executeInMainThreadSync(() -> {
            if (vm.hardware == null) return false;
            Direction dir = TargetUtil.getDirectionRelative(direction, vm.hardware.getBlockState());
            if (dir == null) return false;
            BlockPos pos = vm.hardware.getBlockPos().relative(dir);
            BlockEntity be = vm.hardware.getLevel().getBlockEntity(pos);

            if (be instanceof AdvancedMachineBlockEntity targetMachine && targetMachine.vm.isRunning) {
                targetMachine.vm.triggerEvent("lan_receive", "adjacent", message);
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "全方角の隣接マシンにメッセージをブロードキャストします。",
            en = "Broadcasts a message to all adjacent machines in all directions.",
            args = {"str:message"},
            rets = {},
            isAsync = true
    )
    public void broadcast(String message) {
        vm.executeInMainThreadSync(() -> {
            if (vm.hardware == null) return null;
            Level level = vm.hardware.getLevel();
            for (Direction dir : Direction.values()) {
                BlockPos pos = vm.hardware.getBlockPos().relative(dir);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AdvancedMachineBlockEntity targetMachine && targetMachine.vm.isRunning) {
                    targetMachine.vm.triggerEvent("lan_receive", "broadcast", message);
                }
            }
            return null;
        });
    }
}