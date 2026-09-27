package com.nishiyu.lunex.event;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.ItemMachineContext;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;
import com.nishiyu.lunex.program.server.tool.api.ToolAPIBase;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Lunex.MODID)
public class ToolEventHandler {

    private static final Map<UUID, ToolServerLuaVM> VM_CACHE = new ConcurrentHashMap<>();

    private static final Map<UUID, HomingData> HOMING_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, ReflectData> REFLECT_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, GravityData> GRAVITY_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, BindData> BIND_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ParticleData>> PARTICLE_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<DespawnData>> DESPAWN_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<SpinData>> SPIN_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<HoldData>> HOLD_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<SyncData>> SYNC_TASKS = new ConcurrentHashMap<>();

    public static void startHomingTask(Player p, List<UUID> ids, double tr, int t) {
        HOMING_TASKS.put(p.getUUID(), new HomingData(ids, tr, t));
    }

    public static void startReflectTask(Player p, List<UUID> ids, int t) {
        REFLECT_TASKS.put(p.getUUID(), new ReflectData(ids, t));
    }

    public static void startGravityTask(Player p, Vec3 pos, double r, double pw, int t) {
        GRAVITY_TASKS.put(p.getUUID(), new GravityData(pos, r, pw, t));
    }

    public static void startBindTask(Player p, net.minecraft.world.entity.Entity target, int t) {
        BIND_TASKS.put(p.getUUID(), new BindData(target, t));
    }

    public static void startParticleTask(Player p, UUID targetId, String particleName, int t) {
        PARTICLE_TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new ParticleData(targetId, particleName, t));
    }

    public static void startDespawnTask(Player p, UUID targetId, int t) {
        DESPAWN_TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new DespawnData(targetId, t));
    }

    public static void startSpinTask(Player p, UUID targetId, float speedX, float speedY, float speedZ, int t) {
        SPIN_TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new SpinData(targetId, speedX, speedY, speedZ, t));
    }

    public static void startHoldTask(Player p, UUID targetId, double distance, int t) {
        HOLD_TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new HoldData(targetId, distance, t));
    }

    public static void startSyncTask(Player p, UUID followerId, UUID targetId, double ox, double oy, double oz, int t) {
        SYNC_TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new SyncData(followerId, targetId, ox, oy, oz, t));
    }

    public static void cancelActionTasks(Player p, UUID targetId) {
        UUID pid = p.getUUID();
        if (PARTICLE_TASKS.containsKey(pid)) PARTICLE_TASKS.get(pid).removeIf(d -> d.targetId.equals(targetId));
        if (SPIN_TASKS.containsKey(pid)) SPIN_TASKS.get(pid).removeIf(d -> d.targetId.equals(targetId));
        if (HOLD_TASKS.containsKey(pid)) HOLD_TASKS.get(pid).removeIf(d -> d.targetId.equals(targetId));
        if (SYNC_TASKS.containsKey(pid)) SYNC_TASKS.get(pid).removeIf(d -> d.followerId.equals(targetId));
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID id = player.getUUID();

        if (HOMING_TASKS.containsKey(id)) {
            HomingData data = HOMING_TASKS.get(id);
            executeHomingTask(player, data);
            if (--data.remainingTicks <= 0) HOMING_TASKS.remove(id);
        }

        if (REFLECT_TASKS.containsKey(id)) {
            ReflectData data = REFLECT_TASKS.get(id);
            executeReflectTask(player, data);
            if (--data.remainingTicks <= 0) REFLECT_TASKS.remove(id);
        }

        if (GRAVITY_TASKS.containsKey(id)) {
            GravityData data = GRAVITY_TASKS.get(id);
            executeGravityTask(player, data);
            if (--data.remainingTicks <= 0) GRAVITY_TASKS.remove(id);
        }

        if (BIND_TASKS.containsKey(id)) {
            BindData data = BIND_TASKS.get(id);
            executeBindTask(player, data);
            if (--data.remainingTicks <= 0 || !data.target.isAlive()) BIND_TASKS.remove(id);
        }

        if (PARTICLE_TASKS.containsKey(id)) {
            List<ParticleData> tasks = PARTICLE_TASKS.get(id);
            tasks.removeIf(data -> {
                executeParticleTask(player, data);
                return --data.remainingTicks <= 0;
            });
            if (tasks.isEmpty()) PARTICLE_TASKS.remove(id);
        }

        if (DESPAWN_TASKS.containsKey(id)) {
            List<DespawnData> tasks = DESPAWN_TASKS.get(id);
            tasks.removeIf(data -> {
                if (--data.remainingTicks <= 0) {
                    executeDespawnTask(player, data);
                    return true;
                }
                return false;
            });
            if (tasks.isEmpty()) DESPAWN_TASKS.remove(id);
        }

        if (SPIN_TASKS.containsKey(id)) {
            List<SpinData> tasks = SPIN_TASKS.get(id);
            tasks.removeIf(data -> {
                executeSpinTask(player, data);
                return --data.remainingTicks <= 0;
            });
            if (tasks.isEmpty()) SPIN_TASKS.remove(id);
        }

        if (HOLD_TASKS.containsKey(id)) {
            List<HoldData> tasks = HOLD_TASKS.get(id);
            tasks.removeIf(data -> {
                executeHoldTask(player, data);
                return --data.remainingTicks <= 0;
            });
            if (tasks.isEmpty()) HOLD_TASKS.remove(id);
        }

        if (SYNC_TASKS.containsKey(id)) {
            List<SyncData> tasks = SYNC_TASKS.get(id);
            tasks.removeIf(data -> {
                executeSyncTask(player, data);
                return --data.remainingTicks <= 0;
            });
            if (tasks.isEmpty()) SYNC_TASKS.remove(id);
        }
    }

    private static void executeSyncTask(Player player, SyncData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        net.minecraft.world.entity.Entity follower = serverLevel.getEntity(data.followerId);
        net.minecraft.world.entity.Entity target = serverLevel.getEntity(data.targetId);

        if (follower != null && target != null && target.isAlive()) {
            follower.setDeltaMovement(0, 0, 0);
            follower.fallDistance = 0.0F;
            follower.moveTo(target.getX() + data.offsetX, target.getY() + data.offsetY, target.getZ() + data.offsetZ);
        }
    }

    private static void executeHoldTask(Player player, HoldData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        net.minecraft.world.entity.Entity e = serverLevel.getEntity(data.targetId);

        if (e != null && e.isAlive()) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 targetPos = eyePos.add(look.scale(data.distance));

            e.setDeltaMovement(0, 0, 0);
            e.fallDistance = 0.0F;

            double offset = (e instanceof net.minecraft.world.entity.decoration.ArmorStand) ? 1.5 : (e.getBbHeight() / 2.0);
            e.moveTo(targetPos.x, targetPos.y - offset, targetPos.z);
        }
    }

    private static void executeSpinTask(Player player, SpinData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        net.minecraft.world.entity.Entity e = serverLevel.getEntity(data.targetId);

        if (e != null && e.isAlive()) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand) {
                net.minecraft.core.Rotations current = armorStand.getHeadPose();
                float newX = (current.getX() + data.speedX) % 360.0F;
                float newY = (current.getY() + data.speedY) % 360.0F;
                float newZ = (current.getZ() + data.speedZ) % 360.0F;
                armorStand.setHeadPose(new net.minecraft.core.Rotations(newX, newY, newZ));
            } else {
                e.setYRot(e.getYRot() + data.speedY);
                e.setXRot(e.getXRot() + data.speedX);
                e.yRotO = e.getYRot();
                e.xRotO = e.getXRot();
            }
        }
    }

    private static void executeDespawnTask(Player player, DespawnData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        net.minecraft.world.entity.Entity e = serverLevel.getEntity(data.targetId);
        if (e != null && e.isAlive()) {
            e.discard();
        }
    }

    private static void executeParticleTask(Player player, ParticleData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        net.minecraft.world.entity.Entity e = serverLevel.getEntity(data.targetId);
        if (e != null && e.isAlive()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(data.particleName);
                net.minecraft.core.particles.ParticleType<?> pt = BuiltInRegistries.PARTICLE_TYPE.get(rl);
                if (pt instanceof net.minecraft.core.particles.SimpleParticleType spt) {
                    serverLevel.sendParticles(spt, e.getX(), e.getY() + 1.5, e.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void executeHomingTask(Player player, HomingData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        for (UUID uuid : data.targetIds) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile p && p.isAlive()) {

                net.minecraft.world.phys.AABB trackAabb = p.getBoundingBox().inflate(data.trackRadius);
                List<net.minecraft.world.entity.LivingEntity> enemies = serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, trackAabb, e -> e != player && e.isAlive());
                if (enemies.isEmpty()) continue;

                net.minecraft.world.entity.LivingEntity target = null;
                double minDist = Double.MAX_VALUE;
                for (net.minecraft.world.entity.LivingEntity e : enemies) {
                    double dist = p.distanceToSqr(e);
                    if (dist < minDist) {
                        minDist = dist;
                        target = e;
                    }
                }

                if (target != null) {
                    Vec3 currentVel = p.getDeltaMovement();
                    double speed = Math.max(currentVel.length(), 1.0);
                    Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2.0, 0);
                    Vec3 dir = targetPos.subtract(p.position()).normalize();
                    p.setDeltaMovement(dir.scale(speed));
                    p.hurtMarked = true;

                    p.setYRot((float) (Math.atan2(dir.x, dir.z) * (180F / Math.PI)));
                    p.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180F / Math.PI)));
                    p.yRotO = p.getYRot();
                    p.xRotO = p.getXRot();
                }
            }
        }
    }

    private static void executeReflectTask(Player player, ReflectData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        for (UUID uuid : data.targetIds) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile p && p.isAlive()) {
                Vec3 awayDir = p.position().subtract(player.position()).normalize();
                double speed = Math.max(p.getDeltaMovement().length(), 1.5);

                p.setDeltaMovement(awayDir.scale(speed));
                p.hurtMarked = true;
                p.setOwner(player);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }

    private static void executeGravityTask(Player player, GravityData data) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, data.pos.x, data.pos.y, data.pos.z, 20, 0.5, 0.5, 0.5, 0.5);

        net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(data.pos.x - data.radius, data.pos.y - data.radius, data.pos.z - data.radius, data.pos.x + data.radius, data.pos.y + data.radius, data.pos.z + data.radius);

        List<net.minecraft.world.entity.Entity> entities = serverLevel.getEntities(player, aabb, e -> e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.item.ItemEntity);

        for (net.minecraft.world.entity.Entity e : entities) {
            Vec3 toCenter = data.pos.subtract(e.position()).normalize().scale(data.power);
            e.setDeltaMovement(e.getDeltaMovement().add(toCenter));
            e.hurtMarked = true;
        }
    }

    private static void executeBindTask(Player player, BindData data) {
        if (data.target != null && data.target.isAlive()) {
            data.target.setDeltaMovement(0, 0, 0);
            data.target.hurtMarked = true;
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, data.target.getX(), data.target.getY(), data.target.getZ(), 5, 0.3, 0.1, 0.3, 0.0);
            }
        }
    }

    private static boolean isUpgradeItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return name.contains("upgrade") || name.contains("module");
    }

    private static String getUpgradeCategory(ItemStack stack) {
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (name.contains("exec")) return "execution";
        if (name.contains("storage")) return "storage";
        if (name.contains("speed")) return "speed";
        if (name.contains("efficiency")) return "efficiency";
        if (name.contains("capacity")) return "capacity";
        if (name.contains("generator")) return "generator";
        return name;
    }

    public static void recalculateUpgrades(CompoundTag tag, net.minecraft.core.RegistryAccess registryAccess) {
        tag.remove("ExecUpgradeLevel");
        tag.remove("StorageUpgradeLevel");
        tag.remove("SpeedUpgradeLevel");
        tag.remove("EfficiencyUpgradeLevel");
        tag.remove("CapacityUpgradeLevel");
        tag.remove("GeneratorUpgradeLevel");

        if (tag.contains("InstalledUpgrades")) {
            ListTag upgrades = tag.getList("InstalledUpgrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < upgrades.size(); i++) {
                CompoundTag itemTag = upgrades.getCompound(i);
                Optional<ItemStack> opt = ItemStack.parse(registryAccess, itemTag);
                if (opt.isPresent()) {
                    ItemStack uStack = opt.get();
                    String name = BuiltInRegistries.ITEM.getKey(uStack.getItem()).getPath();

                    int level = 1;
                    if (name.contains("2") || name.contains("advanced") || name.contains("gold")) level = 2;
                    if (name.contains("3") || name.contains("elite") || name.contains("diamond")) level = 3;

                    if (name.contains("exec")) tag.putInt("ExecUpgradeLevel", level);
                    else if (name.contains("storage")) tag.putInt("StorageUpgradeLevel", level);
                    else if (name.contains("speed")) tag.putInt("SpeedUpgradeLevel", level);
                    else if (name.contains("efficiency")) tag.putInt("EfficiencyUpgradeLevel", level);
                    else if (name.contains("capacity")) tag.putInt("CapacityUpgradeLevel", level);
                    else if (name.contains("generator")) tag.putInt("GeneratorUpgradeLevel", level);
                }
            }
        }
    }

    public static boolean hasCartridge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("WorkspaceId");
    }

    private static boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();

        if (stack.is(net.minecraft.tags.ItemTags.SWORDS) ||
                stack.is(net.minecraft.tags.ItemTags.AXES) ||
                stack.is(net.minecraft.tags.ItemTags.PICKAXES) ||
                stack.is(net.minecraft.tags.ItemTags.SHOVELS) ||
                stack.is(net.minecraft.tags.ItemTags.HOES)) {
            return true;
        }

        if (item instanceof net.minecraft.world.item.SwordItem ||
                item instanceof net.minecraft.world.item.DiggerItem ||
                item instanceof net.minecraft.world.item.BowItem ||
                item instanceof net.minecraft.world.item.CrossbowItem ||
                item instanceof net.minecraft.world.item.TridentItem ||
                item instanceof net.minecraft.world.item.MaceItem) {
            return true;
        }
        return false;
    }

    private static UUID getOrCreateCartridgeUUID(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("CartridgeUUID")) {
            UUID newId = UUID.randomUUID();
            tag.putUUID("CartridgeUUID", newId);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
            return newId;
        }
        return tag.getUUID("CartridgeUUID");
    }

    public static void rebootToolVM(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("CartridgeUUID")) {
            UUID cartId = tag.getUUID("CartridgeUUID");
            VM_CACHE.remove(cartId);
        }
        tag.remove("AutoMemory");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void runToolProgram(ItemStack stack, Player player, net.minecraft.world.entity.Entity targetEntity, String eventName, Object... args) {
        Level level = player.level();
        if (level.isClientSide) return;

        ItemMachineContext tempCtx = new ItemMachineContext(stack);
        String wsId = tempCtx.getWorkspaceId();
        String progName = tempCtx.getProgramName();

        if (wsId == null || wsId.isEmpty() || progName == null || progName.isEmpty()) return;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean("IsRunning")) {
            tag.putBoolean("IsRunning", true);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
        }

        ItemMachineContext finalCtx = new ItemMachineContext(stack);
        UUID cartId = getOrCreateCartridgeUUID(stack);

        ToolServerLuaVM vm = VM_CACHE.computeIfAbsent(cartId, id -> {
            ToolServerLuaVM newVm = new ToolServerLuaVM(finalCtx);
            CompoundTag currentTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            CompoundTag mem = currentTag.contains("AutoMemory") ? currentTag.getCompound("AutoMemory") : null;
            newVm.loadProgram(progName, mem);
            return newVm;
        });

        vm.context = finalCtx;
        vm.currentPlayer = player;
        vm.currentTarget = targetEntity;

        vm.triggerEventSync(eventName, args);

        ToolAPIBase.refundDeposit(vm);

        CompoundTag memory = vm.extractMemoryToTag();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.put("AutoMemory", memory));
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();

        if (player.isShiftKeyDown()) {

            // 【追加】メインハンドがレンチ、オフハンドにツール（ガジェット入り）がある場合、中身を全て排出する
            if (mainStack.getItem() instanceof com.nishiyu.lunex.item.WrenchItem && hasCartridge(offStack) && !offStack.is(Lunex.MACHINE_CARTRIDGE.get())) {
                if (!level.isClientSide) {
                    CompoundTag tag = offStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

                    // 1. カートリッジを排出
                    ItemStack extractedCartridge = new ItemStack(Lunex.MACHINE_CARTRIDGE.get());
                    CompoundTag cartTag = new CompoundTag();
                    if (tag.contains("WorkspaceId")) cartTag.putString("WorkspaceId", tag.getString("WorkspaceId"));
                    if (tag.contains("ProgramName")) cartTag.putString("ProgramName", tag.getString("ProgramName"));
                    if (tag.contains("MachineLabel")) cartTag.putString("MachineLabel", tag.getString("MachineLabel"));
                    if (tag.contains("PersistentData"))
                        cartTag.put("PersistentData", tag.getCompound("PersistentData"));
                    if (tag.contains("Energy")) cartTag.putInt("Energy", tag.getInt("Energy"));
                    if (tag.contains("CartridgeUUID")) cartTag.putUUID("CartridgeUUID", tag.getUUID("CartridgeUUID"));
                    if (tag.contains("AutoMemory")) cartTag.put("AutoMemory", tag.getCompound("AutoMemory"));
                    CustomData.update(DataComponents.CUSTOM_DATA, extractedCartridge, t -> t.merge(cartTag));

                    if (!player.getInventory().add(extractedCartridge)) {
                        player.drop(extractedCartridge, false);
                    }

                    // 2. アップグレードを全て排出
                    if (tag.contains("InstalledUpgrades")) {
                        ListTag upgradesList = tag.getList("InstalledUpgrades", Tag.TAG_COMPOUND);
                        for (int i = 0; i < upgradesList.size(); i++) {
                            CompoundTag itemTag = upgradesList.getCompound(i);
                            Optional<ItemStack> opt = ItemStack.parse(player.registryAccess(), itemTag);
                            opt.ifPresent(uStack -> {
                                if (!player.getInventory().add(uStack)) {
                                    player.drop(uStack, false);
                                }
                            });
                        }
                    }

                    // 3. ツールのNBTから情報を完全消去
                    offStack.remove(DataComponents.CUSTOM_DATA);

                    player.getInventory().setChanged();
                    player.displayClientMessage(Component.literal("§e[System] カートリッジとアップグレードをすべて排出しました。§r"), true);
                }
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide));
                return;
            }


            if (isTool(mainStack)) {
                ItemStack toolStack = mainStack;
                ItemStack targetStack = offStack;

                if (targetStack.is(Lunex.MACHINE_CARTRIDGE.get()) && !hasCartridge(toolStack)) {
                    if (!level.isClientSide) {
                        CompoundTag cartTag = targetStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        if (!cartTag.contains("WorkspaceId")) cartTag.putString("WorkspaceId", "");
                        cartTag.putBoolean("IsRunning", true);
                        CustomData.update(DataComponents.CUSTOM_DATA, toolStack, t -> t.merge(cartTag));
                        player.getInventory().setChanged();
                        player.displayClientMessage(Component.literal("§b[System] カートリッジを結合しました！§r"), true);
                    }
                    if (!player.isCreative()) targetStack.shrink(1);
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide));
                    return;
                }

                if (hasCartridge(toolStack) && isUpgradeItem(targetStack)) {
                    CompoundTag checkTag = toolStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    ListTag checkUpgrades = checkTag.getList("InstalledUpgrades", Tag.TAG_COMPOUND);
                    String newCategory = getUpgradeCategory(targetStack);
                    boolean isReplacing = false;

                    for (int i = 0; i < checkUpgrades.size(); i++) {
                        CompoundTag existingTag = checkUpgrades.getCompound(i);
                        Optional<ItemStack> existingOpt = ItemStack.parse(player.registryAccess(), existingTag);
                        if (existingOpt.isPresent() && getUpgradeCategory(existingOpt.get()).equals(newCategory)) {
                            isReplacing = true;
                            break;
                        }
                    }

                    if (!isReplacing && checkUpgrades.size() >= 4) {
                        if (!level.isClientSide)
                            player.displayClientMessage(Component.literal("§c[System] スロットが満杯です。(最大4つ)§r"), true);
                        event.setCanceled(true);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                        return;
                    }

                    if (!level.isClientSide) {
                        CompoundTag toolTag = toolStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        ListTag upgrades = toolTag.getList("InstalledUpgrades", Tag.TAG_COMPOUND);
                        boolean replaced = false;

                        for (int i = 0; i < upgrades.size(); i++) {
                            CompoundTag existingTag = upgrades.getCompound(i);
                            Optional<ItemStack> existingOpt = ItemStack.parse(player.registryAccess(), existingTag);
                            if (existingOpt.isPresent()) {
                                ItemStack existingStack = existingOpt.get();
                                if (getUpgradeCategory(existingStack).equals(newCategory)) {
                                    if (!player.getInventory().add(existingStack)) player.drop(existingStack, false);
                                    ItemStack singleNew = targetStack.copyWithCount(1);
                                    upgrades.set(i, (Tag) singleNew.saveOptional(player.registryAccess()));
                                    replaced = true;
                                    player.displayClientMessage(Component.literal("§a[System] アップグレードを交換しました！§r"), true);
                                    break;
                                }
                            }
                        }

                        if (!replaced) {
                            ItemStack singleNew = targetStack.copyWithCount(1);
                            upgrades.add((Tag) singleNew.saveOptional(player.registryAccess()));
                            player.displayClientMessage(Component.literal("§a[System] アップグレードを装着しました！§r"), true);
                        }

                        toolTag.put("InstalledUpgrades", upgrades);
                        recalculateUpgrades(toolTag, player.registryAccess());
                        CustomData.update(DataComponents.CUSTOM_DATA, toolStack, t -> t.merge(toolTag));
                        player.getInventory().setChanged();
                    }

                    if (!player.isCreative()) targetStack.shrink(1);
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide));
                    return;
                }

                if (targetStack.isEmpty() && hasCartridge(toolStack)) {
                    if (!level.isClientSide) {
                        CompoundTag tag = toolStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        tag.putInt("Energy", 1000000);
                        CustomData.update(DataComponents.CUSTOM_DATA, toolStack, t -> t.merge(tag));
                        player.getInventory().setChanged();
                        player.displayClientMessage(Component.literal("§d[Debug] ツールにエネルギーをフルチャージしました！§r"), true);
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide));
                    return;
                }
            }
        }

        if (hasCartridge(mainStack)) {
            runToolProgram(mainStack, player, null, "on_right_click", 0);
        } else if (hasCartridge(offStack)) {
            runToolProgram(offStack, player, null, "on_right_click", 0);
        }
    }

    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack stack = event.getItem();
            if (hasCartridge(stack)) {
                int chargeTicks = stack.getUseDuration(player) - event.getDuration();
                runToolProgram(stack, player, null, "on_right_click", chargeTicks);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack mainStack = event.getEntity().getMainHandItem();
        ItemStack offStack = event.getEntity().getOffhandItem();
        String actionState = event.getAction().name();
        float attackCooldown = event.getEntity().getAttackStrengthScale(0.5f);

        if (hasCartridge(mainStack)) {
            runToolProgram(mainStack, event.getEntity(), null, "on_left_click_block", event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(), actionState, attackCooldown);
        } else if (hasCartridge(offStack)) {
            runToolProgram(offStack, event.getEntity(), null, "on_left_click_block", event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(), actionState, attackCooldown);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();

        if (hasCartridge(mainStack)) {
            runToolProgram(mainStack, player, null, "on_block_break", event.getPos().getX(), event.getPos().getY(), event.getPos().getZ());
        } else if (hasCartridge(offStack)) {
            runToolProgram(offStack, player, null, "on_block_break", event.getPos().getX(), event.getPos().getY(), event.getPos().getZ());
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        String targetId = event.getTarget().getStringUUID();
        float attackCooldown = player.getAttackStrengthScale(0.5f);

        if (hasCartridge(mainStack)) {
            runToolProgram(mainStack, player, event.getTarget(), "on_attack_entity", targetId, attackCooldown);
        } else if (hasCartridge(offStack)) {
            runToolProgram(offStack, player, event.getTarget(), "on_attack_entity", targetId, attackCooldown);
        }
    }

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof Player player) {
            ItemStack mainStack = player.getMainHandItem();
            ItemStack offStack = player.getOffhandItem();

            ItemStack toolStack = ItemStack.EMPTY;
            if (hasCartridge(mainStack)) {
                toolStack = mainStack;
            } else if (hasCartridge(offStack)) {
                toolStack = offStack;
            }

            if (!toolStack.isEmpty()) {
                HitResult hit = event.getRayTraceResult();
                String projId = event.getProjectile().getStringUUID();

                if (hit.getType() == HitResult.Type.ENTITY) {
                    net.minecraft.world.entity.Entity hitEntity = ((net.minecraft.world.phys.EntityHitResult) hit).getEntity();
                    runToolProgram(toolStack, player, hitEntity, "on_projectile_hit_entity", hitEntity.getStringUUID(), projId);
                } else if (hit.getType() == HitResult.Type.BLOCK) {
                    net.minecraft.core.BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                    runToolProgram(toolStack, player, null, "on_projectile_hit_block", pos.getX(), pos.getY(), pos.getZ(), projId);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileShoot(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            if (projectile.getOwner() instanceof Player player) {
                ItemStack mainStack = player.getMainHandItem();
                ItemStack offStack = player.getOffhandItem();

                ItemStack toolStack = ItemStack.EMPTY;
                if (hasCartridge(mainStack)) {
                    toolStack = mainStack;
                } else if (hasCartridge(offStack)) {
                    toolStack = offStack;
                }

                if (!toolStack.isEmpty()) {
                    runToolProgram(toolStack, player, null, "on_projectile_shoot", projectile.getStringUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(Lunex.MACHINE_CARTRIDGE.get())) return;

        if (hasCartridge(stack)) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            event.getToolTip().add(Component.literal(""));
            event.getToolTip().add(Component.literal("◆ マシンカートリッジ内蔵").withStyle(net.minecraft.ChatFormatting.AQUA));
            event.getToolTip().add(Component.literal("Workspace: " + tag.getString("WorkspaceId")).withStyle(net.minecraft.ChatFormatting.GRAY));

            String label = (!stack.is(Lunex.MACHINE_CARTRIDGE.get())) ? "Program: " : "OS: ";
            if (tag.contains("ProgramName") && !tag.getString("ProgramName").isEmpty()) {
                event.getToolTip().add(Component.literal(label + tag.getString("ProgramName")).withStyle(net.minecraft.ChatFormatting.GREEN));
            } else {
                event.getToolTip().add(Component.literal(label + "未設定").withStyle(net.minecraft.ChatFormatting.RED));
            }

            if (tag.contains("InstalledUpgrades")) {
                ListTag upgrades = tag.getList("InstalledUpgrades", Tag.TAG_COMPOUND);
                if (!upgrades.isEmpty()) {
                    event.getToolTip().add(Component.literal("Upgrades: " + upgrades.size() + "/4").withStyle(net.minecraft.ChatFormatting.YELLOW));
                }
            }
        }
    }

    public static class ToolTaskData {
        public int remainingTicks;

        public ToolTaskData(int ticks) {
            this.remainingTicks = ticks;
        }
    }

    public static class HomingData extends ToolTaskData {
        public List<UUID> targetIds;
        public double trackRadius;

        public HomingData(List<UUID> ids, double tr, int ticks) {
            super(ticks);
            this.targetIds = ids;
            this.trackRadius = tr;
        }
    }

    public static class ReflectData extends ToolTaskData {
        public List<UUID> targetIds;

        public ReflectData(List<UUID> ids, int ticks) {
            super(ticks);
            this.targetIds = ids;
        }
    }

    public static class GravityData extends ToolTaskData {
        public Vec3 pos;
        public double radius;
        public double power;

        public GravityData(Vec3 p, double r, double pw, int ticks) {
            super(ticks);
            this.pos = p;
            this.radius = r;
            this.power = pw;
        }
    }

    public static class BindData extends ToolTaskData {
        public net.minecraft.world.entity.Entity target;

        public BindData(net.minecraft.world.entity.Entity t, int ticks) {
            super(ticks);
            this.target = t;
        }
    }

    public static class ParticleData extends ToolTaskData {
        public UUID targetId;
        public String particleName;

        public ParticleData(UUID id, String pName, int ticks) {
            super(ticks);
            this.targetId = id;
            this.particleName = pName;
        }
    }

    public static class DespawnData extends ToolTaskData {
        public UUID targetId;

        public DespawnData(UUID id, int ticks) {
            super(ticks);
            this.targetId = id;
        }
    }

    public static class SpinData extends ToolTaskData {
        public UUID targetId;
        public float speedX;
        public float speedY;
        public float speedZ;

        public SpinData(UUID id, float sx, float sy, float sz, int ticks) {
            super(ticks);
            this.targetId = id;
            this.speedX = sx;
            this.speedY = sy;
            this.speedZ = sz;
        }
    }

    public static class HoldData extends ToolTaskData {
        public UUID targetId;
        public double distance;

        public HoldData(UUID id, double d, int ticks) {
            super(ticks);
            this.targetId = id;
            this.distance = d;
        }
    }

    public static class SyncData extends ToolTaskData {
        public UUID followerId;
        public UUID targetId;
        public double offsetX, offsetY, offsetZ;

        public SyncData(UUID f, UUID t, double ox, double oy, double oz, int ticks) {
            super(ticks);
            this.followerId = f;
            this.targetId = t;
            this.offsetX = ox;
            this.offsetY = oy;
            this.offsetZ = oz;
        }
    }
}