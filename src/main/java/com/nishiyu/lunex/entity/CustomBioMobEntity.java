package com.nishiyu.lunex.entity;

import com.mojang.authlib.GameProfile;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsMenu;
import com.nishiyu.lunex.program.server.entity.EntityServerLuaVM;
import com.nishiyu.lunex.server.ServerProgramData;
import com.nishiyu.lunex.util.WorkspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CustomBioMobEntity extends PathfinderMob implements Merchant {

    private static final EntityDataAccessor<String> SYNCED_TRAITS = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SYNCED_BEHAVIORS = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SYNCED_ABILITIES = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SYNCED_APPEARANCE = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);

    public final Set<String> traits = new HashSet<>();
    public final Set<String> abilities = new HashSet<>();
    public final List<String> behaviors = new ArrayList<>();
    public SimpleContainer inventory;
    public int inventorySize = 0;
    public EntityServerLuaVM vm = null;

    // ★ 変更点: 起動状態を保存・復元するためのフラグを追加
    public boolean wasRunning = false;

    public BlockPos targetPos = null;
    public net.minecraft.world.entity.Entity targetEntity = null;

    public String workspaceId = "";
    public String programName = "";

    public int shearCooldown = 0;
    public boolean isSaddled = false;
    private boolean hasWallClimbing, isPhotosensitive, hasFireCore, hasVoidCore, hasEnderBlood, hasAbsorbent;
    private boolean isAmphibious, hasRegeneration, hasWings, hasExtraEyes, hasBlindness, isGlowing;
    private boolean hasEcholocation, hasFragrance, hasLuminousLure, isNoisy, isMagnetic, hasPhotosynthesis;
    private boolean isBuoyant, isBouncy, hasLightCore, hasIceCore, hasElectricCore, isVolatile;
    private boolean hasDrySkin, hasVenomCore, hasDarkCore, isSticky, hasThornsSkin, hasTeleportation;
    private boolean isMount, isMerchant, isEquipable, isMilkable, isShearable, isPackMule, hasExplosiveDeath;
    private boolean isMechanical;
    private Player tradingPlayer;
    private MerchantOffers offers;
    private String customSkinName = "default";

    private FakePlayer fakePlayer;

    public CustomBioMobEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.inventory = new SimpleContainer(Math.max(this.inventorySize, 0));
    }

    public static AttributeSupplier.Builder createBaseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.STEP_HEIGHT, 0.6D)
                .add(Attributes.SCALE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SYNCED_TRAITS, "");
        builder.define(SYNCED_BEHAVIORS, "");
        builder.define(SYNCED_ABILITIES, "");
        builder.define(SYNCED_APPEARANCE, "default");
    }

    public String getAppearance() {
        return this.entityData.get(SYNCED_APPEARANCE);
    }

    public void setAppearance(String newAppearance) {
        this.entityData.set(SYNCED_APPEARANCE, newAppearance);
    }

    public void syncDataToClient() {
        if (!this.level().isClientSide) {
            this.entityData.set(SYNCED_TRAITS, String.join(",", this.traits));
            this.entityData.set(SYNCED_BEHAVIORS, String.join(",", this.behaviors));
            this.entityData.set(SYNCED_ABILITIES, String.join(",", this.abilities));
        }
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide) {
            if (SYNCED_TRAITS.equals(key)) {
                this.traits.clear();
                String data = this.entityData.get(SYNCED_TRAITS);
                if (!data.isEmpty()) this.traits.addAll(Arrays.asList(data.split(",")));
                updateCaches();
            } else if (SYNCED_BEHAVIORS.equals(key)) {
                this.behaviors.clear();
                String data = this.entityData.get(SYNCED_BEHAVIORS);
                if (!data.isEmpty()) this.behaviors.addAll(Arrays.asList(data.split(",")));
                updateCaches();
            } else if (SYNCED_ABILITIES.equals(key)) {
                this.abilities.clear();
                String data = this.entityData.get(SYNCED_ABILITIES);
                if (!data.isEmpty()) this.abilities.addAll(Arrays.asList(data.split(",")));
            }
        }
    }

    private void updateCaches() {
        this.hasWallClimbing = this.traits.contains("wall_climbing");
        this.isPhotosensitive = this.traits.contains("photosensitive");
        this.hasFireCore = this.traits.contains("fire_core");
        this.hasVoidCore = this.traits.contains("void_core");
        this.hasEnderBlood = this.traits.contains("ender_blood");
        this.hasAbsorbent = this.traits.contains("absorbent");
        this.isAmphibious = this.traits.contains("amphibious");
        this.hasRegeneration = this.traits.contains("regeneration");
        this.hasWings = this.traits.contains("wings");
        this.hasExtraEyes = this.traits.contains("extra_eyes");
        this.hasBlindness = this.traits.contains("blindness");
        this.isGlowing = this.traits.contains("glowing");
        this.hasEcholocation = this.traits.contains("echolocation");
        this.hasFragrance = this.traits.contains("fragrance");
        this.hasLuminousLure = this.traits.contains("luminous_lure");
        this.isNoisy = this.traits.contains("noisy");
        this.isMagnetic = this.traits.contains("magnetic");
        this.hasPhotosynthesis = this.traits.contains("photosynthesis");
        this.isBuoyant = this.traits.contains("buoyant");
        this.isBouncy = this.traits.contains("bouncy");
        this.hasLightCore = this.traits.contains("light_core");
        this.hasIceCore = this.traits.contains("ice_core");
        this.hasElectricCore = this.traits.contains("electric_core");
        this.isVolatile = this.traits.contains("volatile");
        this.hasDrySkin = this.traits.contains("dry_skin");
        this.hasVenomCore = this.traits.contains("venom_core");
        this.hasDarkCore = this.traits.contains("dark_core");
        this.isSticky = this.traits.contains("sticky");
        this.hasThornsSkin = this.traits.contains("thorns_skin");
        this.hasTeleportation = this.traits.contains("teleportation");
        this.isMount = this.traits.contains("mount");
        this.isMerchant = this.traits.contains("merchant");
        this.isEquipable = this.traits.contains("equipable");
        this.isMilkable = this.traits.contains("milkable");
        this.isShearable = this.traits.contains("shearable");
        this.isPackMule = this.traits.contains("pack_mule");
        this.hasExplosiveDeath = this.traits.contains("explosive_death");
        this.isMechanical = this.behaviors.contains("mechanical");
    }

    public void initializeMob(List<String> traits, List<String> abilities, List<String> behaviors,
                              int invSize, BioMobGenerator.MobStatus status) {
        this.traits.addAll(traits);
        this.abilities.addAll(abilities);
        this.behaviors.addAll(behaviors);
        updateCaches();

        if (invSize > 0) {
            int rows = (int) Math.ceil(invSize / 9.0);
            this.inventorySize = Math.min(rows * 9, 54);
        } else {
            this.inventorySize = 0;
        }
        this.inventory = new SimpleContainer(Math.max(this.inventorySize, 0));

        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(status.maxHealth());
        this.setHealth((float) status.maxHealth());
        Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(status.armor());
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(status.speed());
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(status.attackDamage());
        Objects.requireNonNull(this.getAttribute(Attributes.SCALE)).setBaseValue(status.scale());

        if (this.traits.contains("long_legs")) {
            Objects.requireNonNull(this.getAttribute(Attributes.STEP_HEIGHT)).setBaseValue(1.5D);
        }

        setupAI();
        syncDataToClient();
    }

    private void setupAI() {
        if (this.level().isClientSide) return;
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);

        if (this.isMechanical) {
            if (this.vm == null) this.vm = new EntityServerLuaVM(this);
        }

        int priority = 10;
        for (String behaviorKey : this.behaviors) {
            CustomBehaviorRegistry.BehaviorDef def = CustomBehaviorRegistry.getBehaviorByKey(behaviorKey);
            if (def != null && def.aiSetup() != null) {
                def.aiSetup().accept(this, priority);
                priority += 10;
            }
        }
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (this.isSaddled && this.getFirstPassenger() instanceof Player player) {
            return player;
        }
        return super.getControllingPassenger();
    }

    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (this.isAlive() && this.isVehicle() && this.getControllingPassenger() instanceof Player player) {
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;
            float f = player.xxa * 0.5F;
            float f1 = player.zza;
            if (f1 <= 0.0F) {
                f1 *= 0.25F;
            }
            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
            super.travel(new Vec3(f, travelVector.y, f1));
            return;
        }

        if (this.isAlive() && this.hasWallClimbing && this.horizontalCollision) {
            travelVector = new Vec3(travelVector.x, 0.2D, travelVector.z);
        }
        super.travel(travelVector);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {

            if (this.shearCooldown > 0) this.shearCooldown--;
            int staggeredTick = this.tickCount + this.getId();

            if (this.isPhotosensitive) {
                if (this.level().isDay() && !this.level().isRaining() && this.level().canSeeSky(this.blockPosition())) {
                    this.igniteForSeconds(8.0F);
                }
            }
            if (this.hasFireCore || this.hasVoidCore || this.hasEnderBlood) {
                if (staggeredTick % 20 == 0 && this.isInWaterRainOrBubble()) {
                    this.hurt(this.damageSources().drown(), 1.0F);
                }
            }
            if (this.hasAbsorbent) {
                if (staggeredTick % 40 == 0 && this.isInWaterRainOrBubble()) {
                    this.heal(1.0F);
                }
            }
            if (this.isAmphibious) {
                this.setAirSupply(this.getMaxAirSupply());
            }
            if (this.hasRegeneration && staggeredTick % 100 == 0) {
                this.heal(1.0F);
            }
            if (staggeredTick % 20 == 0) {
                if (this.hasWings) this.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
                if (this.hasExtraEyes)
                    this.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
                if (this.hasBlindness)
                    this.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 220, 0, false, false));
                if (this.isGlowing) this.addEffect(new MobEffectInstance(MobEffects.GLOWING, 220, 0, false, false));
            }
            if (this.hasEcholocation && staggeredTick % 20 == 0) {
                this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(16.0D), e -> e != this)
                        .forEach(e -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false)));
            }
            if (this.hasFragrance && staggeredTick % 40 == 0) {
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.5, 0.5, 0.5, 0);
                }
                this.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Bee.class, this.getBoundingBox().inflate(16.0D))
                        .forEach(bee -> {
                            if (bee.getTarget() == null && bee.getNavigation().isDone())
                                bee.getNavigation().moveTo(this, 1.0D);
                        });
            }
            if (this.hasLuminousLure && staggeredTick % 40 == 0) {
                this.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, this.getBoundingBox().inflate(16.0D))
                        .forEach(animal -> {
                            if (animal.getTarget() == null && animal.getNavigation().isDone())
                                animal.getNavigation().moveTo(this, 1.0D);
                        });
            }
            if (this.isNoisy && staggeredTick % 40 == 0) {
                this.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, this.getBoundingBox().inflate(16.0D))
                        .forEach(monster -> {
                            if (monster.getTarget() == null) monster.setTarget(this);
                        });
            }
            if (this.isMechanical && this.vm != null) {
                this.vm.tick();
            }
            if (this.isMagnetic && staggeredTick % 4 == 0) {
                List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(4.0D));
                for (ItemEntity item : items) {
                    Vec3 vec3 = new Vec3(this.getX() - item.getX(), this.getY() + (double) this.getEyeHeight() / 2.0D - item.getY(), this.getZ() - item.getZ());
                    if (vec3.lengthSqr() < 16.0D) {
                        item.setDeltaMovement(item.getDeltaMovement().add(vec3.normalize().scale(0.08D)));
                    }
                }
            }
            if (this.hasPhotosynthesis && staggeredTick % 600 == 0) {
                if (this.level().isDay() && !this.level().isRaining() && this.level().canSeeSky(this.blockPosition())) {
                    if (this.random.nextBoolean()) {
                        this.spawnAtLocation(Items.WHEAT_SEEDS);
                    }
                }
            }
            if (this.isBuoyant && this.isInWater()) {
                Vec3 vec3 = this.getDeltaMovement();
                if (vec3.y < 0.1D) {
                    this.setDeltaMovement(vec3.x, 0.1D, vec3.z);
                }
            }
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        if (this.hasWings || this.isBouncy) {
            if (this.isBouncy && fallDistance > 2.0F) {
                this.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 1.0F);
                Vec3 vec3 = this.getDeltaMovement();
                this.setDeltaMovement(vec3.x, fallDistance * 0.2F, vec3.z);
            }
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public boolean doHurtTarget(@NotNull net.minecraft.world.entity.Entity target) {
        boolean flag = super.doHurtTarget(target);
        if (flag && this.hasLightCore && target instanceof LivingEntity le) {
            if (le.isInvertedHealAndHarm()) {
                le.hurt(this.damageSources().magic(), 4.0F);
            }
        }
        return flag;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.hasFireCore && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return false;
        if (this.hasIceCore && source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) return false;
        if (this.hasElectricCore && source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return false;
        if (this.hasVoidCore && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return false;
        if (this.isVolatile && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) amount *= 2.0F;
        if (this.hasDrySkin && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) amount *= 2.0F;

        boolean wasHurt = super.hurt(source, amount);

        if (wasHurt && source.getEntity() instanceof LivingEntity attacker) {
            if (this.hasFireCore) attacker.igniteForSeconds(5);
            if (this.hasIceCore) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            if (this.hasVenomCore) {
                attacker.hurt(this.damageSources().thorns(this), 2.0F);
                attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            }
            if (this.hasDarkCore) attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            if (this.isSticky) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            if (this.hasThornsSkin && !this.hasVenomCore) {
                attacker.hurt(this.damageSources().thorns(this), 2.0F);
            }
            if (this.hasElectricCore) {
                List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.0D), e -> e != this && e != attacker);
                attacker.hurt(this.damageSources().lightningBolt(), 2.0F);
                for (LivingEntity e : nearby) e.hurt(this.damageSources().lightningBolt(), 1.0F);
            }
        }

        if (!this.level().isClientSide && (this.hasVoidCore || this.hasTeleportation) && source.getEntity() != null) {
            if (this.random.nextFloat() < 0.25f) {
                this.randomTeleport(this.getX() + (random.nextDouble() - 0.5) * 16, this.getY() + (random.nextDouble() - 0.5) * 8, this.getZ() + (random.nextDouble() - 0.5) * 16, true);
            }
        }

        if (!this.level().isClientSide() && this.isMechanical && this.vm != null) {
            String sourceName = source.getEntity() != null ? source.getEntity().getName().getString() : "unknown";
            this.vm.triggerEvent("hurt", amount, sourceName);
        }
        return wasHurt;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public @NotNull MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.generateRandomOffers();
        }
        return this.offers;
    }

    private void generateRandomOffers() {
        List<VillagerProfession> professions = BuiltInRegistries.VILLAGER_PROFESSION.stream().toList();
        if (professions.isEmpty()) return;
        VillagerProfession profession = professions.get(this.random.nextInt(professions.size()));
        var trades = VillagerTrades.TRADES.get(profession);
        if (trades != null) {
            for (int i = 1; i <= 5; i++) {
                var levelTrades = trades.get(i);
                if (levelTrades != null && levelTrades.length > 0) {
                    var trade = levelTrades[this.random.nextInt(levelTrades.length)];
                    var offer = trade.getOffer(this, this.random);
                    if (offer != null) this.offers.add(offer);
                }
            }
        }
    }

    @Override
    public void overrideOffers(@NotNull MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.level().playSound(null, this.blockPosition(), SoundEvents.VILLAGER_YES, this.getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    public void notifyTradeUpdated(@NotNull ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public @NotNull SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    public boolean hasTraits(String[] requiredTraits) {
        if (requiredTraits == null) return true;
        for (String trait : requiredTraits) {
            if (!this.traits.contains(trait)) return false;
        }
        return true;
    }

    public boolean canSeeEntity(net.minecraft.world.entity.Entity entity) {
        if (this.hasEcholocation) {
            return true;
        }
        return this.getSensing().hasLineOfSight(entity);
    }

    public double getSensingRange(double requestedRadius) {
        if (this.hasBlindness) {
            return Math.min(requestedRadius, 4.0);
        }
        return requestedRadius;
    }

    public double getSafeSpeed(double requestedSpeed) {
        return Math.min(requestedSpeed, 2.0);
    }

    public float getSafeHealAmount(float requestedAmount) {
        float maxHeal = this.getMaxHealth() * 0.25F;
        return Math.min(requestedAmount, maxHeal);
    }

    public float getSafeExplosionPower(float requestedPower) {
        return Math.min(requestedPower, 5.0F);
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, @NotNull InteractionHand hand) {
        ItemStack stackInHand = player.getItemInHand(hand);

        if (stackInHand.getItem() instanceof com.nishiyu.lunex.item.TabletItem) {
            if (!player.isShiftKeyDown()) {
                if (!this.level().isClientSide) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        if (this.workspaceId == null || this.workspaceId.isEmpty()) {
                            this.workspaceId = "biomob_" + this.getUUID().toString().substring(0, 8);
                            WorkspaceManager.initializeWorkspace(this.level().getServer(), this.workspaceId);
                        }
                        serverPlayer.openMenu(new SimpleMenuProvider(
                                (id, inventory, p) -> new BioEntitySettingsMenu(id, inventory, this.getId()),
                                Component.literal("Bio Mob Settings")
                        ), buf -> buf.writeInt(this.getId()));
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if (this.isMechanical) {
            if (this.vm != null && this.vm.isRunning && !player.isShiftKeyDown() && stackInHand.isEmpty()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!this.level().isClientSide) {
                        this.vm.triggerEvent("on_click");
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
        }

        if (this.isMount) {
            if (stackInHand.is(Items.SADDLE) && !this.isSaddled) {
                if (!this.level().isClientSide) {
                    this.isSaddled = true;
                    this.playSound(SoundEvents.HORSE_SADDLE, 1.0F, 1.0F);
                    if (!player.isCreative()) stackInHand.shrink(1);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            } else if (this.isSaddled && stackInHand.isEmpty() && !player.isShiftKeyDown()) {
                if (!this.level().isClientSide) {
                    player.startRiding(this);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if (this.isMerchant) {
            if (stackInHand.isEmpty() && !player.isShiftKeyDown() && this.getTradingPlayer() == null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!this.level().isClientSide) {
                        this.setTradingPlayer(player);
                        this.openTradingScreen(player, this.getDisplayName(), 1);
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
        }

        if (this.isEquipable) {
            if (player.isShiftKeyDown() && stackInHand.isEmpty()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    boolean removedAny = false;
                    if (!this.level().isClientSide) {
                        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            if (slot.isArmor() && !this.getItemBySlot(slot).isEmpty()) {
                                this.spawnAtLocation(this.getItemBySlot(slot));
                                this.setItemSlot(slot, ItemStack.EMPTY);
                                removedAny = true;
                            }
                        }
                    } else {
                        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            if (slot.isArmor() && !this.getItemBySlot(slot).isEmpty()) {
                                removedAny = true;
                                break;
                            }
                        }
                    }
                    if (removedAny) return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
            if (!stackInHand.isEmpty() && stackInHand.getItem() instanceof net.minecraft.world.item.Equipable equipable) {
                net.minecraft.world.entity.EquipmentSlot slot = equipable.getEquipmentSlot();
                if (slot.isArmor()) {
                    if (!this.level().isClientSide) {
                        ItemStack currentArmor = this.getItemBySlot(slot);
                        this.setItemSlot(slot, stackInHand.copyWithCount(1));
                        this.setDropChance(slot, 1.0F);
                        if (!player.isCreative()) stackInHand.shrink(1);
                        if (!currentArmor.isEmpty()) this.spawnAtLocation(currentArmor);
                    }
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
        }

        if (this.isMilkable && stackInHand.is(Items.BUCKET)) {
            if (!this.level().isClientSide) {
                player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
                ItemStack milk = net.minecraft.world.item.ItemUtils.createFilledResult(stackInHand, player, Items.MILK_BUCKET.getDefaultInstance());
                player.setItemInHand(hand, milk);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isShearable && stackInHand.is(Items.SHEARS) && this.shearCooldown == 0) {
            if (!this.level().isClientSide) {
                player.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
                this.spawnAtLocation(Items.WHITE_WOOL, 1);
                stackInHand.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                this.shearCooldown = 6000;
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isPackMule && this.inventorySize > 0 && player.isShiftKeyDown()) {
            if (hand == InteractionHand.MAIN_HAND) {
                if (!this.level().isClientSide) {
                    player.openMenu(new SimpleMenuProvider(
                            (id, playerInv, p) -> switch (this.inventorySize) {
                                case 18 -> new ChestMenu(MenuType.GENERIC_9x2, id, playerInv, this.inventory, 2);
                                case 27 -> new ChestMenu(MenuType.GENERIC_9x3, id, playerInv, this.inventory, 3);
                                case 36 -> new ChestMenu(MenuType.GENERIC_9x4, id, playerInv, this.inventory, 4);
                                case 45 -> new ChestMenu(MenuType.GENERIC_9x5, id, playerInv, this.inventory, 5);
                                case 54 -> new ChestMenu(MenuType.GENERIC_9x6, id, playerInv, this.inventory, 6);
                                default -> new ChestMenu(MenuType.GENERIC_9x1, id, playerInv, this.inventory, 1);
                            },
                            Component.literal("Bio Mob Inventory")
                    ));
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(@NotNull DamageSource cause) {
        if (this.hasExplosiveDeath && !this.level().isClientSide) {
            this.level().explode(this, this.getX(), this.getY(), this.getZ(), 3.0F, Level.ExplosionInteraction.MOB);
        }
        super.die(cause);
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull ServerLevel level, @NotNull DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                if (!this.inventory.getItem(i).isEmpty()) this.spawnAtLocation(this.inventory.getItem(i));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShearCooldown", this.shearCooldown);
        tag.putBoolean("IsSaddled", this.isSaddled);

        ListTag traitsTag = new ListTag();
        for (String trait : traits) traitsTag.add(StringTag.valueOf(trait));
        tag.put("Traits", traitsTag);

        ListTag behaviorsTag = new ListTag();
        for (String behavior : behaviors) behaviorsTag.add(StringTag.valueOf(behavior));
        tag.put("Behaviors", behaviorsTag);

        tag.putInt("InventorySize", this.inventorySize);
        if (this.inventorySize > 0) tag.put("Inventory", this.inventory.createTag(this.registryAccess()));

        if (this.workspaceId != null) tag.putString("WorkspaceId", this.workspaceId);
        if (this.programName != null) tag.putString("ProgramName", this.programName);

        tag.putString("Appearance", this.getAppearance());

        // ★変更点: 現在のVMの稼働状態、もしくは以前保存された稼働状態を記録する
        tag.putBoolean("WasRunning", (this.vm != null && this.vm.isRunning) || this.wasRunning);
    }

    public void resetForProgram() {
        if (this.level().isClientSide) return;
        
        this.goalSelector.getAvailableGoals().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        this.targetSelector.getAvailableGoals().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);

        this.goalSelector.removeAllGoals(g -> true);
        this.targetSelector.removeAllGoals(g -> true);

        this.targetPos = null;
        this.targetEntity = null;

        this.getNavigation().stop();
        this.setTarget(null);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ShearCooldown")) this.shearCooldown = tag.getInt("ShearCooldown");
        if (tag.contains("IsSaddled")) this.isSaddled = tag.getBoolean("IsSaddled");

        if (tag.contains("Traits")) {
            ListTag t = tag.getList("Traits", Tag.TAG_STRING);
            for (int i = 0; i < t.size(); i++) this.traits.add(t.getString(i));
        }
        if (tag.contains("Behaviors")) {
            ListTag b = tag.getList("Behaviors", Tag.TAG_STRING);
            for (int i = 0; i < b.size(); i++) this.behaviors.add(b.getString(i));
        }

        updateCaches();

        if (tag.contains("InventorySize")) {
            int rawSize = tag.getInt("InventorySize");
            if (rawSize > 0) {
                int rows = (int) Math.ceil(rawSize / 9.0);
                this.inventorySize = Math.min(rows * 9, 54);
            } else {
                this.inventorySize = 0;
            }
        }
        this.inventory = new SimpleContainer(Math.max(this.inventorySize, 0));

        if (tag.contains("Inventory") && this.inventorySize > 0) {
            this.inventory.fromTag(tag.getList("Inventory", 10), this.registryAccess());
        }

        if (tag.contains("WorkspaceId")) this.workspaceId = tag.getString("WorkspaceId");
        if (tag.contains("ProgramName")) this.programName = tag.getString("ProgramName");

        if (tag.contains("Appearance")) this.setAppearance(tag.getString("Appearance"));

        // ★変更点: セーブデータから前回の起動状態を読み込む
        this.wasRunning = tag.getBoolean("WasRunning");

        setupAI();
        syncDataToClient();

        // ★変更点: 無条件起動ではなく、前回の起動状態 (wasRunning) が true だった場合のみ再ロード時に起動を再開する
        if (!this.level().isClientSide && this.isMechanical && this.programName != null && !this.programName.isEmpty() && this.wasRunning) {
            if (this.vm != null && !this.vm.isRunning) {
                try {
                    String wsId = this.workspaceId != null && !this.workspaceId.isEmpty() ? this.workspaceId : "biomob_" + this.getUUID().toString().substring(0, 8);
                    ServerProgramData.load(wsId);
                    String code = ServerProgramData.getPrograms(wsId).get(this.programName);
                    if (code != null && !code.isEmpty()) {
                        this.vm.startCode(code, this.programName);
                    }
                } catch (Exception e) {
                    Lunex.LOGGER.error("[BioMob] Auto-start failed", e);
                }
            }
        }
    }

    public String getCustomSkinName() {
        return this.customSkinName;
    }

    public void setCustomSkinName(String skinName) {
        this.customSkinName = skinName;
    }

    public FakePlayer getFakePlayer() {
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.fakePlayer == null) {
                GameProfile profile = new GameProfile(this.getUUID(), "[Bot]" + this.getName().getString());
                this.fakePlayer = FakePlayerFactory.get(serverLevel, profile);
            }
            this.fakePlayer.setPos(this.getX(), this.getY(), this.getZ());
            this.fakePlayer.setYRot(this.getYRot());
            this.fakePlayer.setXRot(this.getXRot());
            this.fakePlayer.setYHeadRot(this.getYHeadRot());
            this.fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, this.getItemInHand(InteractionHand.MAIN_HAND));
            this.fakePlayer.setItemInHand(InteractionHand.OFF_HAND, this.getItemInHand(InteractionHand.OFF_HAND));
            return this.fakePlayer;
        }
        return null;
    }
}