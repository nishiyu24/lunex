package com.nishiyu.lunex.program.client.api;

import com.nishiyu.lunex.client.ClientPubSubManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;
import java.util.Map;

public class ClientPubSubAPI {
    private final String sessionId;

    public ClientPubSubAPI(String sessionId) {
        this.sessionId = sessionId;
    }

    @LuaFunction(
            value = "指定したチャンネルの購読を開始します。（UIバインドを使わずに値が欲しい場合）",
            en = "Starts subscribing to the specified channel.",
            args = {"str:channel"}
    )
    public void subscribe(String channel) {
        ClientPubSubManager.requestSubscribe(sessionId, channel);
    }

    @LuaFunction(
            value = "キャッシュされているチャンネルの単一データを取得します。",
            en = "Gets single data from the cached channel.",
            args = {"str:channel", "str:path"},
            rets = {"str:value"}
    )
    public String getValue(String channel, String path) {
        return ClientPubSubManager.getValue(channel, path);
    }

    @LuaFunction(
            value = "VirtualStorageチャンネルのデータを、配列形式(id, count)に変換して取得します。",
            en = "Gets VirtualStorage channel data converted to an array format (id, count).",
            args = {"str:channel"},
            rets = {"table:items"}
    )
    public LuaTable getStorageList(String channel) {
        List<Map<String, String>> list = ClientPubSubManager.getStorageListAsMap(channel);
        LuaTable luaList = new LuaTable();
        if (list != null) {
            int i = 1;
            for (Map<String, String> item : list) {
                LuaTable luaItem = new LuaTable();
                luaItem.set("id", LuaValue.valueOf(item.get("id")));
                luaItem.set("count", LuaValue.valueOf(item.get("count")));
                luaList.set(i++, luaItem);
            }
        }
        return luaList;
    }
}