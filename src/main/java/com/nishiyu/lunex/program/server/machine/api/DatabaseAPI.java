package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class DatabaseAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public DatabaseAPI() {}
    public DatabaseAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return "database"; }

    @Override
    public String getRequiredFeature() { return ""; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new DatabaseAPI(vm);
    }

    private SimpleMachineBlockEntity getMainframe(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null) return null;
        if ("self".equals(targetStr) || "localhost".equals(targetStr)) return machine;

        BlockPos pos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
        if (pos != null && machine.getLevel() != null) {
            BlockEntity be = machine.getLevel().getBlockEntity(pos);
            if (be instanceof SimpleMachineBlockEntity master && master.isMainframeMaster) {
                return master;
            }
        }
        return null;
    }

    @LuaFunction(
            value = "データベースのストレージ使用量を文字列形式（使用数 / 最大数）で取得します。",
            args = {"str:target"},
            rets = {"str:usage"},
            isAsync = false
    )
    public String getUsage(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null) return "0 / 0";

            long used = master.resourceUsages.getOrDefault("item", 0L);
            long max = master.resourceCapacities.getOrDefault("item", 0L);
            return String.format("%d / %d", used, max);
        });
    }

    @LuaFunction(
            value = "データベースの現在の使用容量（アイテム個数）を取得します。",
            args = {"str:target"},
            rets = {"num:usedCount"},
            isAsync = false
    )
    public int getUsedCount(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null) return 0;
            return master.resourceUsages.getOrDefault("item", 0L).intValue();
        });
    }

    @LuaFunction(
            value = "データベースの最大容量（アイテム個数）を取得します。",
            args = {"str:target"},
            rets = {"num:maxCount"},
            isAsync = false
    )
    public int getMaxCount(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null) return 0;
            return master.resourceCapacities.getOrDefault("item", 0L).intValue();
        });
    }

    @LuaFunction(
            value = "指定した動的リソース（gas, mana, energy等）の使用量を取得します。",
            args = {"str:target", "str:resourceType"},
            rets = {"num:used"},
            isAsync = false
    )
    public int getResourceUsage(String targetStr, String resourceType) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null) return 0;
            return master.resourceUsages.getOrDefault(resourceType, 0L).intValue();
        });
    }

    @LuaFunction(
            value = "指定した動的リソース（gas, mana, energy等）の最大容量を取得します。",
            args = {"str:target", "str:resourceType"},
            rets = {"num:max"},
            isAsync = false
    )
    public int getResourceCapacity(String targetStr, String resourceType) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null) return 0;
            return master.resourceCapacities.getOrDefault(resourceType, 0L).intValue();
        });
    }

    @LuaFunction(
            value = "プログラム（文字列コード）をデータベースにアップロード(保存)します。",
            args = {"str:target", "str:programName", "str:code"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean uploadProgram(String targetStr, String programName, String code) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null || master.getLevel() == null || programName == null || code == null) return false;

            // 既存のプログラムを上書きする場合（すでに容量として 5 を消費済みのため追加チェックは不要）
            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    if (db.storedPrograms.containsKey(programName)) {
                        db.storedPrograms.put(programName, code);
                        db.setChanged();
                        return true;
                    }
                }
            }

            // 新規プログラムを追加する場合（追加で容量 5 を消費する）
            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    if (db.getUsedCount() + 5 <= db.getMaxCapacity()) {
                        db.storedPrograms.put(programName, code);
                        db.setChanged();
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @LuaFunction(
            value = "データベースからプログラム（文字列コード）をダウンロード(取得)します。",
            args = {"str:target", "str:programName"},
            rets = {"str:code"},
            isAsync = false
    )
    public String downloadProgram(String targetStr, String programName) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null || master.getLevel() == null) return null;

            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    if (db.storedPrograms.containsKey(programName)) {
                        return db.storedPrograms.get(programName);
                    }
                }
            }
            return null;
        });
    }

    @LuaFunction(
            value = "データベースに保存されているプログラム名のリストを取得します。",
            args = {"str:target"},
            rets = {"table:programs"},
            isAsync = false
    )
    public LuaTable listPrograms(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null || master.getLevel() == null) return result;

            int i = 1;
            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    for (String name : db.storedPrograms.keySet()) {
                        result.set(i++, LuaValue.valueOf(name));
                    }
                }
            }
            return result;
        });
    }
}