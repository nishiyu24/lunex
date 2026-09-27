package com.nishiyu.lunex.entity.goals;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.*;

public class DynamicComponentGoal extends Goal {
    private final CustomBioMobEntity mob;
    private final List<ConfiguredCondition> conditions = new ArrayList<>();
    private final List<ConfiguredAction> actions = new ArrayList<>();
    private final Map<String, Object> state = new HashMap<>();
    private final boolean isTargetGoal;

    public DynamicComponentGoal(CustomBioMobEntity mob, LuaTable definition) {
        this.mob = mob;

        // ★最重要修正: isTarget かどうかで占有するAIフラグを分ける
        this.isTargetGoal = !definition.get("isTarget").isnil() && definition.get("isTarget").toboolean();
        if (this.isTargetGoal) {
            this.setFlags(EnumSet.of(Goal.Flag.TARGET)); // 思考・ターゲット用
        } else {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)); // 実際の行動用
        }

        // 1. Goal自体の開始条件
        LuaValue canUseList = definition.get("canUse");
        if (canUseList.istable()) {
            for (int i = 1; i <= canUseList.length(); i++) {
                LuaValue item = canUseList.get(i);
                if (item.istable()) {
                    String type = item.get("type").checkjstring();
                    GoalComponentRegistry.ConditionComponent comp = GoalComponentRegistry.getCondition(type);
                    if (comp != null && mob.hasTraits(GoalComponentRegistry.getConditionRequiredTraits(type))) {
                        conditions.add(new ConfiguredCondition(comp, (LuaTable) item));
                    }
                }
            }
        }

        // 2. 毎フレーム実行するアクションリスト
        LuaValue tickList = definition.get("tick");
        if (tickList.istable()) {
            for (int i = 1; i <= tickList.length(); i++) {
                LuaValue item = tickList.get(i);
                if (item.istable()) {
                    String type = item.get("type").checkjstring();
                    GoalComponentRegistry.ActionComponent comp = GoalComponentRegistry.getAction(type);

                    if (comp != null && mob.hasTraits(GoalComponentRegistry.getActionRequiredTraits(type))) {
                        List<ConfiguredCondition> runConditions = new ArrayList<>();
                        LuaValue condVal = item.get("condition");
                        if (!condVal.isnil() && condVal.istable()) {
                            if (condVal.get("type").isnil()) {
                                for (int j = 1; j <= condVal.length(); j++) {
                                    LuaValue c = condVal.get(j);
                                    String condType = c.get("type").checkjstring();
                                    GoalComponentRegistry.ConditionComponent cc = GoalComponentRegistry.getCondition(condType);
                                    if (cc != null && mob.hasTraits(GoalComponentRegistry.getConditionRequiredTraits(condType))) {
                                        runConditions.add(new ConfiguredCondition(cc, (LuaTable) c));
                                    }
                                }
                            } else {
                                String condType = condVal.get("type").checkjstring();
                                GoalComponentRegistry.ConditionComponent cc = GoalComponentRegistry.getCondition(condType);
                                if (cc != null && mob.hasTraits(GoalComponentRegistry.getConditionRequiredTraits(condType))) {
                                    runConditions.add(new ConfiguredCondition(cc, (LuaTable) condVal));
                                }
                            }
                        }
                        actions.add(new ConfiguredAction(comp, (LuaTable) item, runConditions));
                    }
                }
            }
        }
    }

    private boolean evaluateCondition(ConfiguredCondition c) {
        try {
            boolean result = c.component().test(mob, state, c.args());
            LuaValue isNotVal = c.args().get("isNot");
            // isNot = true が指定されている場合は結果を反転させる
            if (!isNotVal.isnil() && isNotVal.toboolean()) {
                result = !result;
            }
            return result;
        } catch (Exception e) {
            Lunex.LOGGER.error("[BioMob] AI condition evaluation error: " + c.args().get("type"), e);
            return false;
        }
    }

    @Override
    public boolean canUse() {
        if (conditions.isEmpty()) return true;

        for (ConfiguredCondition c : conditions) {
            if (!evaluateCondition(c)) return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        super.stop();
        // ★追加: ゴールの条件を満たさなくなった時に、慣性で滑り続けるのを防ぐ
        if (!this.isTargetGoal) {
            mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        for (ConfiguredAction a : actions) {
            boolean canRun = true;
            for (ConfiguredCondition c : a.runConditions()) {
                if (!evaluateCondition(c)) {
                    canRun = false;
                    break;
                }
            }
            if (canRun) {
                try {
                    a.component().execute(mob, state, a.args());
                } catch (Exception e) {
                    Lunex.LOGGER.error("[BioMob] AI action execution error: " + a.args().get("type"), e);
                }
            }
        }
    }

    private record ConfiguredCondition(GoalComponentRegistry.ConditionComponent component, LuaTable args) {
    }

    private record ConfiguredAction(GoalComponentRegistry.ActionComponent component, LuaTable args,
                                    List<ConfiguredCondition> runConditions) {
    }
}