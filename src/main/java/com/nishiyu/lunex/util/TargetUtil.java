package com.nishiyu.lunex.util;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class TargetUtil {

    public static Direction getDirectionRelative(String dirStr, BlockState state) {
        if (dirStr == null) return null;
        dirStr = dirStr.toLowerCase();
        if (dirStr.equals("up")) return Direction.UP;
        if (dirStr.equals("down")) return Direction.DOWN;

        Direction facing = Direction.NORTH;
        try {
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            } else if (state.hasProperty(BlockStateProperties.FACING)) {
                facing = state.getValue(BlockStateProperties.FACING);
                if (facing == Direction.UP || facing == Direction.DOWN) {
                    facing = Direction.NORTH;
                }
            }
        } catch (Exception ignored) {
        }

        return switch (dirStr) {
            case "front", "forward" -> facing;
            case "back", "backward" -> facing.getOpposite();
            case "left" -> facing.getCounterClockWise();
            case "right" -> facing.getClockWise();
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    public static BlockPos resolveDevice(BlockEntity sourceBE, String targetStr) {
        if (targetStr == null || targetStr.isEmpty() || sourceBE == null || sourceBE.getLevel() == null) return null;

        if (targetStr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            Level level = sourceBE.getLevel();
            if (sourceBE instanceof RouterBlockEntity router) {
                return router.localRoutes.get(targetStr);
            } else if (sourceBE instanceof AdvancedMachineBlockEntity machine) {
                if (machine.persistentData.contains("RouterPos")) {
                    BlockPos routerPos = BlockPos.of(machine.persistentData.getLong("RouterPos"));
                    BlockEntity be = level.getBlockEntity(routerPos);
                    if (be instanceof RouterBlockEntity router) {
                        return router.localRoutes.get(targetStr);
                    }
                }
            }
            return null;
        }

        Direction dir = getDirectionRelative(targetStr, sourceBE.getBlockState());
        if (dir != null) {
            return sourceBE.getBlockPos().relative(dir);
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

        RouterBlockEntity router = null;
        if (sourceBE instanceof RouterBlockEntity r) {
            router = r;
        } else if (sourceBE instanceof AdvancedMachineBlockEntity machine) {
            if (machine.persistentData.contains("RouterPos")) {
                BlockPos routerPos = BlockPos.of(machine.persistentData.getLong("RouterPos"));
                BlockEntity be = machine.getLevel().getBlockEntity(routerPos);
                if (be instanceof RouterBlockEntity r) router = r;
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