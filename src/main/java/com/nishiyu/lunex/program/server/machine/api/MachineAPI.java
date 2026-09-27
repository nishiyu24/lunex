package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.function.Consumer;

public class MachineAPI {
    private final ServerLuaVM vm;

    public MachineAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    // ========== 物理アクション系 ==========

    @LuaFunction(
            value = "指定したデバイス(マシン)の現在のエネルギー（電力）残量を取得します。",
            en = "Gets the remaining energy (power) of the specified device (machine).",
            args = {"str:target"},
            rets = {"num:energy"},
            isAsync = false
    )
    public int getEnergy(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) {
                if (("self".equalsIgnoreCase(targetStr) || targetStr == null) && vm.hardware != null) {
                    return vm.hardware.getEnergy();
                }
                return 0;
            }
            Level level = vm.hardware.getLevel();
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, null);
            return energy != null ? energy.getEnergyStored() : 0;
        }, 0, false);
    }

    @LuaFunction(
            value = "インベントリスロットに入っているアイテムの個数を取得します。",
            en = "Gets the number of items in the inventory slot.",
            args = {"str:target", "num:slot"},
            rets = {"num:count"},
            isAsync = false
    )
    public int getItemCount(String targetStr, int slot) {
        return vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new).getItemCount(targetStr, slot);
    }

    @LuaFunction(
            value = "インベントリスロットに入っているアイテムの名前（ID）を取得します。",
            en = "Gets the name (ID) of the item in the inventory slot.",
            args = {"str:target", "num:slot"},
            rets = {"str:itemName"},
            isAsync = false
    )
    public String getItemName(String targetStr, int slot) {
        return vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new).getItemName(targetStr, slot);
    }

    @LuaFunction(
            value = "このデバイス（または指定方向）の先のブロックを破壊します。",
            en = "Breaks the block in front of this device.",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean dig(String targetStr) {
        if (!vm.hardware.consumeActionEnergy(100)) return false;

        return vm.executeInMainThreadSync(() -> {
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) return false;
            Level level = vm.hardware.getLevel();

            if (level.isEmptyBlock(targetPos) || level.getBlockState(targetPos).getDestroySpeed(level, targetPos) < 0) {
                return false;
            }
            level.destroyBlock(targetPos, true);
            return true;
        }, 1000, false);
    }

    @LuaFunction(
            value = "このデバイス（または指定方向）の先へブロックを設置します。",
            en = "Places a block in front of this device.",
            args = {"str:target", "num:slot"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean place(String targetStr, int slot) {
        if (!vm.hardware.consumeActionEnergy(50)) return false;

        return vm.executeInMainThreadSync(() -> {
            if (!vm.hardware.isValidSlot(slot)) return false;
            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) return false;
            Level level = vm.hardware.getLevel();

            if (!level.isEmptyBlock(targetPos) && !level.getBlockState(targetPos).canBeReplaced()) {
                return false;
            }

            ItemStack stack = vm.hardware.itemHandler.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                return false;
            }

            level.setBlockAndUpdate(targetPos, blockItem.getBlock().defaultBlockState());
            stack.shrink(1);
            vm.hardware.setChanged();
            return true;
        }, 500, false);
    }

    // ========== システム制御系 ==========

    @LuaFunction(
            value = "マシンに設定されているラベル(名前)を取得します。",
            en = "Gets the label (name) set for the machine.",
            args = {}, rets = {"str:label"}, isAsync = false
    )
    public String getLabel() {
        return vm.executeInMainThreadSync(() -> vm.hardware != null && vm.hardware.getMachineLabel() != null ? vm.hardware.getMachineLabel() : "");
    }

    @LuaFunction(
            value = "マシンにラベル(名前)を設定します。",
            en = "Sets the label (name) for the machine.",
            args = {"str:label"}, rets = {}, isAsync = true
    )
    public void setLabel(String label) {
        vm.executeInMainThreadSync(() -> {
            if (vm.hardware != null) {
                vm.hardware.setMachineLabel(label != null ? label : "");
                vm.hardware.setChanged();
                vm.hardware.sync();
            }
            return null;
        });
    }

    @LuaFunction(
            value = "マシンの再起動時に実行されるデフォルトのプログラム名を取得します。",
            en = "Gets the name of the default program executed when the machine restarts.",
            args = {}, rets = {"str:programName"}, isAsync = false
    )
    public String getBootProgram() {
        return vm.executeInMainThreadSync(() -> {
            if (vm.hardware != null) {
                String prog = vm.hardware.getProgramName();
                return (prog == null || prog.isEmpty()) ? AdvancedMachineBlockEntity.DEFAULT_BOOT_FILE : prog;
            }
            return AdvancedMachineBlockEntity.DEFAULT_BOOT_FILE;
        });
    }

    @LuaFunction(
            value = "マシンの再起動時に実行されるデフォルトのプログラム名を設定します。",
            en = "Sets the name of the default program executed when the machine restarts.",
            args = {"str:programName"}, rets = {}, isAsync = true
    )
    public void setBootProgram(String programName) {
        vm.executeInMainThreadSync(() -> {
            if (vm.hardware != null) vm.hardware.setProgramName(programName != null ? programName : "");
            return null;
        });
    }

    @LuaFunction(
            value = "指定したマシン（省略時は自身）のプログラムを終了し、電源を落とします。",
            en = "Terminates the program of the specified machine (or itself if omitted) and powers it down.",
            args = {"str:target"}, rets = {"bool:success"}, isAsync = true
    )
    public boolean shutdown(String target) {
        return executeLifecycle(target, vm -> vm.stopProgram());
    }

    @LuaFunction(
            value = "指定したマシン（省略時は自身）を再起動します。起動するプログラム名を指定可能です。",
            en = "Reboots the specified machine (or itself if omitted). A program name to launch can be specified.",
            args = {"str:target", "str:programName"}, rets = {"bool:success"}, isAsync = true
    )
    public boolean reboot(String target, String programName) {
        String prog = (programName != null && !programName.isEmpty()) ? programName : "startup";
        return executeLifecycle(target, vm -> vm.restartProgram(prog));
    }

    @LuaFunction(
            value = "指定したネットワーク上のマシンで、登録済みのプログラムを遠隔実行(強制起動)させます。",
            en = "Remotely executes a registered program on the specified machine on the network.",
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
            en = "Sends arbitrary Lua code as a string to the specified machine for direct remote execution.",
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
        if (vm.hardware == null) return null;
        BlockPos pos = vm.hardware.resolveDevice(targetStr);

        if (pos != null) {
            BlockEntity be = vm.hardware.getLevel().getBlockEntity(pos);
            if (be instanceof AdvancedMachineBlockEntity machine) return machine.vm;
            // ルーターはVMを持たなくなったため除外
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