package com.nishiyu.lunex.entity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.entity.goals.MobGoalRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class CustomBehaviorRegistry implements ITranslationGatherer {

    public static final List<BehaviorDef> BEHAVIORS = List.of(
            new BehaviorDef(0, "follower", "Follow Player", true, List.of(), counts -> true, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("FollowPlayer", mob, 1.2D, MobGoalRegistry.buildOptions("startDist", 10.0, "stopDist", 3.0), null)));
            }),
            new BehaviorDef(1, "wait", "Wait / Stay", true, List.of(), counts -> true, (mob, prio) -> {
            }),
            new BehaviorDef(2, "wander", "Wander Freely", true, List.of(), counts -> true, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new FloatGoal(mob));
                mob.goalSelector.addGoal(prio + 1, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
                mob.goalSelector.addGoal(prio + 2, new LookAtPlayerGoal(mob, Player.class, 6.0F));
                mob.goalSelector.addGoal(prio + 3, new RandomLookAroundGoal(mob));
            }),
            new BehaviorDef(3, "autonomous", "Autonomous (Act Naturally)", true, List.of(), counts -> true, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new FloatGoal(mob));
                mob.goalSelector.addGoal(prio + 1, new LookAtPlayerGoal(mob, Player.class, 6.0F));
                mob.goalSelector.addGoal(prio + 2, new RandomLookAroundGoal(mob));
            }),
            new BehaviorDef(4, "hostile", "Hostile (Attack)", true, List.of(), counts -> true, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new MeleeAttackGoal(mob, 1.2D, false));
                mob.targetSelector.addGoal(prio, new NearestAttackableTargetGoal<>(mob, Player.class, true));
            }),
            new BehaviorDef(5, "fighter", "Fighter (Defend)", true, List.of(), counts -> true, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new MeleeAttackGoal(mob, 1.2D, false));
                mob.targetSelector.addGoal(prio, new HurtByTargetGoal(mob));
                mob.targetSelector.addGoal(prio + 1, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
            }),
            new BehaviorDef(6, "timid", "Timid (Flee)", true, List.of(), counts -> true, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new PanicGoal(mob, 1.25D));
            }),
            new BehaviorDef(7, "loyal", "Loyal (Guard Player)", false, List.of(Items.LEAD), counts -> BioMobGenerator.getCount(counts, Items.LEAD) >= 5, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new MeleeAttackGoal(mob, 1.2D, false));
                mob.targetSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("LoyalTarget", mob, 1.0D, null, null)));
            }),
            new BehaviorDef(8, "berserker", "Berserker (Attack All)", false, List.of(Items.REDSTONE_BLOCK), counts -> BioMobGenerator.getCount(counts, Items.REDSTONE_BLOCK) >= 3, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new MeleeAttackGoal(mob, 1.2D, false));
                mob.targetSelector.addGoal(prio, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, e -> !(e instanceof CustomBioMobEntity)));
            }),
            new BehaviorDef(9, "archer", "Archer (Ranged)", false, List.of(Items.BOW), counts -> BioMobGenerator.getCount(counts, Items.BOW) >= 5, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("RangedAttack", mob, 1.0D, null, null)));
                mob.targetSelector.addGoal(prio, new HurtByTargetGoal(mob));
                mob.targetSelector.addGoal(prio + 1, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
            }),
            new BehaviorDef(10, "swordsman", "Swordsman (Dash Attack)", false, List.of(Items.SHIELD), counts -> BioMobGenerator.getCount(counts, Items.SHIELD) >= 3, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, new MeleeAttackGoal(mob, 1.5D, true));
                mob.targetSelector.addGoal(prio, new HurtByTargetGoal(mob));
                mob.targetSelector.addGoal(prio + 1, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
            }),
            new BehaviorDef(11, "hit_and_run", "Hit and Run", false, List.of(Items.RABBIT_FOOT), counts -> BioMobGenerator.getCount(counts, Items.RABBIT_FOOT) >= 2, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("HitAndRun", mob, 1.2D, MobGoalRegistry.buildOptions("fleeSpeed", 1.5D), null)));
                mob.targetSelector.addGoal(prio, new HurtByTargetGoal(mob));
                mob.targetSelector.addGoal(prio + 1, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
            }),
            new BehaviorDef(12, "kamikaze", "Kamikaze (Explode)", false, List.of(Items.TNT), counts -> BioMobGenerator.getCount(counts, Items.TNT) >= 10, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("Kamikaze", mob, 1.3D, null, null)));
                mob.targetSelector.addGoal(prio, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
            }),
            new BehaviorDef(13, "torch_placing", "Torch Placer", false, List.of(Items.TORCH), counts -> BioMobGenerator.getCount(counts, Items.TORCH) >= 5, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("PlaceTorch", mob, 1.0D, null, null)));
            }),
            new BehaviorDef(14, "farming", "Farming", false, List.of(Items.IRON_HOE), counts -> BioMobGenerator.getCount(counts, Items.IRON_HOE) >= 1, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("Farming", mob, 1.0D, MobGoalRegistry.buildOptions("searchRange", 16), null)));
            }),
            new BehaviorDef(15, "collector", "Item Collector", false, List.of(Items.HOPPER), counts -> BioMobGenerator.getCount(counts, Items.HOPPER) >= 3, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("PickupItem", mob, 1.2D, null, null)));
            }),
            new BehaviorDef(16, "lumberjack", "Lumberjack", false, List.of(Items.IRON_AXE), counts -> BioMobGenerator.getCount(counts, Items.IRON_AXE) >= 3, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("Lumberjack", mob, 1.0D, MobGoalRegistry.buildOptions("searchRange", 16), null)));
            }),
            new BehaviorDef(17, "breeder", "Animal Breeder", false, List.of(Items.WHEAT), counts -> BioMobGenerator.getCount(counts, Items.WHEAT) >= 20, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("Breeder", mob, 1.2D, null, null)));
            }),
            new BehaviorDef(18, "fisher", "Fisher", false, List.of(Items.FISHING_ROD), counts -> BioMobGenerator.getCount(counts, Items.FISHING_ROD) >= 3, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("Fisher", mob, 1.0D, null, null)));
            }),
            // ★ 表記を "Lua Control" に変更
            new BehaviorDef(19, "mechanical", "Lua Control", false, List.of(Lunex.ADVANCED_MACHINE_ITEM.get()), counts -> {
                Item machineItem = Lunex.ADVANCED_MACHINE_ITEM.get();
                int machineBlocks = BioMobGenerator.getCount(counts, machineItem);
                if (machineBlocks < 1) return false;
                int machinePoints = BioMobGenerator.getMachinePoints(counts);
                int totalPoints = machinePoints + BioMobGenerator.getMonsterPoints(counts) + BioMobGenerator.getAnimalPoints(counts);
                if (totalPoints == 0) totalPoints = 1;
                return ((double) machinePoints / totalPoints) >= 0.15;
            }, (mob, prio) -> {
                mob.goalSelector.addGoal(prio, Objects.requireNonNull(MobGoalRegistry.createGoal("MachineMove", mob, 1.0D, null, null)));
            })
    );

    private static final int DEFAULT_UNLOCKED_MASK = calculateDefaultUnlockedMask();
    private static Set<Item> validMaterialsCache = null;

    private static int calculateDefaultUnlockedMask() {
        int mask = 0;
        for (BehaviorDef def : BEHAVIORS) if (def.isDefault()) mask |= (1 << def.id());
        return mask;
    }

    public static Set<Item> getAllValidMaterials() {
        if (validMaterialsCache == null) {
            validMaterialsCache = new HashSet<>();
            for (BehaviorDef def : BEHAVIORS) {
                if (def.validMaterials() != null) {
                    validMaterialsCache.addAll(def.validMaterials());
                }
            }
        }
        return validMaterialsCache;
    }

    public static int getUnlockedBehaviorsMask(Map<String, Integer> materialCounts) {
        int mask = DEFAULT_UNLOCKED_MASK;
        for (BehaviorDef def : BEHAVIORS) {
            if (!def.isDefault() && def.unlockCondition().test(materialCounts)) {
                mask |= (1 << def.id());
            }
        }
        return mask;
    }

    public static BehaviorDef getBehaviorByKey(String key) {
        for (BehaviorDef def : BEHAVIORS) if (def.key().equals(key)) return def;
        return null;
    }

    @Override
    public void gatherTranslations(AutoLanguageProvider provider, String locale) {
        provider.addTranslation("behavior.lunex.follower.name", "Follow Player", "Follow Player");
        provider.addTranslation("behavior.lunex.follower.desc", "Follows the player.", "プレイヤーに追従します。");

        provider.addTranslation("behavior.lunex.wait.name", "Wait / Stay", "Wait / Stay");
        provider.addTranslation("behavior.lunex.wait.desc", "Stays in the current location.", "その場に待機します。");

        provider.addTranslation("behavior.lunex.wander.name", "Wander Freely", "Wander Freely");
        provider.addTranslation("behavior.lunex.wander.desc", "Wanders around freely.", "自由に行動します。");

        provider.addTranslation("behavior.lunex.autonomous.name", "Autonomous", "Autonomous");
        provider.addTranslation("behavior.lunex.autonomous.desc", "Acts naturally on its own.", "自律的に自然な行動をとります。");

        provider.addTranslation("behavior.lunex.hostile.name", "Hostile (Attack)", "Hostile (Attack)");
        provider.addTranslation("behavior.lunex.hostile.desc", "Attacks players on sight.", "プレイヤーを見つけると攻撃します。");

        provider.addTranslation("behavior.lunex.fighter.name", "Fighter (Defend)", "Fighter (Defend)");
        provider.addTranslation("behavior.lunex.fighter.desc", "Fights back against hostile monsters.", "敵対的なモンスターに応戦します。");

        provider.addTranslation("behavior.lunex.timid.name", "Timid (Flee)", "Timid (Flee)");
        provider.addTranslation("behavior.lunex.timid.desc", "Flees when attacked.", "攻撃されると逃亡します。");

        provider.addTranslation("behavior.lunex.loyal.name", "Loyal (Guard Player)", "Loyal (Guard Player)");
        provider.addTranslation("behavior.lunex.loyal.desc", "Guards and attacks threats to the player.", "プレイヤーを守り、脅威を排除します。");

        provider.addTranslation("behavior.lunex.berserker.name", "Berserker (Attack All)", "Berserker (Attack All)");
        provider.addTranslation("behavior.lunex.berserker.desc", "Attacks all living entities.", "周囲のあらゆる生物を無差別に攻撃します。");

        provider.addTranslation("behavior.lunex.archer.name", "Archer (Ranged)", "Archer (Ranged)");
        provider.addTranslation("behavior.lunex.archer.desc", "Attacks from a distance using ranged weapons.", "遠距離から飛び道具で攻撃します。");

        provider.addTranslation("behavior.lunex.swordsman.name", "Swordsman (Dash Attack)", "Swordsman (Dash Attack)");
        provider.addTranslation("behavior.lunex.swordsman.desc", "Dashes towards targets to perform melee attacks.", "標的に突進して近接攻撃を行います。");

        provider.addTranslation("behavior.lunex.hit_and_run.name", "Hit and Run", "Hit and Run");
        provider.addTranslation("behavior.lunex.hit_and_run.desc", "Attacks and immediately retreats.", "攻撃を当てた直後に距離を取ります。");

        provider.addTranslation("behavior.lunex.kamikaze.name", "Kamikaze (Explode)", "Kamikaze (Explode)");
        provider.addTranslation("behavior.lunex.kamikaze.desc", "Explodes upon reaching the target.", "標的に接近して自爆します。");

        provider.addTranslation("behavior.lunex.torch_placing.name", "Torch Placer", "Torch Placer");
        provider.addTranslation("behavior.lunex.torch_placing.desc", "Automatically places torches in dark areas.", "周囲の暗い場所に自動で松明を設置します。");

        provider.addTranslation("behavior.lunex.farming.name", "Farming", "Farming");
        provider.addTranslation("behavior.lunex.farming.desc", "Harvests and replants crops.", "周囲の作物を収穫し、種を植え直します。");

        provider.addTranslation("behavior.lunex.collector.name", "Item Collector", "Item Collector");
        provider.addTranslation("behavior.lunex.collector.desc", "Picks up items lying on the ground.", "周囲に落ちているアイテムを拾い集めます。");

        provider.addTranslation("behavior.lunex.lumberjack.name", "Lumberjack", "Lumberjack");
        provider.addTranslation("behavior.lunex.lumberjack.desc", "Chops down trees.", "周囲の木を伐採します。");

        provider.addTranslation("behavior.lunex.breeder.name", "Animal Breeder", "Animal Breeder");
        provider.addTranslation("behavior.lunex.breeder.desc", "Breeds animals.", "周囲の動物を繁殖させます。");

        provider.addTranslation("behavior.lunex.fisher.name", "Fisher", "Fisher");
        provider.addTranslation("behavior.lunex.fisher.desc", "Fishes at nearby water sources.", "周囲の水場で釣りをします。");

        provider.addTranslation("behavior.lunex.mechanical.name", "Lua Control", "Lua Control");
        provider.addTranslation("behavior.lunex.mechanical.desc", "Controlled by a Lua program.", "Luaプログラムによって行動を制御されます。");
    }

    public record BehaviorDef(int id, String key, String displayName, boolean isDefault,
                              List<Item> validMaterials,
                              Predicate<Map<String, Integer>> unlockCondition,
                              BiConsumer<CustomBioMobEntity, Integer> aiSetup) {
    }
}