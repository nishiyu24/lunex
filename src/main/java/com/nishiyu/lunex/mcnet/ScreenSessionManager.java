package com.nishiyu.lunex.mcnet;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import com.nishiyu.lunex.webrender.HtmlNodeSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenSessionManager {
    private static final Map<ServerLuaVM, ScreenSessionManager> MANAGERS = new ConcurrentHashMap<>();

    private final ServerLuaVM vm;
    private final String fallbackSessionId;
    private final Map<String, ScreenSession> sessions = new ConcurrentHashMap<>();

    public ScreenSessionManager(ServerLuaVM vm) {
        this.vm = vm;

        ScreenSessionManager oldManager = MANAGERS.put(vm, this);
        if (oldManager != null) {
            oldManager.onShutdown();
        }

        String determinedId = UUID.randomUUID().toString();

        SimpleMachineBlockEntity sm = (vm instanceof CoreMachineServerLuaVM cvm) ? cvm.simpleMachine : null;
        if (sm != null) {
            // ★修正: getCore() 経由に変更
            CompoundTag data = sm.getCore().persistentData;
            if (data.contains("NetworkId")) {
                determinedId = data.getString("NetworkId");
            } else {
                data.putString("NetworkId", determinedId);
                sm.setChanged();
            }
            if (sm.isMainframeMaster && sm.getCore().machineId != null) {
                determinedId = sm.getCore().machineId.toString();
            }
        }

        this.fallbackSessionId = determinedId;

        for (ScreenBlockEntity screen : getScreensFast("all")) {
            ScreenSession session = getSession("all");
            break;
        }
    }

    public static void forceSyncToPlayer(ServerPlayer player, String sessionId) {
        for (ScreenSessionManager manager : MANAGERS.values()) {
            if (manager.isSessionMatch(sessionId)) {
                String targetStr = targetStrFromSessionId(sessionId, manager.getUniversalSessionId());
                ScreenSession session = manager.getSession(targetStr);
                if (session != null) {
                    CompoundTag tag = packDom(session);
                    PacketDistributor.sendToPlayer(player, new com.nishiyu.lunex.network.packet.s2c.AppMessageS2CPacket(sessionId, "dom_sync", tag));
                    return;
                }
            }
        }
    }

    public static CompoundTag packDom(ScreenSession session) {
        CompoundTag tag = new CompoundTag();
        if (session.getDocument() != null && session.getDocument().root != null) {
            tag.put("DomTree", HtmlNodeSerializer.serialize(session.getDocument().root));
            tag.putString("CssString", session.getDocument().authorCss != null ? session.getDocument().authorCss : "");
            tag.putString("ClientScript", session.getDocument().clientScript != null ? session.getDocument().clientScript : "");
            tag.putInt("RootW", session.getWidth());
            tag.putInt("RootH", session.getHeight());
        } else {
            tag.putBoolean("IsEmpty", true);
        }
        return tag;
    }

    private static String targetStrFromSessionId(String sessionId, String universalSessionId) {
        if (sessionId.startsWith(universalSessionId + ":"))
            return sessionId.substring((universalSessionId + ":").length());
        return "all";
    }

    public static void handleFetchRequest(ServerPlayer player, String sessionId, String requestId, String endpoint, String jsonPayload) {
        for (ScreenSessionManager manager : MANAGERS.values()) {
            if (manager.isSessionMatch(sessionId)) {
                manager.vm.forceTriggerEvent("on_fetch_request", player.getName().getString(), requestId, endpoint, jsonPayload);
                return;
            }
        }
    }

    public String getUniversalSessionId() {
        SimpleMachineBlockEntity sm = (vm instanceof CoreMachineServerLuaVM cvm) ? cvm.simpleMachine : null;
        if (sm != null && sm.getLevel() != null) {
            // ★修正: getCore() 経由に変更
            if (sm.getCore().persistentData.contains("RouterPos")) {
                BlockPos routerPos = BlockPos.of(sm.getCore().persistentData.getLong("RouterPos"));
                BlockEntity be = sm.getLevel().getBlockEntity(routerPos);
                if (be instanceof SimpleMachineBlockEntity router && router.isMainframeMaster && router.getCore().machineId != null) {
                    return router.getCore().machineId.toString();
                }
            }
            if (sm.isMainframeMaster && sm.getCore().machineId != null) {
                return sm.getCore().machineId.toString();
            }
            if (sm.getCore().persistentData.contains("NetworkId")) {
                return sm.getCore().persistentData.getString("NetworkId");
            }
        }
        return this.fallbackSessionId;
    }

    public ScreenSession getSession(String targetStr) {
        String currentUniversalId = getUniversalSessionId();
        String sessionId = currentUniversalId;
        int w = 3 * (int) ScreenBlockEntity.RESOLUTION;
        int h = 4 * (int) ScreenBlockEntity.RESOLUTION;

        if (targetStr != null && targetStr.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
            sessionId = currentUniversalId + ":" + targetStr;
        } else {
            List<ScreenBlockEntity> screens = getScreensFast(targetStr);
            if (!screens.isEmpty()) {
                ScreenBlockEntity screen = screens.get(0);
                ScreenBlockEntity master = screen.isMaster ? screen :
                        (screen.masterPos != null && screen.getLevel() != null && screen.getLevel().getBlockEntity(screen.masterPos) instanceof ScreenBlockEntity m ? m : screen);
                w = (int) (master.screenWidth * ScreenBlockEntity.RESOLUTION);
                h = (int) (master.screenHeight * ScreenBlockEntity.RESOLUTION);
            }
        }

        final int finalW = w;
        final int finalH = h;
        return sessions.computeIfAbsent(sessionId, id -> {
            ScreenSession s = new ScreenSession(id, this);
            s.updateSize(finalW, finalH);
            return s;
        });
    }

    public List<ScreenSession> resolveSessions(String targetStr) {
        if ("all".equals(targetStr)) {
            if (sessions.isEmpty()) {
                return List.of(getSession("all"));
            }
            return new ArrayList<>(sessions.values());
        }

        if (targetStr != null && !targetStr.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
            List<ScreenBlockEntity> screens = getScreensFast(targetStr);
            if (screens.isEmpty()) {
                return List.of();
            }
        }

        return List.of(getSession(targetStr));
    }

    private boolean isSessionMatch(String clientId) {
        String currentUniversalId = getUniversalSessionId();
        if (clientId.equals(currentUniversalId)) return true;
        if (clientId.startsWith(currentUniversalId + ":")) return true;

        if (clientId.startsWith("block:")) {
            for (ScreenBlockEntity screen : getScreensFast("all")) {
                BlockPos pos = screen.isMaster ? screen.getBlockPos() : (screen.masterPos != null ? screen.masterPos : screen.getBlockPos());
                String bId = "block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
                if (clientId.equals(bId)) return true;
            }
        }
        return false;
    }

    public void broadcastDom(ScreenSession session) {
        CompoundTag tag = packDom(session);
        PacketDistributor.sendToAllPlayers(new com.nishiyu.lunex.network.packet.s2c.AppMessageS2CPacket(session.getSessionId(), "dom_sync", tag));
    }

    public void onShutdown() {
        for (ScreenSession session : sessions.values()) {
            session.clear();
        }
        sessions.clear();
        MANAGERS.remove(vm, this);
    }

    public List<ScreenBlockEntity> getScreensFast(String targetStr) {
        List<ScreenBlockEntity> screens = new ArrayList<>();
        SimpleMachineBlockEntity sm = (vm instanceof CoreMachineServerLuaVM cvm) ? cvm.simpleMachine : null;
        if (sm == null || sm.getLevel() == null) return screens;

        if (targetStr != null && targetStr.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
            return screens;
        }

        Level level = sm.getLevel();
        BlockPos pos = sm.getBlockPos();

        if (targetStr != null && !targetStr.equals("all")) {
            BlockPos resolvedPos = TargetUtil.resolveDevice(sm, targetStr);
            if (resolvedPos != null) {
                if (level.getBlockEntity(resolvedPos) instanceof ScreenBlockEntity screen) {
                    forceSyncNetworkIdToScreen(screen);
                    screens.add(screen);
                }
            }
            return screens;
        }

        List<BlockPos> connected = MCNetUtil.getConnectedDevices(level, pos, 2048);
        for (BlockPos p : connected) {
            if (level.getBlockEntity(p) instanceof ScreenBlockEntity screen) {
                forceSyncNetworkIdToScreen(screen);
                screens.add(screen);
            }
        }
        return screens;
    }

    private void forceSyncNetworkIdToScreen(ScreenBlockEntity screen) {
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(this.getUniversalSessionId());
        } catch (IllegalArgumentException e) {
            return;
        }

        ScreenBlockEntity master = screen.isMaster ? screen :
                (screen.masterPos != null && screen.getLevel() != null && screen.getLevel().getBlockEntity(screen.masterPos) instanceof ScreenBlockEntity m ? m : screen);

        if (master.networkId == null || !master.networkId.equals(targetUuid)) {
            master.networkId = targetUuid;
            master.sync();

            Level level = master.getLevel();
            if (level != null && master.getBlockState().getBlock() instanceof com.nishiyu.lunex.block.ScreenBlock) {
                Direction facing = master.getBlockState().getValue(com.nishiyu.lunex.block.ScreenBlock.FACING);
                Direction screenRight = facing.getCounterClockWise();

                for (int x = 0; x < master.screenWidth; x++) {
                    for (int y = 0; y < master.screenHeight; y++) {
                        if (x == 0 && y == 0) continue;

                        BlockPos p = master.getBlockPos().relative(screenRight, x).above(y);
                        if (level.getBlockEntity(p) instanceof ScreenBlockEntity sbe) {
                            if (sbe.networkId == null || !sbe.networkId.equals(targetUuid)) {
                                sbe.networkId = targetUuid;
                                sbe.sync();
                            }
                        }
                    }
                }
            }
        } else if (screen.networkId == null || !screen.networkId.equals(targetUuid)) {
            screen.networkId = targetUuid;
            screen.sync();
        }
    }
}