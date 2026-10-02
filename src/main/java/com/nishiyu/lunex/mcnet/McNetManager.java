package com.nishiyu.lunex.mcnet;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.api.mainframe.MainframeConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McNetManager {
    // WAN(グローバルIP)のルーティングテーブルのみを残す
    private static final Map<String, ServerLuaVM> ACTIVE_WAN_NODES = new ConcurrentHashMap<>();

    public static void registerWanNode(String ip, ServerLuaVM vm) {
        if (ip != null && !ip.isEmpty() && !"0.0.0.0".equals(ip)) {
            ACTIVE_WAN_NODES.put(ip, vm);
        }
    }

    public static void unregisterWanNode(String ip) {
        if (ip != null && !ip.isEmpty()) {
            ACTIVE_WAN_NODES.remove(ip);
        }
    }

    public static ServerLuaVM getWanNode(String ip) {
        return ACTIVE_WAN_NODES.get(ip);
    }

    public static Map<String, ServerLuaVM> getAllWanNodes() {
        return ACTIVE_WAN_NODES;
    }

    public static boolean canCommunicateWAN(String srcIp, String destIp) {
        ServerLuaVM srcVm = ACTIVE_WAN_NODES.get(srcIp);
        ServerLuaVM destVm = ACTIVE_WAN_NODES.get(destIp);

        if (srcVm == null || destVm == null) return false;

        // ★修正: CoreMachineServerLuaVM にキャストして simpleMachine から Router機能を判定
        if (srcVm instanceof CoreMachineServerLuaVM cvmSrc && destVm instanceof CoreMachineServerLuaVM cvmDest) {
            if (cvmSrc.simpleMachine != null && cvmDest.simpleMachine != null) {
                return cvmSrc.simpleMachine.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)
                        && cvmDest.simpleMachine.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER);
            }
        }
        return false;
    }
}