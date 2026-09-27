package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.UUID;

public class ToolWorldAPI extends ToolAPIBase {

    private static final java.util.Map<UUID, BlockState> PICKED_BLOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    public ToolWorldAPI(ToolServerLuaVM vm) {
        super(vm);
    }

    @LuaFunction(
            value = "指定座標のブロックを引き抜き、ゴーストのIDを返します。ブロックの向きなどの状態も保持されます。(5000 FE消費)",
            en = "Picks up the block at the specified coordinates and returns the ghost ID. Block states like direction are preserved. (Costs 5000 FE)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"str:ghostId"},
            isAsync = true
    )
    public LuaValue pickupBlock(int x, int y, int z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        if (consumeEnergy(COST_BASE * 5, "pickupBlock")) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = serverLevel.getBlockState(pos);

            if (state.isAir() || state.getDestroySpeed(serverLevel, pos) < 0 || serverLevel.getBlockEntity(pos) != null) {
                return LuaValue.NIL;
            }

            serverLevel.removeBlock(pos, false);

            net.minecraft.world.entity.decoration.ArmorStand ghost = new net.minecraft.world.entity.decoration.ArmorStand(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            ghost.setInvisible(true);
            ghost.setNoGravity(true);
            ghost.setInvulnerable(true);

            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            ghost.saveWithoutId(tag);
            tag.putBoolean("Marker", true);
            ghost.load(tag);

            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(state.getBlock().asItem());
            if (!stack.isEmpty()) {
                ghost.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, stack);
            }

            serverLevel.addFreshEntity(ghost);

            UUID ghostId = ghost.getUUID();
            PICKED_BLOCKS.put(ghostId, state);

            return LuaValue.valueOf(ghostId.toString());
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "引き抜いたブロック(ゴースト)を指定座標に再設置し、ゴーストを消去します。(2000 FE消費)",
            en = "Replaces the picked-up block (ghost) at the specified coordinates and removes the ghost. (Costs 2000 FE)",
            args = {"str:ghostId", "num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean placePickedBlock(String ghostIdStr, int x, int y, int z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy(COST_BASE * 2, "placePickedBlock")) {
            try {
                UUID ghostId = UUID.fromString(ghostIdStr);

                BlockState state = PICKED_BLOCKS.remove(ghostId);

                if (state != null) {
                    BlockPos pos = new BlockPos(x, y, z);

                    BlockState targetState = serverLevel.getBlockState(pos);
                    if (!targetState.canBeReplaced()) return false;

                    serverLevel.setBlock(pos, state, 3);

                    Entity ghost = serverLevel.getEntity(ghostId);
                    if (ghost != null) {
                        ghost.discard();
                    }

                    serverLevel.playSound(null, pos, state.getSoundType(serverLevel, pos, null).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標のブロックのID(名前)を取得します。(100 FE消費)",
            en = "Gets the ID (name) of the block at the specified coordinates. (Costs 100 FE)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"str:blockId"},
            isAsync = false
    )
    public String getBlockName(int x, int y, int z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return "minecraft:air";

        if (consumeEnergy((int) (COST_BASE * 0.1), "getBlockName")) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = serverLevel.getBlockState(pos);
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }
        return "minecraft:air";
    }

    @LuaFunction(
            value = "指定した座標のブロックを消去(空気に)します。アイテム化はしません。(2000FE + 距離*1000FE 消費)",
            en = "Removes the block at the specified coordinates (turns to air). Does not drop as an item. (Costs 2000 FE + 1000 FE per block distance)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean removeBlock(int x, int y, int z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = serverLevel.getBlockState(pos);

        if (state.isAir() || state.getDestroySpeed(serverLevel, pos) < 0 || serverLevel.getBlockEntity(pos) != null)
            return false;

        double dist = vm.currentPlayer.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
        int distanceBlocks = (int) Math.sqrt(dist);
        int cost = (COST_BASE * 2) + (distanceBlocks * COST_BASE);

        if (consumeEnergy(cost, "removeBlock")) {
            serverLevel.removeBlock(pos, false);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標のブロックを破壊します。(on_block_break内専用。2000FE + 距離*1000FE 消費)",
            en = "Breaks the block at the specified coordinates. (Only usable within on_block_break. Costs 2000 FE + 1000 FE per block distance)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean breakBlock(int x, int y, int z) {
        if (!"on_block_break".equals(vm.currentEventName)) {
            chat("§c[警告] breakBlock はブロック破壊時(on_block_break)にしか使用できません！");
            return false;
        }
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = serverLevel.getBlockState(pos);

        if (state.isAir() || state.getDestroySpeed(serverLevel, pos) < 0) return false;

        double dist = vm.currentPlayer.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
        int distanceBlocks = (int) Math.sqrt(dist);
        int cost = (COST_BASE * 2) + (distanceBlocks * COST_BASE);

        if (consumeEnergy(cost, "breakBlock")) {
            serverLevel.destroyBlock(pos, true, null);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標に、オフハンド(左手)に持っているブロックを設置します。(3000 FE消費)",
            en = "Places the block held in the offhand at the specified coordinates. (Costs 3000 FE)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean placeBlock(int x, int y, int z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        ItemStack offhandStack = vm.currentPlayer.getOffhandItem();
        if (offhandStack.isEmpty() || !(offhandStack.getItem() instanceof BlockItem blockItem)) {
            chat("§cオフハンド(左手)に設置するブロックを持っていません！");
            return false;
        }

        BlockPos pos = new BlockPos(x, y, z);
        BlockState targetState = serverLevel.getBlockState(pos);
        if (!targetState.isAir() && targetState.getFluidState().isEmpty()) return false;

        if (consumeEnergy(COST_BASE * 3, "placeBlock")) {
            if (!vm.currentPlayer.isCreative()) offhandStack.shrink(1);
            BlockState placeState = blockItem.getBlock().defaultBlockState();
            serverLevel.setBlockAndUpdate(pos, placeState);
            serverLevel.playSound(null, pos, placeState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標に爆発を起こします。(威力1につき 10000 FE消費)",
            en = "Creates an explosion at the specified coordinates. (Costs 10000 FE per power unit)",
            args = {"num:x", "num:y", "num:z", "num:power"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean createExplosion(double x, double y, double z, double power) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        float p = (float) Math.min(power, 5.0);
        if (consumeEnergy((int) (p * COST_BASE * 10), "createExplosion")) {
            serverLevel.explode(vm.currentPlayer, x, y, z, p, Level.ExplosionInteraction.TNT);
            return true;
        }
        return false;
    }

    @LuaFunction(
            value = "周囲の指定したブロックの座標をリスト(配列)で返します。(探索1マス10FE、発見1つ500FE消費)",
            en = "Scans for the specified block around the player and returns an array of coordinates. (Costs 10 FE per searched block, 500 FE per found block)",
            args = {"num:radius", "str:blockId"},
            rets = {"table:positions"},
            isAsync = true
    )
    public LuaValue scanBlocks(int radius, String blockId) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return LuaValue.NIL;

        int r = Math.min(radius, 10);
        int searchCost = (int) ((r * r * r) * (COST_BASE * 0.01));

        if (!consumeEnergy(searchCost, "scanBlocks 探索")) return LuaValue.NIL;

        LuaTable resultTable = new LuaTable();
        int foundCount = 0;

        try {
            ResourceLocation rl = ResourceLocation.parse(blockId);
            Block targetBlock = BuiltInRegistries.BLOCK.get(rl);
            BlockPos center = vm.currentPlayer.blockPosition();
            int foundCost = (int) (COST_BASE * 0.5);

            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos checkPos = center.offset(x, y, z);
                        if (serverLevel.getBlockState(checkPos).is(targetBlock)) {
                            if (consumeEnergy(foundCost, "scanBlocks 発見")) {
                                LuaTable posTable = new LuaTable();
                                posTable.set("x", checkPos.getX());
                                posTable.set("y", checkPos.getY());
                                posTable.set("z", checkPos.getZ());
                                resultTable.set(++foundCount, posTable);
                            } else {
                                chat("§eエネルギー切れのため、スキャンを中断しました。");
                                return resultTable;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            chat("§cブロックIDが正しくありません: " + blockId);
        }
        return resultTable;
    }

    @LuaFunction(
            value = "指定した座標の植物に骨粉効果を与えます。(1回 500 FE消費)",
            en = "Applies a bonemeal effect to the plant at the specified coordinates. (Costs 500 FE)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean applyBonemeal(int x, int y, int z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy((int) (COST_BASE * 0.5), "applyBonemeal")) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = serverLevel.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bonemealable) {
                if (bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {
                    bonemealable.performBonemeal(serverLevel, serverLevel.random, pos, state);
                    serverLevel.levelEvent(2005, pos, 0);
                    return true;
                }
            }
        }
        return false;
    }

    @LuaFunction(
            value = "指定した座標に雷を落とします。(1回 50000 FE消費)",
            en = "Strikes lightning at the specified coordinates. (Costs 50000 FE)",
            args = {"num:x", "num:y", "num:z"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean strikeLightning(double x, double y, double z) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel == null) return false;

        if (consumeEnergy(COST_BASE * 50, "strikeLightning")) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(x, y, z);
                serverLevel.addFreshEntity(lightning);
                return true;
            }
        }
        return false;
    }
}