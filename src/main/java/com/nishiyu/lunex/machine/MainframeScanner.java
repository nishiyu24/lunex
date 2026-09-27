package com.nishiyu.lunex.machine;

import com.nishiyu.lunex.block.DatabaseBlock;
import com.nishiyu.lunex.block.MachineFrameBlock;
import com.nishiyu.lunex.block.ProbeBlock;
import com.nishiyu.lunex.block.ScreenBlock;
import com.nishiyu.lunex.block.SimpleMachineBlock;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainframeScanner {

    public static class ScanResult {
        public boolean isValid = false;
        public SimpleMachineBlockEntity masterNode = null;
        public final List<BlockPos> databases = new ArrayList<>();
        public final List<BlockPos> machines = new ArrayList<>();
        public final List<BlockPos> allParts = new ArrayList<>();
    }

    public static boolean attemptFormMainframe(Level level, BlockPos triggerPos) {
        if (level.isClientSide) return false;

        if (level.getBlockEntity(triggerPos) instanceof IMainframePart part && part.getMasterPos() != null) {
            BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
            if (!(masterBe instanceof SimpleMachineBlockEntity sm) || !sm.isMainframeMaster) {
                part.setMasterPos(null);
            } else {
                return false;
            }
        }

        for (int size = 4; size >= 2; size--) {
            for (int offsetX = 0; offsetX < size; offsetX++) {
                for (int offsetY = 0; offsetY < size; offsetY++) {
                    for (int offsetZ = 0; offsetZ < size; offsetZ++) {
                        BlockPos startPos = triggerPos.offset(-offsetX, -offsetY, -offsetZ);
                        ScanResult result = scanRegion(level, startPos, size);
                        if (result.isValid) {
                            formMainframe(level, result);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static ScanResult scanRegion(Level level, BlockPos start, int size) {
        ScanResult result = new ScanResult();
        BlockPos end = start.offset(size - 1, size - 1, size - 1);

        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();

                    boolean isSurface = (x == start.getX() || x == end.getX() ||
                            y == start.getY() || y == end.getY() ||
                            z == start.getZ() || z == end.getZ());

                    if (!isSurface) {
                        if (!(block instanceof MachineFrameBlock) && !(block instanceof DatabaseBlock)) {
                            return new ScanResult();
                        }
                    }

                    if (!(block instanceof MachineFrameBlock) && !(block instanceof DatabaseBlock) &&
                            !(block instanceof SimpleMachineBlock) && !(block instanceof ProbeBlock) &&
                            !(block instanceof ScreenBlock)) {
                        return new ScanResult();
                    }

                    if (block instanceof DatabaseBlock) result.databases.add(pos);
                    if (block instanceof SimpleMachineBlock) result.machines.add(pos);

                    if (level.getBlockEntity(pos) instanceof IMainframePart part && part.getMasterPos() != null) {
                        BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
                        if (!(masterBe instanceof SimpleMachineBlockEntity sm) || !sm.isMainframeMaster) {
                            part.setMasterPos(null);
                        } else {
                            return new ScanResult();
                        }
                    }

                    result.allParts.add(pos);
                }
            }
        }

        if (result.databases.isEmpty() || result.machines.isEmpty()) return new ScanResult();

        BlockPos masterPos = result.machines.get(0);
        result.masterNode = (SimpleMachineBlockEntity) level.getBlockEntity(masterPos);
        result.isValid = true;
        return result;
    }

    private static void formMainframe(Level level, ScanResult result) {
        SimpleMachineBlockEntity master = result.masterNode;
        master.isMainframeMaster = true;
        master.mainframeParts.clear();
        master.mainframeParts.addAll(result.allParts);
        master.mainframeMachines = result.machines.size();
        master.mainframeDatabases.clear();
        master.mainframeDatabases.addAll(result.databases);

        for (BlockPos pos : result.allParts) {
            if (level.getBlockEntity(pos) instanceof IMainframePart part) {
                part.setMasterPos(master.getBlockPos());
            }
        }

        master.rebuildMainframe();
        updateMainframeVisuals(level, result.allParts, true);
    }

    public static void updateMainframeVisuals(Level level, List<BlockPos> parts, boolean isFormed) {
        Set<BlockPos> partSet = new HashSet<>(parts);

        for (BlockPos pos : parts) {
            BlockState state = level.getBlockState(pos);

            net.minecraft.world.level.block.state.properties.Property<?> assembledProp = state.getBlock().getStateDefinition().getProperty("assembled");
            if (!(assembledProp instanceof net.minecraft.world.level.block.state.properties.BooleanProperty boolProp)) {
                continue;
            }

            BlockState newState = state;

            boolean isProbeActive = false;
            if (state.getBlock() instanceof ProbeBlock) {
                if (level.getBlockEntity(pos) instanceof com.nishiyu.lunex.blockentity.ProbeBlockEntity probe && probe.isDetected) {
                    isProbeActive = true;
                }
            }

            // ★修正: 接続状態の判定から isProbeActive を除外
            if (newState.hasProperty(BlockStateProperties.UP))
                newState = newState.setValue(BlockStateProperties.UP, isFormed && partSet.contains(pos.above()));
            if (newState.hasProperty(BlockStateProperties.DOWN))
                newState = newState.setValue(BlockStateProperties.DOWN, isFormed && partSet.contains(pos.below()));
            if (newState.hasProperty(BlockStateProperties.NORTH))
                newState = newState.setValue(BlockStateProperties.NORTH, isFormed && partSet.contains(pos.north()));
            if (newState.hasProperty(BlockStateProperties.SOUTH))
                newState = newState.setValue(BlockStateProperties.SOUTH, isFormed && partSet.contains(pos.south()));
            if (newState.hasProperty(BlockStateProperties.EAST))
                newState = newState.setValue(BlockStateProperties.EAST, isFormed && partSet.contains(pos.east()));
            if (newState.hasProperty(BlockStateProperties.WEST))
                newState = newState.setValue(BlockStateProperties.WEST, isFormed && partSet.contains(pos.west()));

            newState = newState.setValue(boolProp, isFormed);

            // ★追加: ACTIVEプロパティがあればセットする
            if (newState.hasProperty(ProbeBlock.ACTIVE)) {
                newState = newState.setValue(ProbeBlock.ACTIVE, isFormed && isProbeActive);
            }

            if (state != newState) {
                level.setBlock(pos, newState, 3);
            }
        }
    }

    public static InteractionResult tryOpenMainframeTerminal(Level level, BlockPos pos, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMainframePart part && part.getMasterPos() != null) {
            BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
            if (masterBe instanceof SimpleMachineBlockEntity master) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new com.nishiyu.lunex.menu.MainframeOverviewMenu(id, inventory, master.getBlockPos()),
                            net.minecraft.network.chat.Component.literal("Mainframe Overview")
                    ), master.getBlockPos());
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}