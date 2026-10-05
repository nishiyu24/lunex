package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.mcnet.ScreenSessionManager;
import com.nishiyu.lunex.server.ServerProgramData;
import com.nishiyu.lunex.network.packet.s2c.AppMessageS2CPacket;
import com.nishiyu.lunex.util.WorkspaceManager;
import com.nishiyu.lunex.network.packet.s2c.ErrorToastS2CPacket;
import com.nishiyu.lunex.api.MainframeConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record AppMessageC2SPacket(String sessionId, String action, CompoundTag payload) implements CustomPacketPayload {
    public static final Type<AppMessageC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "app_msg_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AppMessageC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AppMessageC2SPacket::sessionId,
            ByteBufCodecs.STRING_UTF8, AppMessageC2SPacket::action,
            ByteBufCodecs.COMPOUND_TAG, AppMessageC2SPacket::payload,
            AppMessageC2SPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (action) {
                    case "request_sync":
                        ScreenSessionManager.forceSyncToPlayer(player, sessionId);
                        break;
                    case "fetch_request":
                        ScreenSessionManager.handleFetchRequest(player, sessionId, payload.getString("requestId"), payload.getString("endpoint"), payload.getString("json"));
                        break;
                    case "machine_command":
                        handleMachineCommand(player, payload);
                        break;
                    case "bio_mob_command":
                        handleBioMobCommand(player, payload);
                        break;
                    case "editor_request_program":
                        handleEditorRequest(player, payload);
                        break;
                    case "sync_program":
                        handleSyncProgram(player, payload);
                        break;
                    case "delete_program":
                        handleDeleteProgram(player, payload);
                        break;
                    case "broadcast_event":
                        PacketDistributor.sendToAllPlayers(new AppMessageS2CPacket(sessionId, "server_event", payload));
                        break;
                }
            }
        });
    }

    private String resolveWorkspaceId(ServerPlayer player, CompoundTag tag) {
        if (tag.contains("workspaceId") && !tag.getString("workspaceId").isEmpty()) {
            return tag.getString("workspaceId");
        }
        if (tag.contains("pos")) {
            BlockPos pos = BlockPos.of(tag.getLong("pos"));
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof TurtleBotBlockEntity turtle) {
                return turtle.getCore().workspaceId != null && !turtle.getCore().workspaceId.isEmpty() ? turtle.getCore().workspaceId : "default";
            }
            if (be instanceof SimpleMachineBlockEntity sm) {
                // ★修正: getCore() 経由に変更
                if (sm.getCore().persistentData != null && sm.getCore().persistentData.contains("WorkspaceId")) {
                    return sm.getCore().persistentData.getString("WorkspaceId");
                }
            }
        }
        if (tag.contains("entityId")) {
            int entityId = tag.getInt("entityId");
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                net.minecraft.world.entity.Entity e = sl.getEntity(entityId);
                if (e instanceof com.nishiyu.lunex.entity.CustomBioMobEntity bio) {
                    return bio.workspaceId != null && !bio.workspaceId.isEmpty() ? bio.workspaceId : "default";
                }
            }
        }
        return "default";
    }

    private void handleMachineCommand(ServerPlayer player, CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        String cmd = tag.getString("command").toLowerCase();
        String arg = tag.getString("arg");

        Level level = player.level();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof TurtleBotBlockEntity machine) {
            if (machine.getCore().isPrivateMode && machine.getCore().ownerUUID != null && !machine.getCore().ownerUUID.equals(player.getUUID())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] You do not have permission to modify this machine.§r"), true);
                return;
            }

            switch (cmd) {
                case "setup":
                    machine.getCore().workspaceId = arg;
                    machine.setChanged();
                    machine.sync();
                    ServerProgramData.load(arg);
                    break;
                case "sync_files":
                    if (machine.getCore().workspaceId != null && !machine.getCore().workspaceId.isEmpty()) {
                        ServerProgramData.load(machine.getCore().workspaceId);
                        machine.getCore().installedPrograms.clear();
                        machine.getCore().installedPrograms.addAll(ServerProgramData.getPrograms(machine.getCore().workspaceId).keySet());
                        machine.setChanged();
                        machine.sync();
                    }
                    break;
                case "touch":
                    if (!arg.isEmpty()) {
                        arg = enforceValidExtension(arg);
                        if (isHiddenFile(arg)) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] Cannot create files starting with '.'§r"), true);
                            break;
                        }
                        String wsTouch = machine.getCore().workspaceId == null || machine.getCore().workspaceId.isEmpty() ? "default" : machine.getCore().workspaceId;
                        ServerProgramData.load(wsTouch);
                        ServerProgramData.getPrograms(wsTouch).putIfAbsent(arg, "-- New File\n");
                        ServerProgramData.save(wsTouch);
                        if (!machine.getCore().installedPrograms.contains(arg)) {
                            machine.getCore().installedPrograms.add(arg);
                            machine.setChanged();
                            machine.sync();
                        }
                    }
                    break;
                case "mkdir":
                    if (!arg.isEmpty()) {
                        if (isHiddenFile(arg)) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] Cannot create folders starting with '.'§r"), true);
                            break;
                        }
                        String wsMkdir = machine.getCore().workspaceId == null || machine.getCore().workspaceId.isEmpty() ? "default" : machine.getCore().workspaceId;
                        ServerProgramData.load(wsMkdir);
                        ServerProgramData.getPrograms(wsMkdir).putIfAbsent(arg, "");
                        ServerProgramData.save(wsMkdir);
                        if (!machine.getCore().installedPrograms.contains(arg)) {
                            machine.getCore().installedPrograms.add(arg);
                            machine.setChanged();
                            machine.sync();
                        }
                    }
                    break;
                case "rm":
                    if (!arg.isEmpty()) {
                        String wsRm = machine.getCore().workspaceId == null || machine.getCore().workspaceId.isEmpty() ? "default" : machine.getCore().workspaceId;
                        ServerProgramData.load(wsRm);
                        boolean removed = false;

                        if (ServerProgramData.getPrograms(wsRm).remove(arg) != null) {
                            machine.getCore().installedPrograms.remove(arg);
                            removed = true;
                        }

                        String dirPrefix = arg + "/";
                        List<String> keysToRemove = new ArrayList<>();
                        for (String key : ServerProgramData.getPrograms(wsRm).keySet()) {
                            if (key.equals(dirPrefix) || key.startsWith(dirPrefix)) {
                                keysToRemove.add(key);
                            }
                        }
                        for (String key : keysToRemove) {
                            ServerProgramData.getPrograms(wsRm).remove(key);
                            machine.getCore().installedPrograms.remove(key);
                            removed = true;
                        }

                        if (removed) {
                            ServerProgramData.save(wsRm);
                            machine.setChanged();
                            machine.sync();
                        }
                    }
                    break;
                case "rename":
                    String[] parts = arg.split(" ");
                    if (parts.length == 2) {
                        String oldName = parts[0];
                        String rawNewName = parts[1];
                        String wsRename = machine.getCore().workspaceId == null || machine.getCore().workspaceId.isEmpty() ? "default" : machine.getCore().workspaceId;
                        ServerProgramData.load(wsRename);

                        boolean isFolder = false;
                        for (String key : ServerProgramData.getPrograms(wsRename).keySet()) {
                            if (key.equals(oldName + "/") || key.startsWith(oldName + "/")) {
                                isFolder = true;
                                break;
                            }
                        }
                        String newName = isFolder ? rawNewName : enforceValidExtension(rawNewName);
                        if (isHiddenFile(newName)) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] Cannot create files starting with '.'§r"), true);
                            break;
                        }
                        boolean renamed = false;

                        if (ServerProgramData.getPrograms(wsRename).containsKey(oldName)) {
                            String content = ServerProgramData.getPrograms(wsRename).remove(oldName);
                            ServerProgramData.getPrograms(wsRename).put(newName, content);
                            machine.getCore().installedPrograms.remove(oldName);
                            machine.getCore().installedPrograms.add(newName);
                            renamed = true;
                        }

                        String oldDirPrefix = oldName + "/";
                        String newDirPrefix = newName + "/";
                        List<String> keysToRename = new ArrayList<>();
                        for (String key : ServerProgramData.getPrograms(wsRename).keySet()) {
                            if (key.equals(oldDirPrefix) || key.startsWith(oldDirPrefix)) {
                                keysToRename.add(key);
                            }
                        }
                        for (String key : keysToRename) {
                            String content = ServerProgramData.getPrograms(wsRename).remove(key);
                            String newKey = newDirPrefix + key.substring(oldDirPrefix.length());
                            ServerProgramData.getPrograms(wsRename).put(newKey, content);
                            machine.getCore().installedPrograms.remove(key);
                            machine.getCore().installedPrograms.add(newKey);
                            renamed = true;
                        }

                        if (renamed) {
                            ServerProgramData.save(wsRename);
                            machine.setChanged();
                            machine.sync();
                        }
                    }
                    break;
                case "boot":
                case "reboot":
                    if (cmd.equals("boot") && !machine.getCore().installedPrograms.contains(arg)) break;
                    String pName = cmd.equals("boot") ? arg : machine.getCore().programName;
                    if (pName != null && !pName.isEmpty()) {
                        if (cmd.equals("boot")) machine.setProgramName(arg);
                        machine.getCore().vm.currentPlayer = player;
                        machine.getCore().vm.workspaceId = machine.getCore().workspaceId;
                        machine.getCore().vm.restartProgram(pName);
                    } else if (machine.getCore().vm.isRunning) {
                        machine.getCore().vm.stopProgram();
                    }
                    break;
                case "stop":
                case "kill":
                    if (machine.getCore().vm.isRunning) machine.getCore().vm.stopProgram();
                    break;
                case "label":
                    machine.setMachineLabel(arg.startsWith("set ") ? arg.substring(4).trim() : (arg.equals("clear") ? "" : machine.getMachineLabel()));
                    break;
                case "set_startup":
                    machine.setProgramName(arg);
                    machine.setChanged();
                    machine.sync();
                    break;
                case "clear_startup":
                    machine.setProgramName("");
                    machine.setChanged();
                    machine.sync();
                    break;
                case "wipe_memory":
                    machine.getCore().persistentData.remove("AutoMemory");
                    machine.setChanged();
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[System] Memory wiped successfully.§r"), true);
                    break;
                case "toggle_private":
                    machine.getCore().isPrivateMode = Boolean.parseBoolean(arg);
                    machine.setChanged();
                    machine.sync();
                    break;
                case "toggle_wake":
                    machine.getCore().wakeOnRedstone = Boolean.parseBoolean(arg);
                    machine.setChanged();
                    machine.sync();
                    break;
                case "toggle_debug":
                    machine.getCore().debugChat = Boolean.parseBoolean(arg);
                    machine.setChanged();
                    machine.sync();
                    break;
                case "show_error":
                    if (machine.getCore().persistentData.contains("LastError")) {
                        String errMsg = machine.getCore().persistentData.getString("LastError");
                        PacketDistributor.sendToPlayer(player, new ErrorToastS2CPacket(errMsg));
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[System] No recent errors found.§r"), true);
                    }
                    break;
                case "clear_error":
                    machine.getCore().persistentData.remove("LastError");
                    machine.setChanged();
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[System] Error history cleared.§r"), true);
                    break;
            }
        } else if (be instanceof SimpleMachineBlockEntity master) {
            // ★修正: getCore() 経由に変更
            if (master.getCore().persistentData.getBoolean("IsPrivateMode")) {
                if (master.getCore().persistentData.contains("OwnerUUID")) {
                    if (!master.getCore().persistentData.getUUID("OwnerUUID").equals(player.getUUID())) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] You do not have permission to modify this machine.§r"), true);
                        return;
                    }
                }
            }

            switch (cmd) {
                case "sync_router":
                    master.sync();
                    break;
                case "boot":
                    if (master.getCore().vm != null) master.getCore().vm.isRunning = true;
                    master.setChanged();
                    master.sync();
                    break;
                case "stop":
                case "kill":
                    if (master.getCore().vm != null) master.getCore().vm.stopProgram();
                    master.setChanged();
                    master.sync();
                    break;
                case "reboot":
                    if (master.getCore().vm != null) master.getCore().vm.restartProgram(master.getCore().getProgramName());
                    master.setChanged();
                    master.sync();
                    break;
                case "label":
                    master.getCore().setMachineLabel(arg.startsWith("set ") ? arg.substring(4).trim() : (arg.equals("clear") ? "" : master.getCore().getMachineLabel()));
                    break;
                case "toggle_private":
                    master.getCore().persistentData.putBoolean("IsPrivateMode", Boolean.parseBoolean(arg));
                    master.setChanged();
                    master.sync();
                    break;
                case "show_error":
                    if (master.getCore().persistentData.contains("LastError")) {
                        String errMsg = master.getCore().persistentData.getString("LastError");
                        PacketDistributor.sendToPlayer(player, new ErrorToastS2CPacket(errMsg));
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[System] No recent errors found.§r"), true);
                    }
                    break;
                case "clear_error":
                    master.getCore().persistentData.remove("LastError");
                    master.setChanged();
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[System] Error history cleared.§r"), true);
                    break;
                case "update_dhcp":
                    if (master.isMainframeMaster && master.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                        String[] updateParts = arg.split("\\|");
                        if (updateParts.length >= 2) {
                            String mac = updateParts[0];
                            String ip = updateParts[1];
                            String port = updateParts.length >= 3 ? updateParts[2] : "";

                            CompoundTag leases = master.getCore().persistentData.contains("DHCPLeases") ? master.getCore().persistentData.getCompound("DHCPLeases") : new CompoundTag();
                            leases.putString(mac, ip);
                            master.getCore().persistentData.put("DHCPLeases", leases);

                            CompoundTag pfTag = master.getCore().persistentData.contains("PortForwards") ? master.getCore().persistentData.getCompound("PortForwards") : new CompoundTag();
                            if (!port.isEmpty()) {
                                pfTag.putString(port, ip);
                            }
                            master.getCore().persistentData.put("PortForwards", pfTag);

                            master.setChanged();
                            master.sync();
                        }
                    }
                    break;
                case "remove_dhcp":
                    if (!arg.isEmpty() && master.isMainframeMaster && master.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                        CompoundTag leases = master.getCore().persistentData.contains("DHCPLeases") ? master.getCore().persistentData.getCompound("DHCPLeases") : new CompoundTag();
                        String ipToRemove = leases.getString(arg);
                        leases.remove(arg);
                        master.getCore().persistentData.put("DHCPLeases", leases);

                        if (!ipToRemove.isEmpty()) {
                            CompoundTag pfTag = master.getCore().persistentData.contains("PortForwards") ? master.getCore().persistentData.getCompound("PortForwards") : new CompoundTag();
                            List<String> keysToRemove = new ArrayList<>();
                            for (String p : pfTag.getAllKeys()) {
                                if (pfTag.getString(p).equals(ipToRemove)) {
                                    keysToRemove.add(p);
                                }
                            }
                            for (String p : keysToRemove) {
                                pfTag.remove(p);
                            }
                            master.getCore().persistentData.put("PortForwards", pfTag);
                        }
                        master.setChanged();
                        master.sync();
                    }
                    break;
                case "set_wan_target":
                    if (master.isMainframeMaster && master.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                        master.getCore().persistentData.putString("WAN_Target", arg);
                        master.setChanged();
                        master.sync();
                    }
                    break;
            }
        }
    }

    private void handleBioMobCommand(ServerPlayer player, CompoundTag tag) {
        int entityId = tag.getInt("entityId");
        String act = tag.getString("command").toLowerCase();
        String dat = tag.getString("arg");

        net.minecraft.world.entity.Entity entity = null;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            entity = serverLevel.getEntity(entityId);
        }

        if (entity instanceof com.nishiyu.lunex.entity.CustomBioMobEntity bioMob) {
            String wsId = bioMob.workspaceId != null && !bioMob.workspaceId.isEmpty() ? bioMob.workspaceId : "biomob_" + bioMob.getUUID().toString().substring(0, 8);

            switch (act) {
                case "request_bio_info":
                    CompoundTag infoTag = new CompoundTag();
                    infoTag.putString("programName", bioMob.programName != null ? bioMob.programName : "");
                    if (bioMob.getPersistentData().contains("LastError")) {
                        infoTag.putString("lastError", bioMob.getPersistentData().getString("LastError"));
                    }
                    infoTag.putBoolean("PrivateMode", bioMob.getPersistentData().getBoolean("PrivateMode"));
                    infoTag.putBoolean("DebugLog", bioMob.getPersistentData().getBoolean("DebugLog"));

                    PacketDistributor.sendToPlayer(player, new AppMessageS2CPacket(this.sessionId(), "bio_info_sync", infoTag));
                    break;

                case "toggle_private":
                    boolean curPriv = bioMob.getPersistentData().getBoolean("PrivateMode");
                    bioMob.getPersistentData().putBoolean("PrivateMode", !curPriv);
                    break;
                case "toggle_debug":
                    boolean curDebug = bioMob.getPersistentData().getBoolean("DebugLog");
                    bioMob.getPersistentData().putBoolean("DebugLog", !curDebug);
                    break;
                case "show_error":
                    if (bioMob.getPersistentData().contains("LastError")) {
                        String errMsg = bioMob.getPersistentData().getString("LastError");
                        PacketDistributor.sendToPlayer(player, new ErrorToastS2CPacket(errMsg));
                    } else {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[System] No recent errors found.§r"), true);
                    }
                    break;
                case "clear_error":
                    bioMob.getPersistentData().remove("LastError");
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[System] Error history cleared.§r"), true);
                    break;

                case "label":
                    if (dat.startsWith("set ")) {
                        bioMob.setCustomName(net.minecraft.network.chat.Component.literal(dat.substring(4).trim()));
                        bioMob.setCustomNameVisible(true);
                    } else if (dat.equals("clear")) {
                        bioMob.setCustomName(null);
                        bioMob.setCustomNameVisible(false);
                    }
                    break;
                case "appearance":
                    String safeAppearance = dat.toLowerCase().trim();
                    bioMob.setAppearance(safeAppearance);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[System] Appearance updated to: " + safeAppearance + "§r"), true);
                    break;
                case "wipe_memory":
                    if (bioMob.vm != null) {
                        bioMob.vm.stopProgram();
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[System] Mob memory wiped successfully.§r"), true);
                    break;
                case "set_startup":
                    bioMob.programName = dat;
                    break;
                case "clear_startup":
                    bioMob.programName = "";
                    break;
                case "boot":
                    if (bioMob.vm != null) {
                        String progName = dat.isEmpty() ? bioMob.programName : dat;
                        if (progName != null && !progName.isEmpty()) {
                            bioMob.programName = progName;
                            bioMob.vm.currentPlayer = player;
                            String code = ServerProgramData.getPrograms(wsId).getOrDefault(progName, "while true do system.sleep(1000) end");
                            bioMob.vm.startCode(code, progName);
                        }
                    }
                    break;
                case "stop":
                    if (bioMob.vm != null) bioMob.vm.stopProgram();
                    break;
                case "reboot":
                    if (bioMob.vm != null) {
                        bioMob.vm.stopProgram();
                        String progName = dat.isEmpty() ? bioMob.programName : dat;
                        if (progName != null && !progName.isEmpty()) {
                            bioMob.programName = progName;
                            bioMob.vm.currentPlayer = player;
                            String code = ServerProgramData.getPrograms(wsId).getOrDefault(progName, "while true do system.sleep(1000) end");
                            bioMob.vm.startCode(code, progName);
                        }
                    }
                    break;
                case "touch":
                    dat = enforceValidExtension(dat);
                    if (isHiddenFile(dat)) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] Cannot create files starting with '.'§r"), true);
                        break;
                    }
                    ServerProgramData.getPrograms(wsId).putIfAbsent(dat, "-- New File\n");
                    ServerProgramData.save(wsId);
                    break;
                case "mkdir":
                    if (isHiddenFile(dat)) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] Cannot create folders starting with '.'§r"), true);
                        break;
                    }
                    ServerProgramData.getPrograms(wsId).putIfAbsent(dat, "");
                    ServerProgramData.save(wsId);
                    break;
                case "rm":
                    if (!dat.isEmpty()) {
                        boolean removed = false;
                        if (ServerProgramData.getPrograms(wsId).remove(dat) != null) removed = true;

                        String dirPrefix = dat + "/";
                        List<String> toRemove = new ArrayList<>();
                        for (String key : ServerProgramData.getPrograms(wsId).keySet()) {
                            if (key.equals(dirPrefix) || key.startsWith(dirPrefix)) toRemove.add(key);
                        }
                        for (String key : toRemove) {
                            ServerProgramData.getPrograms(wsId).remove(key);
                            removed = true;
                        }
                        if (removed) ServerProgramData.save(wsId);
                    }
                    break;
                case "rename":
                    String[] parts = dat.split(" ");
                    if (parts.length == 2) {
                        String oldName = parts[0];
                        String rawNewName = parts[1];

                        boolean isFolder = false;
                        for (String key : ServerProgramData.getPrograms(wsId).keySet()) {
                            if (key.equals(oldName + "/") || key.startsWith(oldName + "/")) {
                                isFolder = true;
                                break;
                            }
                        }
                        String newName = isFolder ? rawNewName : enforceValidExtension(rawNewName);
                        if (isHiddenFile(newName)) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Error] Cannot create files starting with '.'§r"), true);
                            break;
                        }
                        boolean renamed = false;

                        if (ServerProgramData.getPrograms(wsId).containsKey(oldName)) {
                            String content = ServerProgramData.getPrograms(wsId).remove(oldName);
                            ServerProgramData.getPrograms(wsId).put(newName, content);
                            renamed = true;
                        }

                        String oldDirPrefix = oldName + "/";
                        String newDirPrefix = newName + "/";
                        List<String> keysToRename = new ArrayList<>();
                        for (String key : ServerProgramData.getPrograms(wsId).keySet()) {
                            if (key.equals(oldDirPrefix) || key.startsWith(oldDirPrefix)) {
                                keysToRename.add(key);
                            }
                        }
                        for (String key : keysToRename) {
                            String content = ServerProgramData.getPrograms(wsId).remove(key);
                            String newKey = newDirPrefix + key.substring(oldDirPrefix.length());
                            ServerProgramData.getPrograms(wsId).put(newKey, content);
                            renamed = true;
                        }

                        if (renamed) ServerProgramData.save(wsId);
                    }
                    break;
                case "request_file":
                    dat = enforceValidExtension(dat);
                    String reqContent = ServerProgramData.getPrograms(wsId).getOrDefault(dat, "");
                    CompoundTag replyTag = new CompoundTag();
                    replyTag.putString("programName", dat);
                    replyTag.putString("content", reqContent);
                    PacketDistributor.sendToPlayer(player, new AppMessageS2CPacket("global", "file_content", replyTag));
                    break;
            }

            if (act.equals("sync_files") || act.equals("touch") || act.equals("rm") || act.equals("rename") || act.equals("mkdir")) {
                ServerProgramData.load(wsId);
                List<String> fileList = new ArrayList<>(ServerProgramData.getPrograms(wsId).keySet());
                CompoundTag listTag = new CompoundTag();
                ListTag nbtList = new ListTag();
                for (String f : fileList) nbtList.add(StringTag.valueOf(f));
                listTag.put("files", nbtList);
                PacketDistributor.sendToPlayer(player, new AppMessageS2CPacket("global", "file_list", listTag));
            }
        }
    }

    private void handleEditorRequest(ServerPlayer player, CompoundTag tag) {
        String pName = tag.getString("programName");
        String wsId = resolveWorkspaceId(player, tag);

        if (pName.contains("/")) {
            int idx = pName.indexOf("/");
            wsId = pName.substring(0, idx);
            pName = pName.substring(idx + 1);
        }

        pName = enforceValidExtension(pName);

        String data = ServerProgramData.getPrograms(wsId).get(pName);
        if (data == null) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                Path filePath = WorkspaceManager.getBaseDir(server).resolve(wsId).resolve(pName);
                try {
                    data = Files.exists(filePath) ? Files.readString(filePath) : "";
                    if (!data.isEmpty()) ServerProgramData.getPrograms(wsId).put(pName, data);
                } catch (IOException e) {
                    data = "";
                }
            } else {
                data = "";
            }
        }

        CompoundTag replyTag = new CompoundTag();
        replyTag.putString("programName", pName);
        replyTag.putString("content", data);
        PacketDistributor.sendToPlayer(player, new AppMessageS2CPacket("global", "file_content", replyTag));
    }

    private void handleSyncProgram(ServerPlayer player, CompoundTag tag) {
        String pName = tag.getString("programName");
        String code = tag.getString("code");
        String wsId = resolveWorkspaceId(player, tag);

        if (pName.contains("/")) {
            int idx = pName.indexOf("/");
            wsId = pName.substring(0, idx);
            pName = pName.substring(idx + 1);
        }

        pName = enforceValidExtension(pName);
        if (isHiddenFile(pName)) return;

        ServerProgramData.getPrograms(wsId).put(pName, code);
        MinecraftServer server = player.getServer();
        if (server != null) {
            try {
                Path wsDir = WorkspaceManager.getBaseDir(server).resolve(wsId);
                if (!Files.exists(wsDir)) Files.createDirectories(wsDir);
                Files.writeString(wsDir.resolve(pName), code);
            } catch (IOException ignored) {
            }
        }
    }

    private void handleDeleteProgram(ServerPlayer player, CompoundTag tag) {
        String pName = tag.getString("programName");
        String wsId = resolveWorkspaceId(player, tag);

        if (pName.contains("/")) {
            int idx = pName.indexOf("/");
            wsId = pName.substring(0, idx);
            pName = pName.substring(idx + 1);
        }

        pName = enforceValidExtension(pName);

        ServerProgramData.getPrograms(wsId).remove(pName);
        MinecraftServer server = player.getServer();
        if (server != null) {
            try {
                Path filePath = WorkspaceManager.getBaseDir(server).resolve(wsId).resolve(pName);
                if (Files.exists(filePath)) Files.delete(filePath);
            } catch (IOException ignored) {
            }
        }
    }

    private String enforceValidExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return fileName;
        if (fileName.endsWith("/")) return fileName;

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") ||
                lower.endsWith(".css") ||
                lower.endsWith(".json") ||
                lower.endsWith(".lua") ||
                lower.endsWith(".txt")) {
            return fileName;
        }
        return fileName + ".lua";
    }

    private boolean isHiddenFile(String path) {
        if (path == null || path.isEmpty()) return false;
        for (String part : path.split("/")) {
            if (part.startsWith(".")) return true;
        }
        return false;
    }
}