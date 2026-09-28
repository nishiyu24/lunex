package com.nishiyu.lunex.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

public class MobTraitManager {
    private final CustomBioMobEntity mob;

    // キャッシュされたTraitフラグ
    public boolean hasWallClimbing, isPhotosensitive, hasFireCore, hasVoidCore, hasEnderBlood, hasAbsorbent;
    public boolean isAmphibious, hasRegeneration, hasWings, hasExtraEyes, hasBlindness, isGlowing;
    public boolean hasEcholocation, hasFragrance, hasLuminousLure, isNoisy, isMagnetic, hasPhotosynthesis;
    public boolean isBuoyant, isBouncy, hasLightCore, hasIceCore, hasElectricCore, isVolatile;
    public boolean hasDrySkin, hasVenomCore, hasDarkCore, isSticky, hasThornsSkin, hasTeleportation;
    public boolean isMount, isMerchant, isEquipable, isMilkable, isShearable, isPackMule, hasExplosiveDeath;

    // 変動ステータス
    private float miningSpeedMultiplier = 0.5f;
    private double interactRange = 2.0;
    private double sensingRangeMax = 12.0;
    private double maxMovementSpeed = 1.0;
    private float damageTakenMultiplier = 1.5f;
    private float fallDamageMultiplier = 1.0f;
    private double knockbackResistance = 0.0;
    private float stealthMultiplier = 1.0f;

    public MobTraitManager(CustomBioMobEntity mob) {
        this.mob = mob;
    }

    public void updateTraits(Set<String> traits) {
        // フラグ群の更新
        this.hasWallClimbing = traits.contains("wall_climbing");
        this.isPhotosensitive = traits.contains("photosensitive");
        this.hasFireCore = traits.contains("fire_core");
        this.hasVoidCore = traits.contains("void_core");
        this.hasEnderBlood = traits.contains("ender_blood");
        this.hasAbsorbent = traits.contains("absorbent");
        this.isAmphibious = traits.contains("amphibious");
        this.hasRegeneration = traits.contains("regeneration");
        this.hasWings = traits.contains("wings");
        this.hasExtraEyes = traits.contains("extra_eyes");
        this.hasBlindness = traits.contains("blindness");
        this.isGlowing = traits.contains("glowing");
        this.hasEcholocation = traits.contains("echolocation");
        this.hasFragrance = traits.contains("fragrance");
        this.hasLuminousLure = traits.contains("luminous_lure");
        this.isNoisy = traits.contains("noisy");
        this.isMagnetic = traits.contains("magnetic");
        this.hasPhotosynthesis = traits.contains("photosynthesis");
        this.isBuoyant = traits.contains("buoyant");
        this.isBouncy = traits.contains("bouncy");
        this.hasLightCore = traits.contains("light_core");
        this.hasIceCore = traits.contains("ice_core");
        this.hasElectricCore = traits.contains("electric_core");
        this.isVolatile = traits.contains("volatile");
        this.hasDrySkin = traits.contains("dry_skin");
        this.hasVenomCore = traits.contains("venom_core");
        this.hasDarkCore = traits.contains("dark_core");
        this.isSticky = traits.contains("sticky");
        this.hasThornsSkin = traits.contains("thorns_skin");
        this.hasTeleportation = traits.contains("teleportation");
        this.isMount = traits.contains("mount");
        this.isMerchant = traits.contains("merchant");
        this.isEquipable = traits.contains("equipable");
        this.isMilkable = traits.contains("milkable");
        this.isShearable = traits.contains("shearable");
        this.isPackMule = traits.contains("pack_mule");
        this.hasExplosiveDeath = traits.contains("explosive_death");
    }

    // ★追加: ビルド済みのステータスを適用する
    public void applyStatus(BioMobGenerator.MobStatus status) {
        this.miningSpeedMultiplier = status.miningSpeed();
        this.interactRange = status.interactRange();
        this.sensingRangeMax = status.sensingRange();
        this.maxMovementSpeed = status.maxMovementSpeed();
        this.damageTakenMultiplier = status.damageTaken();
        this.fallDamageMultiplier = status.fallDamage();
        this.knockbackResistance = status.knockbackResistance();
        this.stealthMultiplier = status.stealth();
    }

    // Getters
    public float getMiningSpeedMultiplier() { return this.miningSpeedMultiplier; }
    public double getInteractRange() { return this.interactRange; }
    public double getSensingRangeMax() { return this.sensingRangeMax; }
    public double getMaxMovementSpeed() { return this.maxMovementSpeed; }
    public float getDamageTakenMultiplier() { return this.damageTakenMultiplier; }
    public float getFallDamageMultiplier() { return this.fallDamageMultiplier; }
    public double getKnockbackResistance() { return this.knockbackResistance; }
    public float getStealthMultiplier() { return this.stealthMultiplier; }

    public void tick(int staggeredTick) {
        if (this.isPhotosensitive) {
            if (mob.level().isDay() && !mob.level().isRaining() && mob.level().canSeeSky(mob.blockPosition())) {
                mob.igniteForSeconds(8.0F);
            }
        }
        if (this.hasFireCore || this.hasVoidCore || this.hasEnderBlood) {
            if (staggeredTick % 20 == 0 && mob.isInWaterRainOrBubble()) {
                mob.hurt(mob.damageSources().drown(), 1.0F);
            }
        }
        if (this.hasAbsorbent) {
            if (staggeredTick % 40 == 0 && mob.isInWaterRainOrBubble()) {
                mob.heal(1.0F);
            }
        }
        if (this.isAmphibious) {
            mob.setAirSupply(mob.getMaxAirSupply());
        }
        if (this.hasRegeneration && staggeredTick % 100 == 0) {
            mob.heal(1.0F);
        }
        if (staggeredTick % 20 == 0) {
            if (this.hasWings) mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
            if (this.hasExtraEyes) mob.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
            if (this.hasBlindness) mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 220, 0, false, false));
            if (this.isGlowing) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 220, 0, false, false));
        }
        if (this.hasEcholocation && staggeredTick % 20 == 0) {
            mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(16.0D), e -> e != mob)
                    .forEach(e -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false)));
        }
        if (this.hasFragrance && staggeredTick % 40 == 0) {
            if (mob.level() instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.0, mob.getZ(), 3, 0.5, 0.5, 0.5, 0);
            }
            mob.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Bee.class, mob.getBoundingBox().inflate(16.0D))
                    .forEach(bee -> {
                        if (bee.getTarget() == null && bee.getNavigation().isDone())
                            bee.getNavigation().moveTo(mob, 1.0D);
                    });
        }
        if (this.hasLuminousLure && staggeredTick % 40 == 0) {
            mob.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, mob.getBoundingBox().inflate(16.0D))
                    .forEach(animal -> {
                        if (animal.getTarget() == null && animal.getNavigation().isDone())
                            animal.getNavigation().moveTo(mob, 1.0D);
                    });
        }
        if (this.isNoisy && staggeredTick % 40 == 0) {
            mob.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, mob.getBoundingBox().inflate(16.0D))
                    .forEach(monster -> {
                        if (monster.getTarget() == null) monster.setTarget(mob);
                    });
        }
        if (this.isMagnetic && staggeredTick % 4 == 0) {
            List<ItemEntity> items = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(4.0D));
            for (ItemEntity item : items) {
                Vec3 vec3 = new Vec3(mob.getX() - item.getX(), mob.getY() + (double) mob.getEyeHeight() / 2.0D - item.getY(), mob.getZ() - item.getZ());
                if (vec3.lengthSqr() < 16.0D) {
                    item.setDeltaMovement(item.getDeltaMovement().add(vec3.normalize().scale(0.08D)));
                }
            }
        }
        if (this.hasPhotosynthesis && staggeredTick % 600 == 0) {
            if (mob.level().isDay() && !mob.level().isRaining() && mob.level().canSeeSky(mob.blockPosition())) {
                if (mob.getRandom().nextBoolean()) {
                    mob.spawnAtLocation(Items.WHEAT_SEEDS);
                }
            }
        }
        if (this.isBuoyant && mob.isInWater()) {
            Vec3 vec3 = mob.getDeltaMovement();
            if (vec3.y < 0.1D) {
                mob.setDeltaMovement(vec3.x, 0.1D, vec3.z);
            }
        }
    }
}