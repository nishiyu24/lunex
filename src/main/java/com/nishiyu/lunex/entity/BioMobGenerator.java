package com.nishiyu.lunex.entity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.BioPrinterBlock;
import com.nishiyu.lunex.entity.traits.TraitRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class BioMobGenerator {

    public static final Set<Item> STATUS_BONUS_ITEMS = Set.of(
            Items.APPLE, Items.BEEF, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.IRON_INGOT, Items.LEATHER, Items.DIAMOND, Items.NETHERITE_INGOT,
            Items.SUGAR, Items.PHANTOM_MEMBRANE,
            Items.IRON_SWORD, Items.BONE, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
            Items.NETHER_STAR, Items.EXPERIENCE_BOTTLE, Items.BOOK, Items.GOLD_INGOT,
            Items.CHEST, Items.REDSTONE, Lunex.SIMPLE_MACHINE_ITEM.get()
    );

    public static MobStatus calculateStatus(Map<String, Integer> materialCounts, List<String> traits) {
        // 体力・防御・攻撃をプレイヤーの半分に設定し、それ以外をプレイヤー同等に調整
        MobStatus baseStatus = new MobStatus(
                10.0D, // maxHealth: プレイヤー(20.0)の半分
                0.0D,  // armor: プレイヤー(0.0)の半分
                0.25D, // speed: プレイヤーと同等の移動速度 (Mobの標準値)
                0.5D,  // attackDamage: プレイヤーの素手攻撃力(1.0)の半分
                1.0D,  // scale: プレイヤーと同じ大きさ
                0,     // inventorySize: 初期インベントリなし
                0.6D,  // stepHeight: プレイヤーと同じ段差乗り越え高さ
                1.0f,  // miningSpeed: プレイヤーと同じ採掘速度
                4.5D,  // interactRange: プレイヤーの標準的なブロック操作距離
                16.0D, // sensingRange: 標準的な索敵距離
                1.0D,  // maxMovementSpeed: 速度上限の倍率
                1.0f,  // damageTaken: 被ダメージ倍率 (100%)
                1.0f,  // fallDamage: 落下ダメージ倍率 (100%)
                0.0D,  // knockbackResistance: プレイヤーと同じノックバック耐性 (0.0)
                1.0f   // stealth: ステルス倍率 (1.0)
        );

        return TraitRegistry.applyTraitModifiers(traits, baseStatus);
    }

    public static void generateMob(Level level, BlockPos pos, Map<String, Integer> materialCounts, float failureRate, List<Integer> selectedBehaviors, List<Integer> selectedTraits) {
        if (level == null || level.isClientSide) return;

        double spawnX = pos.getX() + 0.5;
        double spawnY = pos.getY() + 1.0;
        double spawnZ = pos.getZ() + 0.5;

        float yRot = 0.0f;
        BlockState blockState = level.getBlockState(pos);
        if (blockState.hasProperty(BioPrinterBlock.FACING)) {
            Direction facing = blockState.getValue(BioPrinterBlock.FACING);
            yRot = facing.toYRot();
        }

        if (level.random.nextFloat() < failureRate) {
            boolean isCriticalFail = level.random.nextFloat() < 0.3333f;

            Slime slime = EntityType.SLIME.create(level);
            if (slime != null) {
                slime.moveTo(spawnX, spawnY, spawnZ, yRot, 0.0F);
                slime.yHeadRot = yRot;
                slime.yBodyRot = yRot;
                slime.setSize((int) (failureRate * 10) + 1, true);

                if (isCriticalFail) {
                    level.explode(null, spawnX, spawnY, spawnZ, 3.0f, Level.ExplosionInteraction.MOB);

                    List<String> traits = getTraits(materialCounts, selectedTraits, level.random);
                    MobStatus status = calculateStatus(materialCounts, traits);

                    if (slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null)
                        Objects.requireNonNull(slime.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(status.maxHealth());
                    if (slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) != null)
                        Objects.requireNonNull(slime.getAttribute(Attributes.ARMOR)).setBaseValue(status.armor());
                    if (slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null)
                        Objects.requireNonNull(slime.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(status.speed());
                    if (slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null)
                        Objects.requireNonNull(slime.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(status.attackDamage());

                    slime.setHealth(slime.getMaxHealth());
                }

                level.addFreshEntity(slime);
            }
            return;
        }

        List<String> abilities = new ArrayList<>();
        List<String> behaviors = new ArrayList<>();

        for (int id : selectedBehaviors) {
            for (CustomBehaviorRegistry.BehaviorDef def : CustomBehaviorRegistry.BEHAVIORS) {
                if (def.id() == id) {
                    behaviors.add(def.key());
                    break;
                }
            }
        }

        List<String> traits = getTraits(materialCounts, selectedTraits, level.random);

        MobStatus status = calculateStatus(materialCounts, traits);
        int inventorySize = status.inventorySize();

        CustomBioMobEntity mob = Lunex.CUSTOM_BIO_MOB.get().create(level);

        if (mob != null) {
            mob.moveTo(spawnX, spawnY, spawnZ, yRot, 0.0F);
            mob.yHeadRot = yRot;
            mob.yBodyRot = yRot;

            mob.initializeMob(traits, abilities, behaviors, inventorySize, status);
            level.addFreshEntity(mob);
        }
    }

    public static List<String> getTraits(Map<String, Integer> materialCounts, List<Integer> selectedTraits, RandomSource random) {
        List<String> traits = new ArrayList<>();
        for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
            if (selectedTraits.contains(i)) {
                com.nishiyu.lunex.entity.traits.TraitDef def = TraitRegistry.TRAITS.get(i);
                traits.add(def.key());
            }
        }
        return traits;
    }

    public static int getCount(Map<String, Integer> materialCounts, Item item) {
        String baseKey = BuiltInRegistries.ITEM.getKey(item).toString();
        int total = 0;
        for (Map.Entry<String, Integer> entry : materialCounts.entrySet()) {
            String key = entry.getKey();
            if (key.equals(baseKey) || key.startsWith(baseKey + ";")) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public static int getMachinePoints(Map<String, Integer> materialCounts) {
        return getCount(materialCounts, Items.REDSTONE) + getCount(materialCounts, Items.IRON_INGOT);
    }

    public static int getMonsterPoints(Map<String, Integer> materialCounts) {
        return getCount(materialCounts, Items.ROTTEN_FLESH) + getCount(materialCounts, Items.BONE) + getCount(materialCounts, Items.SPIDER_EYE);
    }

    public static int getAnimalPoints(Map<String, Integer> materialCounts) {
        return getCount(materialCounts, Items.BEEF) + getCount(materialCounts, Items.PORKCHOP) + getCount(materialCounts, Items.LEATHER);
    }

    public static int getAbilityMask(Map<String, Integer> materialCounts) {
        return 0;
    }

    // ★修正: 全ての強化パラメータを格納
    public record MobStatus(double maxHealth, double armor, double speed, double attackDamage, double scale,
                            int inventorySize, double stepHeight,
                            float miningSpeed, double interactRange, double sensingRange, double maxMovementSpeed,
                            float damageTaken, float fallDamage, double knockbackResistance, float stealth) {
    }
}