package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ToolTaskAPI extends ToolAPIBase {

    public ToolTaskAPI(ToolServerLuaVM vm) {
        super(vm);
    }

    @LuaFunction(
            value = "指定したIDのエンティティ(follower)を、別のエンティティ(target)の相対座標(offsetX, Y, Z)に同期追従させます。(1回 50 FE消費)",
            en = "Synchronizes the movement of the entity with followerId to the relative coordinates (offsetX, Y, Z) of the target entity. (Costs 50 FE)",
            args = {"str:followerId", "str:targetId", "num:offsetX", "num:offsetY", "num:offsetZ", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startSyncTask(String followerId, String targetId, double offsetX, double offsetY, double offsetZ, int durationTicks) {
        if (vm.currentPlayer == null) return false;
        if (consumeEnergy((int) (COST_BASE * 0.05), "startSyncTask")) {
            try {
                UUID fid = UUID.fromString(followerId);
                UUID tid = UUID.fromString(targetId);
                com.nishiyu.lunex.event.ToolEventHandler.startSyncTask(vm.currentPlayer, fid, tid, offsetX, offsetY, offsetZ, durationTicks);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティ(モブやゴースト)を、プレイヤーの視線の先(クロスヘアの中央)に完全固定(ホールド)します。(1回 50 FE消費)",
            en = "Holds the specified entity completely fixed in the center of the player's crosshair. (Costs 50 FE)",
            args = {"str:id", "num:distance", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startHoldTask(String id, double distance, int durationTicks) {
        if (vm.currentPlayer == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "startHoldTask")) {
            try {
                UUID uuid = UUID.fromString(id);
                com.nishiyu.lunex.event.ToolEventHandler.startHoldTask(vm.currentPlayer, uuid, distance, durationTicks);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティに関連するアクション(ホールド、同期、回転、パーティクル)を強制キャンセルし、解放します。(消費なし)",
            en = "Forcefully cancels and releases any actions (hold, sync, spin, particles) related to the specified entity. (No cost)",
            args = {"str:id"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean cancelActionTasks(String id) {
        if (vm.currentPlayer == null) return false;
        try {
            UUID uuid = UUID.fromString(id);
            com.nishiyu.lunex.event.ToolEventHandler.cancelActionTasks(vm.currentPlayer, uuid);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを、指定した時間(tick)だけ3D回転させ続けます。(1回 50 FE消費)",
            en = "Spins the specified entity in 3D space for the specified duration (ticks). (Costs 50 FE)",
            args = {"str:id", "num:speedX", "num:speedY", "num:speedZ", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startSpinTask(String id, double speedX, double speedY, double speedZ, int durationTicks) {
        if (vm.currentPlayer == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "startSpinTask")) {
            try {
                UUID uuid = UUID.fromString(id);
                com.nishiyu.lunex.event.ToolEventHandler.startSpinTask(vm.currentPlayer, uuid, (float) speedX, (float) speedY, (float) speedZ, durationTicks);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定した飛来物(IDの配列)に対して、指定時間(tick)だけ自動ホーミング処理を付与します。",
            en = "Applies automatic homing to the specified projectiles (array of IDs) for the specified duration (ticks).",
            args = {"table:projectileIds", "num:trackRadius", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startHomingTask(LuaTable projectileIds, double trackRadius, int durationTicks) {
        if (vm.currentPlayer == null) return false;

        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= projectileIds.length(); i++) {
            LuaValue val = projectileIds.get(i);
            if (val.isstring()) {
                try {
                    ids.add(UUID.fromString(val.checkjstring()));
                } catch (Exception ignored) {
                }
            }
        }
        if (ids.isEmpty()) return false;

        int cost = (int) (ids.size() * COST_BASE * 0.5);
        if (consumeEnergy(cost, "startHomingTask")) {
            com.nishiyu.lunex.event.ToolEventHandler.startHomingTask(vm.currentPlayer, ids, trackRadius, durationTicks);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定した飛来物(IDの配列)に対して、指定時間(tick)だけ反発力を与え続けます。",
            en = "Continuously applies repulsive force to the specified projectiles (array of IDs) for the specified duration (ticks).",
            args = {"table:projectileIds", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startReflectTask(LuaTable projectileIds, int durationTicks) {
        if (vm.currentPlayer == null) return false;

        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= projectileIds.length(); i++) {
            LuaValue val = projectileIds.get(i);
            if (val.isstring()) {
                try {
                    ids.add(UUID.fromString(val.checkjstring()));
                } catch (Exception ignored) {
                }
            }
        }
        if (ids.isEmpty()) return false;

        int cost = (int) (ids.size() * COST_BASE * 0.5);
        if (consumeEnergy(cost, "startReflectTask")) {
            com.nishiyu.lunex.event.ToolEventHandler.startReflectTask(vm.currentPlayer, ids, durationTicks);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標に、周囲のエンティティを引き寄せ続ける重力場を生成します。",
            en = "Creates a gravity field at the specified coordinates that continuously pulls in surrounding entities.",
            args = {"num:x", "num:y", "num:z", "num:radius", "num:power", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean createGravityField(double x, double y, double z, double radius, double power, int durationTicks) {
        if (vm.currentPlayer == null) return false;

        if (consumeEnergy((int) (durationTicks * COST_BASE * 1.0), "createGravityField")) {
            com.nishiyu.lunex.event.ToolEventHandler.startGravityTask(vm.currentPlayer, new Vec3(x, y, z), radius, power, durationTicks);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティに、指定した時間(tick)だけパーティクルを追従発生させます。(1回 50 FE消費)",
            en = "Generates trailing particles on the specified entity for the specified duration (ticks). (Costs 50 FE)",
            args = {"str:id", "str:particleName", "num:durationTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startParticleTask(String id, String particleName, int durationTicks) {
        if (vm.currentPlayer == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.05), "startParticleTask")) {
            try {
                UUID uuid = UUID.fromString(id);
                com.nishiyu.lunex.event.ToolEventHandler.startParticleTask(vm.currentPlayer, uuid, particleName, durationTicks);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定したIDのエンティティを、指定した時間(tick)経過後に完全に消去します。(1回 10 FE消費)",
            en = "Completely despawns the specified entity after the specified delay (ticks). (Costs 10 FE)",
            args = {"str:id", "num:delayTicks"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean startDespawnTask(String id, int delayTicks) {
        if (vm.currentPlayer == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.01), "startDespawnTask")) {
            try {
                UUID uuid = UUID.fromString(id);
                com.nishiyu.lunex.event.ToolEventHandler.startDespawnTask(vm.currentPlayer, uuid, delayTicks);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}