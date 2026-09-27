package com.nishiyu.lunex.entity.goals;

import com.nishiyu.lunex.entity.goals.GoalComponentRegistry.ActionComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GoalActions {

    // ==========================================
    // 1. 思考・記憶 (Targeting & Memory)
    // ==========================================
    @GoalActionDef(value = "TargetEntity.RememberNear", args = {"str:entityId", "num:radius"}, desc = "半径N以内の指定モブをターゲットに記憶する", descEn = "Remember Near Entity as Target")
    public static final ActionComponent TARGET_ENTITY_REMEMBER_NEAR = (mob, blackboard, args) -> {
        // ★修正: ターゲットが死んだり無効になった場合はリセットする
        if (mob.targetEntity != null) {
            if (!mob.targetEntity.isAlive()) {
                mob.targetEntity = null;
            } else {
                return;
            }
        }

        String entityId = args.get("entityId").checkjstring();
        String timeKey = "rem_ent_time_" + entityId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) return;
        blackboard.put(timeKey, (long) mob.tickCount);

        double radius = args.get("radius").checkdouble();
        List<Entity> entities = mob.level().getEntitiesOfClass(Entity.class, mob.getBoundingBox().inflate(radius),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(entityId) && mob.canSeeEntity(e));

        if (!entities.isEmpty()) {
            mob.targetEntity = entities.get(0);
        }
    };

    @GoalActionDef(value = "TargetEntity.RememberAttacker", desc = "自分を攻撃してきた相手をターゲットに記憶する", descEn = "Remember Attacker as Target")
    public static final ActionComponent TARGET_ENTITY_REMEMBER_ATTACKER = (mob, blackboard, args) -> {
        if (mob.getTarget() != null) {
            mob.targetEntity = mob.getTarget();
        } else if (mob.getLastHurtByMob() != null) {
            mob.targetEntity = mob.getLastHurtByMob();
        }
    };

    @GoalActionDef(value = "TargetEntity.Forget", desc = "ターゲットエンティティの記憶を消去する", descEn = "Forget Target Entity")
    public static final ActionComponent TARGET_ENTITY_FORGET = (mob, blackboard, args) -> {
        mob.targetEntity = null;
    };

    @GoalActionDef(value = "TargetBlock.RememberNear", args = {"str:blockId", "num:radius"}, desc = "半径N以内の指定ブロックをターゲットに記憶する", descEn = "Remember Near Block as Target")
    public static final ActionComponent TARGET_BLOCK_REMEMBER_NEAR = (mob, blackboard, args) -> {
        if (mob.targetPos != null) return;

        String blockId = args.get("blockId").checkjstring();
        // ★最適化: ブロック記憶処理も20ティック(1秒)のクールダウンを設ける
        String timeKey = "rem_blk_time_" + blockId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) return;
        blackboard.put(timeKey, (long) mob.tickCount);

        int r = Math.min(args.get("radius").checkint(), 16);
        BlockPos pos = mob.blockPosition();
        Level level = mob.level();
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = pos.offset(x, y, z);
                    BlockState bs = level.getBlockState(p);
                    if (BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString().equals(blockId)) {
                        mob.targetPos = p;
                        return;
                    }
                }
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.Forget", desc = "ターゲットブロックの記憶を消去する", descEn = "Forget Target Block")
    public static final ActionComponent TARGET_BLOCK_FORGET = (mob, blackboard, args) -> {
        mob.targetPos = null;
    };

    @GoalActionDef(value = "System.SetTimer", args = {"str:timerId", "num:ticks"}, desc = "Nチック後に発火するタイマーをセットする", descEn = "Set Timer (Ticks)")
    public static final ActionComponent SYSTEM_SET_TIMER = (mob, blackboard, args) -> {
        blackboard.put("timer_" + args.get("timerId").checkjstring(), args.get("ticks").checkint());
    };


    // ==========================================
    // 2. 基本動作 (Movement & Look)
    // ==========================================
    @GoalActionDef(value = "Move.ToTargetEntity", args = {"num:speed"}, desc = "記憶したモブへ近づく", descEn = "Move To Target Entity")
    public static final ActionComponent MOVE_TO_TARGET_ENTITY = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            boolean shouldRecalculate = true;
            if (mob.getNavigation().getPath() != null && !mob.getNavigation().isDone()) {
                net.minecraft.world.phys.Vec3 targetPos = mob.targetEntity.position();
                net.minecraft.core.BlockPos targetNode = mob.getNavigation().getPath().getTarget();
                if (targetNode != null) {
                    net.minecraft.world.phys.Vec3 currentPathEnd = net.minecraft.world.phys.Vec3.atLowerCornerOf(targetNode);
                    if (currentPathEnd.distanceToSqr(targetPos) < 2.25) {
                        shouldRecalculate = false;
                    }
                }
            }

            if (shouldRecalculate) {
                mob.getNavigation().moveTo(mob.targetEntity, args.get("speed").checkdouble());
            }
        }
    };

    @GoalActionDef(value = "Move.ToTargetBlock", args = {"num:speed"}, desc = "記憶したブロックへ近づく", descEn = "Move To Target Block")
    public static final ActionComponent MOVE_TO_TARGET_BLOCK = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            // ★最適化: ブロックは動かないため、すでに経路を持っていて移動中なら再計算をスキップする
            if (mob.getNavigation().getPath() == null || mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(mob.targetPos.getX() + 0.5D, mob.targetPos.getY(), mob.targetPos.getZ() + 0.5D, args.get("speed").checkdouble());
            }
        }
    };

    @GoalActionDef(value = "Move.Stop", desc = "立ち止まる", descEn = "Stop Moving")
    public static final ActionComponent MOVE_STOP = (mob, blackboard, args) -> {
        mob.getNavigation().stop();
    };

    @GoalActionDef(value = "Look.AtTargetEntity", desc = "記憶したモブの方を向く", descEn = "Look At Target Entity")
    public static final ActionComponent LOOK_AT_TARGET_ENTITY = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            mob.getLookControl().setLookAt(mob.targetEntity, 10.0F, (float) mob.getMaxHeadXRot());
        }
    };


    // ==========================================
    // 3. 戦闘・インタラクト (Combat & Interact)
    // ==========================================
    @GoalActionDef(value = "TargetEntity.Attack", desc = "ターゲットを近接攻撃する", descEn = "Attack Target Entity")
    public static final ActionComponent TARGET_ENTITY_ATTACK = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(mob.targetEntity);
        }
    };

    @GoalActionDef(value = "TargetEntity.ShootArrow", args = {"num:velocity", "num:inaccuracy"}, desc = "ターゲットに矢を射る", descEn = "Shoot Arrow at Target")
    public static final ActionComponent TARGET_ENTITY_SHOOT_ARROW = (mob, blackboard, args) -> {
        LivingEntity target = mob.getTarget() != null ? mob.getTarget() : (mob.targetEntity instanceof LivingEntity ? (LivingEntity) mob.targetEntity : null);
        if (target != null) {
            boolean consumed = false;
            for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                ItemStack stack = mob.inventory.getItem(i);
                if (stack.getItem() instanceof net.minecraft.world.item.ArrowItem && !stack.isEmpty()) {
                    stack.shrink(1);
                    consumed = true;
                    break;
                }
            }
            if (consumed) {
                net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, mob.level());
                arrow.setOwner(mob);
                arrow.setPos(mob.getX(), mob.getEyeY() - 0.1D, mob.getZ());
                double d0 = target.getX() - mob.getX(), d1 = target.getY(0.33D) - arrow.getY(), d2 = target.getZ() - mob.getZ();

                float velocity = (float) args.get("velocity").checkdouble();
                float inaccuracy = (float) args.get("inaccuracy").checkdouble();
                arrow.shoot(d0, d1 + Math.sqrt(d0 * d0 + d2 * d2) * 0.2D, d2, velocity, inaccuracy);

                arrow.setBaseDamage(arrow.getBaseDamage() + mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
                mob.level().addFreshEntity(arrow);
            }
        }
    };

    @GoalActionDef(value = "TargetEntity.PickUp", args = {"num:reach"}, desc = "ターゲットのアイテムを拾う", descEn = "Pick up Target Item")
    public static final ActionComponent TARGET_ENTITY_PICK_UP = (mob, blackboard, args) -> {
        if (mob.targetEntity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
            double reach = args.get("reach").isnil() ? 2.0 : args.get("reach").checkdouble();
            if (mob.distanceToSqr(itemEntity) <= reach * reach) {
                ItemStack stack = itemEntity.getItem();
                ItemStack remainder = mob.inventory.addItem(stack);
                if (remainder.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(remainder);
                }
                mob.onItemPickup(itemEntity);
                mob.targetEntity = null;
            }
        }
    };

    @GoalActionDef(value = "TargetEntity.Breed", args = {"num:reach"}, desc = "ターゲットの動物に餌を与えて繁殖させる", descEn = "Breed Target Animal")
    public static final ActionComponent TARGET_ENTITY_BREED = (mob, blackboard, args) -> {
        if (mob.targetEntity instanceof net.minecraft.world.entity.animal.Animal animal) {
            double reach = args.get("reach").isnil() ? 3.0 : args.get("reach").checkdouble();
            if (mob.distanceToSqr(animal) <= reach * reach && animal.getAge() == 0 && animal.canFallInLove()) {
                for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                    ItemStack stack = mob.inventory.getItem(i);
                    if (animal.isFood(stack)) {
                        stack.shrink(1);
                        animal.setInLove(mob.getFakePlayer() != null ? mob.getFakePlayer() : null);
                        mob.swing(InteractionHand.MAIN_HAND);
                        mob.targetEntity = null;
                        break;
                    }
                }
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.Break", desc = "ターゲットブロックを破壊する", descEn = "Break Target Block")
    public static final ActionComponent TARGET_BLOCK_BREAK = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                var fakePlayer = mob.getFakePlayer();
                if (fakePlayer != null) {
                    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, mob.getItemInHand(InteractionHand.MAIN_HAND));
                    fakePlayer.gameMode.destroyBlock(mob.targetPos);
                    mob.setItemInHand(InteractionHand.MAIN_HAND, fakePlayer.getItemInHand(InteractionHand.MAIN_HAND));
                } else {
                    mob.level().destroyBlock(mob.targetPos, true, mob);
                }
            }
            mob.swing(InteractionHand.MAIN_HAND);
        }
    };

    @GoalActionDef(value = "TargetBlock.Place", args = {"str:blockId"}, desc = "ターゲットブロックの位置にブロックを置く", descEn = "Place Block at Target Position")
    public static final ActionComponent TARGET_BLOCK_PLACE = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(args.get("blockId").checkjstring()));
            Item blockItem = block.asItem();

            boolean consumed = false;
            for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                ItemStack stack = mob.inventory.getItem(i);
                if (stack.is(blockItem) && !stack.isEmpty()) {
                    stack.shrink(1);
                    consumed = true;
                    break;
                }
            }

            if (consumed) {
                mob.level().setBlock(mob.targetPos, block.defaultBlockState(), 3);
                mob.swing(InteractionHand.MAIN_HAND);
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.Interact", desc = "ターゲットブロックを右クリックする(ドア開閉など)", descEn = "Interact with Target Block")
    public static final ActionComponent TARGET_BLOCK_INTERACT = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            if (mob.level() instanceof net.minecraft.server.level.ServerLevel) {
                var fakePlayer = mob.getFakePlayer();
                if (fakePlayer != null) {
                    net.minecraft.world.phys.BlockHitResult hitResult = new net.minecraft.world.phys.BlockHitResult(
                            Vec3.atCenterOf(mob.targetPos), net.minecraft.core.Direction.UP, mob.targetPos, false
                    );
                    fakePlayer.gameMode.useItemOn(
                            fakePlayer, mob.level(), fakePlayer.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hitResult
                    );
                }
            }
            mob.swing(InteractionHand.MAIN_HAND);
        }
    };

    // ==========================================
    // 4. マクロ動作 (Complex Actions)
    // ==========================================
    @GoalActionDef(value = "Self.CraftItem", args = {"str:resultId", "num:resultCount", "str:ingredients"}, desc = "インベントリ内で指定レシピをクラフトする", descEn = "Craft Item in Inventory")
    public static final ActionComponent SELF_CRAFT_ITEM = (mob, blackboard, args) -> {
        String[] parts = args.get("ingredients").checkjstring().split(",");
        boolean canCraft = true;

        for (String part : parts) {
            String[] itemAndCount = part.split("\\*");
            if (itemAndCount.length == 2) {
                Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemAndCount[0]));
                int required = Integer.parseInt(itemAndCount[1]);
                int current = 0;
                for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                    ItemStack stack = mob.inventory.getItem(i);
                    if (stack.is(targetItem)) current += stack.getCount();
                }
                if (current < required) {
                    canCraft = false;
                    break;
                }
            }
        }

        if (canCraft) {
            for (String part : parts) {
                String[] itemAndCount = part.split("\\*");
                if (itemAndCount.length == 2) {
                    Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemAndCount[0]));
                    int required = Integer.parseInt(itemAndCount[1]);
                    for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                        if (required <= 0) break;
                        ItemStack stack = mob.inventory.getItem(i);
                        if (stack.is(targetItem)) {
                            int take = Math.min(stack.getCount(), required);
                            stack.shrink(take);
                            required -= take;
                        }
                    }
                }
            }
            Item resultItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args.get("resultId").checkjstring()));
            ItemStack resultStack = new ItemStack(resultItem, args.get("resultCount").checkint());

            ItemStack remainder = mob.inventory.addItem(resultStack);
            if (!remainder.isEmpty()) {
                mob.spawnAtLocation(remainder);
            }
            mob.swing(InteractionHand.MAIN_HAND);
        }
    };

    @GoalActionDef(value = "TargetBlock.InsertItemToFurnace", args = {"str:slotType", "str:itemId", "num:count"}, desc = "ターゲットのかまどにアイテムを入れる", descEn = "Insert Item to Target Furnace")
    public static final ActionComponent TARGET_BLOCK_INSERT_ITEM_TO_FURNACE = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            net.minecraft.world.level.block.entity.BlockEntity be = mob.level().getBlockEntity(mob.targetPos);
            if (be instanceof net.minecraft.world.Container furnace) {
                int targetSlot = args.get("slotType").checkjstring().equals("fuel") ? 1 : 0;
                Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args.get("itemId").checkjstring()));
                int required = args.get("count").checkint();

                for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                    if (required <= 0) break;
                    ItemStack stack = mob.inventory.getItem(i);
                    if (stack.is(targetItem)) {
                        ItemStack furnaceStack = furnace.getItem(targetSlot);
                        if (furnaceStack.isEmpty() || (ItemStack.isSameItemSameComponents(furnaceStack, stack) && furnaceStack.getCount() < furnaceStack.getMaxStackSize())) {
                            int move = Math.min(stack.getCount(), Math.min(required, furnace.getMaxStackSize() - furnaceStack.getCount()));
                            if (furnaceStack.isEmpty()) {
                                furnace.setItem(targetSlot, stack.split(move));
                            } else {
                                furnaceStack.grow(move);
                                stack.shrink(move);
                            }
                            required -= move;
                        }
                    }
                }
                mob.swing(InteractionHand.MAIN_HAND);
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.TakeItemFromContainer", args = {"str:itemId", "num:count"}, desc = "ターゲットのチェストからアイテムを取り出す", descEn = "Take Item From Target Container")
    public static final ActionComponent TARGET_BLOCK_TAKE_ITEM_FROM_CONTAINER = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            net.minecraft.world.level.block.entity.BlockEntity be = mob.level().getBlockEntity(mob.targetPos);
            if (be instanceof net.minecraft.world.Container chest) {
                Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args.get("itemId").checkjstring()));
                int amountNeeded = args.get("count").checkint();
                for (int j = 0; j < chest.getContainerSize(); j++) {
                    if (amountNeeded <= 0) break;
                    ItemStack chestStack = chest.getItem(j);
                    if (!chestStack.isEmpty() && chestStack.is(targetItem)) {
                        int take = Math.min(chestStack.getCount(), amountNeeded);
                        ItemStack takenStack = chest.removeItem(j, take);
                        for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                            if (takenStack.isEmpty()) break;
                            ItemStack mobStack = mob.inventory.getItem(i);
                            if (mobStack.isEmpty()) {
                                mob.inventory.setItem(i, takenStack.copy());
                                takenStack.setCount(0);
                            } else if (ItemStack.isSameItemSameComponents(mobStack, takenStack) && mobStack.getCount() < mobStack.getMaxStackSize()) {
                                int space = mobStack.getMaxStackSize() - mobStack.getCount();
                                int move = Math.min(takenStack.getCount(), space);
                                mobStack.grow(move);
                                takenStack.shrink(move);
                            }
                        }
                        if (!takenStack.isEmpty()) {
                            mob.spawnAtLocation(takenStack);
                        }
                        amountNeeded -= take;
                    }
                }
                mob.swing(InteractionHand.MAIN_HAND);
            }
        }
    };
}