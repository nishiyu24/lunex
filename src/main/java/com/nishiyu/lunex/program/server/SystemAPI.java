package com.nishiyu.lunex.program.server;

import com.google.gson.*;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.server.ServerProgramData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SystemAPI {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ServerLuaVM vm;
    private final Map<Integer, Thread> activeTimers = new ConcurrentHashMap<>();
    private final AtomicInteger timerCounter = new AtomicInteger(0);

    public SystemAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    @LuaFunction(
            value = "指定した間隔（ミリ秒）で定期的にイベントを発火させるタイマーを開始します。",
            en = "Starts a timer that periodically triggers an event at the specified interval (in milliseconds).",
            args = {"num:interval", "str:callbackName"},
            rets = {"num:timerId"},
            isAsync = true,
            execOuts = {"Out", "OnTimer"}
    )
    public int startTimer(int interval, String callbackName) {
        int timerId = timerCounter.incrementAndGet();
        // ★修正: タイマー起動時の実行IDを保持する
        final int execId = vm.executionId.get();

        Thread t = Thread.startVirtualThread(() -> {
            // ★修正: isRunningだけでなく、実行IDが最新であるか（再起動されていないか）チェック
            while (vm.isValidRun(execId) && activeTimers.containsKey(timerId)) {
                try {
                    Thread.sleep(interval);
                    if (vm.isValidRun(execId) && activeTimers.containsKey(timerId)) {
                        vm.triggerEvent(callbackName, timerId);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            activeTimers.remove(timerId);
        });
        activeTimers.put(timerId, t);
        return timerId;
    }

    @LuaFunction(
            value = "指定した時間（ミリ秒）後に一度だけイベントを発火させるタイマーを開始します。",
            en = "Starts a timer that triggers an event exactly once after the specified delay (in milliseconds).",
            args = {"num:delay", "str:callbackName"},
            rets = {"num:timerId"},
            isAsync = true,
            execOuts = {"Out", "OnTimeout"}
    )
    public int startTimeout(int delay, String callbackName) {
        int timerId = timerCounter.incrementAndGet();
        // ★修正: こちらも同様に実行IDを保持する
        final int execId = vm.executionId.get();

        Thread t = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(delay);
                if (vm.isValidRun(execId) && activeTimers.containsKey(timerId)) {
                    vm.triggerEvent(callbackName, timerId);
                }
            } catch (InterruptedException e) {
            } finally {
                activeTimers.remove(timerId);
            }
        });
        activeTimers.put(timerId, t);
        return timerId;
    }

    @LuaFunction(
            value = "指定したタイマーIDの待機スレッドを停止(キャンセル)します。",
            en = "Stops (cancels) the waiting thread of the specified timer ID.",
            args = {"num:timerId"},
            rets = {},
            isAsync = true
    )
    public void stopTimer(int timerId) {
        Thread t = activeTimers.remove(timerId);
        if (t != null) {
            t.interrupt();
        }
    }

    @LuaFunction(
            value = "現在のシステム時刻（ミリ秒）を取得します。",
            en = "Gets the current system time in milliseconds.",
            args = {},
            rets = {"num:timeMs"},
            isAsync = false
    )
    public long getTime() {
        return System.currentTimeMillis();
    }

    @LuaFunction(
            value = "システムメッセージとしてチャットを送信します。",
            en = "Sends a chat message as a system broadcast.",
            args = {"str:message"},
            rets = {},
            isAsync = true
    )
    public void chat(String message) {
        vm.executeInMainThreadSync(() -> {
            if (vm.machine != null) {
                Lunex.LOGGER.info("[Lunex Chat] " + message);
                Level lvl = null;
                if (vm.hardware != null) lvl = vm.hardware.getLevel();
                else if (vm.machine instanceof BlockEntity be) lvl = be.getLevel();

                if (lvl instanceof ServerLevel serverLevel) {
                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§e[システム] §f" + message), false
                    );
                }
            }
            return null;
        });
    }

    @LuaFunction(
            value = "サーバーのコンソール画面（ログ）にメッセージを出力します。",
            en = "Outputs a message to the server console (log).",
            args = {"str:message"},
            rets = {},
            isAsync = true
    )
    public void print(String message) {
        Lunex.LOGGER.info("[Lunex] {}", message);
    }

    @LuaFunction(
            value = "指定したミリ秒（1000 = 1秒）だけプログラムの実行を意図的に待機します。",
            en = "Intentionally pauses program execution for the specified milliseconds (1000 = 1 second).",
            args = {"num:millis"},
            rets = {},
            isAsync = true
    )
    public void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @LuaFunction(
            value = "別のプログラムをネットワークから読み込み、実行可能な関数として返します。",
            en = "Loads another program from the network and returns it as an executable function.",
            args = {"str:programName"},
            rets = {"val:chunk"},
            isAsync = true
    )
    public LuaValue loadProgram(String programName) {
        String wsId = vm.machine.getWorkspaceId();
        Map<String, String> progs = ServerProgramData.getPrograms(wsId);

        if (progs == null) return LuaValue.NIL;
        String rawCode = progs.get(programName);
        if (rawCode == null || rawCode.isEmpty()) return LuaValue.NIL;

        try {
            return vm.globals.load(rawCode);
        } catch (Exception e) {
            return LuaValue.NIL;
        }
    }

    @LuaFunction(
            value = "文字列のLuaコードをコンパイルして実行可能な関数(チャンク)として返します。",
            en = "Compiles Lua code from a string and returns it as an executable function (chunk).",
            args = {"str:code"},
            rets = {"val:chunk"},
            isAsync = false
    )
    public LuaValue compile(String code) {
        if (code == null || code.isEmpty()) return LuaValue.NIL;
        try {
            return vm.globals.load(code);
        } catch (Exception e) {
            Lunex.LOGGER.warn("[Lunex] Compile error: " + e.getMessage());
            return LuaValue.NIL;
        }
    }

    @LuaFunction(
            value = "文字列が有効なJSONフォーマットかどうかを判定します。",
            en = "Determines whether a string is in a valid JSON format.",
            args = {"str:jsonString"},
            rets = {"bool:isValid"},
            isAsync = false
    )
    public boolean isJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return false;
        try {
            JsonParser.parseString(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @LuaFunction(
            value = "JSON文字列をパースしてLuaのテーブル（配列・辞書）に変換します。",
            en = "Parses a JSON string and converts it into a Lua table (array/dictionary).",
            args = {"str:jsonString"},
            rets = {"table:data"},
            isAsync = false
    )
    public LuaValue parseJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return LuaValue.NIL;
        try {
            JsonElement element = JsonParser.parseString(jsonString);
            return jsonToLua(element);
        } catch (Exception e) {
            Lunex.LOGGER.warn("[Lunex] JSONパースエラー: " + e.getMessage());
            return LuaValue.NIL;
        }
    }

    @LuaFunction(
            value = "Luaのテーブル（配列・辞書）をJSON文字列に変換します。",
            en = "Converts a Lua table (array/dictionary) into a JSON string.",
            args = {"table:data"},
            rets = {"str:jsonString"},
            isAsync = false
    )
    public String toJson(LuaValue table) {
        if (table == null || table.isnil()) return "null";
        try {
            JsonElement element = luaToJson(table);
            return GSON.toJson(element);
        } catch (Exception e) {
            Lunex.LOGGER.warn("[Lunex] JSON変換エラー: " + e.getMessage());
            return null;
        }
    }

    private LuaValue jsonToLua(JsonElement element) {
        if (element.isJsonNull()) {
            return LuaValue.NIL;
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return LuaValue.valueOf(primitive.getAsBoolean());
            else if (primitive.isNumber()) return LuaValue.valueOf(primitive.getAsDouble());
            else if (primitive.isString()) return LuaValue.valueOf(primitive.getAsString());
        } else if (element.isJsonArray()) {
            LuaTable table = new LuaTable();
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                table.set(i + 1, jsonToLua(array.get(i)));
            }
            return table;
        } else if (element.isJsonObject()) {
            LuaTable table = new LuaTable();
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                table.set(entry.getKey(), jsonToLua(entry.getValue()));
            }
            return table;
        }
        return LuaValue.NIL;
    }

    private JsonElement luaToJson(LuaValue value) {
        if (value.isnil()) {
            return JsonNull.INSTANCE;
        } else if (value.isboolean()) {
            return new JsonPrimitive(value.toboolean());
        } else if (value.isint()) {
            return new JsonPrimitive(value.toint());
        } else if (value.isnumber()) {
            return new JsonPrimitive(value.todouble());
        } else if (value.isstring()) {
            return new JsonPrimitive(value.tojstring());
        } else if (value.istable()) {
            LuaTable table = value.checktable();
            boolean isArray = true;
            int maxIndex = 0;
            int count = 0;
            for (LuaValue key : table.keys()) {
                count++;
                if (!key.isint() || key.toint() <= 0) {
                    isArray = false;
                    break;
                }
                maxIndex = Math.max(maxIndex, key.toint());
            }
            if (isArray && maxIndex == count) {
                JsonArray array = new JsonArray();
                for (int i = 1; i <= maxIndex; i++) {
                    array.add(luaToJson(table.get(i)));
                }
                return array;
            } else {
                JsonObject obj = new JsonObject();
                for (LuaValue key : table.keys()) {
                    obj.add(key.tojstring(), luaToJson(table.get(key)));
                }
                return obj;
            }
        }
        return JsonNull.INSTANCE;
    }
}