package com.nishiyu.lunex.mcnet;

import com.nishiyu.lunex.block.LANCableBlock;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class MCNetUtil {

    public static List<BlockPos> getConnectedDevices(Level level, BlockPos startPos) {
        return getConnectedDevices(level, startPos, 2048);
    }

    public static List<BlockPos> getConnectedDevices(Level level, BlockPos startPos, int maxNodes) {
        List<BlockPos> connectedDevices = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        visited.add(startPos);
        queue.add(startPos);

        int scannedCount = 0;

        while (!queue.isEmpty() && scannedCount < maxNodes) {
            BlockPos currentPos = queue.poll();
            Block currentBlock = level.getBlockState(currentPos).getBlock();
            scannedCount++;

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = currentPos.relative(dir);

                if (visited.contains(neighborPos)) continue;

                if (currentBlock instanceof LANCableBlock) {
                    LANCableBlock.CableState outState = level.getBlockState(currentPos).getValue(LANCableBlock.PROPERTY_BY_DIRECTION.get(dir));
                    if (outState != LANCableBlock.CableState.CONNECTED) continue;
                }

                Block neighborBlock = level.getBlockState(neighborPos).getBlock();
                BlockEntity neighborBE = level.getBlockEntity(neighborPos);
                boolean isCable = neighborBlock instanceof LANCableBlock;

                if (isCable) {
                    LANCableBlock.CableState inState = level.getBlockState(neighborPos).getValue(LANCableBlock.PROPERTY_BY_DIRECTION.get(dir.getOpposite()));
                    if (inState != LANCableBlock.CableState.CONNECTED) continue;
                }

                boolean isDevice = isMCNetDevice(neighborBE);

                if (isCable) {
                    visited.add(neighborPos);
                    queue.add(neighborPos);
                }

                if (isDevice) {
                    visited.add(neighborPos);

                    BlockPos targetPos = neighborPos;

                    if (neighborBE instanceof IMainframePart part && part.getMasterPos() != null) {
                        targetPos = part.getMasterPos();
                    }
                    else if (neighborBE instanceof ScreenBlockEntity sbe && !sbe.isMaster && sbe.masterPos != null) {
                        targetPos = sbe.masterPos;
                    }

                    if (!connectedDevices.contains(targetPos)) {
                        connectedDevices.add(targetPos);
                    }
                }
            }
        }
        return connectedDevices;
    }

    public static void triggerNetworkUpdate(Level level, BlockPos eventPos) {
        if (level.isClientSide) return;

        Set<BlockPos> routersToUpdate = new HashSet<>();
        List<BlockPos> searchStarts = new ArrayList<>();
        searchStarts.add(eventPos);

        for (Direction dir : Direction.values()) {
            searchStarts.add(eventPos.relative(dir));
        }

        for (BlockPos start : searchStarts) {
            List<BlockPos> connected = getConnectedDevices(level, start, 2048);
            for (BlockPos p : connected) {
                if (level.getBlockEntity(p) instanceof RouterBlockEntity) {
                    routersToUpdate.add(p);
                }
            }
        }

        for (BlockPos p : routersToUpdate) {
            if (level.getBlockEntity(p) instanceof RouterBlockEntity router) {
                router.requestNetworkUpdate();
            }
        }
    }

    public static boolean isMCNetDevice(BlockEntity be) {
        if (be instanceof IMainframePart part && part.getMasterPos() != null) {
            return true;
        }
        if (be instanceof com.nishiyu.lunex.mcnet.IMCNetDevice device) {
            return device.isNetworkActive();
        }
        return false;
    }

    public static List<String> getIPsByTag(Level level, BlockPos startPos, String targetTag) {
        List<String> result = new ArrayList<>();
        if (level == null || targetTag == null || targetTag.isEmpty()) return result;

        List<BlockPos> connected = getConnectedDevices(level, startPos, 2048);
        for (BlockPos pos : connected) {
            BlockEntity be = level.getBlockEntity(pos);
            if (isMCNetDevice(be)) {
                net.minecraft.nbt.CompoundTag data;
                if (be instanceof AdvancedMachineBlockEntity machine) data = machine.persistentData;
                else if (be instanceof RouterBlockEntity router) data = router.persistentData;
                else data = be.getPersistentData();

                String tag = data.getString("NetworkTag");

                if (be instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity sm && sm.isMainframeMaster) {
                    if (data.contains("MainframeNetworkTag") && !data.getString("MainframeNetworkTag").isEmpty()) {
                        tag = data.getString("MainframeNetworkTag");
                    }
                }

                if (targetTag.equals(tag)) {
                    String ip = data.getString("IPAddress");
                    if (ip != null && !ip.isEmpty() && !"0.0.0.0".equals(ip)) {
                        result.add(ip);
                    }
                }
            }
        }
        return result;
    }

    public static boolean isTagFormat(String str) {
        if (str == null || str.isEmpty() || str.equals("all")) return false;
        String lower = str.toLowerCase();
        boolean isDirection = lower.equals("up") || lower.equals("down") || lower.equals("north") ||
                lower.equals("south") || lower.equals("east") || lower.equals("west") ||
                lower.equals("front") || lower.equals("forward") || lower.equals("back") ||
                lower.equals("backward") || lower.equals("left") || lower.equals("right");
        boolean isIp = str.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

        return !isDirection && !isIp;
    }

    /**
     * ポータブルデバイス(アイテム)をルーターに登録する共通メソッド
     */
    public static boolean registerPortableDevice(Level level, RouterBlockEntity router, ItemStack stack, Player player, String deviceType, String msgRegistered, String msgFailedIp, String msgDhcpDisabled) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        String deviceId = tag.contains("DeviceId") ? tag.getString("DeviceId") : UUID.randomUUID().toString();
        tag.putString("DeviceId", deviceId);

        if (router.machineId == null) {
            router.machineId = UUID.randomUUID();
            router.setChanged();
        }
        tag.putUUID("NetworkId", router.machineId);

        tag.putLong("RouterPos", router.getBlockPos().asLong());
        tag.putString("RouterDim", level.dimension().location().toString());
        int upgradeLevel = router.getDistanceUpgradeLevel();
        double maxDist = upgradeLevel == 1 ? 256.0 : (upgradeLevel == 2 ? 1024.0 : (upgradeLevel >= 3 ? Double.MAX_VALUE : 64.0));
        tag.putDouble("RouterRange", maxDist);

        CompoundTag rData = router.persistentData;
        if (rData != null && rData.getBoolean("DHCPServerEnabled")) {
            CompoundTag leases = rData.contains("DHCPLeases") ? rData.getCompound("DHCPLeases") : new CompoundTag();
            CompoundTag deviceTypes = rData.contains("DeviceTypes") ? rData.getCompound("DeviceTypes") : new CompoundTag();
            String assignedIp = "";

            if (leases.contains(deviceId)) {
                assignedIp = leases.getString(deviceId);
            } else {
                String baseIp = rData.getString("DHCPBaseIP");
                int start = rData.getInt("DHCPStartOctet");
                int size = rData.getInt("DHCPPoolSize");

                for (int i = 0; i < size; i++) {
                    String testIp = baseIp + "." + (start + i);
                    boolean used = false;
                    for (String key : leases.getAllKeys()) {
                        if (leases.getString(key).equals(testIp)) {
                            used = true;
                            break;
                        }
                    }
                    if (!used) {
                        assignedIp = testIp;
                        break;
                    }
                }

                if (assignedIp.isEmpty()) {
                    for (String key : leases.getAllKeys()) {
                        if (key.length() == 36 && key.split("-").length == 5) {
                            assignedIp = leases.getString(key);
                            leases.remove(key);
                            deviceTypes.remove(key);
                            break;
                        }
                    }
                }

                if (!assignedIp.isEmpty()) {
                    leases.putString(deviceId, assignedIp);
                    deviceTypes.putString(deviceId, deviceType);
                    rData.put("DHCPLeases", leases);
                    rData.put("DeviceTypes", deviceTypes);
                    router.setChanged();
                    router.sync();
                }
            }

            if (!assignedIp.isEmpty()) {
                tag.putString("IPAddress", assignedIp);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(msgRegistered, assignedIp).withStyle(net.minecraft.ChatFormatting.GREEN), true);
                }
            } else {
                if (player != null) {
                    player.displayClientMessage(Component.translatable(msgFailedIp).withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        } else {
            if (player != null) {
                player.displayClientMessage(Component.translatable(msgDhcpDisabled).withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }
}