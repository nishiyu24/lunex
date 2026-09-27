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
        // 分割されていたクラス群を廃止し、ModTraitsのregisterへ一本化
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
        // 素材条件を満たしているかチェック
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
        MobStatusBuilder builder = new MobStatusBuilder(
                baseStatus.maxHealth(), baseStatus.armor(), baseStatus.speed(), baseStatus.attackDamage(), baseStatus.scale(), baseStatus.inventorySize()
        );
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

        public MobStatusBuilder(double health, double armor, double speed, double attack, double scale, int inventorySize) {
            this.health = health;
            this.armor = armor;
            this.speed = speed;
            this.attack = attack;
            this.scale = scale;
            this.inventorySize = inventorySize;
        }

        public void addHealth(double amount) {
            this.health += amount;
        }

        public void addArmor(double amount) {
            this.armor += amount;
        }

        public void addSpeed(double amount) {
            this.speed += amount;
        }

        public void addAttack(double amount) {
            this.attack += amount;
        }

        public void addInventorySize(int rows) {
            this.inventorySize += rows * 9;
        }

        public void multiplyHealth(double factor) {
            this.health *= factor;
        }

        public void multiplyArmor(double factor) {
            this.armor *= factor;
        }

        public void multiplySpeed(double factor) {
            this.speed *= factor;
        }

        public void multiplyAttack(double factor) {
            this.attack *= factor;
        }

        public void setScale(double scale) {
            this.scale = scale;
        }

        public BioMobGenerator.MobStatus build() {
            return new BioMobGenerator.MobStatus(
                    Math.clamp(health, 1.0D, 300.0D),
                    Math.clamp(armor, 0.0D, 30.0D),
                    Math.clamp(speed, 0.1D, 1.5D),
                    Math.clamp(attack, 0.0D, 50.0D),
                    Math.clamp(scale, 0.1D, 3.0D),
                    Math.clamp(inventorySize, 0, 54)
            );
        }
    }
}