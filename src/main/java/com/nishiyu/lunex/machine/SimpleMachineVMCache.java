package com.nishiyu.lunex.machine;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.SimpleMachineServerLuaVM;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleMachineVMCache {
    private static final Map<UUID, SimpleMachineServerLuaVM> VM_CACHE = new ConcurrentHashMap<>();

    public static SimpleMachineServerLuaVM getOrCreateVM(UUID machineId, SimpleMachineBlockEntity entity) {
        return VM_CACHE.computeIfAbsent(machineId, id -> {
            // シンプルマシン用のVMを作成して返す
            return new SimpleMachineServerLuaVM(entity);
        });
    }

    public static void removeVM(UUID machineId) {
        SimpleMachineServerLuaVM vm = VM_CACHE.remove(machineId);
        if (vm != null) {
            vm.stopProgram();
        }
    }
}