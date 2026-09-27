package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.mcnet.IPUtils;
import com.nishiyu.lunex.mcnet.McNetManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;

import java.util.Map;

public class NetAPI {
    private final ServerLuaVM vm;

    public NetAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    private CompoundTag getBeData(BlockEntity be) {
        if (be instanceof AdvancedMachineBlockEntity machine) return machine.persistentData;
        if (be instanceof RouterBlockEntity router) return router.persistentData;
        return be.getPersistentData();
    }

    private String getDeviceType(BlockEntity be) {
        if (be == null || be.getLevel() == null) return "unknown";
        String rawType = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
        return DeviceAPIRegistry.getDeviceTypeFromBlockName(rawType);
    }

    private RouterBlockEntity findConnectedRouter() {
        if (vm.hardware == null || vm.hardware.getLevel() == null) return null;
        CompoundTag data = getBeData(vm.hardware);

        if (data != null && data.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
            BlockEntity be = vm.hardware.getLevel().getBlockEntity(routerPos);
            if (be instanceof RouterBlockEntity router) return router;
        }
        return null;
    }

    @LuaFunction(
            value = "ネットワーク設定を行います。",
            en = "Configures the network settings.",
            args = {"str:ip", "str:subnet", "str:gateway"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setConfig(String ip, String subnet, String gateway) {
        if (!IPUtils.isValidIp(ip) || !IPUtils.isValidIp(subnet) || !IPUtils.isValidIp(gateway)) return false;
        return vm.executeInMainThreadSync(() -> {
            BlockEntity be = vm.hardware != null ? vm.hardware : null;
            if (be == null) return false;

            CompoundTag data = getBeData(be);
            if (data != null) {
                String oldIp = data.getString("IPAddress");
                RouterBlockEntity router = findConnectedRouter();
                if (router != null && oldIp != null && !oldIp.isEmpty()) {
                    router.localRoutes.remove(oldIp);
                }
                data.putString("IPAddress", ip);
                data.putString("SubnetMask", subnet);
                data.putString("DefaultGateway", gateway);
                be.setChanged();
                if (router != null && !"0.0.0.0".equals(ip) && vm.isRunning) {
                    router.localRoutes.put(ip, be.getBlockPos());
                }
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "現在のIPアドレスを取得します。",
            en = "Gets the current IP address.",
            args = {},
            rets = {"str:ip"},
            isAsync = false
    )
    public String getIp() {
        BlockEntity be = vm.hardware != null ? vm.hardware : null;
        if (be == null) return "0.0.0.0";
        CompoundTag data = getBeData(be);
        return (data != null && data.contains("IPAddress")) ? data.getString("IPAddress") : "0.0.0.0";
    }

    @LuaFunction(
            value = "現在のサブネットマスクを取得します。",
            en = "Gets the current subnet mask.",
            args = {},
            rets = {"str:subnet"},
            isAsync = false
    )
    public String getSubnet() {
        BlockEntity be = vm.hardware != null ? vm.hardware : null;
        if (be == null) return "0.0.0.0";
        CompoundTag data = getBeData(be);
        return (data != null && data.contains("SubnetMask")) ? data.getString("SubnetMask") : "0.0.0.0";
    }

    @LuaFunction(
            value = "現在のデフォルトゲートウェイを取得します。",
            en = "Gets the current default gateway.",
            args = {},
            rets = {"str:gateway"},
            isAsync = false
    )
    public String getGateway() {
        BlockEntity be = vm.hardware != null ? vm.hardware : null;
        if (be == null) return "0.0.0.0";
        CompoundTag data = getBeData(be);
        return (data != null && data.contains("DefaultGateway")) ? data.getString("DefaultGateway") : "0.0.0.0";
    }

    @LuaFunction(
            value = "指定したIPアドレスに向けてデータを送信します。",
            en = "Sends data to the specified IP address.",
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
            RouterBlockEntity router = findConnectedRouter();
            if (router == null) {
                vm.triggerEvent("print", "Network Error: Gateway unreachable. (No Router found)");
                return false;
            }

            boolean isSameSubnet = IPUtils.isSameSubnet(myIp, targetIp, mySubnet);

            if (isSameSubnet) {
                BlockPos targetPos = router.localRoutes.get(targetIp);
                if (targetPos != null) {
                    BlockEntity targetBe = router.getLevel().getBlockEntity(targetPos);
                    if (targetBe instanceof AdvancedMachineBlockEntity machine && machine.vm.isRunning) {
                        machine.vm.triggerEvent("net_receive", myIp, port, data);
                        return true;
                    }
                }
            } else {
                // サブネット外（WAN）への送信
                if (router.isRunning()) {
                    boolean sent = false;
                    for (ServerLuaVM wanNode : McNetManager.getAllWanNodes().values()) {
                        wanNode.triggerEvent("net_wan_receive", router.getWanIp(), port, data);
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
            en = "Resolves a domain name to an IP address via the connected router.",
            args = {"str:domain"},
            rets = {"str:ip"},
            isAsync = true
    )
    public String resolve(String domain) {
        if (domain == null || domain.isEmpty()) return null;
        if (IPUtils.isValidIp(domain)) return domain;

        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = findConnectedRouter();
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
            en = "Returns a list (ip, type, tag) of all devices connected to the network.",
            args = {},
            rets = {"table:devices"},
            isAsync = true
    )
    public LuaTable scan() {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            RouterBlockEntity router = findConnectedRouter();
            if (router == null) return result;
            Level level = router.getLevel();
            if (level == null) return result;

            int index = 1;

            for (Map.Entry<String, BlockPos> entry : router.localRoutes.entrySet()) {
                String ip = entry.getKey();
                BlockPos pos = entry.getValue();
                BlockEntity be = level.getBlockEntity(pos);

                if (be != null) {
                    CompoundTag data = getBeData(be);
                    if (data != null) {
                        LuaTable dev = new LuaTable();
                        dev.set("ip", ip);
                        dev.set("type", getDeviceType(be));
                        dev.set("tag", data.getString("NetworkTag"));
                        result.set(index++, dev);
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
            en = "Gets a list of IPs for the specified block type.",
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
            en = "Gets a list of IPs matching the specified network tag.",
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
            en = "Sets a network tag for this device (for searching).",
            args = {"str:tag"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setTag(String tag) {
        return vm.executeInMainThreadSync(() -> {
            BlockEntity be = vm.hardware != null ? vm.hardware : null;
            if (be == null) return false;

            if (be instanceof ProbeBlockEntity probe) {
                probe.setNetworkTag(tag);
                return true;
            } else if (be instanceof AdvancedMachineBlockEntity machine) {
                machine.setMachineLabel(tag);
                return true;
            } else if (be instanceof RouterBlockEntity targetRouter) {
                targetRouter.setMachineLabel(tag);
                return true;
            } else {
                CompoundTag data = getBeData(be);
                if (data != null) {
                    data.putString("NetworkTag", tag);
                    be.setChanged();
                    if (be.getLevel() != null) {
                        be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
                    }
                    return true;
                }
            }
            return false;
        });
    }
}