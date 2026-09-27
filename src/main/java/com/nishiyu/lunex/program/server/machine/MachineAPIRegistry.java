package com.nishiyu.lunex.program.server.machine;

import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.program.core.APIRegistry;
import com.nishiyu.lunex.program.core.SuggestionDef;
import com.nishiyu.lunex.program.server.machine.api.*;

import java.util.ArrayList;
import java.util.List;

public class MachineAPIRegistry extends APIRegistry {

    @Override
    protected void registerConstants() {
        super.registerConstants();

        String[] dirs = {"UP", "DOWN", "LEFT", "RIGHT", "FRONT", "BACK", "NORTH", "SOUTH", "EAST", "WEST"};
        for (String dir : dirs) {
            suggestions.add(new SuggestionDef("enum", "Direction", dir, new ArrayList<>(), List.of("str:value"),
                    "方向を指定する定数", "Constant for specifying direction", false, true, List.of("Out")));
        }

        for (String type : DeviceAPIRegistry.getRegisteredTypes()) {
            suggestions.add(new SuggestionDef("enum", "DEVICE", type.toUpperCase(), new ArrayList<>(), List.of("str:value"),
                    "デバイスの種類を指定する定数", "Constant for specifying device type", false, true, List.of("Out")));
        }
    }

    @Override
    protected void registerAPIs() {
        registerAPIClass("machine", MachineAPI.class);
        registerAPIClass("printer", PrinterAPI.class);
        registerAPIClass("database", DatabaseAPI.class);
        registerAPIClass("http", HttpAPI.class);
        registerAPIClass("fs", FileAPI.class);
        registerAPIClass("device", DeviceAPI.class);
        registerAPIClass("rs", RedPowerAPI.class);
        registerAPIClass("speaker", SpeakerAPI.class);
        registerAPIClass("commands", CommandAPI.class);
        registerAPIClass("screen", ScreenAPI.class);
        registerAPIClass("net", NetAPI.class);
        registerAPIClass("lan", LanAPI.class);
        registerAPIClass("Pubsub", ServerPubSubAPI.class);
        registerAPIClass("storage", StorageAPI.class);
        registerAPIClass("inventory", InventoryAPI.class);
        registerAPIClass("router", RouterAPI.class);

        suggestions.add(new SuggestionDef("api", "fs", "readAsync", List.of("str:path"), List.of("str:data", "str:error"),
                "ファイルを非同期で読み込みます。", "Reads a file asynchronously.", true, true, List.of("Out", "OnRead")));
        suggestions.add(new SuggestionDef("api", "http", "request", List.of("str:url", "str:method", "str:body", "table:headers"), List.of("table:response"),
                "HTTPリクエストを送信し、結果を待機します。", "Sends an HTTP request and waits for the result.", true, true, List.of("Out", "OnResponse")));
        suggestions.add(new SuggestionDef("api", "http", "websocket", List.of("str:url", "table:headers"), List.of("table:wsObj", "str:error"),
                "WebSocketに接続し、操作オブジェクトを返します。", "Connects to a WebSocket and returns a control object.", true, true, List.of("Out", "OnMessage", "OnClose")));
    }
}