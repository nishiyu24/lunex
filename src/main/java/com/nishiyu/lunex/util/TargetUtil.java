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
        // ★修正: 方角指定を廃止し、ネットワーク内の接続デバイスからタグ/IPで検索するように統一
        List<BlockPos> connected = MCNetUtil.getConnectedDevices(level, sourceBE.getBlockPos());

        for (BlockPos p : connected) {
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) {
                CompoundTag data = be.getPersistentData();
                if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster) {
                    if (data.contains("MainframeNetworkTag") && !data.getString("MainframeNetworkTag").isEmpty()) {
                        if (targetStr.equals(data.getString("MainframeNetworkTag"))) return p;
                    }
                }
                if (targetStr.equals(data.getString("NetworkTag"))) return p;
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
        if (sourceBE instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
            router = sm;
        } else {
            CompoundTag data = sourceBE.getPersistentData();
            if (data.contains("RouterPos")) {
                BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
                BlockEntity be = sourceBE.getLevel().getBlockEntity(routerPos);
                if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                    router = sm;
                }
            }
        }

        if (router != null) {
            CompoundTag leases = router.persistentData.getCompound("DHCPLeases");
            for (String key : leases.getAllKeys()) {
                if (leases.getString(key).equals(targetStr)) {
                    // PortableScreenの登録キーはUUID(長さ36, ハイフン4つ)であることを利用
                    return key.length() == 36 && key.split("-").length == 5;
                }
            }
        }
        return false;
    }
}