package com.nishiyu.lunex.program.server.entity.api;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.entity.goals.DynamicComponentGoal;
import com.nishiyu.lunex.entity.goals.MobGoalRegistry;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.entity.EntityServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MobEntityAPI {
    private final CustomBioMobEntity mob;
    private final EntityServerLuaVM vm;
    private final Map<String, List<LuaValue>> eventListeners = new ConcurrentHashMap<>();

    public MobEntityAPI(CustomBioMobEntity mob, EntityServerLuaVM vm) {
        this.mob = mob;
        this.vm = vm;
    }

    @LuaFunction(
            value = "イベントリスナーを登録します。",
            en = "Registers an event listener.",
            args = {"str:eventName", "val:callback"},
            rets = {}
    )
    public void addEventListener(String eventName, LuaValue callback) {
        if (callback.isfunction()) {
            eventListeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
    }

    @LuaFunction(
            value = "イベントリスナーを登録します(addEventListenerのエイリアス)。",
            en = "Registers an event listener (alias for addEventListener).",
            args = {"str:eventName", "val:callback"},
            rets = {}
    )
    public void on(String eventName, LuaValue callback) {
        addEventListener(eventName, callback);
    }

    @LuaFunction(
            value = "イベントを強制発火させます。(システム内部から呼ばれます)",
            en = "Dispatches an event.",
            args = {"str:eventName", "table:argsTable"},
            rets = {}
    )
    public void dispatchEvent(String eventName, LuaTable argsTable) {
        List<LuaValue> listeners = eventListeners.get(eventName);
        if (listeners != null) {
            for (LuaValue listener : listeners) {
                try {
                    if (argsTable != null && argsTable.istable() && argsTable.length() > 0) {
                        LuaValue arg1 = argsTable.get(1);
                        LuaValue arg2 = argsTable.get(2);
                        LuaValue arg3 = argsTable.get(3);
                        if (!arg3.isnil()) listener.call(arg1, arg2, arg3);
                        else if (!arg2.isnil()) listener.call(arg1, arg2);
                        else if (!arg1.isnil()) listener.call(arg1);
                        else listener.call();
                    } else {
                        listener.call();
                    }
                } catch (Exception e) {
                    Lunex.LOGGER.error("Event error: " + eventName, e);
                }
            }
        }
    }

    @LuaFunction(
            value = "モブに事前定義されたAIゴールを追加します。",
            en = "Adds a predefined AI goal to the mob.",
            args = {"str:goalName", "table:options"},
            rets = {}
    )
    public void addGoal(String name, LuaTable options) {
        int priority = options == null || options.get("priority").isnil() ? 5 : options.get("priority").checkint();

        vm.executeInMainThreadSync(() -> {
            double speed = options == null || options.get("speed").isnil() ? 1.0 : options.get("speed").checkdouble();

            String callbackId = null;
            if (options != null && !options.get("onAction").isnil() && options.get("onAction").isfunction()) {
                callbackId = "goal_" + name.toLowerCase() + "_" + UUID.randomUUID().toString();
                eventListeners.computeIfAbsent(callbackId, k -> new CopyOnWriteArrayList<>()).add(options.get("onAction"));
            }

            Goal goal = MobGoalRegistry.createGoal(name, mob, speed, options, callbackId);

            if (goal != null) {
                boolean isTargetGoal = (goal instanceof DynamicComponentGoal &&
                        options != null && !options.get("isTarget").isnil() && options.get("isTarget").toboolean())
                        || name.equals("LoyalTarget");

                if (isTargetGoal) {
                    mob.targetSelector.addGoal(priority, goal);
                } else {
                    mob.goalSelector.addGoal(priority, goal);
                }
            } else {
                Lunex.LOGGER.warn("[BioMob] Unknown AI Goal requested: " + name);
            }

            return null;
        }, 0, false);
    }

    @LuaFunction(
            value = "カスタムAIゴール(JSON)を定義・追加します。",
            en = "Defines and adds a custom AI goal using JSON.",
            args = {"table:definition"},
            rets = {}
    )
    public void buildGoal(LuaTable definition) {
        int priority = definition.get("priority").isnil() ? 5 : definition.get("priority").checkint();
        boolean isTarget = !definition.get("isTarget").isnil() && definition.get("isTarget").toboolean();

        vm.executeInMainThreadSync(() -> {
            // シンプルな Goal (canUse + tick) を生成
            DynamicComponentGoal customGoal = new DynamicComponentGoal(mob, definition);
            if (isTarget) {
                mob.targetSelector.addGoal(priority, customGoal);
            } else {
                mob.goalSelector.addGoal(priority, customGoal);
            }
            return null;
        }, 0, false);
    }

    @LuaFunction(
            value = "設定されたAIゴールを全てクリアします。",
            en = "Clears all set AI goals.",
            args = {},
            rets = {}
    )
    public void clearGoals() {
        vm.executeInMainThreadSync(() -> {
            mob.goalSelector.getAvailableGoals().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            mob.targetSelector.getAvailableGoals().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);

            mob.goalSelector.removeAllGoals(g -> true);
            mob.targetSelector.removeAllGoals(g -> true);
            return null;
        }, 0, false);
    }

    @LuaFunction(
            value = "指定した座標へ移動します。",
            en = "Moves to the specified coordinates.",
            args = {"num:x", "num:y", "num:z"},
            rets = {}
    )
    public void moveTo(int x, int y, int z) {
        vm.executeInMainThreadSync(() -> {
            mob.targetPos = new BlockPos(x, y, z);
            return null;
        }, 50, false);
    }

    @LuaFunction(
            value = "移動を停止します。",
            en = "Stops movement.",
            args = {},
            rets = {}
    )
    public void stop() {
        vm.executeInMainThreadSync(() -> {
            mob.targetPos = null;
            mob.getNavigation().stop();
            return null;
        }, 10, false);
    }
}