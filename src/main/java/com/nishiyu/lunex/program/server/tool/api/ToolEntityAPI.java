package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;
import java.util.UUID;

public class ToolEntityAPI extends ToolAPIBase {

    public ToolEntityAPI(ToolServerLuaVM vm) {
        super(vm);
    }

    @LuaFunction(
            value = "指定した座標に魔法弾(Projectile)を生成し、指定した速度で発射します。所有者はプレイヤーになります。(1回 3000 FE消費)",
            en = "Spawns a magic projectile at the specified coordinates and fires it at the specified velocity. The owner will be the player. (Costs 3000 FE)",
            args = {"str:entityType", "num:x", "num:y", "num:z", "num:vx", "num:vy", "num:vz", "num:durationTicks"},
            rets = {"str:entityId"},
            isAsync = true
    )
    public LuaValue spawnProjectile(String entityTypeStr, double x, double y, double z, double vx, double vy, double vz, int durationTicks) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null || vm.currentPlayer == null) return LuaValue.NIL;

        if (consumeEnergy(COST_BASE * 3, "spawnProjectile")) {
            try {
                ResourceLocation rl = ResourceLocation.parse(entityTypeStr);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                if (type != null) {
                    Entity entity = type.create(serverLevel);
                    if (entity instanceof Projectile projectile) {
                        projectile.moveTo(x, y, z);
                        projectile.setOwner(vm.currentPlayer);
                        projectile.setDeltaMovement(vx, vy, vz);
                        serverLevel.addFreshEntity(projectile);

                        if (durationTicks > 0) {
                            com.nishiyu.lunex.event.ToolEventHandler.startDespawnTask(vm.currentPlayer, projectile.getUUID(), durationTicks);
                        }
                        return LuaValue.valueOf(projectile.getStringUUID());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "指定したIDのエンティティ(ゴースト等)の頭に、指定したアイテム(ブロック)を被せます。(1回 50 FE消費)",
            en = "Places the specified item (block) on the head of the entity (like a ghost) with the specified ID. (Costs 50 FE)",
            args = {"str:id", "str:itemId"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setHeadItem(String id, String itemId) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "setHeadItem")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e instanceof LivingEntity le) {
                    ResourceLocation rl = ResourceLocation.parse(itemId);
                    net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(rl);
                    le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(item));
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの現在の移動ベクトルに、指定した値(X,Y,Z)を加算します。(1回 500 FE消費)",
            en = "Adds the specified values (X, Y, Z) to the current movement vector of the entity with the specified ID. (Costs 500 FE)",
            args = {"str:id", "num:vx", "num:vy", "num:vz"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addVelocity(String id, double vx, double vy, double vz) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.5), "addVelocity")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.setDeltaMovement(e.getDeltaMovement().add(vx, vy, vz));
                    e.hurtMarked = true;
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標にゴースト(マーカー)を生成します。durationTicks経過後に自動消滅します。(1回 100 FE消費)",
            en = "Spawns a ghost (marker) at the specified coordinates. It will automatically despawn after durationTicks. (Costs 100 FE)",
            args = {"num:x", "num:y", "num:z", "num:durationTicks"},
            rets = {"str:entityId"},
            isAsync = true
    )
    public LuaValue spawnGhost(double x, double y, double z, int durationTicks) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        if (consumeEnergy((int) (COST_BASE * 0.1), "spawnGhost")) {
            net.minecraft.world.entity.decoration.ArmorStand ghost = new net.minecraft.world.entity.decoration.ArmorStand(serverLevel, x, y, z);
            ghost.setInvisible(true);
            ghost.setNoGravity(true);
            ghost.setInvulnerable(true);

            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            ghost.saveWithoutId(tag);
            tag.putBoolean("Marker", true);
            ghost.load(tag);

            serverLevel.addFreshEntity(ghost);
            if (durationTicks > 0)
                com.nishiyu.lunex.event.ToolEventHandler.startDespawnTask(vm.currentPlayer, ghost.getUUID(), durationTicks);
            return LuaValue.valueOf(ghost.getStringUUID());
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "プレイヤーの視線方向に透明な魔法弾を発射します。durationTicks経過後に自動消滅します。(1回 3000 FE消費)",
            en = "Fires an invisible magic projectile in the direction the player is looking. It will automatically despawn after durationTicks. (Costs 3000 FE)",
            args = {"num:durationTicks"},
            rets = {"str:entityId"},
            isAsync = true
    )
    public LuaValue shootGhostProjectile(int durationTicks) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null || vm.currentPlayer == null) return LuaValue.NIL;

        if (consumeEnergy(COST_BASE * 3, "shootGhostProjectile")) {
            net.minecraft.world.entity.projectile.Snowball ghostProjectile = new net.minecraft.world.entity.projectile.Snowball(serverLevel, vm.currentPlayer);
            ghostProjectile.setItem(net.minecraft.world.item.ItemStack.EMPTY);
            ghostProjectile.setInvisible(true);
            ghostProjectile.shootFromRotation(vm.currentPlayer, vm.currentPlayer.getXRot(), vm.currentPlayer.getYRot(), 0.0F, 1.5F, 1.0F);
            serverLevel.addFreshEntity(ghostProjectile);

            if (durationTicks > 0)
                com.nishiyu.lunex.event.ToolEventHandler.startDespawnTask(vm.currentPlayer, ghostProjectile.getUUID(), durationTicks);
            return LuaValue.valueOf(ghostProjectile.getStringUUID());
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "指定した種類(ID)のエンティティを座標に生成します。durationTicks経過後に自動消滅します。(1回 2000 FE消費)",
            en = "Spawns an entity of the specified type (ID) at the coordinates. It will automatically despawn after durationTicks. (Costs 2000 FE)",
            args = {"str:entityType", "num:x", "num:y", "num:z", "num:durationTicks"},
            rets = {"str:entityId"},
            isAsync = true
    )
    public LuaValue spawnEntity(String entityTypeStr, double x, double y, double z, int durationTicks) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        if (consumeEnergy(COST_BASE * 2, "spawnEntity")) {
            try {
                ResourceLocation rl = ResourceLocation.parse(entityTypeStr);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                if (type != null) {
                    Entity entity = type.create(serverLevel);
                    if (entity != null) {
                        entity.moveTo(x, y, z);
                        serverLevel.addFreshEntity(entity);
                        if (durationTicks > 0)
                            com.nishiyu.lunex.event.ToolEventHandler.startDespawnTask(vm.currentPlayer, entity.getUUID(), durationTicks);
                        return LuaValue.valueOf(entity.getStringUUID());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "指定した種類(ID)の飛来物(矢など)を視線方向に発射します。(1回 3000 FE消費)",
            en = "Fires a projectile (like an arrow) of the specified type (ID) in the direction of sight. (Costs 3000 FE)",
            args = {"str:entityType", "num:speed", "num:durationTicks"},
            rets = {"str:entityId"},
            isAsync = true
    )
    public LuaValue shootEntity(String entityTypeStr, double speed, int durationTicks) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null || vm.currentPlayer == null) return LuaValue.NIL;

        if (consumeEnergy(COST_BASE * 3, "shootEntity")) {
            try {
                ResourceLocation rl = ResourceLocation.parse(entityTypeStr);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                if (type != null) {
                    Entity entity = type.create(serverLevel);
                    if (entity != null) {
                        entity.moveTo(vm.currentPlayer.getX(), vm.currentPlayer.getEyeY(), vm.currentPlayer.getZ());
                        if (entity instanceof Projectile projectile) {
                            projectile.setOwner(vm.currentPlayer);
                            projectile.shootFromRotation(vm.currentPlayer, vm.currentPlayer.getXRot(), vm.currentPlayer.getYRot(), 0.0F, (float) speed, 1.0F);
                        } else {
                            Vec3 look = vm.currentPlayer.getLookAngle();
                            entity.setDeltaMovement(look.scale(speed));
                        }
                        serverLevel.addFreshEntity(entity);
                        if (durationTicks > 0)
                            com.nishiyu.lunex.event.ToolEventHandler.startDespawnTask(vm.currentPlayer, entity.getUUID(), durationTicks);
                        return LuaValue.valueOf(entity.getStringUUID());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "周囲のエンティティを検索し、情報を配列で返します。(半径1マス 100 FE消費)",
            en = "Scans surrounding entities and returns their info in an array. (Costs 100 FE per 1 block radius)",
            args = {"num:radius"},
            rets = {"table:entities"},
            isAsync = true
    )
    public LuaValue scanEntities(double radius) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        float r = (float) Math.min(radius, 30.0);
        if (!consumeEnergy((int) (r * COST_BASE * 0.1), "scanEntities")) return LuaValue.NIL;

        AABB aabb = vm.currentPlayer.getBoundingBox().inflate(r);
        List<Entity> entities = serverLevel.getEntities(vm.currentPlayer, aabb, e -> e instanceof LivingEntity);

        LuaTable resultTable = new LuaTable();
        int index = 1;
        for (Entity e : entities) {
            LivingEntity le = (LivingEntity) e;
            LuaTable et = new LuaTable();
            et.set("id", le.getStringUUID());
            et.set("type", BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).toString());
            et.set("x", le.getX());
            et.set("y", le.getY());
            et.set("z", le.getZ());
            et.set("hp", le.getHealth());
            et.set("max_hp", le.getMaxHealth());
            et.set("name", le.getDisplayName().getString());
            et.set("distance", vm.currentPlayer.distanceTo(le));
            resultTable.set(index++, et);
        }
        return resultTable;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの詳細データを取得します。(100 FE消費)",
            en = "Gets detailed data of the entity with the specified ID. (Costs 100 FE)",
            args = {"str:id"},
            rets = {"table:info"},
            isAsync = false
    )
    public LuaValue inspect(String id) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        if (consumeEnergy((int) (COST_BASE * 0.1), "inspect")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    LuaTable t = new LuaTable();
                    t.set("id", e.getStringUUID());
                    t.set("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
                    t.set("x", e.getX());
                    t.set("y", e.getY());
                    t.set("z", e.getZ());
                    t.set("name", e.getDisplayName().getString());
                    if (e instanceof LivingEntity living) {
                        t.set("hp", living.getHealth());
                        t.set("max_hp", living.getMaxHealth());
                        t.set("armor", living.getArmorValue());
                    }
                    return t;
                }
            } catch (Exception ignored) {
            }
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの視線の先にあるブロック座標(x,y,z)を取得します。(100 FE消費)",
            en = "Gets the block coordinates (x,y,z) in the line of sight of the entity with the specified ID. (Costs 100 FE)",
            args = {"str:id", "num:maxDistance"},
            rets = {"table:pos"},
            isAsync = false
    )
    public LuaValue getLookPos(String id, double maxDistance) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        if (consumeEnergy((int) (COST_BASE * 0.1), "getLookPos")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    float dist = (float) Math.min(maxDistance, 50.0);
                    Vec3 eyePos = e.getEyePosition();
                    Vec3 look = e.getLookAngle();
                    Vec3 endPos = eyePos.add(look.x * dist, look.y * dist, look.z * dist);
                    ClipContext ctx = new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, e);
                    BlockHitResult hit = e.level().clip(ctx);

                    if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = hit.getBlockPos();
                        LuaTable table = new LuaTable();
                        table.set("x", pos.getX());
                        table.set("y", pos.getY());
                        table.set("z", pos.getZ());
                        return table;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "視線の先にあるエンティティ(モブ等)のIDを取得します。何もなければnilを返します。(100 FE消費)",
            en = "Gets the ID of the entity (like a mob) in the line of sight. Returns nil if none. (Costs 100 FE)",
            args = {"num:maxDistance"},
            rets = {"str:entityId"},
            isAsync = false
    )
    public LuaValue getLookEntity(double maxDistance) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null || vm.currentPlayer == null) return LuaValue.NIL;

        if (consumeEnergy((int) (COST_BASE * 0.1), "getLookEntity")) {
            Vec3 eyePos = vm.currentPlayer.getEyePosition();
            Vec3 look = vm.currentPlayer.getLookAngle();
            Vec3 endPos = eyePos.add(look.scale(maxDistance));
            AABB aabb = vm.currentPlayer.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.0);

            Entity closest = null;
            double minDist = Double.MAX_VALUE;

            for (Entity e : serverLevel.getEntities(vm.currentPlayer, aabb, e -> (e instanceof LivingEntity || e instanceof net.minecraft.world.entity.decoration.ArmorStand) && e != vm.currentPlayer)) {
                AABB entityAabb = e.getBoundingBox().inflate(0.3);
                java.util.Optional<Vec3> opt = entityAabb.clip(eyePos, endPos);
                if (opt.isPresent()) {
                    double dist = eyePos.distanceToSqr(opt.get());
                    if (dist < minDist) {
                        minDist = dist;
                        closest = e;
                    }
                }
            }
            if (closest != null) return LuaValue.valueOf(closest.getStringUUID());
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "周囲の飛来物(矢や魔法弾)を検索し情報を返します。(半径1マス 100 FE消費)",
            en = "Scans surrounding projectiles (arrows or magic bullets) and returns their info. (Costs 100 FE per 1 block radius)",
            args = {"num:radius", "bool:onlyOwn"},
            rets = {"table:projectiles"},
            isAsync = true
    )
    public LuaValue getProjectiles(double radius, boolean onlyOwn) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        float r = (float) Math.min(radius, 50.0);
        if (!consumeEnergy((int) (r * COST_BASE * 0.1), "getProjectiles")) return LuaValue.NIL;

        AABB aabb = vm.currentPlayer.getBoundingBox().inflate(r);
        List<Projectile> projectiles = serverLevel.getEntitiesOfClass(Projectile.class, aabb);

        LuaTable resultTable = new LuaTable();
        int index = 1;
        for (Projectile p : projectiles) {
            if (onlyOwn && p.getOwner() != vm.currentPlayer) continue;
            LuaTable pt = new LuaTable();
            pt.set("id", p.getStringUUID());
            pt.set("type", BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).toString());
            pt.set("x", p.getX());
            pt.set("y", p.getY());
            pt.set("z", p.getZ());
            pt.set("vx", p.getDeltaMovement().x);
            pt.set("vy", p.getDeltaMovement().y);
            pt.set("vz", p.getDeltaMovement().z);
            pt.set("is_own", p.getOwner() == vm.currentPlayer ? LuaValue.TRUE : LuaValue.FALSE);
            resultTable.set(index++, pt);
        }
        return resultTable;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの移動ベクトル(X,Y,Z)を上書きします。(1回 500 FE消費)",
            en = "Overwrites the movement vector (X, Y, Z) of the entity with the specified ID. (Costs 500 FE)",
            args = {"str:id", "num:vx", "num:vy", "num:vz"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setVelocity(String id, double vx, double vy, double vz) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.5), "setVelocity")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.setDeltaMovement(new Vec3(vx, vy, vz));
                    e.hurtMarked = true;
                    if (e instanceof Projectile p) {
                        Vec3 dir = p.getDeltaMovement().normalize();
                        double d0 = dir.x;
                        double d1 = dir.y;
                        double d2 = dir.z;
                        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
                        p.setYRot((float) (Math.atan2(d0, d2) * (180F / Math.PI)));
                        p.setXRot((float) (Math.atan2(d1, d3) * (180F / Math.PI)));
                        p.yRotO = p.getYRot();
                        p.xRotO = p.getXRot();
                    }
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを絶対座標(x,y,z)へテレポートさせます。(1マス 5000 FE消費)",
            en = "Teleports the entity with the specified ID to absolute coordinates (x,y,z). (Costs 5000 FE per block)",
            args = {"str:id", "num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean teleportTo(String id, double x, double y, double z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        try {
            Entity e = serverLevel.getEntity(UUID.fromString(id));
            if (e != null) {
                double dist = e.position().distanceTo(new Vec3(x, y, z));
                if (consumeEnergy((int) (dist * COST_BASE * 5), "teleportTo")) {
                    BlockPos beforePos = e.blockPosition();
                    e.teleportTo(x, y, z);
                    serverLevel.playSound(null, beforePos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    serverLevel.playSound(null, e.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを相対座標(dx,dy,dz)へテレポートさせます。(1マス 5000 FE消費)",
            en = "Teleports the entity with the specified ID to relative coordinates (dx,dy,dz). (Costs 5000 FE per block)",
            args = {"str:id", "num:dx", "num:dy", "num:dz"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean teleportRelative(String id, double dx, double dy, double dz) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (consumeEnergy((int) (dist * COST_BASE * 5), "teleportRelative")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    BlockPos beforePos = e.blockPosition();
                    e.teleportTo(e.getX() + dx, e.getY() + dy, e.getZ() + dz);
                    serverLevel.playSound(null, beforePos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    serverLevel.playSound(null, e.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを向いている方向にダッシュ(射出)させます。(威力1につき 3000 FE消費)",
            en = "Dashes (launches) the entity with the specified ID in the direction it is facing. (Costs 3000 FE per power unit)",
            args = {"str:id", "num:power"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean launch(String id, double power) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        float p = (float) Math.min(power, 3.0);
        if (consumeEnergy((int) (p * COST_BASE * 3), "launch")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    Vec3 look = e.getLookAngle();
                    e.setDeltaMovement(look.scale(p));
                    e.hurtMarked = true;
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを指定した角度(yaw)へノックバックさせます。(威力1につき 1500 FE消費)",
            en = "Knocks back the entity with the specified ID at the specified angle (yaw). (Costs 1500 FE per strength unit)",
            args = {"str:id", "num:yaw", "num:strength"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addKnockback(String id, double yaw, double strength) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        float s = (float) Math.min(strength, 3.0);
        if (consumeEnergy((int) (s * COST_BASE * 1.5), "addKnockback")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e instanceof LivingEntity le) {
                    double radians = Math.toRadians(yaw);
                    le.knockback(s, Math.sin(radians), -Math.cos(radians));
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの体力を回復します。(回復量1につき 5000 FE消費)",
            en = "Heals the entity with the specified ID. (Costs 5000 FE per heal point)",
            args = {"str:id", "num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean heal(String id, double amount) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        float healAmount = (float) Math.min(amount, 20.0);
        if (consumeEnergy((int) (healAmount * COST_BASE * 5), "heal")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e instanceof LivingEntity le) {
                    le.heal(healAmount);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティに直接魔法ダメージを与えます。(1ダメージにつき 1000 FE消費)",
            en = "Deals direct magic damage to the entity with the specified ID. (Costs 1000 FE per damage point)",
            args = {"str:id", "num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean damage(String id, double amount) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        float dmgAmount = (float) Math.min(amount, 20.0);
        if (consumeEnergy((int) (dmgAmount * COST_BASE), "damage")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.hurt(e.damageSources().magic(), dmgAmount);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティにポーション効果を付与します。(1秒 200*(強度+1) FE消費)",
            en = "Applies a potion effect to the entity with the specified ID. (Costs 200*(amplifier+1) FE per second)",
            args = {"str:id", "str:effectId", "num:seconds", "num:amplifier"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addPotionEffect(String id, String effectId, double seconds, int amplifier) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        try {
            ResourceLocation rl = ResourceLocation.parse(effectId);
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
            if (effect != null) {
                int amp = Math.min(Math.max(amplifier, 0), 4);
                float s = (float) Math.min(seconds, 60.0);
                int cost = (int) (s * COST_BASE * 0.2 * (amp + 1));

                if (consumeEnergy(cost, "addPotionEffect")) {
                    Entity e = serverLevel.getEntity(UUID.fromString(id));
                    if (e instanceof LivingEntity le) {
                        le.addEffect(new MobEffectInstance(effect, (int) (s * 20), amp));
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを燃やします。(1秒 500 FE消費)",
            en = "Sets the entity with the specified ID on fire. (Costs 500 FE per second)",
            args = {"str:id", "num:seconds"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean ignite(String id, double seconds) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        float s = (float) Math.min(seconds, 10.0);
        if (consumeEnergy((int) (s * COST_BASE * 0.5), "ignite")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.setRemainingFireTicks((int) (s * 20));
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDの飛来物を消去(迎撃)します。(1回 500 FE消費)",
            en = "Removes (intercepts) a projectile with the specified ID. (Costs 500 FE)",
            args = {"str:id"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean removeProjectile(String id) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.5), "removeProjectile")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e instanceof Projectile p) {
                    p.discard();
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, p.getX(), p.getY(), p.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの透明状態(見えなくなるか)を変更します。(1回 50 FE消費)",
            en = "Changes the invisibility status of the entity with the specified ID. (Costs 50 FE)",
            args = {"str:id", "bool:invisible"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setInvisible(String id, boolean invisible) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "setInvisible")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.setInvisible(invisible);
                    if (invisible && e instanceof net.minecraft.world.entity.projectile.ThrowableItemProjectile itemProjectile) {
                        itemProjectile.setItem(net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの無敵状態(ダメージを受けなくなるか)を変更します。(1回 1000 FE消費)",
            en = "Changes the invulnerability status of the entity with the specified ID. (Costs 1000 FE)",
            args = {"str:id", "bool:invulnerable"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setInvulnerable(String id, boolean invulnerable) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy(COST_BASE, "setInvulnerable")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.setInvulnerable(invulnerable);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティ(防具立て等)のマーカー状態(当たり判定の完全消失)を変更します。(1回 50 FE消費)",
            en = "Changes the marker status (complete removal of collision box) of the entity (e.g., armor stand) with the specified ID. (Costs 50 FE)",
            args = {"str:id", "bool:marker"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setMarker(String id, boolean marker) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "setMarker")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    e.saveWithoutId(tag);
                    tag.putBoolean("Marker", marker);
                    e.load(tag);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティの重力を無効化(または有効化)します。(1回 50 FE消費)",
            en = "Disables (or enables) gravity for the entity with the specified ID. (Costs 50 FE)",
            args = {"str:id", "bool:noGravity"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setNoGravity(String id, boolean noGravity) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "setNoGravity")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null) {
                    e.setNoGravity(noGravity);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティのスケール（大きさ）を変更します。(1回 3000 FE消費)",
            en = "Changes the scale (size) of the entity with the specified ID. (Costs 3000 FE)",
            args = {"str:id", "num:scale"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setScale(String id, double scale) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy(COST_BASE * 3, "setScale")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e instanceof LivingEntity le) {
                    AttributeInstance attr = le.getAttribute(Attributes.SCALE);
                    if (attr != null) {
                        double clampedScale = Math.max(0.1, Math.min(scale, 10.0));
                        attr.setBaseValue(clampedScale);
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "passengerId のエンティティを、vehicleId のエンティティに乗車させます。(1回 100 FE消費)",
            en = "Makes the entity with passengerId ride the entity with vehicleId. (Costs 100 FE)",
            args = {"str:passengerId", "str:vehicleId"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startRiding(String passengerId, String vehicleId) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.1), "startRiding")) {
            try {
                Entity passenger = serverLevel.getEntity(UUID.fromString(passengerId));
                Entity vehicle = serverLevel.getEntity(UUID.fromString(vehicleId));
                if (passenger != null && vehicle != null && passenger != vehicle) {
                    passenger.startRiding(vehicle, true);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティが何かに乗っている場合、強制的に降ろします。(1回 50 FE消費)",
            en = "Forces the entity with the specified ID to dismount if it is riding something. (Costs 50 FE)",
            args = {"str:id"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean stopRiding(String id) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "stopRiding")) {
            try {
                Entity e = serverLevel.getEntity(UUID.fromString(id));
                if (e != null && e.isPassenger()) {
                    e.stopRiding();
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}