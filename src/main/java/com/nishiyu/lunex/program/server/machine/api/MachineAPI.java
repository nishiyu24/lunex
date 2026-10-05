package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.function.Consumer;

public class MachineAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public MachineAPI() {}
    public MachineAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return "machine"; }

    @Override
    public String getRequiredFeature() { return ""; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new MachineAPI(vm);
    }

    // ========== 物理アクション系 ==========

    @LuaFunction(
            value = "指定したデバイス(マシン)の現在のエネルギー（電力）残量を取得します。",
            args = {"str:target"},
            rets = {"num:energy"},
            isAsync = false
    )
    public int getEnergy(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) {
                if (("self".equalsIgnoreCase(targetStr) || targetStr == null) && machine != null) {
                    return machine.getEnergy();
                }
                return 0;
            }
            if (machine == null || machine.getLevel() == null) return 0;
            Level level = machine.getLevel();
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, null);
            return energy != null ? energy.getEnergyStored() : 0;
        }, 0, false);
    }

    @LuaFunction(
            value = "インベントリスロットに入っているアイテムの個数を取得します。",
            args = {"str:target", "num:slot"},
            rets = {"num:count"},
            isAsync = false
    )
    public int getItemCount(String targetStr, int slot) {
        return vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new).getItemCount(targetStr, slot);
    }

    @LuaFunction(
            value = "インベントリスロットに入っているアイテムの名前（ID）を取得します。",
            args = {"str:target", "num:slot"},
            rets = {"str:itemName"},
            isAsync = false
    )
    public String getItemName(String targetStr, int slot) {
        return vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new).getItemName(targetStr, slot);
    }

    @LuaFunction(
            value = "このデバイス（または指定方向）の先のブロックを破壊します。",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean dig(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || !machine.consumeActionEnergy(100)) return false;

        return vm.executeInMainThreadSync(() -> {
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) return false;
            Level level = machine.getLevel();

            if (level.isEmptyBlock(targetPos) || level.getBlockState(targetPos).getDestroySpeed(level, targetPos) < 0) {
                return false;
            }
            level.destroyBlock(targetPos, true);
            return true;
        }, 1000, false);
    }

    @LuaFunction(
            value = "このデバイス（または指定方向）の先へブロックを設置します。",
            args = {"str:target", "num:slot"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean place(String targetStr, int slot) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || !machine.consumeActionEnergy(50)) return false;

        return vm.executeInMainThreadSync(() -> {
            if (machine.mainframeStorage == null || slot < 1 || slot > machine.mainframeStorage.getSlots()) return false;
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) return false;
            Level level = machine.getLevel();

            if (!level.isEmptyBlock(targetPos) && !level.getBlockState(targetPos).canBeReplaced()) {
                return false;
            }

            ItemStack stack = machine.mainframeStorage.getStackInSlot(slot - 1);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                return false;
            }

            level.setBlockAndUpdate(targetPos, blockItem.getBlock().defaultBlockState());
            machine.mainframeStorage.extractItem(slot - 1, 1, false);
            machine.setChanged();
            return true;
        }, 500, false);
    }

    // ========== システム制御系 ==========

    @LuaFunction(
            value = "マシンに設定されているラベル(名前)を取得します。",
            args = {}, rets = {"str:label"}, isAsync = false
    )
    public String getLabel() {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            // ★修正: getCore()経由に変更
            return (machine != null && machine.getCore() != null && machine.getCore().getMachineLabel() != null) ? machine.getCore().getMachineLabel() : "";
        });
    }

    @LuaFunction(
            value = "マシンにラベル(名前)を設定します。",
            args = {"str:label"}, rets = {}, isAsync = true
    )
    public void setLabel(String label) {
        vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            // ★修正: getCore()経由に変更
            if (machine != null && machine.getCore() != null) {
                machine.getCore().setMachineLabel(label != null ? label : "");
            }
            return null;
        });
    }

    @LuaFunction(
            value = "マシンの再起動時に実行されるデフォルトのプログラム名を取得します。",
            args = {}, rets = {"str:programName"}, isAsync = false
    )
    public String getBootProgram() {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            // ★修正: getCore()経由に変更
            if (machine != null && machine.getCore() != null) {
                String prog = machine.getCore().getProgramName();
                return (prog == null || prog.isEmpty()) ? "startup.lua" : prog;
            }
            return "startup.lua";
        });
    }

    @LuaFunction(
            value = "マシンの再起動時に実行されるデフォルトのプログラム名を設定します。",
            args = {"str:programName"}, rets = {}, isAsync = true
    )
    public void setBootProgram(String programName) {
        vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            // ★修正: getCore()経由に変更
            if (machine != null && machine.getCore() != null) machine.getCore().setProgramName(programName != null ? programName : "");
            return null;
        });
    }

    @LuaFunction(
            value = "指定したマシン（省略時は自身）のプログラムを終了し、電源を落とします。",
            args = {"str:target"}, rets = {"bool:success"}, isAsync = true
    )
    public boolean shutdown(String target) {
        return executeLifecycle(target, vm -> vm.stopProgram());
    }

    @LuaFunction(
            value = "指定したマシン（省略時は自身）を再起動します。起動するプログラム名を指定可能です。",
            args = {"str:target", "str:programName"}, rets = {"bool:success"}, isAsync = true
    )
    public boolean reboot(String target, String programName) {
        String prog = (programName != null && !programName.isEmpty()) ? programName : "startup";
        return executeLifecycle(target, vm -> vm.restartProgram(prog));
    }

    @LuaFunction(
            value = "指定したネットワーク上のマシンで、登録済みのプログラムを遠隔実行(強制起動)させます。",
            args = {"str:target", "str:programName"}, rets = {"bool:success"}, isAsync = true
    )
    public boolean runRemoteProgram(String targetStr, String programName) {
        if (targetStr == null || targetStr.isEmpty() || programName == null || programName.isEmpty()) return false;

        return vm.executeInMainThreadSync(() -> {
            ServerLuaVM remoteVm = getRemoteVM(targetStr);
            if (remoteVm != null) {
                if (remoteVm.isRunning) remoteVm.stopProgram();
                remoteVm.startProgram(programName);
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したネットワーク上のマシンで、任意のLuaコードを文字列で送信して直接遠隔実行させます。",
            args = {"str:target", "str:code"}, rets = {"bool:success"}, isAsync = true
    )
    public boolean execRemoteCode(String targetStr, String code) {
        if (targetStr == null || targetStr.isEmpty() || code == null || code.isEmpty()) return false;

        return vm.executeInMainThreadSync(() -> {
            ServerLuaVM remoteVm = getRemoteVM(targetStr);
            if (remoteVm != null) {
                if (remoteVm.isRunning) remoteVm.stopProgram();
                remoteVm.isWipingMemory = true;
                remoteVm.startCode(code, "RemoteExec");
                return true;
            }
            return false;
        });
    }

    private ServerLuaVM getRemoteVM(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null) return null;
        BlockPos pos = machine.resolveDevice(targetStr);

        if (pos != null) {
            BlockEntity be = machine.getLevel().getBlockEntity(pos);
            // ★修正: getCore()経由に変更
            if (be instanceof SimpleMachineBlockEntity targetMachine && targetMachine.getCore() != null) return targetMachine.getCore().vm;
        }
        return null;
    }

    private boolean executeLifecycle(String targetStr, Consumer<ServerLuaVM> action) {
        if (targetStr == null || targetStr.isEmpty() || targetStr.equals("self") || targetStr.equals("localhost")) {
            action.accept(this.vm);
            return true;
        }
        return vm.executeInMainThreadSync(() -> {
            ServerLuaVM remoteVm = getRemoteVM(targetStr);
            if (remoteVm != null) {
                action.accept(remoteVm);
                return true;
            }
            return false;
        });
    }
}