package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.luaj.vm2.LuaTable;

public class RouterAPI {
    private final ServerLuaVM vm;

    public RouterAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    private RouterBlockEntity getTargetRouter(String targetStr) {
        if (vm.hardware == null || targetStr == null || targetStr.isEmpty()) return null;
        BlockPos pos = vm.hardware.resolveDevice(targetStr);
        if (pos != null && vm.hardware.getLevel().getBlockEntity(pos) instanceof RouterBlockEntity router) {
            return router;
        }
        return null;
    }

    // ==========================================
    // 基本ネットワーク機能
    // ==========================================

    @LuaFunction(
            value = "指定したルーターのDHCPサーバーを起動し、設定を適用します。",
            en = "Starts the DHCP server of the specified router and applies settings.",
            args = {"str:target", "str:baseIp", "num:startOctet", "num:poolSize", "str:subnet", "str:gateway"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startDHCPServer(String target, String baseIp, int startOctet, int poolSize, String subnet, String gateway) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                CompoundTag data = router.persistentData;
                data.putBoolean("DHCPServerEnabled", true);
                data.putString("DHCPBaseIP", baseIp);
                data.putInt("DHCPStartOctet", startOctet);
                data.putInt("DHCPPoolSize", poolSize);
                data.putString("DHCPSubnet", subnet);
                data.putString("DHCPGateway", gateway);
                if (!data.contains("DHCPLeases")) data.put("DHCPLeases", new CompoundTag());
                router.setChanged();
                router.requestNetworkUpdate();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したルーターのDHCPサーバーを停止します。",
            en = "Stops the DHCP server of the specified router.",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean stopDHCPServer(String target) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                router.persistentData.putBoolean("DHCPServerEnabled", false);
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したルーターのDNSサーバーを有効化します。",
            en = "Enables the DNS server on the specified router.",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startDNSServer(String target) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                router.persistentData.putBoolean("DNSServerEnabled", true);
                if (!router.persistentData.contains("DNSRecords"))
                    router.persistentData.put("DNSRecords", new CompoundTag());
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したルーターのDNSにAレコードを登録します。",
            en = "Registers an A record in the DNS of the specified router.",
            args = {"str:target", "str:domain", "str:ip"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addDNSRecord(String target, String domain, String ip) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null && router.persistentData.getBoolean("DNSServerEnabled")) {
                CompoundTag records = router.persistentData.getCompound("DNSRecords");
                records.putString(domain, ip);
                router.persistentData.put("DNSRecords", records);
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したルーターのWAN IPを取得します。",
            en = "Gets the WAN IP of the specified router.",
            args = {"str:target"},
            rets = {"str:wanIp"},
            isAsync = false
    )
    public String getWanIp(String target) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                return router.getWanIp();
            }
            return "0.0.0.0";
        });
    }

    // ==========================================
    // データ操作・ルール制御系 API (New)
    // ==========================================

    @LuaFunction(
            value = "指定したポートに届いたパケットを、指定したIPへ転送するルールを追加します。",
            en = "Adds a port forwarding rule to the specified router.",
            args = {"str:target", "num:port", "str:destIp"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setPortForward(String target, int port, String destIp) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                CompoundTag pfTag = router.persistentData.contains("PortForwards") ? router.persistentData.getCompound("PortForwards") : new CompoundTag();
                pfTag.putString(String.valueOf(port), destIp);
                router.persistentData.put("PortForwards", pfTag);
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したポートの転送ルールを削除します。",
            en = "Removes the port forwarding rule for the specified port.",
            args = {"str:target", "num:port"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean removePortForward(String target, int port) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null && router.persistentData.contains("PortForwards")) {
                CompoundTag pfTag = router.persistentData.getCompound("PortForwards");
                pfTag.remove(String.valueOf(port));
                router.persistentData.put("PortForwards", pfTag);
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "ルーターを通過する特定のデータ文字列を置換するルールを追加します。（例: ポート80番のデータ内の 'foo' を 'bar' に置換）",
            en = "Adds a rule to replace specific data strings in packets passing through the router.",
            args = {"str:target", "num:port", "str:targetStr", "str:replaceStr"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addReplaceRule(String target, int port, String targetStr, String replaceStr) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                ListTag rules = router.persistentData.contains("ReplaceRules") ? router.persistentData.getList("ReplaceRules", Tag.TAG_COMPOUND) : new ListTag();
                CompoundTag rule = new CompoundTag();
                rule.putInt("port", port);
                rule.putString("search", targetStr);
                rule.putString("replace", replaceStr);
                rules.add(rule);
                router.persistentData.put("ReplaceRules", rules);
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "設定されているデータ置換ルールをすべてクリアします。",
            en = "Clears all configured data replacement rules.",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean clearReplaceRules(String target) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                router.persistentData.remove("ReplaceRules");
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "特定の送信元IPからの通信をブロック（または許可）するフィルタを追加します。",
            en = "Adds a filter to block (or allow) communication from a specific source IP.",
            args = {"str:target", "str:ip", "bool:isBlocked"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addFilterRule(String target, String ip, boolean isBlocked) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                CompoundTag filters = router.persistentData.contains("FilterRules") ? router.persistentData.getCompound("FilterRules") : new CompoundTag();
                filters.putBoolean(ip, isBlocked);
                router.persistentData.put("FilterRules", filters);
                router.setChanged();
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "ルーターが中継した直近のパケットのログ（送信元、送信先、ポート、データなど）を取得します。",
            en = "Gets the log of recent packets relayed by the router (source, destination, port, data, etc.).",
            args = {"str:target"},
            rets = {"table:logs"},
            isAsync = false
    )
    public LuaTable getPacketLogs(String target) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable logTable = new LuaTable();
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null && router.persistentData.contains("PacketLogs")) {
                ListTag logs = router.persistentData.getList("PacketLogs", Tag.TAG_COMPOUND);
                for (int i = 0; i < logs.size(); i++) {
                    CompoundTag logEntry = logs.getCompound(i);
                    LuaTable entryTable = new LuaTable();
                    entryTable.set("srcIp", logEntry.getString("srcIp"));
                    entryTable.set("destIp", logEntry.getString("destIp"));
                    entryTable.set("port", logEntry.getInt("port"));
                    entryTable.set("data", logEntry.getString("data"));
                    entryTable.set("time", logEntry.getLong("time"));
                    logTable.set(i + 1, entryTable);
                }
            }
            return logTable;
        });
    }

    @LuaFunction(
            value = "ルーターに記録されているパケットログをクリアします。",
            en = "Clears the packet logs recorded on the router.",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean clearPacketLogs(String target) {
        return vm.executeInMainThreadSync(() -> {
            RouterBlockEntity router = getTargetRouter(target);
            if (router != null) {
                router.persistentData.remove("PacketLogs");
                router.setChanged();
                return true;
            }
            return false;
        });
    }
}