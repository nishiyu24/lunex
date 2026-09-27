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
            Items.CHEST, Items.REDSTONE, Lunex.ADVANCED_MACHINE_ITEM.get()
    );

    public static MobStatus calculateStatus(Map<String, Integer> materialCounts, List<String> traits) {
        // アイテムによる基礎ステータスボーナスを廃止し、固定の初期値を設定（インベントリサイズ0を追加）
        MobStatus baseStatus = new MobStatus(2.0D, 0.0D, 0.5D, 1.0D, 1.0D, 0);
        return TraitRegistry.applyTraitModifiers(traits, baseStatus);
    }

    public static void generateMob(Level level, BlockPos pos, Map<String, Integer> materialCounts, float failureRate, List<Integer> selectedBehaviors, List<Integer> selectedTraits) {
        if (level == null || level.isClientSide) return;

        double spawnX = pos.getX() + 0.5;
        double spawnY = pos.getY() + 1.0;
        double spawnZ = pos.getZ() + 0.5;

        // ブロックの向きを取得してエンティティのYaw（Y軸回転）に変換
        float yRot = 0.0f;
        BlockState blockState = level.getBlockState(pos);
        if (blockState.hasProperty(BioPrinterBlock.FACING)) {
            Direction facing = blockState.getValue(BioPrinterBlock.FACING);
            yRot = facing.toYRot(); // Directionから角度を取得
        }

        if (level.random.nextFloat() < failureRate) {
            boolean isCriticalFail = level.random.nextFloat() < 0.3333f;

            Slime slime = EntityType.SLIME.create(level);
            if (slime != null) {
                // setPosの代わりにmoveToを使用して向きも設定
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

        // calculateStatus によって Traits からインベントリサイズを含めた全ステータスを取得
        MobStatus status = calculateStatus(materialCounts, traits);
        int inventorySize = status.inventorySize();

        CustomBioMobEntity mob = Lunex.CUSTOM_BIO_MOB.get().create(level);

        if (mob != null) {
            // setPosの代わりにmoveToを使用して向きも設定
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

    // inventorySize を追加
    public record MobStatus(double maxHealth, double armor, double speed, double attackDamage, double scale,
                            int inventorySize) {
    }
}