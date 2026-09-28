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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GoalActions {

    // ==========================================
    // 1. ターゲット設定 (Targeting)
    // ==========================================
    @GoalActionDef(value = "TargetEntity.FindAndTarget", args = {"str:entityId"}, desc = "視界内の指定モブを探してターゲットにする", descEn = "Find & Target near Entity")
    public static final ActionComponent TARGET_ENTITY_FIND_AND_TARGET = (mob, blackboard, args) -> {
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

        double radius = mob.traitManager.getSensingRangeMax();
        List<Entity> entities = mob.level().getEntitiesOfClass(Entity.class, mob.getBoundingBox().inflate(radius),
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(entityId) && mob.canSeeEntity(e));

        if (!entities.isEmpty()) {
            mob.targetEntity = entities.get(0);
        }
    };

    @GoalActionDef(value = "TargetEntity.TargetAttacker", desc = "攻撃してきた相手をターゲットにする", descEn = "Target Attacker")
    public static final ActionComponent TARGET_ENTITY_TARGET_ATTACKER = (mob, blackboard, args) -> {
        if (mob.getTarget() != null) {
            mob.targetEntity = mob.getTarget();
        } else if (mob.getLastHurtByMob() != null) {
            mob.targetEntity = mob.getLastHurtByMob();
        }
    };

    @GoalActionDef(value = "TargetEntity.ClearTarget", desc = "モブのターゲットを解除する", descEn = "Clear Entity Target")
    public static final ActionComponent TARGET_ENTITY_CLEAR_TARGET = (mob, blackboard, args) -> {
        mob.targetEntity = null;
    };

    @GoalActionDef(value = "TargetBlock.FindAndTarget", args = {"str:blockId"}, desc = "視界内の指定ブロックを探してターゲットにする", descEn = "Find & Target near Block")
    public static final ActionComponent TARGET_BLOCK_FIND_AND_TARGET = (mob, blackboard, args) -> {
        if (mob.targetPos != null) return;

        String blockId = args.get("blockId").checkjstring();
        String timeKey = "rem_blk_time_" + blockId;
        long lastTime = blackboard.containsKey(timeKey) ? (long) blackboard.get(timeKey) : 0;
        if (mob.tickCount - lastTime < 20) return;
        blackboard.put(timeKey, (long) mob.tickCount);

        int maxR = (int) Math.min(mob.traitManager.getSensingRangeMax(), 32);
        BlockPos pos = mob.blockPosition();
        Level level = mob.level();
        Vec3 eyePos = mob.getEyePosition();

        for (int r = 1; r <= maxR; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) < r && Math.abs(y) < r && Math.abs(z) < r) continue;

                        BlockPos p = pos.offset(x, y, z);
                        BlockState bs = level.getBlockState(p);
                        if (BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString().equals(blockId)) {
                            Vec3 targetPosCenter = Vec3.atCenterOf(p);
                            BlockHitResult hit = level.clip(new ClipContext(eyePos, targetPosCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));

                            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p)) {
                                mob.targetPos = p;
                                return;
                            }
                        }
                    }
                }
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.ClearTarget", desc = "ブロックのターゲットを解除する", descEn = "Clear Block Target")
    public static final ActionComponent TARGET_BLOCK_CLEAR_TARGET = (mob, blackboard, args) -> {
        mob.targetPos = null;
    };

    @GoalActionDef(value = "System.StartTimer", args = {"str:timerId", "num:ticks"}, desc = "Nチック後に発火するタイマーを開始する", descEn = "Start Timer (Ticks)")
    public static final ActionComponent SYSTEM_START_TIMER = (mob, blackboard, args) -> {
        blackboard.put("timer_" + args.get("timerId").checkjstring(), args.get("ticks").checkint());
    };


    // ==========================================
    // 2. 移動・視点 (Movement & Look)
    // ==========================================
    @GoalActionDef(value = "Move.ToTargetEntity", args = {"num:distance"}, desc = "ターゲットモブへ近づく(0で操作可能距離)", descEn = "Move To Target Entity")
    public static final ActionComponent MOVE_TO_TARGET_ENTITY = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            double stopDist = args.get("distance").checkdouble();
            if (stopDist <= 0) stopDist = mob.traitManager.getInteractRange();

            double currentDist = mob.distanceTo(mob.targetEntity);
            if (currentDist > stopDist) {
                blackboard.put("busy_until", mob.tickCount + 2);

                double lastDist = blackboard.containsKey("ent_last_dist") ? (double) blackboard.get("ent_last_dist") : currentDist;
                long lastMove = blackboard.containsKey("ent_last_calc") ? (long) blackboard.get("ent_last_calc") : 0;

                if ((mob.tickCount - lastMove) >= 10) {
                    blackboard.put("ent_last_calc", (long) mob.tickCount);

                    if (Math.abs(lastDist - currentDist) < 0.1) {
                        int failCount = blackboard.containsKey("ent_fail_count") ? (int) blackboard.get("ent_fail_count") : 0;
                        failCount++;
                        if (failCount >= 5) {
                            mob.targetEntity = null;
                            mob.getNavigation().stop();
                            blackboard.put("ent_fail_count", 0);
                            blackboard.remove("busy_until");
                            return;
                        }
                        blackboard.put("ent_fail_count", failCount);
                    } else {
                        blackboard.put("ent_fail_count", 0);
                    }
                    blackboard.put("ent_last_dist", currentDist);
                    mob.getNavigation().moveTo(mob.targetEntity, 1.0D);
                }
            } else {
                mob.getNavigation().stop();
                blackboard.put("ent_fail_count", 0);
            }
        }
    };

    @GoalActionDef(value = "Move.ToTargetBlock", args = {"num:distance"}, desc = "ターゲットブロックへ近づく(0で操作可能距離)", descEn = "Move To Target Block")
    public static final ActionComponent MOVE_TO_TARGET_BLOCK = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            double stopDist = args.get("distance").checkdouble();
            if (stopDist <= 0) stopDist = mob.traitManager.getInteractRange();

            double currentDist = Math.sqrt(mob.distanceToSqr(Vec3.atCenterOf(mob.targetPos)));
            if (currentDist > stopDist) {
                blackboard.put("busy_until", mob.tickCount + 2);

                double lastDist = blackboard.containsKey("blk_last_dist") ? (double) blackboard.get("blk_last_dist") : currentDist;
                long lastMove = blackboard.containsKey("blk_last_calc") ? (long) blackboard.get("blk_last_calc") : 0;

                if ((mob.tickCount - lastMove) >= 10) {
                    blackboard.put("blk_last_calc", (long) mob.tickCount);

                    if (Math.abs(lastDist - currentDist) < 0.1) {
                        int failCount = blackboard.containsKey("blk_fail_count") ? (int) blackboard.get("blk_fail_count") : 0;
                        failCount++;
                        if (failCount >= 5) {
                            mob.targetPos = null;
                            mob.getNavigation().stop();
                            blackboard.put("blk_fail_count", 0);
                            blackboard.remove("busy_until");
                            return;
                        }
                        blackboard.put("blk_fail_count", failCount);
                    } else {
                        blackboard.put("blk_fail_count", 0);
                    }
                    blackboard.put("blk_last_dist", currentDist);
                    mob.getNavigation().moveTo(mob.targetPos.getX() + 0.5D, mob.targetPos.getY(), mob.targetPos.getZ() + 0.5D, 1.0D);
                }
            } else {
                mob.getNavigation().stop();
                blackboard.put("blk_fail_count", 0);
            }
        }
    };

    @GoalActionDef(value = "Move.Stop", desc = "移動を停止する", descEn = "Stop Moving")
    public static final ActionComponent MOVE_STOP = (mob, blackboard, args) -> {
        mob.getNavigation().stop();
    };

    @GoalActionDef(value = "Look.AtTargetEntity", desc = "ターゲットモブの方を向く", descEn = "Look At Target Entity")
    public static final ActionComponent LOOK_AT_TARGET_ENTITY = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            mob.getLookControl().setLookAt(mob.targetEntity, 10.0F, (float) mob.getMaxHeadXRot());
        }
    };


    // ==========================================
    // 3. アクション・操作 (Combat & Interact)
    // ==========================================
    @GoalActionDef(value = "TargetEntity.AttackMelee", desc = "ターゲットに近接攻撃を行う", descEn = "Melee Attack Target")
    public static final ActionComponent TARGET_ENTITY_ATTACK_MELEE = (mob, blackboard, args) -> {
        if (mob.targetEntity != null) {
            long lastAttack = blackboard.containsKey("last_attack_time") ? (long) blackboard.get("last_attack_time") : 0;
            if (mob.tickCount - lastAttack >= 15) {
                // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                mob.swing(InteractionHand.MAIN_HAND, true);

                mob.doHurtTarget(mob.targetEntity);
                blackboard.put("last_attack_time", (long) mob.tickCount);
            }
        }
    };

    @GoalActionDef(value = "TargetEntity.ShootArrow", desc = "ターゲットに矢を撃つ", descEn = "Shoot Arrow at Target")
    public static final ActionComponent TARGET_ENTITY_SHOOT_ARROW = (mob, blackboard, args) -> {
        LivingEntity target = mob.getTarget() != null ? mob.getTarget() : (mob.targetEntity instanceof LivingEntity ? (LivingEntity) mob.targetEntity : null);
        if (target != null) {
            long lastShoot = blackboard.containsKey("last_shoot_time") ? (long) blackboard.get("last_shoot_time") : 0;
            if (mob.tickCount - lastShoot >= 20) {
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

                    arrow.shoot(d0, d1 + Math.sqrt(d0 * d0 + d2 * d2) * 0.2D, d2, 1.6f, 1.0f);
                    arrow.setBaseDamage(arrow.getBaseDamage() + mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
                    mob.level().addFreshEntity(arrow);

                    // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                    mob.swing(InteractionHand.MAIN_HAND, true);

                    blackboard.put("last_shoot_time", (long) mob.tickCount);
                }
            }
        }
    };

    @GoalActionDef(value = "TargetEntity.PickUpItem", desc = "ターゲットアイテムを拾う", descEn = "Pick up Target Item")
    public static final ActionComponent TARGET_ENTITY_PICK_UP_ITEM = (mob, blackboard, args) -> {
        if (mob.targetEntity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
            double reach = mob.traitManager.getInteractRange();
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

    @GoalActionDef(value = "TargetEntity.BreedAnimal", desc = "ターゲットの動物に餌を与えて繁殖させる", descEn = "Breed Target Animal")
    public static final ActionComponent TARGET_ENTITY_BREED_ANIMAL = (mob, blackboard, args) -> {
        if (mob.targetEntity instanceof net.minecraft.world.entity.animal.Animal animal) {
            double reach = mob.traitManager.getInteractRange();
            if (mob.distanceToSqr(animal) <= reach * reach && animal.getAge() == 0 && animal.canFallInLove()) {
                for (int i = 0; i < mob.inventory.getContainerSize(); i++) {
                    ItemStack stack = mob.inventory.getItem(i);
                    if (animal.isFood(stack)) {
                        stack.shrink(1);
                        animal.setInLove(mob.getFakePlayer() != null ? mob.getFakePlayer() : null);

                        // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                        mob.swing(InteractionHand.MAIN_HAND, true);

                        mob.targetEntity = null;
                        break;
                    }
                }
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.Break", desc = "ターゲットブロックを採掘する", descEn = "Mine Target Block")
    public static final ActionComponent TARGET_BLOCK_BREAK = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            boolean broken = mob.tickMining(mob.targetPos);
            if (broken) {
                mob.targetPos = null;
            } else {
                blackboard.put("busy_until", mob.tickCount + 2);
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.Place", args = {"str:blockId"}, desc = "ターゲット位置にブロックを置く", descEn = "Place Block at Target Position")
    public static final ActionComponent TARGET_BLOCK_PLACE = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            long lastPlace = blackboard.containsKey("last_place_time") ? (long) blackboard.get("last_place_time") : 0;
            if (mob.tickCount - lastPlace >= 10) {
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

                    // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                    mob.swing(InteractionHand.MAIN_HAND, true);

                    blackboard.put("last_place_time", (long) mob.tickCount);
                }
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.Interact", desc = "ターゲットブロックを右クリック(操作)する", descEn = "Interact with Target Block")
    public static final ActionComponent TARGET_BLOCK_INTERACT = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            long lastInteract = blackboard.containsKey("last_interact_time") ? (long) blackboard.get("last_interact_time") : 0;
            if (mob.tickCount - lastInteract >= 10) {
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

                // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                mob.swing(InteractionHand.MAIN_HAND, true);

                blackboard.put("last_interact_time", (long) mob.tickCount);
            }
        }
    };

    // ==========================================
    // 4. マクロ動作 (Complex Actions)
    // ==========================================
    @GoalActionDef(value = "Self.CraftItem", args = {"str:resultId", "num:resultCount", "str:ingredients"}, desc = "インベントリ内でアイテムをクラフトする", descEn = "Craft Item in Inventory")
    public static final ActionComponent SELF_CRAFT_ITEM = (mob, blackboard, args) -> {
        long lastCraft = blackboard.containsKey("last_craft_time") ? (long) blackboard.get("last_craft_time") : 0;
        if (mob.tickCount - lastCraft >= 20) {
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

                // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                mob.swing(InteractionHand.MAIN_HAND, true);

                blackboard.put("last_craft_time", (long) mob.tickCount);
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.PutItemInFurnace", args = {"str:slotType", "str:itemId", "num:count"}, desc = "ターゲットのかまどにアイテムを入れる", descEn = "Put Item in Furnace")
    public static final ActionComponent TARGET_BLOCK_PUT_ITEM_IN_FURNACE = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            long lastPut = blackboard.containsKey("last_put_time") ? (long) blackboard.get("last_put_time") : 0;
            if (mob.tickCount - lastPut >= 10) {
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

                    // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                    mob.swing(InteractionHand.MAIN_HAND, true);

                    blackboard.put("last_put_time", (long) mob.tickCount);
                }
            }
        }
    };

    @GoalActionDef(value = "TargetBlock.TakeItemFromContainer", args = {"str:itemId", "num:count"}, desc = "ターゲットのチェスト等からアイテムを取り出す", descEn = "Take Item From Container")
    public static final ActionComponent TARGET_BLOCK_TAKE_ITEM_FROM_CONTAINER = (mob, blackboard, args) -> {
        if (mob.targetPos != null) {
            long lastTake = blackboard.containsKey("last_take_time") ? (long) blackboard.get("last_take_time") : 0;
            if (mob.tickCount - lastTake >= 10) {
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

                    // ★修正: バニラの swing を削除し、独自のイベントパケットのみを送信
                    mob.swing(InteractionHand.MAIN_HAND, true);

                    blackboard.put("last_take_time", (long) mob.tickCount);
                }
            }
        }
    };

    // ==========================================
    // 5. 内部ステート遷移 (Hidden State Transition)
    // ==========================================
    @GoalActionDef(value = "System.SetStateKey", args = {"str:key"}, desc = "(内部処理用) ステートを移行する", descEn = "(Internal) Set State Key")
    public static final ActionComponent SYSTEM_SET_STATE_KEY = (mob, blackboard, args) -> {
        boolean isBusy = blackboard.containsKey("busy_until") && mob.tickCount < (int) blackboard.get("busy_until");
        if (isBusy) return;

        String targetKey = args.get("key").checkjstring();
        blackboard.keySet().removeIf(k -> k.startsWith("state_"));
        blackboard.put(targetKey, true);
    };

    @GoalActionDef(value = "System.ClearStateKey", desc = "(内部処理用) ステートを初期状態に戻す", descEn = "(Internal) Clear All States")
    public static final ActionComponent SYSTEM_CLEAR_STATE_KEY = (mob, blackboard, args) -> {
        boolean isBusy = blackboard.containsKey("busy_until") && mob.tickCount < (int) blackboard.get("busy_until");
        if (isBusy) return;

        blackboard.keySet().removeIf(k -> k.startsWith("state_"));
    };
}