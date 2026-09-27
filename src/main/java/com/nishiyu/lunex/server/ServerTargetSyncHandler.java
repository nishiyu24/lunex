package com.nishiyu.lunex.server;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.network.packet.s2c.PubSubUpdateS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "lunex")
public class ServerTargetSyncHandler {

    private static final Map<UUID, SubState> subscriptions = new ConcurrentHashMap<>();

    public static void setSubscription(ServerPlayer player, boolean needsHud, boolean needsTracker, double radius, String type, List<String> hudPaths, List<String> trackerPaths) {
        SubState state = new SubState();
        state.needsHud = needsHud;
        state.needsTracker = needsTracker;
        state.trackerRadius = radius;
        state.trackerType = type;
        state.hudNbtPaths = hudPaths;
        state.trackerNbtPaths = trackerPaths;
        subscriptions.put(player.getUUID(), state);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();

        if (player.tickCount % 10 != 0) return;

        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.getItem() != Lunex.AR_GLASSES.get()) {
            subscriptions.remove(player.getUUID());
            return;
        }

        SubState sub = subscriptions.get(player.getUUID());
        if (sub == null || (!sub.needsHud && !sub.needsTracker)) return;

        // --- メインスレッドでは安全にデータ抽出のみを行う ---
        Map<String, Object> syncData = new HashMap<>();

        if (sub.needsHud) {
            Map<String, Object> targetData = null;
            HitResult hit = getPlayerPOVHitResult(player, 24.0);

            if (hit.getType() != HitResult.Type.MISS) {
                targetData = new HashMap<>();
                CompoundTag targetNbt = new CompoundTag();

                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                    BlockState state = player.level().getBlockState(pos);
                    targetData.put("type", "block");
                    targetData.put("name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

                    BlockEntity be = player.level().getBlockEntity(pos);
                    if (be != null) targetNbt = be.saveWithFullMetadata(player.level().registryAccess());
                } else if (hit.getType() == HitResult.Type.ENTITY) {
                    net.minecraft.world.entity.Entity entity = ((EntityHitResult) hit).getEntity();
                    targetData.put("type", "entity");
                    targetData.put("name", entity.getDisplayName().getString());
                    if (entity instanceof LivingEntity le) {
                        targetData.put("hp", (double) le.getHealth());
                        targetData.put("max_hp", (double) le.getMaxHealth());
                    }
                    entity.saveWithoutId(targetNbt);
                }

                if (!sub.hudNbtPaths.isEmpty()) {
                    Map<String, Object> nbtMap = new HashMap<>();
                    for (String path : sub.hudNbtPaths) {
                        String val = extractNbtString(targetNbt, path);
                        if (val != null) nbtMap.put(path, val);
                    }
                    targetData.put("nbt", nbtMap);
                }
            }
            syncData.put("target", targetData);
        }

        if (sub.needsTracker) {
            Map<String, Object> trackerData = new HashMap<>();
            AABB box = player.getBoundingBox().inflate(sub.trackerRadius);

            Class<? extends LivingEntity> targetClass = LivingEntity.class;
            if ("player".equalsIgnoreCase(sub.trackerType)) targetClass = net.minecraft.world.entity.player.Player.class;
            else if ("monster".equalsIgnoreCase(sub.trackerType)) targetClass = net.minecraft.world.entity.monster.Monster.class;
            else if ("animal".equalsIgnoreCase(sub.trackerType)) targetClass = net.minecraft.world.entity.animal.Animal.class;

            List<? extends LivingEntity> entities = player.level().getEntitiesOfClass(targetClass, box,
                    e -> e != player && (!e.getActiveEffects().isEmpty() || !sub.trackerNbtPaths.isEmpty()));

            for (LivingEntity le : entities) {
                Map<String, Object> leData = new HashMap<>();
                Map<String, Object> effectsMap = new HashMap<>();

                int i = 0;
                for (net.minecraft.world.effect.MobEffectInstance eff : le.getActiveEffects()) {
                    net.minecraft.resources.ResourceLocation loc = BuiltInRegistries.MOB_EFFECT.getKey(eff.getEffect().value());
                    if (loc != null) effectsMap.put(String.valueOf(i++), loc.toString());
                }
                leData.put("effects", effectsMap);

                if (!sub.trackerNbtPaths.isEmpty()) {
                    CompoundTag leTag = new CompoundTag();
                    le.saveWithoutId(leTag);
                    Map<String, Object> nbtMap = new HashMap<>();
                    for (String path : sub.trackerNbtPaths) {
                        String val = extractNbtString(leTag, path);
                        if (val != null) nbtMap.put(path, val);
                    }
                    leData.put("nbt", nbtMap);
                }
                trackerData.put(String.valueOf(le.getId()), leData);
            }
            syncData.put("tracker_data", trackerData);
        }

        Thread.startVirtualThread(() -> {
            PubSubUpdateS2CPacket packet = new PubSubUpdateS2CPacket("ar_sync", true, syncData);
            if (player.getServer() != null) {
                player.getServer().execute(() -> {
                    PacketDistributor.sendToPlayer(player, packet);
                });
            }
        });
    }

    private static String extractNbtString(Tag tag, String path) {
        if (path == null || path.isEmpty() || tag == null) return getTagValueString(tag);

        String head = path;
        String tail = "";
        int dotIdx = path.indexOf('.');
        int bracketIdx = path.indexOf('[');

        int splitIdx = -1;
        if (dotIdx != -1 && bracketIdx != -1) splitIdx = Math.min(dotIdx, bracketIdx);
        else if (dotIdx != -1) splitIdx = dotIdx;
        else if (bracketIdx != -1) splitIdx = bracketIdx;

        if (splitIdx != -1) {
            if (splitIdx == 0 && bracketIdx == 0) {
                int endBracket = path.indexOf(']');
                if (endBracket != -1) {
                    int index = 0;
                    try {
                        index = Integer.parseInt(path.substring(1, endBracket));
                    } catch (Exception ignored) {
                    }
                    if (tag instanceof net.minecraft.nbt.ListTag listTag) {
                        if (index >= 0 && index < listTag.size()) {
                            String nextPath = path.substring(endBracket + 1);
                            if (nextPath.startsWith(".")) nextPath = nextPath.substring(1);
                            return extractNbtString(listTag.get(index), nextPath);
                        }
                    }
                }
                return null;
            } else {
                head = path.substring(0, splitIdx);
                tail = path.substring(splitIdx);
                if (tail.startsWith(".")) tail = tail.substring(1);
            }
        }

        if (tag instanceof net.minecraft.nbt.CompoundTag compound) {
            if (compound.contains(head)) {
                return extractNbtString(compound.get(head), tail);
            }
        }
        return null;
    }

    private static String getTagValueString(Tag tag) {
        if (tag instanceof net.minecraft.nbt.NumericTag num) return num.getAsNumber().toString();
        if (tag instanceof net.minecraft.nbt.StringTag str) return str.getAsString();
        return tag.toString();
    }

    private static HitResult getPlayerPOVHitResult(ServerPlayer player, double distance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eyePos.add(look.scale(distance));

        HitResult blockHit = player.level().clip(new ClipContext(eyePos, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double hitDist = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation().distanceTo(eyePos) : distance;

        AABB aabb = player.getBoundingBox().expandTowards(look.scale(distance)).inflate(1.0D);
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, eyePos, end, aabb, e -> !e.isSpectator() && e.isPickable());

        if (entityHit != null && eyePos.distanceTo(entityHit.getLocation()) < hitDist) {
            return entityHit;
        }
        return blockHit;
    }

    private static class SubState {
        boolean needsHud;
        boolean needsTracker;
        double trackerRadius = 24.0;
        String trackerType = "living";
        List<String> hudNbtPaths = new ArrayList<>();
        List<String> trackerNbtPaths = new ArrayList<>();
    }
}