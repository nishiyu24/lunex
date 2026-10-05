package com.nishiyu.lunex.util;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.mcnet.MCNetUtil;
import com.nishiyu.lunex.api.MainframeConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class TargetUtil {

    public static BlockPos resolveDevice(BlockEntity sourceBE, String targetStr) {
        if (targetStr == null || targetStr.isEmpty() || sourceBE == null || sourceBE.getLevel() == null) return null;

        Level level = sourceBE.getLevel();
        List<BlockPos> connected = MCNetUtil.getConnectedDevices(level, sourceBE.getBlockPos());

        for (BlockPos p : connected) {
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) {
                // ★修正: SimpleMachineBlockEntity の場合は getCore() 経由で NBT を取得
                if (be instanceof SimpleMachineBlockEntity sm && sm.getCore() != null) {
                    CompoundTag data = sm.getCore().persistentData;
                    if (sm.isMainframeMaster) {
                        if (data.contains("MainframeNetworkTag") && !data.getString("MainframeNetworkTag").isEmpty()) {
                            if (targetStr.equals(data.getString("MainframeNetworkTag"))) return p;
                        }
                    }
                    if (targetStr.equals(data.getString("NetworkTag"))) return p;
                } else {
                    CompoundTag data = be.getPersistentData();
                    if (targetStr.equals(data.getString("NetworkTag"))) return p;
                }
            }
        }
        return null;
    }

    /**
     * 対象のIPアドレスがPortable Screenのものかどうかを判定します。
     * Portable Screenはブロックとして設置されていないため、DHCPのリース情報から判定します。
     */
    public static boolean isPortableScreen(BlockEntity sourceBE, String targetStr) {
        if (targetStr == null || sourceBE == null || sourceBE.getLevel() == null) return false;
        if (!targetStr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return false;

        SimpleMachineBlockEntity router = null;
        // ★修正: getCore() 経由の activeFeatures でルーターか判定
        if (sourceBE instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.getCore() != null && sm.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
            router = sm;
        } else {
            CompoundTag data;
            if (sourceBE instanceof SimpleMachineBlockEntity sm && sm.getCore() != null) {
                data = sm.getCore().persistentData;
            } else {
                data = sourceBE.getPersistentData();
            }

            if (data.contains("RouterPos")) {
                BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
                BlockEntity be = sourceBE.getLevel().getBlockEntity(routerPos);
                if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.getCore() != null && sm.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                    router = sm;
                }
            }
        }

        if (router != null && router.getCore() != null) {
            // ★修正: getCore() 経由に変更
            CompoundTag leases = router.getCore().persistentData.getCompound("DHCPLeases");
            for (String key : leases.getAllKeys()) {
                if (leases.getString(key).equals(targetStr)) {
                    return key.length() == 36 && key.split("-").length == 5;
                }
            }
        }
        return false;
    }
}