package com.nishiyu.lunex.entity.goals;

import com.nishiyu.lunex.entity.CustomBioMobEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.HashMap;
import java.util.Map;

public class MobGoalRegistry {

    private static final Map<String, GoalFactory> REGISTRY = new HashMap<>();

    static {
        // ==========================================
        // 既存のコンポーネント化されたAI群
        // ==========================================

        // ★修正: TargetBlock.MoveTo -> Move.ToTargetBlock に変更
        register("MachineMove", (mob, speed, options, callbackId) -> new DynamicComponentGoal(mob, buildOptions(
                "canUse", buildList(buildOptions("type", "TargetBlock.HasTarget")),
                "tick", buildList(buildOptions("type", "Move.ToTargetBlock", "speed", speed))
        )));

        // --- フォロワー (Follow Player) ---
        register("FollowPlayer", (mob, speed, options, callbackId) -> {
            double startDist = options == null || options.get("startDist").isnil() ? 10.0 : options.get("startDist").checkdouble();
            double stopDist = options == null || options.get("stopDist").isnil() ? 2.0 : options.get("stopDist").checkdouble();
            return new DynamicComponentGoal(mob, buildOptions(
                    "canUse", buildList(buildOptions("type", "System.HasEntityNear", "entityId", "minecraft:player", "radius", startDist)),
                    "tick", buildList(
                            // ★修正: TargetEntity.NotHasTarget 等を正しい構文に修正
                            buildOptions("type", "TargetEntity.RememberNear", "entityId", "minecraft:player", "radius", startDist, "condition", buildOptions("type", "TargetEntity.HasTarget", "isNot", true)),
                            buildOptions("type", "Look.AtTargetEntity", "condition", buildOptions("type", "TargetEntity.HasTarget")),
                            buildOptions("type", "Move.ToTargetEntity", "speed", speed, "condition", buildOptions("type", "TargetEntity.HasTarget")),
                            buildOptions("type", "TargetEntity.Forget", "condition", buildList(
                                    buildOptions("type", "TargetEntity.HasTarget"),
                                    buildOptions("type", "TargetEntity.DistanceLessThan", "isNot", true, "distance", startDist * 2.0)
                            ))
                    )
            ));
        });

        // --- アイテム回収 (Pickup Item) ---
        register("PickupItem", (mob, speed, options, callbackId) -> new DynamicComponentGoal(mob, buildOptions(
                "canUse", buildList(buildOptions("type", "System.HasEntityNear", "entityId", "minecraft:item", "radius", 8.0)),
                "tick", buildList(
                        buildOptions("type", "TargetEntity.RememberNear", "entityId", "minecraft:item", "radius", 8.0, "condition", buildOptions("type", "TargetEntity.HasTarget", "isNot", true)),
                        buildOptions("type", "Move.ToTargetEntity", "speed", speed, "condition", buildOptions("type", "TargetEntity.HasTarget")),
                        buildOptions("type", "TargetEntity.PickUp", "reach", 2.0, "condition", buildList(
                                buildOptions("type", "TargetEntity.HasTarget"),
                                buildOptions("type", "TargetEntity.DistanceLessThan", "distance", 2.0)
                        )),
                        buildOptions("type", "TargetEntity.Forget", "condition", buildList(
                                buildOptions("type", "TargetEntity.HasTarget"),
                                buildOptions("type", "TargetEntity.IsAlive", "isNot", true)
                        ))
                )
        )));

        // --- 農業 (Farming) ---
        register("Farming", (mob, speed, options, callbackId) -> {
            int range = options == null || options.get("searchRange").isnil() ? 8 : options.get("searchRange").checkint();
            return new DynamicComponentGoal(mob, buildOptions(
                    "canUse", buildList(buildOptions("type", "System.HasBlockTypeNear", "blockId", "minecraft:wheat", "radius", range)),
                    "tick", buildList(
                            // ★修正: rangeX等ではなく、Action側で設定された radius 引数を使用。IsAirをIsType(minecraft:air)に変更
                            buildOptions("type", "TargetBlock.RememberNear", "blockId", "minecraft:wheat", "radius", range, "condition", buildOptions("type", "TargetBlock.HasTarget", "isNot", true)),
                            buildOptions("type", "Move.ToTargetBlock", "speed", speed, "condition", buildOptions("type", "TargetBlock.HasTarget")),
                            buildOptions("type", "TargetBlock.Break", "reach", 2.0, "condition", buildList(
                                    buildOptions("type", "TargetBlock.HasTarget"),
                                    buildOptions("type", "TargetBlock.DistanceLessThan", "distance", 2.0)
                            )),
                            buildOptions("type", "TargetBlock.Forget", "condition", buildList(
                                    buildOptions("type", "TargetBlock.HasTarget"),
                                    buildOptions("type", "TargetBlock.IsType", "blockId", "minecraft:air")
                            ))
                    )
            ));
        });

        // --- 伐採 (Lumberjack) ---
        register("Lumberjack", (mob, speed, options, callbackId) -> {
            int range = options == null || options.get("searchRange").isnil() ? 8 : options.get("searchRange").checkint();
            return new DynamicComponentGoal(mob, buildOptions(
                    "canUse", buildList(buildOptions("type", "System.HasBlockTypeNear", "blockId", "minecraft:oak_log", "radius", range)),
                    "tick", buildList(
                            buildOptions("type", "TargetBlock.RememberNear", "blockId", "minecraft:oak_log", "radius", range, "condition", buildOptions("type", "TargetBlock.HasTarget", "isNot", true)),
                            buildOptions("type", "Move.ToTargetBlock", "speed", speed, "condition", buildOptions("type", "TargetBlock.HasTarget")),
                            buildOptions("type", "TargetBlock.Break", "reach", 3.0, "condition", buildList(
                                    buildOptions("type", "TargetBlock.HasTarget"),
                                    buildOptions("type", "TargetBlock.DistanceLessThan", "distance", 3.0)
                            )),
                            buildOptions("type", "TargetBlock.Forget", "condition", buildList(
                                    buildOptions("type", "TargetBlock.HasTarget"),
                                    buildOptions("type", "TargetBlock.IsType", "blockId", "minecraft:air")
                            ))
                    )
            ));
        });

        // --- 繁殖 (Breeder) ---
        register("Breeder", (mob, speed, options, callbackId) -> new DynamicComponentGoal(mob, buildOptions(
                "canUse", buildList(buildOptions("type", "System.HasEntityNear", "entityId", "minecraft:cow", "radius", 10.0)),
                "tick", buildList(
                        buildOptions("type", "TargetEntity.RememberNear", "entityId", "minecraft:cow", "radius", 10.0, "condition", buildOptions("type", "TargetEntity.HasTarget", "isNot", true)),
                        buildOptions("type", "Move.ToTargetEntity", "speed", speed, "condition", buildOptions("type", "TargetEntity.HasTarget")),
                        buildOptions("type", "TargetEntity.Breed", "reach", 3.0, "condition", buildList(
                                buildOptions("type", "TargetEntity.HasTarget"),
                                buildOptions("type", "TargetEntity.DistanceLessThan", "distance", 3.0)
                        )),
                        buildOptions("type", "TargetEntity.Forget", "condition", buildList(
                                buildOptions("type", "TargetEntity.HasTarget"),
                                buildOptions("type", "System.RandomChance", "chance", 0.05)
                        ))
                )
        )));

        // --- 忠誠・戦闘用 (LoyalTarget) ---
        register("LoyalTarget", (mob, speed, options, callbackId) -> new DynamicComponentGoal(mob, buildOptions(
                "isTarget", true,
                "canUse", buildList(),
                "tick", buildList(buildOptions("type", "TargetEntity.RememberAttacker", "condition", buildOptions("type", "TargetEntity.HasTarget", "isNot", true)))
        )));

        // --- ヒットアンドラン (HitAndRun) ---
        register("HitAndRun", (mob, speed, options, callbackId) -> {
            double approach = options == null || options.get("approachSpeed").isnil() ? speed : options.get("approachSpeed").checkdouble();
            double flee = options == null || options.get("fleeSpeed").isnil() ? speed * 1.5 : options.get("fleeSpeed").checkdouble();
            return new DynamicComponentGoal(mob, buildOptions(
                    "canUse", buildList(buildOptions("type", "TargetEntity.HasTarget")),
                    "tick", buildList(
                            buildOptions("type", "Move.ToTargetEntity", "speed", approach),
                            buildOptions("type", "TargetEntity.Attack", "condition", buildList(
                                    buildOptions("type", "TargetEntity.HasTarget"),
                                    buildOptions("type", "TargetEntity.DistanceLessThan", "distance", 2.0)
                            )),
                            buildOptions("type", "TargetEntity.Forget", "condition", buildList(
                                    buildOptions("type", "TargetEntity.HasTarget"),
                                    buildOptions("type", "TargetEntity.IsAlive", "isNot", true)
                            ))
                    )
            ));
        });

    }

    public static LuaTable buildOptions(Object... args) {
        LuaTable table = new LuaTable();
        for (int i = 0; i < args.length; i += 2) {
            String key = (String) args[i];
            Object val = args[i + 1];
            if (val == null) table.set(key, LuaValue.NIL);
            else if (val instanceof String s) table.set(key, s);
            else if (val instanceof Number n) table.set(key, n.doubleValue());
            else if (val instanceof Boolean b) table.set(key, b ? LuaValue.TRUE : LuaValue.FALSE);
            else if (val instanceof LuaValue l) table.set(key, l);
        }
        return table;
    }

    public static LuaTable buildList(LuaTable... tables) {
        LuaTable list = new LuaTable();
        for (int i = 0; i < tables.length; i++) list.set(i + 1, tables[i]);
        return list;
    }

    public static void register(String name, GoalFactory factory) {
        REGISTRY.put(name, factory);
    }

    public static Goal createGoal(String name, CustomBioMobEntity mob, double speed, LuaTable options, String callbackId) {
        GoalFactory factory = REGISTRY.get(name);
        return factory != null ? factory.create(mob, speed, options, callbackId) : null;
    }

    @FunctionalInterface
    public interface GoalFactory {
        Goal create(CustomBioMobEntity mob, double speed, LuaTable options, String callbackId);
    }
}