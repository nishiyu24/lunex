// 上書き: ClientScriptManager.java
package com.nishiyu.lunex.program.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nishiyu.lunex.client.renderer.ARGlassesHudRenderer;
import com.nishiyu.lunex.program.client.api.ClientDOMAPI;
import com.nishiyu.lunex.program.client.api.ClientFetchAPI;
import com.nishiyu.lunex.program.client.api.ClientPubSubAPI;
import com.nishiyu.lunex.program.core.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientScriptManager {

    public static final ClientLuaVM CLIENT_VM = new ClientLuaVM();
    private static final Map<String, LuaTable> sessionEnvironments = new ConcurrentHashMap<>();
    private static final Map<String, LuaValue> pendingRequests = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, LuaValue>> serverEventListeners = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    public static void addPendingRequest(String requestId, LuaValue callback) {
        pendingRequests.put(requestId, callback);
    }

    public static void addServerEventListener(String sessionId, String eventName, LuaValue callback) {
        serverEventListeners.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(eventName, callback);
    }

    public static void loadClientScript(String sessionId, String scriptContent) {
        if (scriptContent == null || scriptContent.trim().isEmpty()) return;

        CLIENT_VM.executeLocally(() -> {
            LuaTable env = new LuaTable();
            LuaTable mt = new LuaTable();
            mt.set(LuaValue.INDEX, CLIENT_VM.globals);
            env.setmetatable(mt);

            injectAPI(env, "document", new ClientDOMAPI(sessionId));
            injectAPI(env, "", new ClientFetchAPI(sessionId));
            injectAPI(env, "pubsub", new ClientPubSubAPI(sessionId));

            sessionEnvironments.put(sessionId, env);

            try {
                LuaValue chunk = CLIENT_VM.globals.load(scriptContent, "script_" + sessionId, env);
                chunk.call();
            } catch (Exception e) {
                System.err.println("[ClientLua Script Error] Session " + sessionId + ": " + e.getMessage());
            }
        });
    }

    private static void injectAPI(LuaTable env, String namespace, Object apiInstance) {
        LuaValue coercedInstance = CoerceJavaToLua.coerce(apiInstance);
        LuaTable apiTable = new LuaTable();

        for (java.lang.reflect.Method m : apiInstance.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(LuaFunction.class)) {
                String methodName = m.getName();
                LuaValue methodVal = coercedInstance.get(methodName);

                apiTable.set(methodName, new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                        int n = args.narg();
                        LuaValue[] combined = new LuaValue[n + 1];
                        combined[0] = coercedInstance;
                        for (int i = 0; i < n; i++) combined[i + 1] = args.arg(i + 1);
                        return methodVal.invoke(LuaValue.varargsOf(combined));
                    }
                });
            }
        }

        if (namespace == null || namespace.isEmpty()) {
            for (LuaValue key : apiTable.keys()) {
                env.set(key, apiTable.get(key));
            }
        } else {
            env.set(namespace, apiTable);
        }
    }

    public static void triggerClientEvent(String sessionId, String functionName, Object arg) {
        LuaTable env = sessionEnvironments.get(sessionId);
        if (env != null) {
            LuaValue func = env.get(functionName);
            if (func.isfunction()) {
                CLIENT_VM.executeLocally(() -> {
                    try {
                        if (arg != null) {
                            // ★ 修正: JavaからLuaへの型変換を厳密に行う (クリック座標などの引数を正しく渡す)
                            LuaValue luaArg;
                            if (arg instanceof String) luaArg = LuaValue.valueOf((String) arg);
                            else if (arg instanceof Integer) luaArg = LuaValue.valueOf((Integer) arg);
                            else if (arg instanceof Double) luaArg = LuaValue.valueOf((Double) arg);
                            else if (arg instanceof Float) luaArg = LuaValue.valueOf((Float) arg);
                            else if (arg instanceof Boolean) luaArg = LuaValue.valueOf((Boolean) arg);
                            else luaArg = LuaValue.valueOf(arg.toString()); // 未知の型は文字列として渡す

                            func.call(luaArg);
                        } else {
                            func.call();
                        }
                    } catch (Exception e) {
                        System.err.println("[ClientLua Callback Error] " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    public static void handleFetchResponse(String requestId, String jsonResponse) {
        LuaValue callback = pendingRequests.remove(requestId);
        if (callback != null && callback.isfunction()) {
            CLIENT_VM.executeLocally(() -> {
                try {
                    LuaValue responseTable = jsonToLuaTable(jsonResponse);
                    callback.call(responseTable);
                } catch (Exception e) {
                    System.err.println("[ClientLua Fetch Callback Error] " + e.getMessage());
                }
            });
        }
    }

    public static void handleServerEvent(String sessionId, String eventName, String jsonPayload) {
        handleLocalEvent(sessionId, eventName, jsonToLuaTable(jsonPayload));
    }

    public static void handleLocalEvent(String sessionId, String eventName, LuaValue payload) {
        Map<String, LuaValue> listeners = serverEventListeners.get(sessionId);
        if (listeners != null) {
            LuaValue callback = listeners.get(eventName);
            if (callback != null && callback.isfunction()) {
                CLIENT_VM.executeLocally(() -> {
                    try {
                        callback.call(payload);
                    } catch (Exception e) {
                        System.err.println("[ClientLua ServerEvent Error] " + e.getMessage());
                    }
                });
            }
        }
    }

    private static LuaTable jsonToLuaTable(String jsonStr) {
        try {
            JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
            return jsonObjectToLuaTable(json);
        } catch (Exception e) {
            return new LuaTable();
        }
    }

    private static LuaTable jsonObjectToLuaTable(JsonObject json) {
        LuaTable table = new LuaTable();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement el = entry.getValue();
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) table.set(entry.getKey(), LuaValue.valueOf(el.getAsDouble()));
                else if (el.getAsJsonPrimitive().isBoolean())
                    table.set(entry.getKey(), LuaValue.valueOf(el.getAsBoolean()));
                else table.set(entry.getKey(), LuaValue.valueOf(el.getAsString()));
            } else if (el.isJsonObject()) {
                table.set(entry.getKey(), jsonObjectToLuaTable(el.getAsJsonObject()));
            }
        }
        return table;
    }

    public static LuaTable getEnv(String sessionId) {
        return sessionEnvironments.get(sessionId);
    }

    public static void clearSession(String sessionId) {
        sessionEnvironments.remove(sessionId);
        serverEventListeners.remove(sessionId);
        ARGlassesHudRenderer.clearSession(sessionId);
    }
}