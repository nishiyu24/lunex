package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.MainframeComponentData;
import com.nishiyu.lunex.api.mainframe.MainframeComponentRegistry;
import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeExtension;
import com.nishiyu.lunex.api.mainframe.extension.MainframeExtensionRegistry;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeItemHandler;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.machine.CoreMachineVMCache;
import com.nishiyu.lunex.machine.VirtualStorage;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SimpleMachineBlockEntity extends BlockEntity implements IMainframePart, IMCNetDevice {

    // VMとデータ管理用
    public CoreMachineServerLuaVM vm = null;
    public UUID machineId = null;
    public CompoundTag persistentData = new CompoundTag();

    public final List<String> clientTerminalLog = new ArrayList<>();
    public final List<String> clientSuggestions = new ArrayList<>();

    // メインフレーム管理用
    public boolean isMainframeMaster = false;
    public BlockPos masterPos = null;
    public final List<BlockPos> mainframeParts = new ArrayList<>();

    public int mainframeMachines = 1;
    public int mainframeTotalCapacityBytes = 0;
    public int mainframeUsedItemBytes = 0;
    public int mainframeUsedFluidBytes = 0;
    public int mainframeUsedProgramBytes = 0;

    public final Map<String, Integer> componentCounts = new HashMap<>();
    public final Set<String> activeFeatures = new HashSet<>();
    public final Set<String> activeApis = new HashSet<>();

    public final Map<ResourceLocation, IMainframeExtension> extensions = new HashMap<>();

    public final VirtualStorage virtualStorage = new VirtualStorage(this);

    public final EnergyStorage energyStorage = new EnergyStorage(1000000, 10000, 10000) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) setChanged();
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) setChanged();
            return extracted;
        }
    };

    public final MainframeItemHandler mainframeStorage = new MainframeItemHandler() {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            updateCapacityUsage();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public SimpleMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.SIMPLE_MACHINE_BE.get(), pos, state);
        this.extensions.putAll(MainframeExtensionRegistry.createAllInstances());
    }

    @Override
    public boolean isNetworkActive() { return true; }

    @Override
    public void setMasterPos(BlockPos pos) { this.masterPos = pos; this.setChanged(); }

    @Override
    public BlockPos getMasterPos() { return this.masterPos; }

    public IMainframeExtension getExtension(ResourceLocation id) {
        return this.extensions.get(id);
    }

    public BlockPos resolveDevice(String targetStr) {
        if (targetStr == null) return null;
        if (targetStr.equals("self") || targetStr.equals("localhost")) return this.getBlockPos();

        if (this.level != null && this.isMainframeMaster) {
            for (BlockPos pos : this.mainframeParts) {
                BlockEntity be = this.level.getBlockEntity(pos);
                if (be != null && targetStr.equals(be.getPersistentData().getString("NetworkTag"))) {
                    return pos;
                }
            }
        }

        if (targetStr.equals(this.persistentData.getString("NetworkTag"))) {
            return this.getBlockPos();
        }
        return null;
    }

    public int getEnergy() {
        return this.energyStorage.getEnergyStored();
    }

    public boolean consumeActionEnergy(int amount) {
        if (this.energyStorage.getEnergyStored() >= amount) {
            this.energyStorage.extractEnergy(amount, false);
            return true;
        }
        return false;
    }

    public String getMachineLabel() {
        return this.persistentData.getString("NetworkTag");
    }

    public void setMachineLabel(String label) {
        this.persistentData.putString("NetworkTag", label != null ? label : "");
        this.setChanged();
        this.sync();
    }

    public String getProgramName() {
        return this.persistentData.getString("BootProgram");
    }

    public void setProgramName(String name) {
        this.persistentData.putString("BootProgram", name != null ? name : "");
        this.setChanged();
    }

    public String getWorkspaceId() {
        return this.persistentData.getString("WorkspaceId");
    }

    public void setWorkspaceId(String id) {
        this.persistentData.putString("WorkspaceId", id != null ? id : "");
        this.setChanged();
    }

    public boolean isValidSlot(int luaSlot) {
        return luaSlot >= 1 && luaSlot <= this.mainframeStorage.getSlots();
    }

    public void updateCapacityUsage() {
        if (level == null || level.isClientSide) return;
        int itemB = 0;
        for (int i = 0; i < this.mainframeStorage.getSlots(); i++) {
            ItemStack stack = this.mainframeStorage.getStackInSlot(i);
            if (!stack.isEmpty()) {
                boolean hasNbt = stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                int bytesPerItem = hasNbt ? 4096 : 1024;
                itemB += stack.getCount() * bytesPerItem;
            }
        }
        this.mainframeUsedItemBytes = itemB;
        this.mainframeTotalCapacityBytes = this.mainframeStorage.getMaxCapacityBytes();
    }

    public void rebuildMainframe() {
        if (level == null || level.isClientSide) return;

        this.mainframeStorage.updateCapacity(100000 * this.mainframeParts.size());
        updateCapacityUsage();

        this.componentCounts.clear();
        this.activeFeatures.clear();
        this.activeApis.clear();

        for (BlockPos pos : this.mainframeParts) {
            BlockEntity be = level.getBlockEntity(pos);
            BlockState originalState = null;
            if (be instanceof MainframeAdapterBlockEntity adapter) {
                originalState = adapter.getOriginalState();
            } else {
                originalState = level.getBlockState(pos);
            }

            if (originalState != null) {
                Block block = originalState.getBlock();
                String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                this.componentCounts.put(blockId, this.componentCounts.getOrDefault(blockId, 0) + 1);

                MainframeComponentData data = MainframeComponentRegistry.get(block);
                if (data != null) {
                    this.activeFeatures.addAll(data.getFeatures());
                    this.activeApis.addAll(data.getApis());
                }
            }
        }

        for (IMainframeExtension ext : this.extensions.values()) {
            ext.onAssembled(this);
        }

        if (this.vm instanceof CoreMachineServerLuaVM cvm) {
            cvm.terminalLog.add("[System] Mainframe assembled.");
            cvm.terminalLog.add("[System] CPUs: " + this.mainframeMachines + ", Base Capacity: " + (this.mainframeTotalCapacityBytes / 1048576) + "MB");
            cvm.upgradeToMainframe(this.activeApis);
            cvm.syncClient();
        }

        this.setChanged();
        this.sync();
    }

    /**
     * ★修正: 解体時に全てのパーツのテクスチャ(assembledプロパティ)とマスター情報を確実にリセットし、
     * 即座にクライアントへブロック更新通知を送信します。
     */
    public void disassembleMainframe() {
        if (level == null || level.isClientSide || !isMainframeMaster) return;

        for (IMainframeExtension ext : this.extensions.values()) {
            ext.onDisassembled(this);
        }

        if (this.vm instanceof CoreMachineServerLuaVM cvm) {
            cvm.terminalLog.add("[System] CRITICAL: Mainframe connection lost. Disassembled.");
            cvm.downgradeToInteractive();
            cvm.syncClient();
        }

        // リストをクリアする前に、現在のパーツ全ての見た目（assembled）をfalseにする
        MainframeScanner.updateMainframeVisuals(level, this.mainframeParts, false);

        for (BlockPos pos : this.mainframeParts) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMainframePart part) {
                part.setMasterPos(null);

                // アダプター等の元の状態復元
                if (part instanceof MainframeAdapterBlockEntity adapter) {
                    adapter.restoreOriginalBlock();
                }

                // クライアント側に確実に「合体解除」のデータを同期する
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }

        this.isMainframeMaster = false;
        this.mainframeParts.clear();
        this.mainframeTotalCapacityBytes = 0;
        this.mainframeUsedItemBytes = 0;
        this.mainframeMachines = 1;
        this.mainframeStorage.updateCapacity(0);
        this.componentCounts.clear();
        this.activeFeatures.clear();
        this.activeApis.clear();

        this.setChanged();
        this.sync();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            if (this.machineId == null) this.machineId = UUID.randomUUID();
            this.vm = CoreMachineVMCache.getOrCreateVM(this.machineId, this);
            if (this.vm instanceof CoreMachineServerLuaVM cvm) {
                cvm.syncClient();
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SimpleMachineBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.isMainframeMaster) {
            // ルーター機能
            if (entity.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                entity.virtualStorage.tick(level);
            }

            // ★追加: 登録されているすべての拡張機能の tick を呼び出す
            for (IMainframeExtension ext : entity.extensions.values()) {
                ext.tick(level, entity);
            }
        }

        if (entity.vm != null) {
            entity.vm.tick();
            if (entity.vm.isRunning) {
                entity.vm.triggerEvent("on_tick");
            }
        }
    }

    @Override
    public void setRemoved() {
        if (this.vm != null) this.vm.stopProgram();
        super.setRemoved();
    }

    public void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);

        tag.putBoolean("IsMainframeMaster", this.isMainframeMaster);
        tag.putInt("MainframeMachines", this.mainframeMachines);
        tag.putInt("MainframeTotalCapacityBytes", this.mainframeTotalCapacityBytes);
        tag.putInt("MainframeUsedItemBytes", this.mainframeUsedItemBytes);
        tag.putInt("MainframeUsedFluidBytes", this.mainframeUsedFluidBytes);
        tag.putInt("MainframeUsedProgramBytes", this.mainframeUsedProgramBytes);

        if (this.masterPos != null) tag.putLong("MasterPos", this.masterPos.asLong());
        tag.put("MainframeStorage", this.mainframeStorage.serializeNBT(registries));
        tag.put("Energy", this.energyStorage.serializeNBT(registries));

        CompoundTag extTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, IMainframeExtension> entry : this.extensions.entrySet()) {
            extTag.put(entry.getKey().toString(), entry.getValue().serializeNBT(registries));
        }
        tag.put("MainframeExtensions", extTag);

        if (!this.mainframeParts.isEmpty()) {
            long[] partsArray = new long[this.mainframeParts.size()];
            for (int i = 0; i < this.mainframeParts.size(); i++) partsArray[i] = this.mainframeParts.get(i).asLong();
            tag.putLongArray("MainframeParts", partsArray);
        }

        tag.put("PersistentData", this.persistentData);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("MachineId")) this.machineId = tag.getUUID("MachineId");

        updateDataFromTag(tag);
        if (tag.contains("MasterPos")) this.masterPos = BlockPos.of(tag.getLong("MasterPos"));
        if (tag.contains("MainframeStorage")) {
            this.mainframeStorage.deserializeNBT(registries, tag.getCompound("MainframeStorage"));
        }
        if (tag.contains("Energy")) {
            this.energyStorage.deserializeNBT(registries, tag.get("Energy"));
        }

        if (tag.contains("MainframeExtensions")) {
            CompoundTag extTag = tag.getCompound("MainframeExtensions");
            for (Map.Entry<ResourceLocation, IMainframeExtension> entry : this.extensions.entrySet()) {
                String keyStr = entry.getKey().toString();
                if (extTag.contains(keyStr)) {
                    entry.getValue().deserializeNBT(extTag.getCompound(keyStr), registries);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);
        tag.putBoolean("IsMainframeMaster", this.isMainframeMaster);
        tag.putInt("MainframeMachines", this.mainframeMachines);
        tag.putInt("MainframeTotalCapacityBytes", this.mainframeTotalCapacityBytes);
        tag.putInt("MainframeUsedItemBytes", this.mainframeUsedItemBytes);
        tag.putInt("MainframeUsedFluidBytes", this.mainframeUsedFluidBytes);
        tag.putInt("MainframeUsedProgramBytes", this.mainframeUsedProgramBytes);

        tag.put("PersistentData", this.persistentData);
        tag.put("MainframeStorage", this.mainframeStorage.serializeNBT(provider));
        tag.put("Energy", this.energyStorage.serializeNBT(provider));

        if (!this.mainframeParts.isEmpty()) {
            long[] partsArray = new long[this.mainframeParts.size()];
            for (int i = 0; i < this.mainframeParts.size(); i++) partsArray[i] = this.mainframeParts.get(i).asLong();
            tag.putLongArray("MainframeParts", partsArray);
        }

        if (this.vm instanceof CoreMachineServerLuaVM cvm) {
            ListTag logTag = new ListTag();
            for (String line : cvm.terminalLog) logTag.add(StringTag.valueOf(line));
            tag.put("TerminalLog", logTag);

            ListTag suggestTag = new ListTag();
            for (String s : cvm.suggestions) suggestTag.add(StringTag.valueOf(s));
            tag.put("Suggestions", suggestTag);
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        CompoundTag tag = pkt.getTag();
        if (tag != null) updateDataFromTag(tag);
    }

    private void updateDataFromTag(CompoundTag tag) {
        if (tag.contains("TerminalLog")) {
            this.clientTerminalLog.clear();
            ListTag logTag = tag.getList("TerminalLog", 8);
            for (int i = 0; i < logTag.size(); i++) this.clientTerminalLog.add(logTag.getString(i));
        }
        if (tag.contains("Suggestions")) {
            this.clientSuggestions.clear();
            ListTag suggestTag = tag.getList("Suggestions", 8);
            for (int i = 0; i < suggestTag.size(); i++) this.clientSuggestions.add(suggestTag.getString(i));
        }
        if (tag.contains("PersistentData")) this.persistentData.merge(tag.getCompound("PersistentData"));

        if (tag.contains("IsMainframeMaster")) this.isMainframeMaster = tag.getBoolean("IsMainframeMaster");
        if (tag.contains("MainframeMachines")) this.mainframeMachines = tag.getInt("MainframeMachines");
        if (tag.contains("MainframeTotalCapacityBytes")) this.mainframeTotalCapacityBytes = tag.getInt("MainframeTotalCapacityBytes");
        if (tag.contains("MainframeUsedItemBytes")) this.mainframeUsedItemBytes = tag.getInt("MainframeUsedItemBytes");
        if (tag.contains("MainframeUsedFluidBytes")) this.mainframeUsedFluidBytes = tag.getInt("MainframeUsedFluidBytes");
        if (tag.contains("MainframeUsedProgramBytes")) this.mainframeUsedProgramBytes = tag.getInt("MainframeUsedProgramBytes");

        if (tag.contains("MainframeParts")) {
            this.mainframeParts.clear();
            for (long l : tag.getLongArray("MainframeParts")) this.mainframeParts.add(BlockPos.of(l));
        }
    }
}