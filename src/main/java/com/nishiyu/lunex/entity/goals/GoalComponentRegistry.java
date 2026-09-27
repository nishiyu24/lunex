package com.nishiyu.lunex.entity.goals;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import org.luaj.vm2.LuaTable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GoalComponentRegistry {
    private static final Map<String, ConditionComponent> CONDITIONS = new HashMap<>();
    private static final Map<String, ActionComponent> ACTIONS = new HashMap<>();
    private static final Map<String, String[]> CONDITION_ARGS = new HashMap<>();
    private static final Map<String, String[]> ACTION_ARGS = new HashMap<>();
    private static final Map<String, String[]> CONDITION_REQUIRED_TRAITS = new HashMap<>();
    private static final Map<String, String[]> ACTION_REQUIRED_TRAITS = new HashMap<>();

    private static final Map<String, String> CONDITION_DESC = new HashMap<>();
    private static final Map<String, String> CONDITION_DESC_EN = new HashMap<>();
    private static final Map<String, String> ACTION_DESC = new HashMap<>();
    private static final Map<String, String> ACTION_DESC_EN = new HashMap<>();

    static {
        registerComponents(GoalConditions.class);
        registerComponents(GoalActions.class);
    }

    private static void registerComponents(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            try {
                if (field.isAnnotationPresent(GoalConditionDef.class)) {
                    GoalConditionDef ann = field.getAnnotation(GoalConditionDef.class);
                    CONDITIONS.put(ann.value(), (ConditionComponent) field.get(null));
                    CONDITION_ARGS.put(ann.value(), ann.args());
                    CONDITION_REQUIRED_TRAITS.put(ann.value(), ann.requiredTraits());
                    CONDITION_DESC.put(ann.value(), ann.desc());
                    CONDITION_DESC_EN.put(ann.value(), ann.descEn());
                } else if (field.isAnnotationPresent(GoalActionDef.class)) {
                    GoalActionDef ann = field.getAnnotation(GoalActionDef.class);
                    ACTIONS.put(ann.value(), (ActionComponent) field.get(null));
                    ACTION_ARGS.put(ann.value(), ann.args());
                    ACTION_REQUIRED_TRAITS.put(ann.value(), ann.requiredTraits());
                    ACTION_DESC.put(ann.value(), ann.desc());
                    ACTION_DESC_EN.put(ann.value(), ann.descEn());
                }
            } catch (IllegalAccessException e) {
                Lunex.LOGGER.error("Failed to register", e);
            }
        }
    }

    public static ActionComponent getAction(String type) {
        return ACTIONS.get(type);
    }

    public static ConditionComponent getCondition(String type) {
        return CONDITIONS.get(type);
    }

    public static String[] getActionArgs(String type) {
        return ACTION_ARGS.getOrDefault(type, new String[0]);
    }

    public static String[] getConditionArgs(String type) {
        return CONDITION_ARGS.getOrDefault(type, new String[0]);
    }

    public static String[] getActionRequiredTraits(String type) {
        return ACTION_REQUIRED_TRAITS.getOrDefault(type, new String[0]);
    }

    public static String[] getConditionRequiredTraits(String type) {
        return CONDITION_REQUIRED_TRAITS.getOrDefault(type, new String[0]);
    }

    public static String getActionDesc(String type) {
        return ACTION_DESC.getOrDefault(type, "");
    }

    public static String getActionDescEn(String type) {
        return ACTION_DESC_EN.getOrDefault(type, "");
    }

    public static String getConditionDesc(String type) {
        return CONDITION_DESC.getOrDefault(type, "");
    }

    public static String getConditionDescEn(String type) {
        return CONDITION_DESC_EN.getOrDefault(type, "");
    }

    public static Set<String> getActionKeys() {
        return ACTIONS.keySet();
    }

    public static Set<String> getConditionKeys() {
        return CONDITIONS.keySet();
    }

    @FunctionalInterface
    public interface ConditionComponent {
        boolean test(CustomBioMobEntity mob, Map<String, Object> blackboard, LuaTable args);
    }

    @FunctionalInterface
    public interface ActionComponent {
        void execute(CustomBioMobEntity mob, Map<String, Object> blackboard, LuaTable args);
    }
}