package com.nishiyu.lunex.program.server.entity.api;

import com.nishiyu.lunex.entity.goals.GoalComponentRegistry;
import com.nishiyu.lunex.program.core.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CustomGoalAPI {

    private final Map<String, LuaTable> actionCache = new ConcurrentHashMap<>();
    private final Map<String, LuaTable> conditionCache = new ConcurrentHashMap<>();

    // ========================================================
    // グローバル名前空間用ファクトリ (EntityServerLuaVM用)
    // ========================================================
    public static LuaTable buildComponentFactories(Set<String> keys, Function<String, String[]> argGetter) {
        LuaTable wrapper = new LuaTable();
        for (String fullKey : keys) {
            String[] parts = fullKey.split("\\.");
            LuaValue current = wrapper;
            for (int i = 0; i < parts.length - 1; i++) {
                LuaValue next = current.get(parts[i]);
                if (next.isnil()) {
                    next = new LuaTable();
                    current.set(parts[i], next);
                }
                current = next;
            }

            String[] argDefs = argGetter.apply(fullKey);
            current.set(parts[parts.length - 1], new VarArgFunction() {
                @Override
                public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                    LuaTable comp = new LuaTable();
                    comp.set("type", fullKey);

                    // ★ Luaからのテーブル呼び出し対応 (テーブルが1つ渡された場合の処理)
                    if (args.narg() == 1 && args.arg1().istable()) {
                        LuaTable tableArg = args.arg1().checktable();
                        for (int i = 0; i < argDefs.length; i++) {
                            String def = argDefs[i];
                            String argName = def.contains(":") ? def.split(":")[1] : def;
                            LuaValue val = tableArg.get(argName);
                            if (!val.isnil()) comp.set(argName, val);
                        }
                        LuaValue isNotVal = tableArg.get("isNot");
                        if (!isNotVal.isnil()) {
                            comp.set("isNot", isNotVal);
                        }
                        LuaValue conditionVal = tableArg.get("condition");
                        if (!conditionVal.isnil()) {
                            comp.set("condition", conditionVal);
                        }
                    } else {
                        // 従来の引数順指定の場合
                        for (int i = 0; i < argDefs.length; i++) {
                            String def = argDefs[i];
                            String argName = def.contains(":") ? def.split(":")[1] : def;
                            LuaValue val = args.arg(i + 1);
                            if (!val.isnil()) comp.set(argName, val);
                        }
                    }
                    return comp;
                }
            });
        }
        return wrapper;
    }

    @LuaFunction(
            value = "指定したカテゴリのアクションメソッド群をまとめたテーブルを返します。",
            en = "Gets a table containing action methods for the specified category.",
            args = {"str:category"},
            rets = {"table:actions"}
    )
    public LuaValue getAction(String category) {
        if (category == null || category.isEmpty()) return LuaValue.NIL;
        return actionCache.computeIfAbsent(category, k ->
                buildCategoryWrapper(k, GoalComponentRegistry.getActionKeys(), GoalComponentRegistry::getActionArgs)
        );
    }

    @LuaFunction(
            value = "指定したカテゴリの条件メソッド群をまとめたテーブルを返します。",
            en = "Gets a table containing condition methods for the specified category.",
            args = {"str:category"},
            rets = {"table:conditions"}
    )
    public LuaValue getCondition(String category) {
        if (category == null || category.isEmpty()) return LuaValue.NIL;
        return conditionCache.computeIfAbsent(category, k ->
                buildCategoryWrapper(k, GoalComponentRegistry.getConditionKeys(), GoalComponentRegistry::getConditionArgs)
        );
    }

    private LuaTable buildCategoryWrapper(String category, Set<String> keys, Function<String, String[]> argGetter) {
        LuaTable wrapper = new LuaTable();
        wrapper.set("category", LuaValue.valueOf(category));
        String prefix = category + ".";
        for (String fullKey : keys) {
            if (fullKey.startsWith(prefix)) {
                String[] argDefs = argGetter.apply(fullKey);
                String methodName = fullKey.substring(prefix.length());
                wrapper.set(methodName, new VarArgFunction() {
                    @Override
                    public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                        LuaTable comp = new LuaTable();
                        comp.set("type", fullKey);
                        for (int i = 0; i < argDefs.length; i++) {
                            String def = argDefs[i];
                            String argName = def.contains(":") ? def.split(":")[1] : def;
                            LuaValue val = args.arg(i + 1);
                            if (!val.isnil()) comp.set(argName, val);
                        }
                        return comp;
                    }
                });
            }
        }
        return wrapper;
    }
}