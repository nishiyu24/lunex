package com.nishiyu.lunex.entity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.program.server.entity.EntityServerLuaVM;
import com.nishiyu.lunex.server.ServerProgramData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CustomBioMobEntity extends PathfinderMob implements Merchant {

    private static final EntityDataAccessor<String> SYNCED_TRAITS = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SYNCED_BEHAVIORS = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SYNCED_ABILITIES = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SYNCED_APPEARANCE = SynchedEntityData.defineId(CustomBioMobEntity.class, EntityDataSerializers.STRING);

    public final Set<String> traits = new HashSet<>();
    public final Set<String> abilities = new HashSet<>();
    public final List<String> behaviors = new ArrayList<>();

    public final MobTraitManager traitManager = new MobTraitManager(this);
    public final MobFakePlayerContext fakePlayerContext = new MobFakePlayerContext(this);

    public final Map<String, Object> aiBlackboard = new ConcurrentHashMap<>();

    public SimpleContainer inventory;
    public int inventorySize = 0;
    public EntityServerLuaVM vm = null;

    public boolean wasRunning = false;
    public BlockPos targetPos = null;
    public net.minecraft.world.entity.Entity targetEntity = null;

    public String workspaceId = "";
    public String programName = "";

    public int shearCooldown = 0;
    public boolean isSaddled = false;
    private Player tradingPlayer;
    private MerchantOffers offers;
    private String customSkinName = "default";

    public CustomBioMobEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.inventory = new SimpleContainer(Math.max(this.inventorySize, 0));
    }

    public static AttributeSupplier.Builder createBaseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.5D)
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
                this.traitManager.updateTraits(this.traits);
            } else if (SYNCED_BEHAVIORS.equals(key)) {
                this.behaviors.clear();
                String data = this.entityData.get(SYNCED_BEHAVIORS);
                if (!data.isEmpty()) this.behaviors.addAll(Arrays.asList(data.split(",")));
            } else if (SYNCED_ABILITIES.equals(key)) {
                this.abilities.clear();
                String data = this.entityData.get(SYNCED_ABILITIES);
                if (!data.isEmpty()) this.abilities.addAll(Arrays.asList(data.split(",")));
            }
        }
    }

    public void initializeMob(List<String> traits, List<String> abilities, List<String> behaviors,
                              int invSize, BioMobGenerator.MobStatus status) {
        this.traits.addAll(traits);
        this.abilities.addAll(abilities);
        this.behaviors.addAll(behaviors);

        this.traitManager.updateTraits(this.traits);
        this.traitManager.applyStatus(status);

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
        Objects.requireNonNull(this.getAttribute(Attributes.STEP_HEIGHT)).setBaseValue(status.stepHeight());

        this.applyTraitAttributes();
        setupAI();
        syncDataToClient();
    }

    public void applyTraitAttributes() {
        if (this.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            Objects.requireNonNull(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(this.traitManager.getKnockbackResistance());
        }
    }

    private void setupAI() {
        if (this.level().isClientSide) return;
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);

        if (this.isMechanical()) {
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

    public boolean isMechanical() {
        return this.behaviors.contains("mechanical");
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

        if (this.isAlive() && this.traitManager.hasWallClimbing && this.horizontalCollision) {
            travelVector = new Vec3(travelVector.x, 0.2D, travelVector.z);
        }
        super.travel(travelVector);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide() && this.swinging) {
            this.updateSwingTime();
        }

        if (!this.level().isClientSide()) {
            if (this.shearCooldown > 0) this.shearCooldown--;
            int staggeredTick = this.tickCount + this.getId();

            this.traitManager.tick(staggeredTick);
            if (this.isMechanical() && this.vm != null) {
                this.vm.tick();
            }
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        multiplier *= this.traitManager.getFallDamageMultiplier();
        if (multiplier <= 0.0f) return false;

        if (this.traitManager.isBouncy && fallDistance > 2.0F) {
            this.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 1.0F);
            Vec3 vec3 = this.getDeltaMovement();
            this.setDeltaMovement(vec3.x, fallDistance * 0.2F, vec3.z);
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public boolean doHurtTarget(@NotNull net.minecraft.world.entity.Entity target) {
        var attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        double originalDamage = attr != null ? attr.getBaseValue() : 2.0;

        boolean flag = super.doHurtTarget(target);

        // ★修正: ここでのスイング処理は削除し、GoalActions の攻撃AI側でコントロールする
        if (flag && this.traitManager.hasLightCore && target instanceof LivingEntity le) {
            if (le.isInvertedHealAndHarm()) {
                le.hurt(this.damageSources().magic(), 4.0F);
            }
        }
        return flag;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        amount *= this.traitManager.getDamageTakenMultiplier();

        if (this.traitManager.hasFireCore && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return false;
        if (this.traitManager.hasIceCore && source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) return false;
        if (this.traitManager.hasElectricCore && source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return false;
        if (this.traitManager.hasVoidCore && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return false;
        if (this.traitManager.isVolatile && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) amount *= 2.0F;
        if (this.traitManager.hasDrySkin && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) amount *= 2.0F;

        boolean wasHurt = super.hurt(source, amount);

        if (wasHurt && source.getEntity() instanceof LivingEntity attacker) {
            if (this.traitManager.hasFireCore) attacker.igniteForSeconds(5);
            if (this.traitManager.hasIceCore) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            if (this.traitManager.hasVenomCore) {
                attacker.hurt(this.damageSources().thorns(this), 2.0F);
                attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            }
            if (this.traitManager.hasDarkCore) attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            if (this.traitManager.isSticky) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            if (this.traitManager.hasThornsSkin && !this.traitManager.hasVenomCore) {
                attacker.hurt(this.damageSources().thorns(this), 2.0F);
            }
            if (this.traitManager.hasElectricCore) {
                List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.0D), e -> e != this && e != attacker);
                attacker.hurt(this.damageSources().lightningBolt(), 2.0F);
                for (LivingEntity e : nearby) e.hurt(this.damageSources().lightningBolt(), 1.0F);
            }
        }

        if (!this.level().isClientSide && (this.traitManager.hasVoidCore || this.traitManager.hasTeleportation) && source.getEntity() != null) {
            if (this.random.nextFloat() < 0.25f) {
                this.randomTeleport(this.getX() + (random.nextDouble() - 0.5) * 16, this.getY() + (random.nextDouble() - 0.5) * 8, this.getZ() + (random.nextDouble() - 0.5) * 16, true);
            }
        }

        if (!this.level().isClientSide() && this.isMechanical() && this.vm != null) {
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
        if (entity == null) return false;
        double maxDist = this.traitManager.getSensingRangeMax();
        if (this.distanceToSqr(entity) > maxDist * maxDist) return false;
        if (this.traitManager.hasEcholocation) return true;
        return this.getSensing().hasLineOfSight(entity);
    }

    public double getSensingRange(double requestedRadius) {
        return Math.min(requestedRadius, this.traitManager.getSensingRangeMax());
    }

    public double getSafeSpeed(double requestedSpeed) {
        return Math.min(requestedSpeed, this.traitManager.getMaxMovementSpeed());
    }

    public double getSafeInteractRange(double requestedRange) {
        return Math.min(requestedRange, this.traitManager.getInteractRange());
    }

    public float getSafeHealAmount(float requestedAmount) {
        float maxHeal = this.getMaxHealth() * 0.25F;
        return Math.min(requestedAmount, maxHeal);
    }

    public float getSafeExplosionPower(float requestedPower) {
        return Math.min(requestedPower, 5.0F);
    }

    public boolean tickMining(BlockPos pos) {
        return this.fakePlayerContext.tickMining(pos);
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        InteractionResult result = MobInteractHandler.handleInteract(this, player, hand);
        if (result != InteractionResult.PASS) return result;
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(@NotNull DamageSource cause) {
        if (this.traitManager.hasExplosiveDeath && !this.level().isClientSide) {
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

        this.aiBlackboard.clear();
        this.fakePlayerContext.resetMining();
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

        this.traitManager.updateTraits(this.traits);

        BioMobGenerator.MobStatus status = BioMobGenerator.calculateStatus(Collections.emptyMap(), new ArrayList<>(this.traits));
        this.traitManager.applyStatus(status);

        this.applyTraitAttributes();

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

        this.wasRunning = tag.getBoolean("WasRunning");

        setupAI();
        syncDataToClient();

        if (!this.level().isClientSide && this.isMechanical() && this.programName != null && !this.programName.isEmpty() && this.wasRunning) {
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
        return this.fakePlayerContext.getFakePlayer();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isMechanical() ? SoundEvents.COPPER_GRATE_STEP : SoundEvents.COW_AMBIENT;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.GENERIC_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        if (!state.liquid()) {
            net.minecraft.world.level.block.SoundType soundtype = state.getSoundType(this.level(), pos, this);
            this.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
        }
    }

    public void startConsumingItem(InteractionHand hand) {
        ItemStack stack = this.getItemInHand(hand);
        if (!stack.isEmpty()) {
            this.startUsingItem(hand);
        }
    }
}