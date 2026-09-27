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
    @GoalConditionDef(value = "TargetBlock.HasTarget", desc = "ターゲットブロックが存在する", descEn = "Has Target Block")
    public static final ConditionComponent TARGET_BLOCK_HAS_TARGET = (mob, blackboard, args) -> mob.targetPos != null;

    @GoalConditionDef(value = "TargetBlock.IsType", args = {"str:blockId"}, desc = "ターゲットブロックが指定の種類である", descEn = "Target Block is Type")
    public static final ConditionComponent TARGET_BLOCK_IS_TYPE = (mob, blackboard, args) -> {
        if (mob.targetPos == null) return false;
        String blockId = args.get("blockId").checkjstring();
        BlockState bs = mob.level().getBlockState(mob.targetPos);
        return BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString().equals(blockId);
    };

    @GoalConditionDef(value = "TargetBlock.DistanceLessThan", args = {"num:distance"}, desc = "ターゲットブロックとの距離がNブロック未満", descEn = "Target Block is Near")
    public static final ConditionComponent TARGET_BLOCK_DISTANCE_LESS_THAN = (mob, blackboard, args) -> {
        if (mob.targetPos == null) return false;
        return mob.distanceToSqr(Vec3.atCenterOf(mob.targetPos)) < Math.pow(args.get("distance").checkdouble(), 2);
    };

    @GoalConditionDef(value = "TargetBlock.CheckContainerItemCount", args = {"str:itemId", "num:minCount"}, desc = "対象チェスト内に指定アイテムがN個以上ある", descEn = "Check Container Item Count")
    public static final ConditionComponent TARGET_BLOCK_CHECK_CONTAINER_ITEM_COUNT = (mob, blackboard, args) -> {
        if (mob.targetPos == null) return false;

        // ★最適化: チェストの走査は重いため10ティック(0.5秒)間隔に制限
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
    @GoalConditionDef(value = "TargetEntity.HasTarget", desc = "ターゲットエンティティが存在する", descEn = "Has Target Entity")
    public static final ConditionComponent TARGET_ENTITY_HAS_TARGET = (mob, blackboard, args) -> mob.targetEntity != null;

    @GoalConditionDef(value = "TargetEntity.IsType", args = {"str:entityId"}, desc = "ターゲットエンティティが指定の種類である", descEn = "Target Entity is Type")
    public static final ConditionComponent TARGET_ENTITY_IS_TYPE = (mob, blackboard, args) -> {
        if (mob.targetEntity == null) return false;
        String entityId = args.get("entityId").checkjstring();
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.targetEntity.getType()).toString().equals(entityId);
    };

    @GoalConditionDef(value = "TargetEntity.DistanceLessThan", args = {"num:distance"}, desc = "ターゲットエンティティとの距離がNブロック未満", descEn = "Target Entity is Near")
    public static final ConditionComponent TARGET_ENTITY_DISTANCE_LESS_THAN = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            return mob.distanceToSqr(mob.targetEntity) < Math.pow(args.get("distance").checkdouble(), 2);
        }
        return false;
    };

    @GoalConditionDef(value = "TargetEntity.HasLineOfSight", desc = "ターゲットエンティティへの射線が通っている", descEn = "Has Line of Sight to Target")
    public static final ConditionComponent TARGET_ENTITY_HAS_LINE_OF_SIGHT = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            return mob.getSensing().hasLineOfSight(mob.targetEntity);
        }
        return false;
    };

    @GoalConditionDef(value = "TargetEntity.IsAlive", desc = "ターゲットエンティティが生きている", descEn = "Target Entity is Alive")
    public static final ConditionComponent TARGET_ENTITY_IS_ALIVE = (mob, blackboard, args) -> {
        return mob.targetEntity != null && mob.targetEntity.isAlive();
    };


    // ==========================================
    // 3. Self (自身) の条件
    // ==========================================
    @GoalConditionDef(value = "Self.InWater", desc = "水中にいる", descEn = "Is in Water")
    public static final ConditionComponent SELF_IN_WATER = (mob, blackboard, args) -> mob.isInWater() || mob.level().isWaterAt(mob.blockPosition().below());

    @GoalConditionDef(value = "Self.IsOnGround", desc = "地面に足がついている", descEn = "Is on Ground")
    public static final ConditionComponent SELF_IS_ON_GROUND = (mob, blackboard, args) -> mob.onGround();

    @GoalConditionDef(value = "Self.IsBurning", desc = "燃えている", descEn = "Is Burning")
    public static final ConditionComponent SELF_IS_BURNING = (mob, blackboard, args) -> mob.isOnFire();

    @GoalConditionDef(value = "Self.HealthLessThan", args = {"num:percent"}, desc = "体力がN%未満である", descEn = "Health Less Than (%)")
    public static final ConditionComponent SELF_HEALTH_LESS_THAN = (mob, blackboard, args) -> (mob.getHealth() / mob.getMaxHealth()) * 100 <= args.get("percent").checkdouble();

    @GoalConditionDef(value = "Self.HasItemInInventory", args = {"str:itemId", "num:minCount"}, desc = "インベントリに指定アイテムをN個以上持っている", descEn = "Has Item in Inventory")
    public static final ConditionComponent SELF_HAS_ITEM_IN_INVENTORY = (mob, blackboard, args) -> {
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
    @GoalConditionDef(value = "System.HasEntityNear", args = {"str:entityId", "num:radius"}, desc = "半径N以内に指定モブがいる", descEn = "Has Entity Near")
    public static final ConditionComponent SYSTEM_HAS_ENTITY_NEAR = (mob, blackboard, args) -> {
        String entityId = args.get("entityId").checkjstring();

        // ★最適化: エンティティ検索を20ティック(1秒)に1回に制限してキャッシュする
        String timeKey = "sys_ent_time_" + entityId;
        String resKey = "sys_ent_res_" + entityId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) {
            return blackboard.containsKey(resKey) && (boolean) blackboard.get(resKey);
        }
        blackboard.put(timeKey, (long) mob.tickCount);

        double radius = args.get("radius").checkdouble();
        List<Entity> entities = mob.level().getEntitiesOfClass(Entity.class, mob.getBoundingBox().inflate(radius),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(entityId) && mob.canSeeEntity(e));

        boolean found = !entities.isEmpty();
        blackboard.put(resKey, found);
        return found;
    };

    @GoalConditionDef(value = "System.HasBlockTypeNear", args = {"str:blockId", "num:radius"}, desc = "半径N以内に指定ブロックがある", descEn = "Has Block Type Near")
    public static final ConditionComponent SYSTEM_HAS_BLOCK_TYPE_NEAR = (mob, blackboard, args) -> {
        String blockId = args.get("blockId").checkjstring();

        // ★最適化: ブロック検索を20ティック(1秒)に1回に制限してキャッシュする
        String timeKey = "sys_blk_time_" + blockId;
        String resKey = "sys_blk_res_" + blockId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) {
            return blackboard.containsKey(resKey) && (boolean) blackboard.get(resKey);
        }
        blackboard.put(timeKey, (long) mob.tickCount);

        int r = Math.min(args.get("radius").checkint(), 16);
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

    @GoalConditionDef(value = "System.HasStateKey", args = {"str:key"}, desc = "指定の記憶(ステート)を持っている", descEn = "Has State Key (Memory)")
    public static final ConditionComponent SYSTEM_HAS_STATE_KEY = (mob, blackboard, args) -> blackboard.containsKey(args.get("key").checkjstring());

    @GoalConditionDef(value = "System.RandomChance", args = {"num:chance"}, desc = "指定確率(0.0~1.0)で真になる", descEn = "Random chance to be true")
    public static final ConditionComponent SYSTEM_RANDOM_CHANCE = (mob, blackboard, args) -> mob.getRandom().nextDouble() < args.get("chance").checkdouble();
}