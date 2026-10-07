package com.nishiyu.lunex.machine.frame;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CoreMachineVMCache {
    private static final Map<UUID, CoreMachineServerLuaVM> VM_CACHE = new ConcurrentHashMap<>();

    public static CoreMachineServerLuaVM getOrCreateVM(UUID machineId, SimpleMachineBlockEntity entity) {
        return VM_CACHE.computeIfAbsent(machineId, id -> {
            // シンプルマシン用のVMを作成して返す
            return new CoreMachineServerLuaVM(entity);
        });
    }

    public static void removeVM(UUID machineId) {
        CoreMachineServerLuaVM vm = VM_CACHE.remove(machineId);
        if (vm != null) {
            vm.stopProgram();
        }
    }
}