package com.nishiyu.lunex.blockentity;

import com.google.gson.JsonObject;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.AdvancedMachineBlock;
import com.nishiyu.lunex.item.UpgradeItem;
import com.nishiyu.lunex.machine.*;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.program.server.machine.MachineServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancedMachineBlockEntity extends BlockEntity implements IMachineContext, IMCNetDevice, IDisguisable {

    public static final String DEFAULT_BOOT_FILE = "boot.lua";
    public final MachineUpgradeManager upgrades = new MachineUpgradeManager();
    public final MachineEnergyManager energyManager = new MachineEnergyManager();
    public final MachineTaskManager tasks = new MachineTaskManager();

    public final IEnergyStorage feStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int space = energyManager.getMaxEnergy(upgrades) - energyManager.energy;
            if (space <= 0) return 0;
            int maxTransfer = Math.min(maxReceive, energyManager.getReceiveRate(upgrades));
            int received = Math.min(maxTransfer, space);
            if (!simulate) {
                energyManager.energy += received;
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) { return 0; }

        @Override
        public int getEnergyStored() { return energyManager.energy; }

        @Override
        public int getMaxEnergyStored() { return energyManager.getMaxEnergy(upgrades); }

        @Override
        public boolean canExtract() { return false; }

        @Override
        public boolean canReceive() { return true; }
    };

    public final ItemStackHandler itemHandler = new ItemStackHandler(36) {
        @Override
        protected void onContentsChanged(int slot) {
            int currentCount = this.getStackInSlot(slot).getCount();
            if (currentCount > lastItemCounts[slot]) {
                ItemStack stack = this.getStackInSlot(slot);
                String itemName = stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                vm.triggerEvent("on_item_in", slot, itemName, currentCount);
            }
            lastItemCounts[slot] = currentCount;
            setChanged();
            notifyStorageChanged();
        }
    };

    public final ItemStackHandler upgradeHandler = new ItemStackHandler(7) {
        @Override
        protected void onContentsChanged(int slot) {
            updateUpgrades();
            setChanged();
        }
    };

    public MachineServerLuaVM vm = new MachineServerLuaVM(this);
    public UUID machineId = null;
    public CompoundTag persistentData = new CompoundTag();
    public boolean wasRunning = false;
    public String workspaceId = "";
    public boolean isPrivateMode = false;
    public boolean wakeOnRedstone = false;
    public boolean debugChat = true;
    public UUID ownerUUID = null;
    public BlockState disguiseState = null;

    public List<String> installedPrograms = new ArrayList<>();
    public int[] lastItemCounts = new int[36];
    public boolean wasPowered = false;
    public Map<Direction, Integer> redstoneOutputs = new ConcurrentHashMap<>();
    private String programName = "";

    private int currentChunkLoadLevel = -1;

    public AdvancedMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.ADVANCED_MACHINE_BE.get(), pos, state);
    }

    public AdvancedMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void notifyStorageChanged() {
        if (this.level == null || this.level.isClientSide) return;
        if (this.persistentData.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(this.persistentData.getLong("RouterPos"));
            BlockEntity be = this.level.getBlockEntity(routerPos);
            if (be instanceof com.nishiyu.lunex.blockentity.RouterBlockEntity router) {
                router.virtualStorage.onStorageChanged(this.level);
            }
        }
    }

    public void notifyNetworkTagChanged() {
        if (this.level == null || this.level.isClientSide) return;
        if (this.persistentData.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(this.persistentData.getLong("RouterPos"));
            BlockEntity be = this.level.getBlockEntity(routerPos);
            if (be instanceof com.nishiyu.lunex.blockentity.RouterBlockEntity router) {
                router.virtualStorage.rebuildNetworkCache(this.level);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AdvancedMachineBlockEntity entity) {
        if (level.isClientSide) return;

        if (level.getGameTime() % 20 == 0) {
            Set<BlockPos> countedMasters = new HashSet<>();
            int totalScreenBlocks = 0;

            for (Direction dir : Direction.values()) {
                BlockPos adjacentPos = pos.relative(dir);
                if (!level.isLoaded(adjacentPos)) continue;

                if (level.getBlockEntity(adjacentPos) instanceof ScreenBlockEntity screen) {
                    BlockPos mPos = screen.isMaster ? screen.getBlockPos() : screen.masterPos;
                    if (mPos != null && countedMasters.add(mPos)) {
                        if (!level.isLoaded(mPos)) continue;
                        if (level.getBlockEntity(mPos) instanceof ScreenBlockEntity master) {
                            totalScreenBlocks += (master.screenWidth * master.screenHeight);
                        }
                    }
                }
            }

            boolean energyChanged = false;

            if (totalScreenBlocks > 0) {
                entity.energyManager.energy -= totalScreenBlocks;
                energyChanged = true;
            }

            if (entity.vm != null && entity.vm.isRunning) {
                int maintainCost = entity.energyManager.getVmMaintainCost(entity.upgrades);
                entity.energyManager.energy -= maintainCost;
                energyChanged = true;
            }

            if (energyChanged) {
                if (entity.energyManager.energy <= 0) {
                    entity.energyManager.energy = 0;
                    if (entity.vm != null && entity.vm.isRunning) {
                        entity.setRunning(false);
                    }
                }
                entity.setChanged();
                entity.sync();
            }
        }

        Objects.requireNonNull(entity.vm).tick();

        if (!entity.vm.isRunning) {
            if (entity.tasks.isBreakingBlock && entity.tasks.breakingPos != null) {
                level.destroyBlockProgress(entity.hashCode(), entity.tasks.breakingPos, -1);
                entity.resetBreakingState();
            }
            return;
        }
        entity.vm.triggerEvent("on_tick");
    }

    public String getVmId() {
        return "machine_" + this.worldPosition.getX() + "_" + this.worldPosition.getY() + "_" + this.worldPosition.getZ();
    }

    public JsonObject getVmStateJson() {
        JsonObject json = new JsonObject();
        json.addProperty("vmId", getVmId());
        json.addProperty("status", this.isRunning() ? "running" : "stopped");
        json.addProperty("workspaceId", this.getWorkspaceId());
        json.addProperty("programName", this.getProgramName());
        json.addProperty("machineType", "AdvancedMachine");
        json.addProperty("memory_usage", "N/A");
        JsonObject vars = new JsonObject();
        json.add("variables", vars);
        return json;
    }

    public void initializeFromFrame(MachineFrameBlockEntity frame) {
        boolean hasCommand = false;
        for (int i = 0; i < frame.upgradeHandler.getSlots(); i++) {
            ItemStack stack = frame.upgradeHandler.getStackInSlot(i);
            this.upgradeHandler.setStackInSlot(i, stack.copy());
            if (stack.getItem() instanceof UpgradeItem upgrade) {
                if (upgrade.getUpgradeType() == UpgradeItem.UpgradeType.COMMAND) hasCommand = true;
            }
        }
        if (hasCommand) this.persistentData.putBoolean("HasCommandUpgrade", true);
        this.updateUpgrades();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.updateChunkLoading();
        if (this.level != null && !this.level.isClientSide && this.wasRunning && this.programName != null && !this.programName.isEmpty()) {
            this.vm.startProgram(this.programName);
            this.vm.triggerEvent("on_start");
        }
        this.wasRunning = false;
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
    public void setRemoved() {
        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel && currentChunkLoadLevel >= 0) {
            net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(this.worldPosition);
            int radius = getChunkRadius(currentChunkLoadLevel);
            if (radius >= 0) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        serverLevel.setChunkForced(center.x + x, center.z + z, false);
                    }
                }
            }
        }
        if (this.vm != null) {
            if (this.vm.isRunning) this.wasRunning = true;
            this.vm.saveMemoryToNBT();
            this.vm.stopProgram();
        }
        super.setRemoved();
    }

    public String getProgramName() { return this.programName; }

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

    public int getSpeedReductionPercent(boolean isRender) {
        int reduction = this.upgrades.speedLevel * 30;
        return Math.min(reduction, isRender ? 100 : 95);
    }

    public double getSpeedMultiplier(boolean isRender) {
        return Math.max(0.0, 1.0 - (getSpeedReductionPercent(isRender) / 100.0));
    }

    public boolean consumeActionEnergy(int baseCost) {
        if (energyManager.consume(baseCost, upgrades)) {
            this.setChanged();
            return true;
        }
        return false;
    }

    public int calculateActionTime(int baseTicks) {
        return (int) Math.max(1, Math.ceil(baseTicks * getSpeedMultiplier(false)));
    }

    public void updateUpgrades() {
        this.upgrades.update(this.level, this.worldPosition, this.upgradeHandler, this.itemHandler, this.energyManager);
        boolean hasCommand = false;
        for (int i = 0; i < this.upgradeHandler.getSlots(); i++) {
            ItemStack stack = this.upgradeHandler.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeItem upgrade) {
                if (upgrade.getUpgradeType() == UpgradeItem.UpgradeType.COMMAND) {
                    hasCommand = true;
                    break;
                }
            }
        }
        this.persistentData.putBoolean("HasCommandUpgrade", hasCommand);
        this.updateChunkLoading();
        this.sync();
    }

    public void resetBreakingState() { this.tasks.resetBreakingState(); }

    public BlockPos resolveDevice(String targetStr) { return TargetUtil.resolveDevice(this, targetStr); }

    public boolean isValidSlot(int slot) {
        if (slot < 0 || slot > 35) return false;
        if (slot <= 8) {
            int maxExecSlot = (this.upgrades.execLevel >= 3) ? 8 : (this.upgrades.execLevel == 2) ? 5 : (this.upgrades.execLevel == 1) ? 2 : -1;
            return slot <= maxExecSlot;
        }
        if (slot <= 35) {
            int maxStorageSlot = (this.upgrades.storageLevel >= 3) ? 35 : (this.upgrades.storageLevel == 2) ? 26 : (this.upgrades.storageLevel == 1) ? 17 : 8;
            return slot <= maxStorageSlot;
        }
        return true;
    }

    public int getDistanceUpgradeLevel() {
        int maxLevel = 0;
        for (int i = 0; i < this.upgradeHandler.getSlots(); i++) {
            ItemStack stack = this.upgradeHandler.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeItem upgrade && upgrade.getUpgradeType() == UpgradeItem.UpgradeType.DISTANCE) {
                maxLevel = Math.max(maxLevel, upgrade.getLevel());
            }
        }
        return maxLevel;
    }

    private int getChunkRadius(int level) {
        return switch (level) {
            case 1 -> 0;
            case 2 -> 1;
            case 3, 4 -> 2;
            default -> -1;
        };
    }

    public void updateChunkLoading() {
        if (this.level == null || this.level.isClientSide()) return;
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        int newLevel = getDistanceUpgradeLevel();
        if (newLevel == currentChunkLoadLevel) return;

        net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(this.worldPosition);

        if (currentChunkLoadLevel >= 0) {
            int oldRadius = getChunkRadius(currentChunkLoadLevel);
            if (oldRadius >= 0) {
                for (int x = -oldRadius; x <= oldRadius; x++) {
                    for (int z = -oldRadius; z <= oldRadius; z++) {
                        serverLevel.setChunkForced(center.x + x, center.z + z, false);
                    }
                }
            }
        }

        int newRadius = getChunkRadius(newLevel);
        if (newRadius >= 0) {
            for (int x = -newRadius; x <= newRadius; x++) {
                for (int z = -newRadius; z <= newRadius; z++) {
                    serverLevel.setChunkForced(center.x + x, center.z + z, true);
                }
            }
        }

        currentChunkLoadLevel = newLevel;
    }

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

        if (this.disguiseState != null) {
            tag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));
        }

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
        tag.put("UpgradeInventory", upgradeHandler.serializeNBT(registries));
        tag.putIntArray("LastItemCounts", this.lastItemCounts);

        tag.putBoolean("WasRunning", this.vm.isRunning || this.wasRunning);

        if (this.vm != null) {
            this.vm.saveMemoryToNBT();
            if (this.vm.memoryBuffer != null) {
                this.persistentData.put("AutoMemory", this.vm.memoryBuffer.copy());
            }
        }
        tag.put("PersistentData", this.persistentData);

        upgrades.save(tag);
        energyManager.save(tag);
        tasks.save(tag);
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

        if (tag.contains("DisguiseState")) {
            this.disguiseState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("DisguiseState"));
        } else {
            this.disguiseState = null;
        }

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
        if (tag.contains("UpgradeInventory"))
            upgradeHandler.deserializeNBT(registries, tag.getCompound("UpgradeInventory"));
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

        upgrades.load(tag);
        energyManager.load(tag);
        tasks.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveSyncData(tag, provider);
        return tag;
    }

    protected void saveSyncData(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);
        tag.putBoolean("WasRunning", this.vm.isRunning || this.wasRunning);
        tag.putBoolean("IsPrivateMode", this.isPrivateMode);
        tag.putBoolean("WakeOnRedstone", this.wakeOnRedstone);
        tag.putBoolean("DebugChat", this.debugChat);
        tag.put("PersistentData", this.persistentData);
        tag.putString("WorkspaceId", this.workspaceId != null ? this.workspaceId : "");
        tag.putString("ProgramName", this.programName != null ? this.programName : "");

        // ★追加: クライアント側へ OwnerUUID を同期する
        if (this.ownerUUID != null) tag.putUUID("OwnerUUID", this.ownerUUID);

        if (this.disguiseState != null) {
            tag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));
        }

        ListTag programsTag = new ListTag();
        for (String p : this.installedPrograms) {
            programsTag.add(StringTag.valueOf(p));
        }
        tag.put("InstalledPrograms", programsTag);

        CompoundTag rsTag = new CompoundTag();
        for (Direction dir : Direction.values()) {
            rsTag.putInt(dir.getName(), this.redstoneOutputs.getOrDefault(dir, 0));
        }
        tag.put("RedstoneOutputs", rsTag);

        tag.put("UpgradeInventory", this.upgradeHandler.serializeNBT(provider));

        upgrades.save(tag);
        energyManager.save(tag);
        tasks.save(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean isBlock() { return true; }

    @Override
    public BlockPos getPos() { return this.worldPosition; }

    @Override
    public String getWorkspaceId() { return this.workspaceId; }

    @Override
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    @Override
    public String getMachineLabel() {
        return this.persistentData.getString("NetworkTag");
    }

    @Override
    public void setMachineLabel(String label) {
        String current = this.persistentData.getString("NetworkTag");
        String newLabel = label != null ? label : "";
        if (!current.equals(newLabel)) {
            this.persistentData.putString("NetworkTag", newLabel);
            this.setChanged();
            this.sync();
            this.notifyNetworkTagChanged();
        }
    }

    @Override
    public boolean isPrivateMode() { return this.isPrivateMode; }

    @Override
    public void setPrivateMode(boolean privateMode) { this.isPrivateMode = privateMode; }

    @Override
    public boolean isWakeOnRedstone() { return this.wakeOnRedstone; }

    @Override
    public void setWakeOnRedstone(boolean wakeOnRedstone) { this.wakeOnRedstone = wakeOnRedstone; }

    @Override
    public boolean isDebugChat() { return this.debugChat; }

    @Override
    public void setDebugChat(boolean debugChat) { this.debugChat = debugChat; }

    @Override
    public boolean isRunning() { return this.vm != null && this.vm.isRunning; }

    @Override
    public void setRunning(boolean running) {
        if (this.vm != null) {
            boolean was = this.vm.isRunning;
            this.wasRunning = running;

            if (this.level != null && !this.level.isClientSide) {
                if (running) {
                    if (was) this.vm.stopProgram();
                    if (this.programName != null && !this.programName.isEmpty()) {
                        String runName = this.programName;
                        if (runName.endsWith(".lua")) runName = runName.substring(0, runName.length() - 4);
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

    @Override
    public List<String> getInstalledPrograms() { return this.installedPrograms; }

    @Override
    public int getEnergy() { return this.energyManager.energy; }

    @Override
    public int getExecUpgradeLevel() { return this.upgrades.execLevel; }

    @Override
    public int getStorageUpgradeLevel() { return this.upgrades.storageLevel; }

    @Override
    public int getSpeedUpgradeLevel() { return this.upgrades.speedLevel; }

    @Override
    public int getEfficiencyUpgradeLevel() { return this.upgrades.efficiencyLevel; }

    @Override
    public int getCapacityUpgradeLevel() { return this.upgrades.capacityLevel; }

    @Override
    public int getGeneratorUpgradeLevel() { return this.upgrades.generatorLevel; }

    @Override
    public BlockState getDisguiseState() {
        return this.disguiseState;
    }

    @Override
    public void setDisguiseState(BlockState state) {
        this.disguiseState = state;
    }

    @Override
    public void applyDisguiseState(boolean isDisguised) {
        if (this.level != null && !this.level.isClientSide) {
            this.level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(AdvancedMachineBlock.IS_DISGUISED, isDisguised));
            this.setChanged();
            this.sync();
        }
    }
}