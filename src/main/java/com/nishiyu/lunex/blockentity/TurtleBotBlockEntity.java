package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.TurtleBotBlock;
import com.nishiyu.lunex.machine.*;
import com.nishiyu.lunex.program.server.turtle.TurtleServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// インターフェースの継承を完全に削除し、独立したBlockEntityとして整理
public class TurtleBotBlockEntity extends BlockEntity {

    public final TurtleTaskManager tasks = new TurtleTaskManager();

    public int energy = 0;
    private static final int DEFAULT_MAX_ENERGY = 100000;
    private static final int DEFAULT_RECEIVE_RATE = 1000;
    private static final int DEFAULT_MAINTAIN_COST = 5;

    public final IEnergyStorage feStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int space = DEFAULT_MAX_ENERGY - energy;
            if (space <= 0) return 0;
            int maxTransfer = Math.min(maxReceive, DEFAULT_RECEIVE_RATE);
            int received = Math.min(maxTransfer, space);
            if (!simulate) {
                energy += received;
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override
        public int getEnergyStored() { return energy; }
        @Override
        public int getMaxEnergyStored() { return DEFAULT_MAX_ENERGY; }
        @Override
        public boolean canExtract() { return false; }
        @Override
        public boolean canReceive() { return true; }
    };

    public final ItemStackHandler itemHandler = new ItemStackHandler(16) {
        @Override
        protected void onContentsChanged(int slot) {
            int currentCount = this.getStackInSlot(slot).getCount();
            if (currentCount > lastItemCounts[slot]) {
                ItemStack stack = this.getStackInSlot(slot);
                String itemName = stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (vm != null) vm.triggerEvent("on_item_in", slot, itemName, currentCount);
            }
            lastItemCounts[slot] = currentCount;
            setChanged();
        }
    };

    public TurtleServerLuaVM vm = new TurtleServerLuaVM(this);
    public UUID machineId = null;
    public CompoundTag persistentData = new CompoundTag();
    public boolean wasRunning = false;
    public String workspaceId = "";
    public boolean isPrivateMode = false;
    public boolean wakeOnRedstone = false;
    public boolean debugChat = true;
    public UUID ownerUUID = null;

    public List<String> installedPrograms = new ArrayList<>();
    public int[] lastItemCounts = new int[16];
    public boolean wasPowered = false;
    public Map<Direction, Integer> redstoneOutputs = new ConcurrentHashMap<>();

    // ★追加: 以前指摘した変数を明確に定義
    public String programName = "";

    // --- タートル独自のアニメーション・移動要素 ---
    public float renderOffsetX = 0, renderOffsetY = 0, renderOffsetZ = 0;
    public float renderRotDiff = 0;
    public long clientAnimStartTime = 0;

    public boolean hasPendingMove = false;
    public BlockPos pendingMoveTarget = null;
    public boolean hasPendingTurn = false;
    public Direction pendingTurnFacing = null;

    public TurtleBotBlockEntity(BlockPos pos, BlockState state) {
        // ★修正: TURTLE_BOT_BE ではなく TURTLE_BOT_BLOCK_ENTITY_TYPE など、正しいSupplierを参照してください
        super(Lunex.TURTLE_BOT_BE.get(), pos, state);
    }

    // ★追加: getter と setter メソッド
    public String getProgramName() {
        return this.programName;
    }

    public void setProgramName(String name) {
        boolean isChanged = (this.programName == null || !this.programName.equals(name));
        this.programName = name;
        this.setChanged();
        this.sync();
        if (isChanged && this.vm != null) {
            this.vm.isWipingMemory = true;
            this.persistentData.remove("AutoMemory");
        }
    }

    public String getWorkspaceId() {
        return this.workspaceId;
    }

    public List<String> getInstalledPrograms() {
        return this.installedPrograms;
    }


    public static void tick(Level level, BlockPos pos, BlockState state, TurtleBotBlockEntity entity) {
        if (level.isClientSide) return;

        if (level.getGameTime() % 20 == 0) {
            if (entity.vm != null && entity.vm.isRunning) {
                entity.energy -= DEFAULT_MAINTAIN_COST;
                if (entity.energy <= 0) {
                    entity.energy = 0;
                    entity.setRunning(false);
                }
                entity.setChanged();
                entity.sync();
            }
        }

        if (entity.vm != null) {
            entity.vm.tick();
            if (!entity.vm.isRunning) {
                if (entity.tasks.isBreakingBlock && entity.tasks.breakingPos != null) {
                    level.destroyBlockProgress(entity.hashCode(), entity.tasks.breakingPos, -1);
                    entity.resetBreakingState();
                }
            } else {
                entity.vm.triggerEvent("on_tick");
            }
        }
    }

    public String getVmId() {
        return "turtle_" + this.worldPosition.getX() + "_" + this.worldPosition.getY() + "_" + this.worldPosition.getZ();
    }

    public void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void onRedstoneUpdate(boolean isPowered) {
        if (isPowered && !this.wasPowered) {
            if (this.wakeOnRedstone && !this.vm.isRunning && this.programName != null && !this.programName.isEmpty()) {
                this.vm.startProgram(this.programName);
            }
            this.vm.triggerEvent("on_redstone", isPowered);
        } else if (!isPowered && this.wasPowered) {
            this.vm.triggerEvent("on_redstone", isPowered);
        }
        this.wasPowered = isPowered;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.wasRunning && this.programName != null && !this.programName.isEmpty()) {
            this.vm.startProgram(this.programName);
            this.vm.triggerEvent("on_start");
        }
        this.wasRunning = false;
    }

    @Override
    public void setRemoved() {
        if (this.vm != null) {
            if (this.vm.isRunning) this.wasRunning = true;
            this.vm.saveMemoryToNBT();
            this.vm.stopProgram();
        }
        super.setRemoved();
    }

    // 内部処理用に必須なVM管理メソッド群
    public boolean isRunning() {
        return this.vm != null && this.vm.isRunning;
    }

    public void setRunning(boolean running) {
        if (this.vm != null) {
            boolean was = this.vm.isRunning;
            this.wasRunning = running;
            if (this.level != null && !this.level.isClientSide) {
                if (running) {
                    if (was) this.vm.stopProgram();
                    if (this.programName != null && !this.programName.isEmpty()) {
                        String runName = this.programName.replace(".lua", "");
                        this.vm.startProgram(runName);
                    }
                } else if (was) {
                    this.vm.stopProgram();
                }
                this.sync();
            } else {
                this.vm.isRunning = running;
            }
        }
    }

    public String getMachineLabel() {
        return this.persistentData.getString("NetworkTag");
    }

    public void setMachineLabel(String label) {
        String current = this.persistentData.getString("NetworkTag");
        String newLabel = label != null ? label : "";
        if (!current.equals(newLabel)) {
            this.persistentData.putString("NetworkTag", newLabel);
            this.setChanged();
            this.sync();
        }
    }

    public boolean consumeActionEnergy(int baseCost) {
        if (this.energy >= baseCost) {
            this.energy -= baseCost;
            this.setChanged();
            return true;
        }
        return false;
    }

    public int calculateActionTime(int baseTicks) {
        return Math.max(1, baseTicks);
    }

    public void resetBreakingState() { this.tasks.resetBreakingState(); }
    public BlockPos resolveDevice(String targetStr) { return TargetUtil.resolveDevice(this, targetStr); }
    public boolean isValidSlot(int slot) { return slot >= 0 && slot < 16; }

    // --- タートルのアニメーション・移動処理 ---
    public long getAnimationDurationMs() { return 1000; }

    public void startAnimation(float dx, float dy, float dz, float dRot) {
        this.renderOffsetX = dx;
        this.renderOffsetY = dy;
        this.renderOffsetZ = dz;
        this.renderRotDiff = dRot;
    }

    public void executePendingActions(net.minecraft.world.level.Level level, BlockPos pos) {
        if (this.isRemoved()) return;

        if (this.hasPendingTurn) {
            this.hasPendingTurn = false;
            BlockState currentState = this.getBlockState();
            BlockState newState = currentState.setValue(TurtleBotBlock.FACING, this.pendingTurnFacing);
            level.setBlock(pos, newState, 3);
            this.renderRotDiff = 0;
            level.sendBlockUpdated(pos, currentState, newState, 3);
        }

        if (this.hasPendingMove && this.pendingMoveTarget != null) {
            this.hasPendingMove = false;
            BlockPos targetPos = this.pendingMoveTarget;
            BlockState currentState = this.getBlockState();

            if (!level.getBlockState(targetPos).canBeReplaced()) {
                this.renderOffsetX = 0;
                this.renderOffsetY = 0;
                this.renderOffsetZ = 0;
                level.sendBlockUpdated(pos, currentState, currentState, 3);
                return;
            }

            CompoundTag tag = this.saveWithFullMetadata(level.registryAccess());
            tag.putBoolean("IsRunning", false);

            for (int i = 0; i < this.itemHandler.getSlots(); i++)
                this.itemHandler.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);

            TurtleServerLuaVM activeVM = this.vm;
            activeVM.isRelocating = true;

            try {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(targetPos, currentState, 3);

                BlockEntity newBe = level.getBlockEntity(targetPos);
                if (newBe instanceof TurtleBotBlockEntity newTurtle) {
                    tag.putInt("x", targetPos.getX());
                    tag.putInt("y", targetPos.getY());
                    tag.putInt("z", targetPos.getZ());
                    newTurtle.loadWithComponents(tag, level.registryAccess());

                    newTurtle.vm.takeOverFrom(activeVM);
                    newTurtle.vm.isRelocating = false;
                    newTurtle.wasRunning = true;

                    newTurtle.renderOffsetX = 0;
                    newTurtle.renderOffsetY = 0;
                    newTurtle.renderOffsetZ = 0;
                    newTurtle.renderRotDiff = 0;
                    level.sendBlockUpdated(targetPos, Blocks.AIR.defaultBlockState(), currentState, 3);
                } else {
                    activeVM.isRelocating = false;
                    activeVM.stopProgram();
                }
            } finally {
                activeVM.isRelocating = false;
            }
        }
    }

    // --- セーブ & ロード ---
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);
        tag.putString("WorkspaceId", this.workspaceId != null ? this.workspaceId : "");
        tag.putString("ProgramName", this.programName);
        tag.putBoolean("IsPrivateMode", this.isPrivateMode);
        tag.putBoolean("WakeOnRedstone", this.wakeOnRedstone);
        tag.putBoolean("DebugChat", this.debugChat);
        if (this.ownerUUID != null) tag.putUUID("OwnerUUID", this.ownerUUID);

        ListTag programsTag = new ListTag();
        for (String p : installedPrograms) programsTag.add(StringTag.valueOf(p));
        tag.put("InstalledPrograms", programsTag);

        tag.putBoolean("WasPowered", this.wasPowered);

        CompoundTag rsTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            rsTag.putInt(dir.getName(), this.redstoneOutputs.getOrDefault(dir, 0));
        }
        tag.put("RedstoneOutputs", rsTag);

        tag.put("Inventory", itemHandler.serializeNBT(registries));
        tag.putIntArray("LastItemCounts", this.lastItemCounts);

        tag.putBoolean("WasRunning", this.vm.isRunning || this.wasRunning);

        if (this.vm != null) {
            this.vm.saveMemoryToNBT();
            if (this.vm.memoryBuffer != null) {
                this.persistentData.put("AutoMemory", this.vm.memoryBuffer.copy());
            }
        }
        tag.put("PersistentData", this.persistentData);
        tag.putInt("Energy", this.energy);
        tasks.save(tag);

        tag.putFloat("AnimDx", this.renderOffsetX);
        tag.putFloat("AnimDy", this.renderOffsetY);
        tag.putFloat("AnimDz", this.renderOffsetZ);
        tag.putFloat("AnimDRot", this.renderRotDiff);

        tag.putBoolean("PendingMove", this.hasPendingMove);
        if (this.pendingMoveTarget != null) tag.putLong("PendingTarget", this.pendingMoveTarget.asLong());
        tag.putBoolean("PendingTurn", this.hasPendingTurn);
        if (this.pendingTurnFacing != null) tag.putString("PendingTurnFacing", this.pendingTurnFacing.getName());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("MachineId")) this.machineId = tag.getUUID("MachineId");
        if (tag.contains("WorkspaceId")) this.workspaceId = tag.getString("WorkspaceId");
        if (tag.contains("ProgramName")) this.programName = tag.getString("ProgramName");

        this.isPrivateMode = tag.getBoolean("IsPrivateMode");
        this.wakeOnRedstone = tag.getBoolean("WakeOnRedstone");
        this.debugChat = !tag.contains("DebugChat") || tag.getBoolean("DebugChat");
        if (tag.contains("OwnerUUID")) this.ownerUUID = tag.getUUID("OwnerUUID");

        this.installedPrograms.clear();
        if (tag.contains("InstalledPrograms")) {
            ListTag list = tag.getList("InstalledPrograms", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) this.installedPrograms.add(list.getString(i));
        }

        this.wasPowered = tag.getBoolean("WasPowered");

        if (tag.contains("RedstoneOutputs")) {
            CompoundTag rsTag = tag.getCompound("RedstoneOutputs");
            for (Direction dir : Direction.values()) {
                this.redstoneOutputs.put(dir, rsTag.getInt(dir.getName()));
            }
        }

        if (tag.contains("Inventory")) itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("LastItemCounts")) this.lastItemCounts = tag.getIntArray("LastItemCounts");

        this.wasRunning = tag.getBoolean("WasRunning");

        if (tag.contains("PersistentData")) {
            this.persistentData = tag.getCompound("PersistentData");
        }
        if (tag.contains("MachineLabel")) {
            String oldLabel = tag.getString("MachineLabel");
            if (!oldLabel.isEmpty() && !this.persistentData.contains("NetworkTag")) {
                this.persistentData.putString("NetworkTag", oldLabel);
            }
        }

        if (tag.contains("Energy")) this.energy = tag.getInt("Energy");
        tasks.load(tag);

        float dx = tag.getFloat("AnimDx");
        float dy = tag.getFloat("AnimDy");
        float dz = tag.getFloat("AnimDz");
        float dRot = tag.getFloat("AnimDRot");

        if (dx != 0 || dy != 0 || dz != 0 || dRot != 0) {
            this.clientAnimStartTime = System.currentTimeMillis();
        }

        this.renderOffsetX = dx;
        this.renderOffsetY = dy;
        this.renderOffsetZ = dz;
        this.renderRotDiff = dRot;

        this.hasPendingMove = tag.getBoolean("PendingMove");
        if (tag.contains("PendingTarget")) this.pendingMoveTarget = BlockPos.of(tag.getLong("PendingTarget"));
        this.hasPendingTurn = tag.getBoolean("PendingTurn");
        if (tag.contains("PendingTurnFacing"))
            this.pendingTurnFacing = Direction.byName(tag.getString("PendingTurnFacing"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);
        tag.putBoolean("WasRunning", this.vm.isRunning || this.wasRunning);
        tag.putBoolean("IsPrivateMode", this.isPrivateMode);
        tag.putBoolean("WakeOnRedstone", this.wakeOnRedstone);
        tag.putBoolean("DebugChat", this.debugChat);
        tag.put("PersistentData", this.persistentData);
        tag.putString("WorkspaceId", this.workspaceId != null ? this.workspaceId : "");
        tag.putString("ProgramName", this.programName != null ? this.programName : "");
        if (this.ownerUUID != null) tag.putUUID("OwnerUUID", this.ownerUUID);

        ListTag programsTag = new ListTag();
        for (String p : this.installedPrograms) programsTag.add(StringTag.valueOf(p));
        tag.put("InstalledPrograms", programsTag);

        CompoundTag rsTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            rsTag.putInt(dir.getName(), this.redstoneOutputs.getOrDefault(dir, 0));
        }
        tag.put("RedstoneOutputs", rsTag);
        tag.putInt("Energy", this.energy);
        tasks.save(tag);

        tag.putFloat("AnimDx", this.renderOffsetX);
        tag.putFloat("AnimDy", this.renderOffsetY);
        tag.putFloat("AnimDz", this.renderOffsetZ);
        tag.putFloat("AnimDRot", this.renderRotDiff);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}