package com.nishiyu.lunex.mcnet;

import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.program.server.ServerLuaVM;

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

    // ★ 追加: WAN経由の通信距離(通信可能)判定
    public static boolean canCommunicateWAN(String srcIp, String destIp) {
        ServerLuaVM srcVm = ACTIVE_WAN_NODES.get(srcIp);
        ServerLuaVM destVm = ACTIVE_WAN_NODES.get(destIp);

        if (srcVm == null || destVm == null) return false;

        // ※VMからIMachineContextを取得するプロパティにアクセスします (環境に合わせて「machine」フィールド等を調整してください)
        if (srcVm.machine instanceof RouterBlockEntity srcRouter && destVm.machine instanceof RouterBlockEntity destRouter) {
            // 送信元ルーターの通信可能範囲内に、送信先ルーターが存在するか判定
            return srcRouter.canCommunicateWith(destRouter.getLevel(), destRouter.getBlockPos());
        }
        return false;
    }
}