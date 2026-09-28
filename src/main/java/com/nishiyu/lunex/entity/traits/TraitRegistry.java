package com.nishiyu.lunex.entity.traits;

import com.nishiyu.lunex.entity.BioMobGenerator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

public class TraitRegistry {

    public static final List<TraitDef> TRAITS = new ArrayList<>();
    private static Set<Item> validMaterialsCache = null;
    private static boolean initialized = false;

    static {
        init();
    }

    public static void init() {
        if (initialized) return;
        ModTraits.register();
        initialized = true;
    }

    public static void registerTrait(TraitDef def) {
        TRAITS.add(def);
    }

    public static Set<Item> getAllValidMaterials() {
        if (validMaterialsCache == null) {
            validMaterialsCache = new HashSet<>();
            for (TraitDef trait : TRAITS) {
                if (trait.requirements() != null) {
                    for (TraitDef.ItemRequirement req : trait.requirements()) {
                        validMaterialsCache.add(req.item());
                    }
                }
            }
        }
        return validMaterialsCache;
    }

    public static TraitDef getTraitByKey(String key) {
        for (TraitDef def : TRAITS) {
            if (def.key().equals(key)) return def;
        }
        return null;
    }

    public static int getTraitIndex(String key) {
        for (int i = 0; i < TRAITS.size(); i++) {
            if (TRAITS.get(i).key().equals(key)) return i;
        }
        return -1;
    }

    public static boolean isTraitUnlocked(int traitId, Map<String, Integer> mats) {
        TraitDef def = TRAITS.get(traitId);
        if (!def.isNegative() && def.requirements() != null) {
            for (TraitDef.ItemRequirement req : def.requirements()) {
                if (BioMobGenerator.getCount(mats, req.item()) < req.count()) {
                    return false;
                }
            }
        }
        for (String preKey : def.prerequisites()) {
            int preIdx = getTraitIndex(preKey);
            if (preIdx != -1 && !isTraitUnlocked(preIdx, mats)) {
                return false;
            }
        }
        return true;
    }

    public static BioMobGenerator.MobStatus applyTraitModifiers(List<String> traitKeys, BioMobGenerator.MobStatus baseStatus) {
        MobStatusBuilder builder = new MobStatusBuilder(baseStatus);
        for (String key : traitKeys) {
            TraitDef def = getTraitByKey(key);
            if (def != null && def.statusModifier() != null) {
                def.statusModifier().accept(builder);
            }
        }
        return builder.build();
    }

    public static int getBaseMaxTraits(Map<String, Integer> materialCounts) {
        int base = 2;
        int bonus = (BioMobGenerator.getCount(materialCounts, Items.BOOK) / 64) +
                (BioMobGenerator.getCount(materialCounts, Items.GOLD_INGOT) / 32) +
                BioMobGenerator.getCount(materialCounts, Items.NETHER_STAR);
        return base + bonus;
    }

    public static int getMaxPoints(Map<String, Integer> materialCounts, List<Integer> selectedTraits) {
        int max = getBaseMaxTraits(materialCounts);
        int negativeBonus = 0;
        for (int idx : selectedTraits) {
            if (TRAITS.get(idx).isNegative()) {
                negativeBonus += 1;
            }
        }
        return Math.min(max + negativeBonus, TRAITS.size());
    }

    public static int getConsumedPoints(List<Integer> selectedTraits) {
        int cost = 0;
        for (int idx : selectedTraits) {
            if (!TRAITS.get(idx).isNegative()) {
                cost += 1;
            }
        }
        return cost;
    }

    public static long getUnlockedTraitsMask(Map<String, Integer> materialCounts) {
        long mask = 0L;
        for (int i = 0; i < TRAITS.size(); i++) {
            if (isTraitUnlocked(i, materialCounts)) {
                mask |= (1L << i);
            }
        }
        return mask;
    }

    public static class MobStatusBuilder {
        private double health;
        private double armor;
        private double speed;
        private double attack;
        private double scale;
        private int inventorySize;
        private double stepHeight;

        private float miningSpeed;
        private double interactRange;
        private double sensingRange;
        private double maxMovementSpeed;
        private float damageTaken;
        private float fallDamage;
        private double knockbackResistance;
        private float stealth;

        public MobStatusBuilder(BioMobGenerator.MobStatus base) {
            this.health = base.maxHealth();
            this.armor = base.armor();
            this.speed = base.speed();
            this.attack = base.attackDamage();
            this.scale = base.scale();
            this.inventorySize = base.inventorySize();
            this.stepHeight = base.stepHeight();

            this.miningSpeed = base.miningSpeed();
            this.interactRange = base.interactRange();
            this.sensingRange = base.sensingRange();
            this.maxMovementSpeed = base.maxMovementSpeed();
            this.damageTaken = base.damageTaken();
            this.fallDamage = base.fallDamage();
            this.knockbackResistance = base.knockbackResistance();
            this.stealth = base.stealth();
        }

        public void addHealth(double amount) { this.health += amount; }
        public void addArmor(double amount) { this.armor += amount; }
        public void addSpeed(double amount) { this.speed += amount; }
        public void addAttack(double amount) { this.attack += amount; }
        public void addInventorySize(int rows) { this.inventorySize += rows * 9; }
        public void addScale(double amount) { this.scale += amount; }
        public void setStepHeight(double stepHeight) { this.stepHeight = stepHeight; }

        public void multiplyHealth(double factor) { this.health *= factor; }
        public void multiplyArmor(double factor) { this.armor *= factor; }
        public void multiplySpeed(double factor) { this.speed *= factor; }
        public void multiplyAttack(double factor) { this.attack *= factor; }
        public void setScale(double scale) { this.scale = scale; }

        // ★追加: 各種特殊ステータス操作メソッド
        public void addMiningSpeed(float amount) { this.miningSpeed += amount; }
        public void multiplyMiningSpeed(float factor) { this.miningSpeed *= factor; }
        public void addInteractRange(double amount) { this.interactRange += amount; }
        public void addSensingRange(double amount) { this.sensingRange += amount; }
        public void setSensingRange(double value) { this.sensingRange = value; }
        public void addMaxMovementSpeed(double amount) { this.maxMovementSpeed += amount; }
        public void addDamageTaken(float amount) { this.damageTaken += amount; }
        public void multiplyDamageTaken(float factor) { this.damageTaken *= factor; }
        public void setFallDamage(float value) { this.fallDamage = value; }
        public void addKnockbackResistance(double amount) { this.knockbackResistance += amount; }
        public void setStealth(float value) { this.stealth = value; }

        public BioMobGenerator.MobStatus build() {
            return new BioMobGenerator.MobStatus(
                    Math.clamp(health, 1.0D, 300.0D),
                    Math.clamp(armor, 0.0D, 30.0D),
                    Math.clamp(speed, 0.05D, 1.5D),
                    Math.clamp(attack, 0.0D, 50.0D),
                    Math.clamp(scale, 0.1D, 5.0D),
                    Math.clamp(inventorySize, 0, 54),
                    Math.clamp(stepHeight, 0.6D, 3.0D),
                    Math.clamp(miningSpeed, 0.1f, 10.0f),
                    Math.clamp(interactRange, 1.0D, 10.0D),
                    Math.clamp(sensingRange, 2.0D, 64.0D),
                    Math.clamp(maxMovementSpeed, 0.1D, 3.0D),
                    Math.clamp(damageTaken, 0.1f, 5.0f),
                    Math.clamp(fallDamage, 0.0f, 2.0f),
                    Math.clamp(knockbackResistance, 0.0D, 1.0D),
                    Math.clamp(stealth, 0.0f, 2.0f)
            );
        }
    }
}