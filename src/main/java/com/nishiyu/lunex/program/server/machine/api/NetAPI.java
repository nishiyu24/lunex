package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.mcnet.IPUtils;
import com.nishiyu.lunex.mcnet.McNetManager;
import com.nishiyu.lunex.mcnet.MCNetUtil;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;

import java.util.List;

public class NetAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public NetAPI() {}
    public NetAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return "net"; }

    @Override
    public String getRequiredFeature() { return ""; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new NetAPI(vm);
    }

    private CompoundTag getBeData(BlockEntity be) {
        return be.getPersistentData();
    }

    private String getDeviceType(BlockEntity be) {
        if (be == null || be.getLevel() == null) return "unknown";
        String rawType = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
        return DeviceAPIRegistry.getDeviceTypeFromBlockName(rawType);
    }

    private SimpleMachineBlockEntity findConnectedRouter() {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || machine.getLevel() == null) return null;

        if (machine.isMainframeMaster && machine.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
            return machine;
        }

        CompoundTag data = getBeData(machine);
        if (data != null && data.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
            BlockEntity be = machine.getLevel().getBlockEntity(routerPos);
            if (be instanceof SimpleMachineBlockEntity router && router.isMainframeMaster && router.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                return router;
            }
        }
        return null;
    }

    @LuaFunction(
            value = "ネットワーク設定を行います。",
            args = {"str:ip", "str:subnet", "str:gateway"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setConfig(String ip, String subnet, String gateway) {
        if (!IPUtils.isValidIp(ip) || !IPUtils.isValidIp(subnet) || !IPUtils.isValidIp(gateway)) return false;
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return false;

            CompoundTag data = getBeData(machine);
            if (data != null) {
                data.putString("IPAddress", ip);
                data.putString("SubnetMask", subnet);
                data.putString("DefaultGateway", gateway);
                machine.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "現在のIPアドレスを取得します。",
            args = {},
            rets = {"str:ip"},
            isAsync = false
    )
    public String getIp() {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null) return "0.0.0.0";
        CompoundTag data = getBeData(machine);
        return (data != null && data.contains("IPAddress")) ? data.getString("IPAddress") : "0.0.0.0";
    }

    @LuaFunction(
            value = "現在のサブネットマスクを取得します。",
            args = {},
            rets = {"str:subnet"},
            isAsync = false
    )
    public String getSubnet() {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null) return "0.0.0.0";
        CompoundTag data = getBeData(machine);
        return (data != null && data.contains("SubnetMask")) ? data.getString("SubnetMask") : "0.0.0.0";
    }

    @LuaFunction(
            value = "現在のデフォルトゲートウェイを取得します。",
            args = {},
            rets = {"str:gateway"},
            isAsync = false
    )
    public String getGateway() {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null) return "0.0.0.0";
        CompoundTag data = getBeData(machine);
        return (data != null && data.contains("DefaultGateway")) ? data.getString("DefaultGateway") : "0.0.0.0";
    }

    @LuaFunction(
            value = "指定したIPアドレスに向けてデータを送信します。",
            args = {"str:targetIp", "num:port", "str:data"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean send(String targetIp, int port, String data) {
        String myIp = getIp();
        String mySubnet = getSubnet();

        if ("0.0.0.0".equals(myIp) || "0.0.0.0".equals(targetIp)) return false;

        if ("127.0.0.1".equals(targetIp) || myIp.equals(targetIp)) {
            vm.triggerEvent("net_receive", "127.0.0.1", port, data);
            return true;
        }

        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = findConnectedRouter();
            if (router == null) {
                vm.triggerEvent("print", "Network Error: Gateway unreachable. (No Router found)");
                return false;
            }

            boolean isSameSubnet = IPUtils.isSameSubnet(myIp, targetIp, mySubnet);

            if (isSameSubnet) {
                List<BlockPos> connected = MCNetUtil.getConnectedDevices(router.getLevel(), router.getBlockPos());
                BlockPos targetPos = null;

                for (BlockPos p : connected) {
                    BlockEntity be = router.getLevel().getBlockEntity(p);
                    if (be != null) {
                        CompoundTag tag = getBeData(be);
                        if (targetIp.equals(tag.getString("IPAddress"))) {
                            targetPos = p;
                            break;
                        }
                    }
                }

                if (targetPos != null) {
                    BlockEntity targetBe = router.getLevel().getBlockEntity(targetPos);
                    if (targetBe instanceof SimpleMachineBlockEntity targetMachine && targetMachine.vm != null && targetMachine.vm.isRunning) {
                        targetMachine.vm.triggerEvent("net_receive", myIp, port, data);
                        return true;
                    }
                }
            } else {
                if (router.persistentData.getBoolean("IsRunningStatus") || (router.vm != null && router.vm.isRunning)) {
                    boolean sent = false;
                    for (ServerLuaVM wanNode : McNetManager.getAllWanNodes().values()) {
                        String routerWanIp = router.persistentData.contains("AssignedWanIP") ? router.persistentData.getString("AssignedWanIP") : "0.0.0.0";
                        wanNode.triggerEvent("net_wan_receive", routerWanIp, port, data);
                        sent = true;
                    }
                    return sent;
                }
            }
            return false;
        });
    }

    @LuaFunction(
            value = "ドメイン名からIPを解決します。",
            args = {"str:domain"},
            rets = {"str:ip"},
            isAsync = true
    )
    public String resolve(String domain) {
        if (domain == null || domain.isEmpty()) return null;
        if (IPUtils.isValidIp(domain)) return domain;

        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = findConnectedRouter();
            if (router != null) {
                CompoundTag rData = router.persistentData;
                if (rData != null && rData.getBoolean("DNSServerEnabled")) {
                    CompoundTag records = rData.getCompound("DNSRecords");
                    if (records.contains(domain)) {
                        return records.getString(domain);
                    }
                }
            }
            return null;
        });
    }

    @LuaFunction(
            value = "ネットワークに接続されている全デバイスのリスト(ip, type, tag)を返します。",
            args = {},
            rets = {"table:devices"},
            isAsync = true
    )
    public LuaTable scan() {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            SimpleMachineBlockEntity router = findConnectedRouter();
            if (router == null) return result;
            Level level = router.getLevel();
            if (level == null) return result;

            int index = 1;

            List<BlockPos> connected = MCNetUtil.getConnectedDevices(level, router.getBlockPos());
            for (BlockPos pos : connected) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    CompoundTag data = getBeData(be);
                    if (data != null && data.contains("IPAddress")) {
                        String ip = data.getString("IPAddress");
                        if (ip != null && !ip.isEmpty() && !"0.0.0.0".equals(ip)) {
                            LuaTable dev = new LuaTable();
                            dev.set("ip", ip);
                            dev.set("type", getDeviceType(be));
                            dev.set("tag", data.getString("NetworkTag"));
                            result.set(index++, dev);
                        }
                    }
                }
            }

            CompoundTag rData = router.persistentData;
            if (rData != null && rData.contains("DHCPLeases")) {
                CompoundTag leases = rData.getCompound("DHCPLeases");
                for (String macOrUuid : leases.getAllKeys()) {
                    if (macOrUuid.length() == 36 && macOrUuid.split("-").length == 5) {
                        LuaTable dev = new LuaTable();
                        dev.set("ip", leases.getString(macOrUuid));
                        dev.set("type", "portable_screen");
                        dev.set("tag", "");
                        result.set(index++, dev);
                    }
                }
            }

            return result;
        });
    }

    @LuaFunction(
            value = "指定したブロックタイプのIP一覧を取得します。",
            args = {"str:type"},
            rets = {"table:ips"},
            isAsync = true
    )
    public LuaTable getIPsByType(String type) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            LuaTable all = scan();
            int index = 1;
            for (int i = 1; i <= all.length(); i++) {
                LuaTable dev = (LuaTable) all.get(i);
                if (type.equalsIgnoreCase(dev.get("type").tojstring())) {
                    result.set(index++, dev.get("ip"));
                }
            }
            return result;
        });
    }

    @LuaFunction(
            value = "指定したネットワークタグのIP一覧を取得します。",
            args = {"str:tag"},
            rets = {"table:ips"},
            isAsync = true
    )
    public LuaTable getIPsByTag(String tag) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            LuaTable all = scan();
            int index = 1;
            for (int i = 1; i <= all.length(); i++) {
                LuaTable dev = (LuaTable) all.get(i);
                if (tag.equals(dev.get("tag").tojstring())) {
                    result.set(index++, dev.get("ip"));
                }
            }
            return result;
        });
    }

    @LuaFunction(
            value = "このデバイスにネットワークタグを設定します。（検索用）",
            args = {"str:tag"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setTag(String tag) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return false;

            machine.setMachineLabel(tag);
            return true;
        });
    }
}