package com.nishiyu.lunex.machine;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.MainframeComponentData;
import com.nishiyu.lunex.api.mainframe.MainframeComponentRegistry;
import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

public class MainframeScanner {

    public static void updateMainframeVisuals(Level level, List<BlockPos> parts, boolean assembled) {
        for (BlockPos partPos : parts) {
            BlockState state = level.getBlockState(partPos);
            for (Property<?> prop : state.getProperties()) {
                if (prop.getName().equals("assembled") && prop instanceof BooleanProperty boolProp) {
                    level.setBlock(partPos, state.setValue(boolProp, assembled), 3);
                    break;
                }
            }
        }
    }

    private static void logToVM(SimpleMachineBlockEntity master, String message) {
        if (master.vm instanceof CoreMachineServerLuaVM cvm) {
            cvm.terminalLog.add(message);
            cvm.syncClient();
        }
    }

    public static boolean attemptFormMainframe(Level level, BlockPos corePos, Player player) {
        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (!(coreBe instanceof SimpleMachineBlockEntity master)) return false;

        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(corePos);
        visited.add(corePos);

        int minX = corePos.getX(), minY = corePos.getY(), minZ = corePos.getZ();
        int maxX = corePos.getX(), maxY = corePos.getY(), maxZ = corePos.getZ();

        // 1. BFS探索（外部ブロックもレジストリ登録されていれば許可する）
        while (!queue.isEmpty()) {
            BlockPos curr = queue.poll();
            minX = Math.min(minX, curr.getX());
            minY = Math.min(minY, curr.getY());
            minZ = Math.min(minZ, curr.getZ());
            maxX = Math.max(maxX, curr.getX());
            maxY = Math.max(maxY, curr.getY());
            maxZ = Math.max(maxZ, curr.getZ());

            if (visited.size() > 1000) {
                logToVM(master, "§c構造が大きすぎます。組み立てを中断しました。§r");
                return false;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = curr.relative(dir);
                if (!visited.contains(neighbor)) {
                    BlockState neighborState = level.getBlockState(neighbor);

                    // レジストリに登録されているブロックなら探索を続行
                    if (MainframeComponentRegistry.isRegistered(neighborState.getBlock())) {
                        BlockEntity be = level.getBlockEntity(neighbor);
                        if (be instanceof IMainframePart part) {
                            if (part.getMasterPos() == null) {
                                visited.add(neighbor);
                                queue.add(neighbor);
                            }
                        } else {
                            // IMainframePartではないが、レジストリにある外部ブロック（バニラ等）
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        if (sizeX > 9 || sizeY > 9 || sizeZ > 9) {
            logToVM(master, "§c最大サイズ(9x9x9)を超過しています: " + sizeX + "x" + sizeY + "x" + sizeZ + "§r");
            return false;
        }
        if (sizeX * sizeY * sizeZ < 2) {
            logToVM(master, "§c最小サイズ(2ブロック以上)を満たしていません。§r");
            return false;
        }

        List<BlockPos> validParts = new ArrayList<>();
        Map<Block, Integer> componentCounts = new HashMap<>();

        // 2. ブロックの検証
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    int edgeMatches = 0;
                    if (x == minX || x == maxX) edgeMatches++;
                    if (y == minY || y == maxY) edgeMatches++;
                    if (z == minZ || z == maxZ) edgeMatches++;

                    String posType;
                    if (edgeMatches >= 2) posType = MainframeConstants.PLACEMENT_EDGE;
                    else if (edgeMatches == 1) posType = MainframeConstants.PLACEMENT_FACE;
                    else posType = MainframeConstants.PLACEMENT_INSIDE;

                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) {
                        if (!posType.equals(MainframeConstants.PLACEMENT_INSIDE)) {
                            logToVM(master, "§c構造が不完全です。辺や面はブロックで塞ぐ必要があります: " + pos.toShortString() + "§r");
                            return false;
                        }
                        continue;
                    }

                    Block block = state.getBlock();
                    MainframeComponentData data = MainframeComponentRegistry.get(block);

                    // IMainframePartであるかに関わらず、未登録ブロックがあれば弾く
                    if (data == null) {
                        logToVM(master, "§c構造内に未登録のブロックが含まれています: " + pos.toShortString() + "§r");
                        return false;
                    }

                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof IMainframePart part && part.getMasterPos() != null && !pos.equals(corePos)) {
                        logToVM(master, "§cすでに他のマシンに組み込まれているパーツがあります: " + pos.toShortString() + "§r");
                        return false;
                    }

                    if (!data.getPlacements().isEmpty() && !data.getPlacements().contains(posType)) {
                        logToVM(master, "§c" + block.getName().getString() + " は [" + posType + "] には配置できません: " + pos.toShortString() + "§r");
                        return false;
                    }

                    int max = data.getMaxCount();
                    int current = componentCounts.getOrDefault(block, 0) + 1;

                    if (max != -1 && current > max) {
                        logToVM(master, "§cパーツの最大接続数を超過しました: " + block.getName().getString() + " (上限:" + max + "個)§r");
                        return false;
                    }

                    componentCounts.put(block, current);
                    validParts.add(pos);
                }
            }
        }

        master.isMainframeMaster = true;
        master.mainframeParts.clear();
        master.mainframeParts.addAll(validParts);
        master.mainframeMachines = validParts.size();

        // 3. 確定処理: マスターの紐づけと、外部ブロックのアダプターへの動的置換
        for (BlockPos partPos : validParts) {
            BlockState partState = level.getBlockState(partPos);
            BlockEntity partBe = level.getBlockEntity(partPos);

            // IMainframePartを持たないブロック(自動作業台やディスペンサー等)をアダプターに置換する
            if (!(partBe instanceof IMainframePart)) {
                CompoundTag originalTag = null;
                if (partBe != null) {
                    // 自動作業台などの中身(インベントリ)を保持するためにNBTを退避
                    originalTag = partBe.saveWithoutMetadata(level.registryAccess());
                }

                // アダプターブロックへ強制置換 (BlockStateごと置き換える)
                level.setBlock(partPos, Lunex.MAINFRAME_ADAPTER_BLOCK.get().defaultBlockState(), 3);
                partBe = level.getBlockEntity(partPos);

                if (partBe instanceof MainframeAdapterBlockEntity adapter) {
                    adapter.setOriginalBlock(partState, originalTag);
                }
            }

            // IMainframePart (元から、または先ほど置換されたアダプター) にマスター位置を設定
            if (partBe instanceof IMainframePart part) {
                part.setMasterPos(corePos);
            }
        }

        updateMainframeVisuals(level, validParts, true);
        master.rebuildMainframe();

        return true;
    }
}