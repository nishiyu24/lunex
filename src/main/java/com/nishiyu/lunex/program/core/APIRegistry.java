package com.nishiyu.lunex.program.core;

import com.nishiyu.lunex.program.client.ClientAPIRegistry;
import com.nishiyu.lunex.program.server.SystemAPI;
import com.nishiyu.lunex.program.server.machine.MachineAPIRegistry;
import com.nishiyu.lunex.program.server.turtle.TurtleAPIRegistry;

import java.lang.reflect.Method;
import java.util.*;

public abstract class APIRegistry {

    protected final List<SuggestionDef> suggestions = new ArrayList<>();

    public APIRegistry() {
        registerBaseSuggestions();
        registerEvents();
        registerConstants();
        registerAPIs();
        registerTypes();
    }

    /**
     * vmId から適切な子クラス(レジストリ)を返します。
     */
    public static APIRegistry getRegistryFor(String vmId) {
        if (vmId == null) return new MachineAPIRegistry();
        String id = vmId.toLowerCase();

        if (id.startsWith("turtle_")) return new TurtleAPIRegistry();
        if (id.startsWith("client_") || id.startsWith("screen_") || id.startsWith("ui_"))
            return new ClientAPIRegistry();
        if (id.startsWith("biomob_") || id.startsWith("entity_"))
            return new com.nishiyu.lunex.program.server.entity.EntityAPIRegistry();

        return new MachineAPIRegistry(); // Default fallback
    }

    /**
     * 各VM固有のAPIクラスを登録するための抽象メソッド。サブクラスで実装します。
     */
    protected abstract void registerAPIs();

    /**
     * 全VM共通の基本イベント。必要に応じてサブクラスでオーバーライド(追加)します。
     */
    protected void registerEvents() {
        String[] events = {"on_init", "on_click", "on_tick", "on_redstone", "on_item_in"};
        for (String e : events) {
            suggestions.add(new SuggestionDef("event", "events", e, new ArrayList<>(), new ArrayList<>(),
                    "基本イベント: " + e, "Basic Event: " + e, true, false, List.of("Out")));
        }
    }

    /**
     * 定数。サブクラスでオーバーライドして固有の定数を追加します。
     */
    protected void registerConstants() {
        // ★ Machine特有の Direction と DEVICE を MachineAPIRegistry へ移動しました。
        // （他のVMでも共通して使う定数が出てきた場合はここに追記します）
    }

    /**
     * 全VM共通のLuaグローバル関数や、システム(SystemAPI)の登録を行います。
     */
    protected void registerBaseSuggestions() {
        // Lua標準機能
        suggestions.add(new SuggestionDef("api", "global", "print", List.of("any:message"), new ArrayList<>(),
                "コンソールにメッセージを出力します。", "Outputs a message to the console.", false, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "global", "require", List.of("str:module_path"), List.of("any:module"),
                "指定したモジュール(Luaファイル)を読み込みます。", "Loads the specified module (Lua file).", false, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "global", "tostring", List.of("any:value"), List.of("str:result"),
                "値を文字列に変換します。", "Converts a value to a string.", false, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "global", "tonumber", List.of("any:value"), List.of("num:result"),
                "値を数値に変換します。", "Converts a value to a number.", false, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "global", "type", List.of("any:value"), List.of("str:type"),
                "値の型を文字列で返します。", "Returns the type of a value as a string.", false, true, List.of("Out")));

        suggestions.add(new SuggestionDef("api", "table", "insert", List.of("table:tbl", "any:value"), new ArrayList<>(),
                "テーブルの末尾に値を追加します。", "Appends a value to the end of a table.", false, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "table", "remove", List.of("table:tbl", "num:index"), List.of("any:removedValue"),
                "テーブルから値を削除して返します。", "Removes and returns a value from a table.", false, true, List.of("Out")));

        // スケジューラ(scheduler.lua)経由のシステム関数
        suggestions.add(new SuggestionDef("api", "system", "addEventListener", List.of("str:eventName", "str:callbackName"), new ArrayList<>(),
                "イベントリスナー(関数名)を登録します。", "Registers an event listener (function name).", true, true, List.of("Out", "OnEvent")));
        suggestions.add(new SuggestionDef("api", "system", "removeEventListener", List.of("str:eventName", "str:callbackName"), new ArrayList<>(),
                "登録済みのイベントリスナーを解除します。", "Removes a registered event listener.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "awaitEvent", List.of("str:targetEvent", "num:timeout"), List.of("any:eventData"),
                "指定したイベントが発生するまで待機します。", "Waits for the specified event to occur.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "waitForChange", List.of("val:getter", "num:timeout"), List.of("any:newValue"),
                "getter関数が返す値が変化するまで待機します。", "Waits until the getter function's return value changes.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "waitUntil", List.of("val:condition", "num:timeout"), List.of("bool:success"),
                "condition関数が true を返すまで待機します。", "Waits until the condition function returns true.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "sleep", List.of("num:millis"), new ArrayList<>(),
                "指定した時間（ミリ秒）だけ一時停止します。", "Pauses execution for the specified milliseconds.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "setInterval", List.of("str:callbackName", "num:interval"), List.of("table:timerObj"),
                "指定間隔ごとにコールバック関数を実行します。", "Executes a callback function at specified intervals.", true, true, List.of("Out", "OnInterval")));
        suggestions.add(new SuggestionDef("api", "system", "clearInterval", List.of("table:timerObj"), new ArrayList<>(),
                "setIntervalの定期実行を解除します。", "Clears a periodic execution set by setInterval.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "setTimeout", List.of("str:callbackName", "num:delay"), List.of("table:timerObj"),
                "指定時間後にコールバック関数を一度だけ実行します。", "Executes a callback function once after a delay.", true, true, List.of("Out", "OnTimeout")));
        suggestions.add(new SuggestionDef("api", "system", "clearTimeout", List.of("table:timerObj"), new ArrayList<>(),
                "setTimeoutの遅延実行を解除します。", "Clears a delayed execution set by setTimeout.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "system", "createState", List.of("table:initial_table"), List.of("table:state"),
                "変更を検知可能な状態テーブルを作成します。", "Creates a state table that can detect changes.", false, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "parallel", "waitForAll", List.of("val:..."), new ArrayList<>(),
                "複数の関数を並行実行し、すべて完了するまで待機します。", "Executes multiple functions in parallel and waits for all to finish.", true, true, List.of("Out")));
        suggestions.add(new SuggestionDef("api", "parallel", "waitForAny", List.of("val:..."), List.of("num:finishedIndex"),
                "複数の関数を並行実行し、どれか1つが完了するまで待機します。", "Executes multiple functions in parallel and waits for one to finish.", true, true, List.of("Out")));

        // 共通APIクラスの登録
        registerAPIClass("system", SystemAPI.class);
    }

    protected void registerAPIClass(String prefix, Class<?> apiClass) {
        for (Method method : apiClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(LuaFunction.class)) {
                suggestions.add(createApiSuggestion(prefix, method));
            }
        }
    }

    protected SuggestionDef createApiSuggestion(String prefix, Method method) {
        String methodName = method.getName();
        Class<?>[] params = method.getParameterTypes();
        List<String> argsList = new ArrayList<>();
        List<String> retsList = new ArrayList<>();

        LuaFunction annotation = method.getAnnotation(LuaFunction.class);
        String description = (annotation != null && !annotation.value().isEmpty()) ? annotation.value() : "";
        String descriptionEn = (annotation != null && !annotation.en().isEmpty()) ? annotation.en() : "";
        boolean isAsync = annotation != null && annotation.isAsync();
        boolean hasExecIn = annotation == null || annotation.hasExecIn();
        List<String> execOutsList = new ArrayList<>(annotation != null ? Arrays.asList(annotation.execOuts()) : List.of("Out"));

        String[] declaredArgs = annotation != null ? annotation.args() : new String[0];
        for (int i = 0; i < params.length; i++) {
            if (i < declaredArgs.length && !declaredArgs[i].isEmpty()) {
                String argDef = declaredArgs[i];
                if (!argDef.contains(":")) argDef = determineArgTypeFallback(params[i]) + ":" + argDef;
                argsList.add(argDef);
            } else {
                argsList.add(determineArgTypeFallback(params[i]) + ":arg" + (i + 1));
            }
        }

        String[] declaredRets = annotation != null ? annotation.rets() : new String[0];
        if (declaredRets.length > 0) {
            for (String retDef : declaredRets) {
                if (!retDef.contains(":")) retDef = "any:" + retDef;
                retsList.add(retDef);
            }
        } else {
            Class<?> retType = method.getReturnType();
            if (retType != void.class) {
                retsList.add(determineArgTypeFallback(retType) + ":out");
            }
        }

        return new SuggestionDef("api", prefix, methodName, argsList, retsList, description, descriptionEn, isAsync, hasExecIn, execOutsList);
    }

    protected String determineArgTypeFallback(Class<?> param) {
        if (param == int.class || param == double.class || param == float.class || param == long.class) return "num";
        if (param == boolean.class) return "bool";
        if (param == String.class) return "str";
        return "val";
    }

    protected void registerTypes() {
        Set<String> usedTypes = new HashSet<>();
        for (SuggestionDef def : suggestions) {
            if (def.args != null)
                for (String arg : def.args) if (arg.contains(":")) usedTypes.add(arg.split(":")[0].trim());
            if (def.rets != null)
                for (String ret : def.rets) if (ret.contains(":")) usedTypes.add(ret.split(":")[0].trim());
        }
        for (String t : usedTypes) {
            suggestions.add(new SuggestionDef("type", "global", t, new ArrayList<>(), new ArrayList<>(),
                    "APIで使用される型", "Type used in the API", false, false, new ArrayList<>()));
        }
    }

    public List<SuggestionDef> getSuggestions() {
        return suggestions;
    }
}