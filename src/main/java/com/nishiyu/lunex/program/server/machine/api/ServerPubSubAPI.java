package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.VirtualStorage;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.server.ServerPubSubManager;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.HashMap;
import java.util.Map;

public class ServerPubSubAPI {
    private final ServerLuaVM vm;

    public ServerPubSubAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    @LuaFunction(
            value = "指定したチャンネルにデータをPublish（配信）します。",
            en = "Publishes data to the specified channel.",
            args = {"str:channel", "table:data"},
            rets = {"bool:success"}
    )
    public boolean publish(String channel, LuaTable data) {
        if (data.istable()) {
            Map<String, Object> mapData = new HashMap<>();
            LuaValue k = LuaValue.NIL;
            while (true) {
                Varargs n = data.next(k);
                if ((k = n.arg1()).isnil()) break;
                LuaValue val = n.arg(2);
                if (val.isint()) mapData.put(k.tojstring(), val.toint());
                else if (val.isnumber()) mapData.put(k.tojstring(), val.todouble());
                else if (val.isboolean()) mapData.put(k.tojstring(), val.toboolean());
                else mapData.put(k.tojstring(), val.tojstring());
            }
            ServerPubSubManager.publish(channel, mapData);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定したストレージオブジェクトを対象に、差分データの自動Publishを開始します。",
            en = "Starts automatic publishing of differential data for the specified storage object.",
            args = {"str:channel", "table:storageObj"},
            rets = {"bool:success"}
    )
    public boolean publishVirtualStorage(String channel, LuaValue storageObj) {
        if (storageObj == null || storageObj.isnil()) return false;

        Thread.startVirtualThread(() -> {
            boolean firstRun = true;
            while (vm.isRunning) {
                try {
                    Thread.sleep(500);

                    final boolean isFirst = firstRun;
                    firstRun = false;

                    vm.mainThreadTasks.add(() -> {
                        if (!vm.isRunning) return;
                        Map<String, Object> currentItems = new HashMap<>();
                        String foundMethod = "NONE";

                        try {
                            Object result = null;
                            Object javaApi = null;

                            if (storageObj.isuserdata()) {
                                javaApi = storageObj.checkuserdata();
                            } else if (storageObj.istable()) {
                                LuaValue ud = storageObj.get("userdata");
                                if (!ud.isnil() && ud.isuserdata()) {
                                    javaApi = ud.checkuserdata();
                                }
                            }

                            // リフレクションを排除し、VirtualStorageクラスへ安全にキャスト
                            if (javaApi instanceof VirtualStorage vs) {
                                result = vs.getAllItems();
                                foundMethod = "Java API -> VirtualStorage.getAllItems()";
                            }

                            // Lua API (テーブル関数) のフォールバック
                            if (result == null && storageObj.istable()) {
                                String[] methodNames = {"getAllItems", "getItems", "list", "getInventory", "getItemList", "getAll"};
                                for (String mName : methodNames) {
                                    LuaValue func = storageObj.get(mName);
                                    if (!func.isnil() && func.isfunction()) {
                                        LuaValue res = func.call(storageObj);
                                        if (res.istable() || res.isuserdata()) {
                                            result = res;
                                            foundMethod = "Lua API -> " + mName;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (result instanceof Map<?, ?> map) {
                                for (Map.Entry<?, ?> entry : map.entrySet()) {
                                    currentItems.put(String.valueOf(entry.getKey()), entry.getValue());
                                }
                            } else if (result instanceof LuaTable table) {
                                LuaValue k = LuaValue.NIL;
                                while (true) {
                                    Varargs n = table.next(k);
                                    if ((k = n.arg1()).isnil()) break;
                                    LuaValue val = n.arg(2);

                                    if (k.isstring() && val.isint()) {
                                        currentItems.put(k.tojstring(), val.toint());
                                    } else if (k.isint() && val.istable()) {
                                        LuaValue idVal = val.get("id");
                                        if (idVal.isnil()) idVal = val.get("name");
                                        LuaValue countVal = val.get("count");

                                        if (!idVal.isnil() && !countVal.isnil()) {
                                            currentItems.put(idVal.tojstring(), countVal.toint());
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            Lunex.LOGGER.error("[Server PubSub] Critical error during extraction: ", e);
                        }

                        ServerPubSubManager.publish(channel, currentItems);

                        if (isFirst || "NONE".equals(foundMethod)) {
                            Lunex.LOGGER.info("[Server PubSub] Tracking started. Channel: " + channel + " | Method: " + foundMethod + " | Total Items: " + currentItems.size());
                        }
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
            Lunex.LOGGER.info("[Server PubSub] Stopped tracking storage. Channel: " + channel);
        });

        return true;
    }
}