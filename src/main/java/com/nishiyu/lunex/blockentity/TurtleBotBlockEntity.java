package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.TurtleBotBlock;
import com.nishiyu.lunex.machine.turtle.TurtleCore;
import com.nishiyu.lunex.program.server.turtle.TurtleServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TurtleBotBlockEntity extends BlockEntity {

    private TurtleCore core;
    private static final int DEFAULT_MAX_ENERGY = 100000;
    private static final int DEFAULT_RECEIVE_RATE = 1000;
    private static final int DEFAULT_MAINTAIN_COST = 5;

    public final IEnergyStorage feStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (core == null || core.isDisposed()) return 0;
            int space = DEFAULT_MAX_ENERGY - core.energy;
            if (space <= 0) return 0;
            int maxTransfer = Math.min(maxReceive, DEFAULT_RECEIVE_RATE);
            int received = Math.min(maxTransfer, space);
            if (!simulate) {
                core.energy += received;
                setChanged();
            }
            return received;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return core != null ? core.energy : 0; }
        @Override public int getMaxEnergyStored() { return DEFAULT_MAX_ENERGY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public float renderOffsetX = 0, renderOffsetY = 0, renderOffsetZ = 0;
    public float renderRotDiff = 0;
    public long animDurationMs = 0;
    public long clientAnimStartTime = 0;

    public boolean hasPendingMove = false;
    public BlockPos pendingMoveTarget = null;
    public boolean hasPendingTurn = false;
    public Direction pendingTurnFacing = null;

    public TurtleBotBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.TURTLE_BOT_BE.get(), pos, state);
        this.core = new TurtleCore(UUID.randomUUID());
        this.core.bind(this);
    }

    public TurtleCore getCore() { return this.core; }

    public void takeOverCore(TurtleCore existingCore) {
        this.core = existingCore;
        this.core.bind(this);
    }

    public String getProgramName() { return this.core.programName; }
    public void setProgramName(String name) {
        boolean isChanged = (this.core.programName == null || !this.core.programName.equals(name));
        this.core.programName = name;
        this.setChanged();
        this.sync();
        if (isChanged && this.core.vm != null) {
            this.core.vm.isWipingMemory = true;
            this.core.persistentData.remove("AutoMemory");
        }
    }

    public String getWorkspaceId() { return this.core.workspaceId; }

    public static void tick(Level level, BlockPos pos, BlockState state, TurtleBotBlockEntity entity) {
        if (level.isClientSide || entity.core == null || entity.core.isDisposed()) return;

        if (level.getGameTime() % 20 == 0) {
            if (entity.core.vm != null && entity.core.vm.isRunning) {
                entity.core.energy -= DEFAULT_MAINTAIN_COST;
                if (entity.core.energy <= 0) {
                    entity.core.energy = 0;
                    entity.setRunning(false);
                }
                entity.setChanged();
                entity.sync();
            }
        }

        if (entity.core.vm != null) {
            entity.core.vm.tick();
            if (!entity.core.vm.isRunning) {
                if (entity.core.tasks.isBreakingBlock && entity.core.tasks.breakingPos != null) {
                    level.destroyBlockProgress(entity.hashCode(), entity.core.tasks.breakingPos, -1);
                    entity.core.tasks.resetBreakingState();
                }
            } else {
                entity.core.vm.triggerEvent("on_tick");
            }
        }
    }

    public void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void onRedstoneUpdate(boolean isPowered) {
        if (this.core == null || this.core.isDisposed()) return;
        if (isPowered && !this.core.wasPowered) {
            if (this.core.wakeOnRedstone && !this.core.vm.isRunning && this.core.programName != null && !this.core.programName.isEmpty()) {
                this.core.vm.startProgram(this.core.programName);
            }
            this.core.vm.triggerEvent("on_redstone", isPowered);
        } else if (!isPowered && this.core.wasPowered) {
            this.core.vm.triggerEvent("on_redstone", isPowered);
        }
        this.core.wasPowered = isPowered;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.core != null && this.core.vm != null) {
            this.core.vm.workspaceId = this.core.workspaceId;
            if (this.level != null && !this.level.isClientSide && this.core.wasRunning && this.core.programName != null && !this.core.programName.isEmpty()) {
                if (!this.core.vm.isRunning) {
                    this.core.vm.startProgram(this.core.programName);
                    this.core.vm.triggerEvent("on_start");
                }
            }
            this.core.wasRunning = false;
        }
    }

    @Override
    public void setRemoved() {
        if (this.core != null && this.core.vm != null && !this.core.isDisposed()) {
            if (this.core.vm.isRunning) this.core.wasRunning = true;
            this.core.vm.saveMemoryToNBT();
        }
        super.setRemoved();
    }

    public boolean isRunning() { return this.core != null && this.core.vm != null && this.core.vm.isRunning; }

    public void setRunning(boolean running) {
        if (this.core != null && this.core.vm != null) {
            this.core.vm.workspaceId = this.core.workspaceId;
            boolean was = this.core.vm.isRunning;
            this.core.wasRunning = running;
            if (this.level != null && !this.level.isClientSide) {
                if (running) {
                    if (was) this.core.vm.stopProgram();
                    if (this.core.programName != null && !this.core.programName.isEmpty()) {
                        String runName = this.core.programName.replace(".lua", "");
                        this.core.vm.startProgram(runName);
                    }
                } else if (was) {
                    this.core.vm.stopProgram();
                }
                this.sync();
            } else {
                this.core.vm.isRunning = running;
            }
        }
    }

    public String getMachineLabel() { return this.core.persistentData.getString("NetworkTag"); }
    public void setMachineLabel(String label) {
        String current = this.core.persistentData.getString("NetworkTag");
        String newLabel = label != null ? label : "";
        if (!current.equals(newLabel)) {
            this.core.persistentData.putString("NetworkTag", newLabel);
            this.setChanged();
            this.sync();
        }
    }

    public boolean consumeActionEnergy(int baseCost) {
        if (this.core.energy >= baseCost) {
            this.core.energy -= baseCost;
            this.setChanged();
            return true;
        }
        return false;
    }

    public void startAnimation(float dx, float dy, float dz, float dRot, long durationMs) {
        this.renderOffsetX = dx;
        this.renderOffsetY = dy;
        this.renderOffsetZ = dz;
        this.renderRotDiff = dRot;
        this.animDurationMs = durationMs;
    }

    public void executePendingActions(net.minecraft.world.level.Level level, BlockPos pos) {
        if (this.isRemoved() || this.core == null || this.core.isDisposed()) return;

        if (this.hasPendingTurn) {
            this.hasPendingTurn = false;
            BlockState currentState = this.getBlockState();
            BlockState newState = currentState.setValue(TurtleBotBlock.FACING, this.pendingTurnFacing);
            level.setBlock(pos, newState, 3);
            this.renderRotDiff = 0; this.animDurationMs = 0;
            level.sendBlockUpdated(pos, currentState, newState, 3);
        }

        if (this.hasPendingMove && this.pendingMoveTarget != null) {
            this.hasPendingMove = false;
            BlockPos targetPos = this.pendingMoveTarget;
            BlockState currentState = this.getBlockState();

            if (!level.getBlockState(targetPos).canBeReplaced()) {
                this.renderOffsetX = 0; this.renderOffsetY = 0; this.renderOffsetZ = 0;
                this.animDurationMs = 0;
                level.sendBlockUpdated(pos, currentState, currentState, 3);
                return;
            }

            TurtleServerLuaVM activeVM = this.core.vm;
            if (activeVM != null) activeVM.isRelocating = true;
            this.core.isRelocating = true; // ドロップ保護

            try {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(targetPos, currentState, 3);

                BlockEntity newBe = level.getBlockEntity(targetPos);
                if (newBe instanceof TurtleBotBlockEntity newTurtle) {
                    newTurtle.takeOverCore(this.core);

                    if (newTurtle.core.vm != null) newTurtle.core.vm.isRelocating = false;
                    newTurtle.core.wasRunning = true;
                    newTurtle.core.isRelocating = false;

                    newTurtle.renderOffsetX = 0; newTurtle.renderOffsetY = 0; newTurtle.renderOffsetZ = 0; newTurtle.renderRotDiff = 0;
                    newTurtle.animDurationMs = 0;
                    level.sendBlockUpdated(targetPos, Blocks.AIR.defaultBlockState(), currentState, 3);
                } else {
                    if (activeVM != null) { activeVM.isRelocating = false; activeVM.stopProgram(); }
                    this.core.isRelocating = false;
                }
            } finally {
                if (activeVM != null) activeVM.isRelocating = false;
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.core != null) {
            tag.putUUID("MachineId", this.core.machineId);
            tag.putString("WorkspaceId", this.core.workspaceId != null ? this.core.workspaceId : "");
            tag.putString("ProgramName", this.core.programName);
            tag.putBoolean("IsPrivateMode", this.core.isPrivateMode);
            tag.putBoolean("WakeOnRedstone", this.core.wakeOnRedstone);
            tag.putBoolean("DebugChat", this.core.debugChat);
            if (this.core.ownerUUID != null) tag.putUUID("OwnerUUID", this.core.ownerUUID);

            ListTag programsTag = new ListTag();
            for (String p : this.core.installedPrograms) programsTag.add(StringTag.valueOf(p));
            tag.put("InstalledPrograms", programsTag);

            tag.putBoolean("WasPowered", this.core.wasPowered);
            CompoundTag rsTag = new CompoundTag();
            for (Direction dir : Direction.values()) rsTag.putInt(dir.getName(), this.core.redstoneOutputs.getOrDefault(dir, 0));
            tag.put("RedstoneOutputs", rsTag);

            tag.put("Inventory", this.core.itemHandler.serializeNBT(registries));
            tag.putIntArray("LastItemCounts", this.core.lastItemCounts);

            tag.putBoolean("WasRunning", this.core.vm != null && this.core.vm.isRunning || this.core.wasRunning);

            if (this.core.vm != null) {
                this.core.vm.saveMemoryToNBT();
                if (this.core.vm.memoryBuffer != null) this.core.persistentData.put("AutoMemory", this.core.vm.memoryBuffer.copy());
            }
            tag.put("PersistentData", this.core.persistentData);
            tag.putInt("Energy", this.core.energy);
            this.core.tasks.save(tag);
        }

        tag.putFloat("AnimDx", this.renderOffsetX); tag.putFloat("AnimDy", this.renderOffsetY);
        tag.putFloat("AnimDz", this.renderOffsetZ); tag.putFloat("AnimDRot", this.renderRotDiff);
        tag.putLong("AnimDurMs", this.animDurationMs);

        tag.putBoolean("PendingMove", this.hasPendingMove);
        if (this.pendingMoveTarget != null) tag.putLong("PendingTarget", this.pendingMoveTarget.asLong());
        tag.putBoolean("PendingTurn", this.hasPendingTurn);
        if (this.pendingTurnFacing != null) tag.putString("PendingTurnFacing", this.pendingTurnFacing.getName());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        UUID loadedId = tag.contains("MachineId") ? tag.getUUID("MachineId") : UUID.randomUUID();
        if (this.core == null || !this.core.machineId.equals(loadedId)) {
            this.core = new TurtleCore(loadedId);
            this.core.bind(this);
        }

        this.core.workspaceId = tag.getString("WorkspaceId");
        this.core.programName = tag.getString("ProgramName");
        this.core.isPrivateMode = tag.getBoolean("IsPrivateMode");
        this.core.wakeOnRedstone = tag.getBoolean("WakeOnRedstone");
        this.core.debugChat = !tag.contains("DebugChat") || tag.getBoolean("DebugChat");
        if (tag.contains("OwnerUUID")) this.core.ownerUUID = tag.getUUID("OwnerUUID");

        this.core.installedPrograms.clear();
        if (tag.contains("InstalledPrograms")) {
            ListTag list = tag.getList("InstalledPrograms", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) this.core.installedPrograms.add(list.getString(i));
        }

        this.core.wasPowered = tag.getBoolean("WasPowered");
        if (tag.contains("RedstoneOutputs")) {
            CompoundTag rsTag = tag.getCompound("RedstoneOutputs");
            for (Direction dir : Direction.values()) this.core.redstoneOutputs.put(dir, rsTag.getInt(dir.getName()));
        }

        if (tag.contains("Inventory")) this.core.itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("LastItemCounts")) {
            int[] loaded = tag.getIntArray("LastItemCounts");
            if (loaded.length == this.core.itemHandler.getSlots()) this.core.lastItemCounts = loaded;
        }

        this.core.wasRunning = tag.getBoolean("WasRunning");
        if (tag.contains("PersistentData")) this.core.persistentData = tag.getCompound("PersistentData");
        if (tag.contains("Energy")) this.core.energy = tag.getInt("Energy");
        this.core.tasks.load(tag);

        this.renderOffsetX = tag.getFloat("AnimDx"); this.renderOffsetY = tag.getFloat("AnimDy");
        this.renderOffsetZ = tag.getFloat("AnimDz"); this.renderRotDiff = tag.getFloat("AnimDRot");
        this.animDurationMs = tag.getLong("AnimDurMs");

        // クライアントで受け取った瞬間にアニメーションタイマーをスタート
        if (this.animDurationMs > 0 && (this.renderOffsetX != 0 || this.renderOffsetY != 0 || this.renderOffsetZ != 0 || this.renderRotDiff != 0)) {
            if (this.clientAnimStartTime == 0) {
                this.clientAnimStartTime = System.currentTimeMillis();
            }
        } else {
            this.clientAnimStartTime = 0;
        }

        this.hasPendingMove = tag.getBoolean("PendingMove");
        if (tag.contains("PendingTarget")) this.pendingMoveTarget = BlockPos.of(tag.getLong("PendingTarget"));
        this.hasPendingTurn = tag.getBoolean("PendingTurn");
        if (tag.contains("PendingTurnFacing")) this.pendingTurnFacing = Direction.byName(tag.getString("PendingTurnFacing"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}