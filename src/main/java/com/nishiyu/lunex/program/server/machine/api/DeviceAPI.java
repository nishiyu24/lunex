package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.mcnet.ScreenSessionManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public class DeviceAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public DeviceAPI() {}
    public DeviceAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return "device"; }

    @Override
    public String getRequiredFeature() { return ""; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new DeviceAPI(vm);
    }

    private Direction parseDirection(String str) {
        if (str == null) return null;
        return switch (str.toLowerCase()) {
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> null;
        };
    }

    @LuaFunction(
            value = "指定したターゲットのデバイスオブジェクトを取得し、操作用メソッドをまとめたテーブルを返します。",
            args = {"str:target", "str:type"},
            rets = {"table:device"}
    )
    public LuaValue get(String targetStr, String typeStr) {
        if (targetStr == null) return LuaValue.NIL;

        if (!isPresent(targetStr)) {
            return LuaValue.NIL;
        }

        String actualType = getType(targetStr);
        String requestedType = (typeStr == null || typeStr.isEmpty()) ? "auto" : typeStr.toLowerCase();

        String typeKey;
        if (requestedType.equals("auto") || requestedType.equals("default")) {
            typeKey = actualType;
        } else {
            if (!requestedType.equals(actualType)) {
                return LuaValue.NIL;
            }
            typeKey = requestedType;
        }

        DeviceAPIRegistry.DeviceBuilder builder = DeviceAPIRegistry.getBuilder(typeKey);

        if (builder == null) {
            typeKey = "default";
            builder = DeviceAPIRegistry.getBuilder("default");
        }

        LuaTable wrapper = new LuaTable();
        wrapper.set("target", LuaValue.valueOf(targetStr));
        wrapper.set("type", LuaValue.valueOf(typeKey));

        wrapper.set("getType", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(getType(targetStr)); }
        });
        wrapper.set("isPresent", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(isPresent(targetStr)); }
        });

        if (typeKey.equals("database")) {
            buildInventoryWrapper(wrapper, targetStr);
        }

        if (builder != null) {
            builder.build(this, wrapper, targetStr);
        }

        return wrapper;
    }

    public void buildScreenWrapper(LuaTable obj, String target) {
        ScreenAPI screen = vm.getOrCreateAPI(ScreenAPI.class, ScreenAPI::new);
        obj.set("getWidth", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(screen.getWidth(target)); } });
        obj.set("getHeight", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(screen.getHeight(target)); } });
        obj.set("load", new org.luaj.vm2.lib.VarArgFunction() {
            @Override
            public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                String source = args.arg(1).tojstring();
                LuaValue arg2 = args.arg(2);
                LuaTable bindings = null;
                String css = null;
                String script = null;
                int nextArgIdx = 2;
                if (arg2.istable()) {
                    bindings = (LuaTable) arg2;
                    nextArgIdx = 3;
                }
                if (args.narg() >= nextArgIdx && !args.arg(nextArgIdx).isnil()) css = args.arg(nextArgIdx).tojstring();
                if (args.narg() >= nextArgIdx + 1 && !args.arg(nextArgIdx + 1).isnil()) script = args.arg(nextArgIdx + 1).tojstring();
                return LuaValue.valueOf(screen.loadWithBindings(target, source, bindings, css, script));
            }
        });
        obj.set("reload", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(screen.reload(target)); } });
        obj.set("clearScreen", new ZeroArgFunction() { @Override public LuaValue call() { screen.clearScreen(target); return LuaValue.NIL; } });
        obj.set("setLODColor", new OneArgFunction() { @Override public LuaValue call(LuaValue color) { screen.setLODColor(target, color.toint()); return LuaValue.NIL; } });
        obj.set("linkSpeakers", new OneArgFunction() { @Override public LuaValue call(LuaValue speakerTargets) { return LuaValue.valueOf(screen.linkSpeakers(target, speakerTargets.checktable())); } });
    }

    public void buildSpeakerWrapper(LuaTable obj, String target) {
        SpeakerAPI speaker = vm.getOrCreateAPI(SpeakerAPI.class, SpeakerAPI::new);
        obj.set("setMaxSoundDistance", new OneArgFunction() { @Override public LuaValue call(LuaValue dist) { return LuaValue.valueOf(speaker.setMaxSoundDistance(target, dist.todouble())); } });
        obj.set("playSound", new TwoArgFunction() { @Override public LuaValue call(LuaValue sound, LuaValue pitch) { return LuaValue.valueOf(speaker.playSound(target, sound.tojstring(), pitch.todouble())); } });
    }

    public void buildMachineWrapper(LuaTable obj, String target) {
        MachineAPI machine = vm.getOrCreateAPI(MachineAPI.class, MachineAPI::new);
        obj.set("getEnergy", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(machine.getEnergy(target)); } });
        obj.set("getItemCount", new OneArgFunction() { @Override public LuaValue call(LuaValue slot) { return LuaValue.valueOf(machine.getItemCount(target, slot.toint())); } });
        obj.set("getItemName", new OneArgFunction() { @Override public LuaValue call(LuaValue slot) { return LuaValue.valueOf(machine.getItemName(target, slot.toint())); } });
        obj.set("dig", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(machine.dig(target)); } });
        obj.set("place", new OneArgFunction() { @Override public LuaValue call(LuaValue slot) { return LuaValue.valueOf(machine.place(target, slot.toint())); } });
        buildRSWrapper(obj, target);
    }

    public void buildRSWrapper(LuaTable obj, String target) {
        RedPowerAPI rs = vm.getOrCreateAPI(RedPowerAPI.class, RedPowerAPI::new);
        obj.set("setRedstoneOutput", new OneArgFunction() { @Override public LuaValue call(LuaValue power) { rs.setRedstoneOutput(target, power.toint()); return LuaValue.NIL; } });
        obj.set("getAnalogRedstoneInput", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(rs.getAnalogRedstoneInput(target)); } });
        obj.set("getRedstoneInput", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(rs.getRedstoneInput(target)); } });
    }

    public void buildRouterWrapper(LuaTable obj, String target) {
        RouterAPI router = vm.getOrCreateAPI(RouterAPI.class, RouterAPI::new);
        obj.set("startDHCPServer", new org.luaj.vm2.lib.VarArgFunction() {
            @Override
            public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                return LuaValue.valueOf(router.startDHCPServer(
                        target,
                        args.arg(1).tojstring(),
                        args.arg(2).toint(),
                        args.arg(3).toint(),
                        args.arg(4).tojstring(),
                        args.arg(5).tojstring()
                ));
            }
        });
        obj.set("stopDHCPServer", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(router.stopDHCPServer(target)); } });
        obj.set("startDNSServer", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(router.startDNSServer(target)); } });
        obj.set("addDNSRecord", new TwoArgFunction() { @Override public LuaValue call(LuaValue domain, LuaValue ip) { return LuaValue.valueOf(router.addDNSRecord(target, domain.tojstring(), ip.tojstring())); } });
        obj.set("getWanIp", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(router.getWanIp(target)); } });
    }

    public void buildInventoryWrapper(LuaTable obj, String target) {
        InventoryAPI inv = vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new);
        obj.set("getInventorySize", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(inv.getInventorySize(target)); } });
        obj.set("getItemName", new OneArgFunction() { @Override public LuaValue call(LuaValue slot) { return LuaValue.valueOf(inv.getItemName(target, slot.toint())); } });
        obj.set("getItemCount", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue slotOrName) {
                if (slotOrName.isstring() && !slotOrName.isnumber()) {
                    String name = slotOrName.tojstring();
                    int total = 0;
                    LuaTable results = inv.searchItem(LuaValue.valueOf(target), name);
                    for (int i = 1; i <= results.length(); i++) {
                        LuaTable match = (LuaTable) results.get(i);
                        total += match.get("count").toint();
                    }
                    return LuaValue.valueOf(total);
                }
                return LuaValue.valueOf(inv.getItemCount(target, slotOrName.toint()));
            }
        });
        obj.set("getMaxStackSize", new OneArgFunction() { @Override public LuaValue call(LuaValue slot) { return LuaValue.valueOf(inv.getMaxStackSize(target, slot.toint())); } });
        obj.set("getTags", new OneArgFunction() { @Override public LuaValue call(LuaValue slot) { return LuaValue.valueOf(inv.getTags(target, slot.toint())); } });
        obj.set("listItems", new ZeroArgFunction() { @Override public LuaValue call() { return inv.listItems(target); } });
        obj.set("searchItem", new OneArgFunction() { @Override public LuaValue call(LuaValue itemName) { return inv.searchItem(LuaValue.valueOf(target), itemName.tojstring()); } });
        obj.set("moveItem", new org.luaj.vm2.lib.ThreeArgFunction() { @Override public LuaValue call(LuaValue from, LuaValue to, LuaValue amt) { return LuaValue.valueOf(inv.moveItem(target, from.toint(), to.toint(), amt.toint())); } });
        obj.set("pushItem", new TwoArgFunction() { @Override public LuaValue call(LuaValue mSlot, LuaValue amt) { return LuaValue.valueOf(inv.pushItem(target, mSlot.toint(), amt.toint())); } });
        obj.set("pullItem", new TwoArgFunction() { @Override public LuaValue call(LuaValue tSlot, LuaValue amt) { return LuaValue.valueOf(inv.pullItem(target, tSlot.toint(), amt.toint())); } });
    }

    public void buildPrinterWrapper(LuaTable obj, String target) {
        PrinterAPI printer = vm.getOrCreateAPI(PrinterAPI.class, PrinterAPI::new);
        obj.set("writeDisc", new TwoArgFunction() { @Override public LuaValue call(LuaValue scriptName, LuaValue code) { return printer.writeDisc(target, scriptName.tojstring(), code.tojstring()); } });
        obj.set("printBook", new TwoArgFunction() { @Override public LuaValue call(LuaValue title, LuaValue content) { return printer.printBook(target, title.tojstring(), content.tojstring()); } });
    }

    public void buildDatabaseWrapper(LuaTable obj, String target) {
        DatabaseAPI dbAPI = vm.getOrCreateAPI(DatabaseAPI.class, DatabaseAPI::new);
        obj.set("getUsageMB", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(dbAPI.getUsageMB(target)); } });
        obj.set("getUsedBytes", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(dbAPI.getUsedBytes(target)); } });
        obj.set("getMaxBytes", new ZeroArgFunction() { @Override public LuaValue call() { return LuaValue.valueOf(dbAPI.getMaxBytes(target)); } });
        obj.set("uploadProgram", new org.luaj.vm2.lib.TwoArgFunction() { @Override public LuaValue call(LuaValue name, LuaValue code) { return LuaValue.valueOf(dbAPI.uploadProgram(target, name.tojstring(), code.tojstring())); } });
        obj.set("downloadProgram", new OneArgFunction() { @Override public LuaValue call(LuaValue name) { String code = dbAPI.downloadProgram(target, name.tojstring()); return code != null ? LuaValue.valueOf(code) : LuaValue.NIL; } });
        obj.set("listPrograms", new ZeroArgFunction() { @Override public LuaValue call() { return dbAPI.listPrograms(target); } });
    }

    public void buildProbeWrapper(LuaTable obj, String target) {
        ProbeAPI probe = vm.getOrCreateAPI(ProbeAPI.class, ProbeAPI::new);
        obj.set("setFaceEnabled", new org.luaj.vm2.lib.TwoArgFunction() { @Override public LuaValue call(LuaValue face, LuaValue enabled) { return LuaValue.valueOf(probe.setFaceEnabled(target, face.tojstring(), enabled.toboolean())); } });
        obj.set("getFaceEnabled", new OneArgFunction() { @Override public LuaValue call(LuaValue face) { return LuaValue.valueOf(probe.getFaceEnabled(target, face.tojstring())); } });
        obj.set("setRedstone", new org.luaj.vm2.lib.TwoArgFunction() { @Override public LuaValue call(LuaValue face, LuaValue power) { return LuaValue.valueOf(probe.setRedstone(target, face.tojstring(), power.toint())); } });
        obj.set("getRedstone", new OneArgFunction() { @Override public LuaValue call(LuaValue face) { return LuaValue.valueOf(probe.getRedstone(target, face.tojstring())); } });
        obj.set("getBlockName", new OneArgFunction() { @Override public LuaValue call(LuaValue face) { return LuaValue.valueOf(probe.getBlockName(target, face.tojstring())); } });
    }

    public boolean isPresent(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            if (targetStr == null) return false;
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return false;

            if (targetStr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                return isIpDevicePresent(targetStr);
            }

            if (TargetUtil.isPortableScreen(machine, targetStr)) return true;

            BlockPos pos = getActionTargetPos(targetStr);
            if (pos != null && machine.getLevel() != null) {
                if (!machine.getLevel().isEmptyBlock(pos)) return true;
            }

            ScreenSessionManager sm = vm.getOrCreateAPI(ScreenSessionManager.class, ScreenSessionManager::new);
            if (!sm.getScreensFast(targetStr).isEmpty()) return true;

            InventoryAPI.TargetInfo target = vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new).resolveTarget(targetStr);
            if (target != null && !target.level().isEmptyBlock(target.pos())) return true;

            return false;
        });
    }

    public String getType(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            if (targetStr == null) return "none";
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return "none";

            if (targetStr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                return resolveDeviceTypeFromIp(targetStr);
            }

            if (TargetUtil.isPortableScreen(machine, targetStr)) {
                return "portable_screen";
            }

            BlockPos pos = getActionTargetPos(targetStr);
            Level level = machine.getLevel();
            if (pos != null && level != null && !level.isEmptyBlock(pos)) {
                BlockState state = level.getBlockState(pos);
                String rawType = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                return DeviceAPIRegistry.getDeviceTypeFromBlockName(rawType);
            }

            ScreenSessionManager sm = vm.getOrCreateAPI(ScreenSessionManager.class, ScreenSessionManager::new);
            if (!sm.getScreensFast(targetStr).isEmpty()) {
                return "screen";
            }

            InventoryAPI.TargetInfo target = vm.getOrCreateAPI(InventoryAPI.class, InventoryAPI::new).resolveTarget(targetStr);
            if (target != null && !target.level().isEmptyBlock(target.pos())) {
                BlockState state = target.level().getBlockState(target.pos());
                String rawType = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                return DeviceAPIRegistry.getDeviceTypeFromBlockName(rawType);
            }

            return "none";
        });
    }

    public BlockPos getActionTargetPos(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || machine.getLevel() == null || targetStr == null) return null;
        Level level = machine.getLevel();

        if (targetStr.contains(":")) {
            String[] parts = targetStr.split(":", 2);
            BlockPos basePos = machine.resolveDevice(parts[0]);
            if (basePos != null) {
                Direction dir = parseDirection(parts[1]);
                if (dir != null) return basePos.relative(dir);
            }
            return null;
        }

        BlockPos resolvedPos = machine.resolveDevice(targetStr);
        if (resolvedPos != null) {
            BlockState state = level.getBlockState(resolvedPos);
            if (state.getBlock() instanceof com.nishiyu.lunex.block.ProbeBlock) {
                for (Direction dir : Direction.values()) {
                    if (state.getValue(com.nishiyu.lunex.block.ProbeBlock.getPropertyByDirection(dir))) {
                        return resolvedPos.relative(dir);
                    }
                }
            }
            return resolvedPos;
        }

        return null;
    }

    private SimpleMachineBlockEntity getRouterForVM() {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine != null && machine.getLevel() != null) {
            if (machine.isMainframeMaster && machine.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                return machine;
            }
            if (machine.getPersistentData().contains("RouterPos")) {
                long posLong = machine.getPersistentData().getLong("RouterPos");
                net.minecraft.world.level.block.entity.BlockEntity be = machine.getLevel().getBlockEntity(net.minecraft.core.BlockPos.of(posLong));
                if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                    return sm;
                }
            }
        }
        return null;
    }

    private boolean isIpDevicePresent(String ip) {
        SimpleMachineBlockEntity router = getRouterForVM();
        if (router == null) return false;

        if (router.persistentData.contains("DHCPLeases")) {
            net.minecraft.nbt.CompoundTag leases = router.persistentData.getCompound("DHCPLeases");
            for (String key : leases.getAllKeys()) {
                if (leases.getString(key).equals(ip)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveDeviceTypeFromIp(String ip) {
        SimpleMachineBlockEntity router = getRouterForVM();
        if (router == null) return "none";

        if (!router.persistentData.contains("DHCPLeases")) return "none";

        net.minecraft.nbt.CompoundTag leases = router.persistentData.getCompound("DHCPLeases");
        net.minecraft.nbt.CompoundTag deviceTypes = router.persistentData.contains("DeviceTypes")
                ? router.persistentData.getCompound("DeviceTypes") : new net.minecraft.nbt.CompoundTag();

        for (String key : leases.getAllKeys()) {
            if (leases.getString(key).equals(ip)) {
                if (deviceTypes.contains(key)) {
                    return deviceTypes.getString(key);
                }

                if (key.length() == 36) {
                    return "portable_screen";
                } else {
                    return "machine";
                }
            }
        }
        return "none";
    }
}