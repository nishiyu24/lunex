package com.nishiyu.lunex.entity.goals;

import com.nishiyu.lunex.entity.goals.GoalComponentRegistry.ConditionComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GoalConditions {

    // ==========================================
    // 1. TargetBlock (ブロック対象) の条件
    // ==========================================
    @GoalConditionDef(value = "TargetBlock.IsSet", desc = "ターゲットブロックが設定されている", descEn = "Target Block is set")
    public static final ConditionComponent TARGET_BLOCK_IS_SET = (mob, blackboard, args) -> mob.targetPos != null;

    @GoalConditionDef(value = "TargetBlock.IsType", args = {"str:blockId"}, desc = "ターゲットブロックが指定の種類である", descEn = "Target Block is Type")
    public static final ConditionComponent TARGET_BLOCK_IS_TYPE = (mob, blackboard, args) -> {
        if (mob.targetPos == null) return false;
        String blockId = args.get("blockId").checkjstring();
        BlockState bs = mob.level().getBlockState(mob.targetPos);
        return BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString().equals(blockId);
    };

    @GoalConditionDef(value = "TargetBlock.IsNear", args = {"num:distance"}, desc = "距離が指定値未満(0で操作可能距離内)", descEn = "Target Block is Near")
    public static final ConditionComponent TARGET_BLOCK_IS_NEAR = (mob, blackboard, args) -> {
        if (mob.targetPos == null) return false;
        double dist = args.get("distance").checkdouble();
        if (dist <= 0) dist = mob.traitManager.getInteractRange();

        return mob.distanceToSqr(Vec3.atCenterOf(mob.targetPos)) < dist * dist;
    };

    @GoalConditionDef(value = "TargetBlock.HasItem", args = {"str:itemId", "num:minCount"}, desc = "ターゲットのチェスト内に指定アイテムがN個以上ある", descEn = "Target Container has Item")
    public static final ConditionComponent TARGET_BLOCK_HAS_ITEM = (mob, blackboard, args) -> {
        if (mob.targetPos == null) return false;

        String timeKey = "chest_check_time_" + mob.targetPos.asLong();
        String resKey = "chest_check_res_" + mob.targetPos.asLong();
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 10) {
            return blackboard.containsKey(resKey) && (boolean) blackboard.get(resKey);
        }
        blackboard.put(timeKey, (long) mob.tickCount);

        net.minecraft.world.level.block.entity.BlockEntity be = mob.level().getBlockEntity(mob.targetPos);
        if (be instanceof net.minecraft.world.Container chest) {
            Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args.get("itemId").checkjstring()));
            int count = 0;
            int min = args.get("minCount").checkint();
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (stack.is(targetItem)) {
                    count += stack.getCount();
                    if (count >= min) {
                        blackboard.put(resKey, true);
                        return true;
                    }
                }
            }
        }
        blackboard.put(resKey, false);
        return false;
    };


    // ==========================================
    // 2. TargetEntity (エンティティ対象) の条件
    // ==========================================
    @GoalConditionDef(value = "TargetEntity.IsSet", desc = "ターゲットモブが設定されている", descEn = "Target Entity is set")
    public static final ConditionComponent TARGET_ENTITY_IS_SET = (mob, blackboard, args) -> mob.targetEntity != null;

    @GoalConditionDef(value = "TargetEntity.IsType", args = {"str:entityId"}, desc = "ターゲットモブが指定の種類である", descEn = "Target Entity is Type")
    public static final ConditionComponent TARGET_ENTITY_IS_TYPE = (mob, blackboard, args) -> {
        if (mob.targetEntity == null) return false;
        String entityId = args.get("entityId").checkjstring();
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.targetEntity.getType()).toString().equals(entityId);
    };

    @GoalConditionDef(value = "TargetEntity.IsNear", args = {"num:distance"}, desc = "距離が指定値未満(0で操作可能距離内)", descEn = "Target Entity is Near")
    public static final ConditionComponent TARGET_ENTITY_IS_NEAR = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            double dist = args.get("distance").checkdouble();
            if (dist <= 0) dist = mob.traitManager.getInteractRange();

            return mob.distanceToSqr(mob.targetEntity) < dist * dist;
        }
        return false;
    };

    @GoalConditionDef(value = "TargetEntity.CanSee", desc = "ターゲットモブへの射線が通っている", descEn = "Can see Target Entity")
    public static final ConditionComponent TARGET_ENTITY_CAN_SEE = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            return mob.getSensing().hasLineOfSight(mob.targetEntity);
        }
        return false;
    };

    @GoalConditionDef(value = "TargetEntity.IsAlive", desc = "ターゲットモブが生きている", descEn = "Target Entity is Alive")
    public static final ConditionComponent TARGET_ENTITY_IS_ALIVE = (mob, blackboard, args) -> {
        return mob.targetEntity != null && mob.targetEntity.isAlive();
    };


    // ==========================================
    // 3. Self (自身) の条件
    // ==========================================
    @GoalConditionDef(value = "Self.InWater", desc = "自分が水中にいる", descEn = "Is in Water")
    public static final ConditionComponent SELF_IN_WATER = (mob, blackboard, args) -> mob.isInWater() || mob.level().isWaterAt(mob.blockPosition().below());

    @GoalConditionDef(value = "Self.OnGround", desc = "自分が地面に足がついている", descEn = "Is on Ground")
    public static final ConditionComponent SELF_ON_GROUND = (mob, blackboard, args) -> mob.onGround();

    @GoalConditionDef(value = "Self.IsBurning", desc = "自分が燃えている", descEn = "Is Burning")
    public static final ConditionComponent SELF_IS_BURNING = (mob, blackboard, args) -> mob.isOnFire();

    @GoalConditionDef(value = "Self.IsHealthLow", args = {"num:percent"}, desc = "自分の体力がN%未満である", descEn = "Health is Low (%)")
    public static final ConditionComponent SELF_IS_HEALTH_LOW = (mob, blackboard, args) -> (mob.getHealth() / mob.getMaxHealth()) * 100 <= args.get("percent").checkdouble();

    @GoalConditionDef(value = "Self.HasItem", args = {"str:itemId", "num:minCount"}, desc = "インベントリに指定アイテムをN個以上持っている", descEn = "Has Item in Inventory")
    public static final ConditionComponent SELF_HAS_ITEM = (mob, blackboard, args) -> {
        if (mob.inventorySize == 0) return false;
        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args.get("itemId").checkjstring()));
        int count = 0;
        int min = args.get("minCount").checkint();
        for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
            ItemStack stack = mob.inventory.getItem(i);
            if (stack.is(targetItem)) {
                count += stack.getCount();
                if (count >= min) return true;
            }
        }
        return false;
    };

    @GoalConditionDef(value = "Self.HasEffect", args = {"str:effectId"}, desc = "指定のポーション効果を受けている", descEn = "Has Potion Effect")
    public static final ConditionComponent SELF_HAS_EFFECT = (mob, blackboard, args) -> {
        ResourceLocation id = ResourceLocation.parse(args.get("effectId").checkjstring());
        for (net.minecraft.world.effect.MobEffectInstance instance : mob.getActiveEffects()) {
            if (BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()).equals(id)) {
                return true;
            }
        }
        return false;
    };


    // ==========================================
    // 4. System (システム) の条件
    // ==========================================
    @GoalConditionDef(value = "System.IsEntityNear", args = {"str:entityId", "num:distance"}, desc = "指定モブが視界内か指定距離(0で視界)にいる", descEn = "Entity is Near")
    public static final ConditionComponent SYSTEM_IS_ENTITY_NEAR = (mob, blackboard, args) -> {
        String entityId = args.get("entityId").checkjstring();

        String timeKey = "sys_ent_time_" + entityId;
        String resKey = "sys_ent_res_" + entityId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) {
            return blackboard.containsKey(resKey) && (boolean) blackboard.get(resKey);
        }
        blackboard.put(timeKey, (long) mob.tickCount);

        double r = args.get("distance").isnil() || args.get("distance").checkdouble() <= 0
                ? mob.traitManager.getSensingRangeMax()
                : args.get("distance").checkdouble();

        List<Entity> entities = mob.level().getEntitiesOfClass(Entity.class, mob.getBoundingBox().inflate(r),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(entityId) && mob.canSeeEntity(e));

        boolean found = !entities.isEmpty();
        blackboard.put(resKey, found);
        return found;
    };

    @GoalConditionDef(value = "System.IsBlockNear", args = {"str:blockId", "num:distance"}, desc = "指定ブロックが視界内か指定距離(0で視界)にある", descEn = "Block is Near")
    public static final ConditionComponent SYSTEM_IS_BLOCK_NEAR = (mob, blackboard, args) -> {
        String blockId = args.get("blockId").checkjstring();

        String timeKey = "sys_blk_time_" + blockId;
        String resKey = "sys_blk_res_" + blockId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) {
            return blackboard.containsKey(resKey) && (boolean) blackboard.get(resKey);
        }
        blackboard.put(timeKey, (long) mob.tickCount);

        double rDist = args.get("distance").isnil() || args.get("distance").checkdouble() <= 0
                ? mob.traitManager.getSensingRangeMax()
                : args.get("distance").checkdouble();
        int r = (int) Math.min(rDist, 32);

        BlockPos pos = mob.blockPosition();
        Level level = mob.level();
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockState bs = level.getBlockState(pos.offset(x, y, z));
                    if (BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString().equals(blockId)) {
                        blackboard.put(resKey, true);
                        return true;
                    }
                }
            }
        }
        blackboard.put(resKey, false);
        return false;
    };

    @GoalConditionDef(value = "System.IsDaytime", desc = "昼間である", descEn = "Is Daytime")
    public static final ConditionComponent SYSTEM_IS_DAYTIME = (mob, blackboard, args) -> mob.level().isDay();

    @GoalConditionDef(value = "System.RandomChance", args = {"num:chance"}, desc = "指定確率(0.0~1.0)で真になる", descEn = "Random chance to be true")
    public static final ConditionComponent SYSTEM_RANDOM_CHANCE = (mob, blackboard, args) -> mob.getRandom().nextDouble() < args.get("chance").checkdouble();

    // ★追加: ユーザーがUIで指定時間を設けてステートを抜けるためのタイマー条件
    @GoalConditionDef(value = "System.TimeInState", args = {"num:ticks"}, desc = "このノードに入ってからNチック経過", descEn = "Time in State")
    public static final ConditionComponent SYSTEM_TIME_IN_STATE = (mob, blackboard, args) -> {
        if (!blackboard.containsKey("goal_start_time")) return false;
        int startTime = (int) blackboard.get("goal_start_time");
        return (mob.tickCount - startTime) >= args.get("ticks").checkint();
    };

    // ==========================================
    // 5. 内部ステート遷移用 (Hidden State Hooks)
    // ==========================================
    @GoalConditionDef(value = "System.HasStateKey", args = {"str:key"}, desc = "(内部処理用) ステートキー判定", descEn = "(Internal) Has State Key")
    public static final ConditionComponent SYSTEM_HAS_STATE_KEY = (mob, blackboard, args) -> blackboard.containsKey(args.get("key").checkjstring());

    @GoalConditionDef(value = "System.IsInitialState", args = {"str:key"}, desc = "(内部処理用) 初期ステート判定", descEn = "(Internal) Is Initial State")
    public static final ConditionComponent SYSTEM_IS_INITIAL_STATE = (mob, blackboard, args) -> {
        String key = args.get("key").checkjstring();
        if (blackboard.containsKey(key)) return false;
        return blackboard.keySet().stream().noneMatch(k -> k.startsWith("state_"));
    };
}