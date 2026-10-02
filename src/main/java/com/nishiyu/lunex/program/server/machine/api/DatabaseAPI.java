package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.mainframe.extension.IMainframeAPI;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.nio.charset.StandardCharsets;

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
            value = "データベースのストレージ使用量をメガバイト(MB)形式の文字列で取得します。",
            args = {"str:target"},
            rets = {"str:usage"},
            isAsync = false
    )
    public String getUsageMB(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null) return "0.00 MB / 0.00 MB";

            int usedBytes = getUsedBytes(targetStr);
            int maxBytes = getMaxBytes(targetStr);

            double used = usedBytes / 1048576.0;
            if (usedBytes > 0 && used < 0.01) used = 0.01;
            double max = maxBytes / 1048576.0;
            return String.format("%.2f MB / %.2f MB", used, max);
        });
    }

    @LuaFunction(
            value = "データベースの現在の使用容量（バイト数）を取得します。",
            args = {"str:target"},
            rets = {"num:usedBytes"},
            isAsync = false
    )
    public int getUsedBytes(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            if (master == null || master.getLevel() == null) return 0;

            int totalUsed = 0;
            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    totalUsed += db.getUsedBytes();
                }
            }
            return totalUsed;
        });
    }

    @LuaFunction(
            value = "データベースの最大容量（バイト数）を取得します。",
            args = {"str:target"},
            rets = {"num:maxBytes"},
            isAsync = false
    )
    public int getMaxBytes(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity master = getMainframe(targetStr);
            return master != null ? master.mainframeTotalCapacityBytes : 0;
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

            int newCodeLength = code.getBytes(StandardCharsets.UTF_8).length;

            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    if (db.storedPrograms.containsKey(programName)) {
                        int current = db.getUsedBytes();
                        int existingLength = db.storedPrograms.get(programName).getBytes(StandardCharsets.UTF_8).length;
                        if (current - existingLength + newCodeLength <= db.getMaxCapacityBytes()) {
                            db.storedPrograms.put(programName, code);
                            db.setChanged();
                            return true;
                        }
                        return false;
                    }
                }
            }

            for (BlockPos dbPos : master.mainframeParts) {
                if (master.getLevel().getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                    if (db.getUsedBytes() + newCodeLength <= db.getMaxCapacityBytes()) {
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