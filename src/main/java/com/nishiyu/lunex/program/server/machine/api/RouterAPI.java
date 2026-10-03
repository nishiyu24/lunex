package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.luaj.vm2.LuaTable;

public class RouterAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public RouterAPI() {}
    public RouterAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return MainframeConstants.API_ROUTER; }

    @Override
    public String getRequiredFeature() { return MainframeConstants.FEATURE_ROUTER; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new RouterAPI(vm);
    }

    private SimpleMachineBlockEntity getTargetRouter(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || targetStr == null || targetStr.isEmpty()) return null;

        // "self" などの特別な指定があればマスター機をそのまま返す
        if (targetStr.equals("self") || targetStr.equals("localhost")) {
            if (machine.isMainframeMaster && machine.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                return machine;
            }
            return null;
        }

        BlockPos pos = machine.resolveDevice(targetStr);
        if (pos != null && machine.getLevel() != null) {
            net.minecraft.world.level.block.entity.BlockEntity be = machine.getLevel().getBlockEntity(pos);
            // 対象がSimpleMachineであり、かつルーター機能を持っているかを判定
            if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                return sm;
            }
        }
        return null;
    }

    @LuaFunction(
            value = "指定したルーターのDHCPサーバーを起動し、設定を適用します。",
            args = {"str:target", "str:baseIp", "num:startOctet", "num:poolSize", "str:subnet", "str:gateway"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startDHCPServer(String target, String baseIp, int startOctet, int poolSize, String subnet, String gateway) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
                // ※ルーター側の明示的な networkUpdate メソッドは削除されたため、単純に sync などを呼ぶ
                if(router.vm != null) router.vm.forceTriggerEvent("network_updated");
                return true;
            }
            return false;
        });
    }

    @LuaFunction(
            value = "指定したルーターのDHCPサーバーを停止します。",
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean stopDHCPServer(String target) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startDNSServer(String target) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target", "str:domain", "str:ip"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addDNSRecord(String target, String domain, String ip) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target"},
            rets = {"str:wanIp"},
            isAsync = false
    )
    public String getWanIp(String target) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
            return (router != null && router.persistentData.contains("AssignedWanIP"))
                    ? router.persistentData.getString("AssignedWanIP")
                    : "0.0.0.0";
        });
    }

    @LuaFunction(
            value = "指定したポートに届いたパケットを、指定したIPへ転送するルールを追加します。",
            args = {"str:target", "num:port", "str:destIp"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setPortForward(String target, int port, String destIp) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target", "num:port"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean removePortForward(String target, int port) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            value = "ルーターを通過する特定のデータ文字列を置換するルールを追加します。",
            args = {"str:target", "num:port", "str:targetStr", "str:replaceStr"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addReplaceRule(String target, int port, String targetStr, String replaceStr) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean clearReplaceRules(String target) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target", "str:ip", "bool:isBlocked"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addFilterRule(String target, String ip, boolean isBlocked) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target"},
            rets = {"table:logs"},
            isAsync = false
    )
    public LuaTable getPacketLogs(String target) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable logTable = new LuaTable();
            SimpleMachineBlockEntity router = getTargetRouter(target);
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
            args = {"str:target"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean clearPacketLogs(String target) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity router = getTargetRouter(target);
            if (router != null) {
                router.persistentData.remove("PacketLogs");
                router.setChanged();
                return true;
            }
            return false;
        });
    }
}