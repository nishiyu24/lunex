package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.MainframeComponentData;
import com.nishiyu.lunex.api.MainframeComponentRegistry;
import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.IMainframeExtension;
import com.nishiyu.lunex.api.mainframe.MainframeExtensionRegistry;
import com.nishiyu.lunex.machine.MainframeCore;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeItemHandler;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.machine.CoreMachineVMCache;
import com.nishiyu.lunex.machine.IResourceProvider;
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

    private MainframeCore core;

    public boolean isMainframeMaster = false;
    public BlockPos masterPos = null;
    public final List<BlockPos> mainframeParts = new ArrayList<>();

    public int mainframeMachines = 1;
    public final Map<String, Integer> componentCounts = new HashMap<>();
    public final Map<String, List<BlockPos>> componentPositions = new HashMap<>();
    public final Map<ResourceLocation, IMainframeExtension> extensions = new HashMap<>();

    public class DynamicEnergyStorage extends EnergyStorage {
        public DynamicEnergyStorage(int capacity) { super(capacity, 10000, 10000); }
        public void setCapacity(int newCapacity) {
            this.capacity = newCapacity;
            if (this.energy > this.capacity) this.energy = this.capacity;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
                updateResourceUsages();
                sync();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) {
                setChanged();
                updateResourceUsages();
                sync();
            }
            return extracted;
        }
    }

    public final DynamicEnergyStorage energyStorage = new DynamicEnergyStorage(1000000);

    public final MainframeItemHandler mainframeStorage = new MainframeItemHandler() {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            updateResourceUsages();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public SimpleMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.SIMPLE_MACHINE_BE.get(), pos, state);

        // 仮想コアの初期化
        this.core = new MainframeCore(UUID.randomUUID());
        this.core.bind(this);

        this.core.registerResourceProvider("item", new IResourceProvider() {
            @Override public long getAmount() { return mainframeStorage.getTotalItems(); }
            @Override public long getCapacity() { return mainframeStorage.getCapacity(); }
        });

        this.core.registerResourceProvider("energy", new IResourceProvider() {
            @Override public long getAmount() { return energyStorage.getEnergyStored(); }
            @Override public long getCapacity() { return energyStorage.getMaxEnergyStored(); }
        });
    }

    public MainframeCore getCore() { return this.core; }

    @Override
    public boolean isNetworkActive() { return true; }

    @Override
    public void setMasterPos(BlockPos pos) { this.masterPos = pos; this.setChanged(); }

    @Override
    public BlockPos getMasterPos() { return this.masterPos; }

    public IMainframeExtension getExtension(ResourceLocation id) { return this.extensions.get(id); }

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

        if (targetStr.equals(this.core.persistentData.getString("NetworkTag"))) {
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

    // 古い参照元との互換性のためのデリゲートメソッド
    public UUID getMachineId() { return this.core.machineId; }
    public String getMachineLabel() { return this.core.getMachineLabel(); }
    public void setMachineLabel(String label) { this.core.setMachineLabel(label); }
    public String getProgramName() { return this.core.getProgramName(); }
    public void setProgramName(String name) { this.core.setProgramName(name); }
    public String getWorkspaceId() { return this.core.getWorkspaceId(); }
    public void setWorkspaceId(String id) { this.core.setWorkspaceId(id); }
    public CompoundTag getPersistentData() { return this.core.persistentData; }
    public CoreMachineServerLuaVM getVM() { return this.core.vm; }

    public boolean isValidSlot(int luaSlot) {
        return luaSlot >= 1 && luaSlot <= this.mainframeStorage.getSlots();
    }

    public void updateResourceUsages() {
        if (level == null || level.isClientSide) return;

        for (Map.Entry<String, IResourceProvider> entry : this.core.resourceProviders.entrySet()) {
            this.core.resourceUsages.put(entry.getKey(), entry.getValue().getAmount());
        }

        for (IMainframeExtension ext : this.extensions.values()) {
            ext.updateResourceUsages(this.core.resourceUsages);
        }
    }

    public void rebuildMainframe() {
        if (level == null || level.isClientSide) return;

        this.componentCounts.clear();
        this.componentPositions.clear();
        this.core.activeFeatures.clear();
        this.core.activeApis.clear();
        this.core.resourceCapacities.clear();

        Set<ResourceLocation> requiredExtensions = new HashSet<>();

        for (BlockPos pos : this.mainframeParts) {
            BlockEntity be = level.getBlockEntity(pos);
            BlockState originalState = null;
            if (be instanceof MainframeAdapterBlockEntity adapter) {
                originalState = adapter.getOriginalState();
            } else if (be != null) {
                originalState = level.getBlockState(pos);
            }

            if (originalState != null) {
                Block block = originalState.getBlock();
                String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                this.componentCounts.put(blockId, this.componentCounts.getOrDefault(blockId, 0) + 1);
                this.componentPositions.computeIfAbsent(blockId, k -> new ArrayList<>()).add(pos);

                MainframeComponentData data = MainframeComponentRegistry.get(block);
                if (data != null) {
                    this.core.activeFeatures.addAll(data.getFeatures());
                    this.core.activeApis.addAll(data.getApis());

                    for (Map.Entry<String, Long> entry : data.getResourceCapacities().entrySet()) {
                        this.core.resourceCapacities.put(entry.getKey(), this.core.resourceCapacities.getOrDefault(entry.getKey(), 0L) + entry.getValue());
                    }

                    requiredExtensions.addAll(data.getExtensions().keySet());
                }
            }
        }

        this.extensions.keySet().retainAll(requiredExtensions);
        for (ResourceLocation extId : requiredExtensions) {
            if (!this.extensions.containsKey(extId)) {
                IMainframeExtension newExt = MainframeExtensionRegistry.createInstance(extId);
                if (newExt != null) {
                    this.extensions.put(extId, newExt);
                }
            }
        }

        long baseItemCap = 0L;
        long baseEnergyCap = this.mainframeMachines * 100L;
        this.core.resourceCapacities.put("item", this.core.resourceCapacities.getOrDefault("item", 0L) + baseItemCap);
        this.core.resourceCapacities.put("energy", this.core.resourceCapacities.getOrDefault("energy", 0L) + baseEnergyCap);

        long itemCap = this.core.resourceCapacities.getOrDefault("item", 0L);
        this.mainframeStorage.updateCapacity((int) Math.min(Integer.MAX_VALUE, itemCap));
        long energyCap = this.core.resourceCapacities.getOrDefault("energy", 0L);
        this.energyStorage.setCapacity((int) Math.min(Integer.MAX_VALUE, energyCap));

        for (BlockPos partPos : this.mainframeParts) {
            BlockEntity be = level.getBlockEntity(partPos);
            if (be instanceof com.nishiyu.lunex.blockentity.DatabaseBlockEntity db) {
                java.util.List<ItemStack> recoveredItems = db.extractAllItems();
                for (ItemStack stack : recoveredItems) {
                    ItemStack remainder = this.mainframeStorage.insertItem(0, stack, false);
                    if (!remainder.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level, partPos.getX(), partPos.getY(), partPos.getZ(), remainder);
                    }
                }
            }
        }

        updateResourceUsages();

        for (IMainframeExtension ext : this.extensions.values()) {
            ext.onAssembled(this);
        }

        if (this.core.vm instanceof CoreMachineServerLuaVM cvm) {
            cvm.terminalLog.add("[System] Mainframe assembled.");
            cvm.terminalLog.add("[System] CPUs: " + this.mainframeMachines + ", Item Capacity: " + itemCap);
            cvm.upgradeToMainframe(this.core.activeApis);
            cvm.syncClient();
        }

        this.setChanged();
        this.sync();
    }

    public void disassembleMainframe() {
        if (level == null || level.isClientSide || !isMainframeMaster) return;

        for (IMainframeExtension ext : this.extensions.values()) {
            ext.onDisassembled(this);
        }

        if (this.core.vm instanceof CoreMachineServerLuaVM cvm) {
            cvm.terminalLog.add("[System] CRITICAL: Mainframe connection lost. Disassembled.");
            cvm.downgradeToInteractive();
            cvm.syncClient();
        }

        MainframeScanner.updateMainframeVisuals(level, this.mainframeParts, false);

        List<com.nishiyu.lunex.blockentity.DatabaseBlockEntity> databases = new ArrayList<>();
        for (BlockPos partPos : this.mainframeParts) {
            BlockEntity be = level.getBlockEntity(partPos);
            if (be instanceof com.nishiyu.lunex.blockentity.DatabaseBlockEntity db) {
                databases.add(db);
            }
        }

        databases.sort((d1, d2) -> {
            int p1 = d1.getPersistentData().contains("Priority") ? d1.getPersistentData().getInt("Priority") : 1;
            int p2 = d2.getPersistentData().contains("Priority") ? d2.getPersistentData().getInt("Priority") : 1;
            return Integer.compare(p1, p2);
        });

        if (!databases.isEmpty()) {
            List<ItemStack> allItems = new ArrayList<>();
            for (ItemStack stack : this.mainframeStorage.getStacks()) {
                if (!stack.isEmpty()) {
                    allItems.add(stack.copy());
                }
            }
            this.mainframeStorage.getStacks().clear();

            for (ItemStack stack : allItems) {
                ItemStack remainder = stack;
                for (com.nishiyu.lunex.blockentity.DatabaseBlockEntity db : databases) {
                    if (remainder.isEmpty()) break;
                    remainder = db.insertItem(remainder, false);
                }

                if (!remainder.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), remainder);
                }
            }
        } else {
            for (ItemStack stack : this.mainframeStorage.getStacks()) {
                if (!stack.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                }
            }
            this.mainframeStorage.getStacks().clear();
        }

        for (BlockPos pos : this.mainframeParts) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMainframePart part) {
                part.setMasterPos(null);
                if (part instanceof MainframeAdapterBlockEntity adapter) {
                    adapter.restoreOriginalBlock();
                }
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }

        this.isMainframeMaster = false;
        this.mainframeParts.clear();
        this.mainframeMachines = 1;

        this.core.resourceCapacities.clear();
        this.core.resourceUsages.clear();

        this.mainframeStorage.updateCapacity(0);
        this.energyStorage.setCapacity(0);

        this.componentCounts.clear();
        this.componentPositions.clear();
        this.core.activeFeatures.clear();
        this.core.activeApis.clear();

        this.setChanged();
        this.sync();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            // ※ CoreMachineVMCacheの引数がこのブロックエンティティを要求している場合はthisを渡します。
            // 将来的にはthis.coreを渡すようリファクタリングすると依存が綺麗になります。
            this.core.vm = CoreMachineVMCache.getOrCreateVM(this.core.machineId, this);
            if (this.core.vm instanceof CoreMachineServerLuaVM cvm) {
                cvm.syncClient();
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SimpleMachineBlockEntity entity) {
        if (level.isClientSide || entity.core == null || entity.core.isDisposed()) return;

        if (entity.isMainframeMaster) {
            if (entity.core.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                entity.core.virtualStorage.tick(level);
            }
            for (IMainframeExtension ext : entity.extensions.values()) {
                ext.tick(level, entity);
            }
        }

        if (entity.core.vm != null) {
            entity.core.vm.tick();
            if (entity.core.vm.isRunning) {
                entity.core.vm.triggerEvent("on_tick");
            }
        }
    }

    @Override
    public void setRemoved() {
        if (this.core != null) this.core.dispose();
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

        if (this.core != null) {
            tag.putUUID("MachineId", this.core.machineId);

            CompoundTag capTag = new CompoundTag();
            for (Map.Entry<String, Long> entry : this.core.resourceCapacities.entrySet()) capTag.putLong(entry.getKey(), entry.getValue());
            tag.put("ResourceCapacities", capTag);

            CompoundTag usageTag = new CompoundTag();
            for (Map.Entry<String, Long> entry : this.core.resourceUsages.entrySet()) usageTag.putLong(entry.getKey(), entry.getValue());
            tag.put("ResourceUsages", usageTag);

            tag.put("PersistentData", this.core.persistentData);
        }

        tag.putBoolean("IsMainframeMaster", this.isMainframeMaster);
        tag.putInt("MainframeMachines", this.mainframeMachines);

        CompoundTag posTag = new CompoundTag();
        for (Map.Entry<String, List<BlockPos>> entry : this.componentPositions.entrySet()) {
            long[] arr = new long[entry.getValue().size()];
            for (int i = 0; i < entry.getValue().size(); i++) {
                arr[i] = entry.getValue().get(i).asLong();
            }
            posTag.putLongArray(entry.getKey(), arr);
        }
        tag.put("ComponentPositions", posTag);

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
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        UUID loadedId = tag.contains("MachineId") ? tag.getUUID("MachineId") : UUID.randomUUID();
        if (this.core == null || !this.core.machineId.equals(loadedId)) {
            this.core = new MainframeCore(loadedId);
            this.core.bind(this);
            // ※再生成時はリソースプロバイダーの再登録を確実に行う
            this.core.registerResourceProvider("item", new IResourceProvider() {
                @Override public long getAmount() { return mainframeStorage.getTotalItems(); }
                @Override public long getCapacity() { return mainframeStorage.getCapacity(); }
            });
            this.core.registerResourceProvider("energy", new IResourceProvider() {
                @Override public long getAmount() { return energyStorage.getEnergyStored(); }
                @Override public long getCapacity() { return energyStorage.getMaxEnergyStored(); }
            });
        }

        updateDataFromTag(tag);

        long itemCap = this.core.resourceCapacities.getOrDefault("item", 0L);
        this.mainframeStorage.updateCapacity((int) Math.min(Integer.MAX_VALUE, itemCap));
        long energyCap = this.core.resourceCapacities.getOrDefault("energy", 0L);
        this.energyStorage.setCapacity((int) Math.min(Integer.MAX_VALUE, energyCap));

        if (tag.contains("MasterPos")) this.masterPos = BlockPos.of(tag.getLong("MasterPos"));
        if (tag.contains("MainframeStorage")) {
            this.mainframeStorage.deserializeNBT(registries, tag.getCompound("MainframeStorage"));
        }
        if (tag.contains("Energy")) {
            this.energyStorage.deserializeNBT(registries, Objects.requireNonNull(tag.get("Energy")));
        }

        if (tag.contains("MainframeExtensions")) {
            CompoundTag extTag = tag.getCompound("MainframeExtensions");
            for (String keyStr : extTag.getAllKeys()) {
                ResourceLocation extId = ResourceLocation.parse(keyStr);
                IMainframeExtension ext = this.extensions.get(extId);
                if (ext == null) {
                    ext = MainframeExtensionRegistry.createInstance(extId);
                    if (ext != null) {
                        this.extensions.put(extId, ext);
                    }
                }
                if (ext != null) {
                    ext.deserializeNBT(extTag.getCompound(keyStr), registries);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);

        if (this.core != null && this.core.vm instanceof CoreMachineServerLuaVM cvm) {
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
        if (tag != null) {
            updateDataFromTag(tag);
            long itemCap = this.core.resourceCapacities.getOrDefault("item", 0L);
            this.mainframeStorage.updateCapacity((int) Math.min(Integer.MAX_VALUE, itemCap));
            long energyCap = this.core.resourceCapacities.getOrDefault("energy", 0L);
            this.energyStorage.setCapacity((int) Math.min(Integer.MAX_VALUE, energyCap));
        }
    }

    private void updateDataFromTag(CompoundTag tag) {
        if (this.core == null) return;

        if (tag.contains("TerminalLog")) {
            this.core.clientTerminalLog.clear();
            ListTag logTag = tag.getList("TerminalLog", 8);
            for (int i = 0; i < logTag.size(); i++) this.core.clientTerminalLog.add(logTag.getString(i));
        }
        if (tag.contains("Suggestions")) {
            this.core.clientSuggestions.clear();
            ListTag suggestTag = tag.getList("Suggestions", 8);
            for (int i = 0; i < suggestTag.size(); i++) this.core.clientSuggestions.add(suggestTag.getString(i));
        }
        if (tag.contains("PersistentData")) this.core.persistentData.merge(tag.getCompound("PersistentData"));

        if (tag.contains("IsMainframeMaster")) this.isMainframeMaster = tag.getBoolean("IsMainframeMaster");
        if (tag.contains("MainframeMachines")) this.mainframeMachines = tag.getInt("MainframeMachines");

        this.core.resourceCapacities.clear();
        if (tag.contains("ResourceCapacities")) {
            CompoundTag capTag = tag.getCompound("ResourceCapacities");
            for (String key : capTag.getAllKeys()) this.core.resourceCapacities.put(key, capTag.getLong(key));
        }

        this.core.resourceUsages.clear();
        if (tag.contains("ResourceUsages")) {
            CompoundTag usageTag = tag.getCompound("ResourceUsages");
            for (String key : usageTag.getAllKeys()) this.core.resourceUsages.put(key, usageTag.getLong(key));
        }

        if (tag.contains("MainframeParts")) {
            this.mainframeParts.clear();
            for (long l : tag.getLongArray("MainframeParts")) this.mainframeParts.add(BlockPos.of(l));
        }

        this.componentPositions.clear();
        if (tag.contains("ComponentPositions")) {
            CompoundTag posTag = tag.getCompound("ComponentPositions");
            for (String key : posTag.getAllKeys()) {
                long[] arr = posTag.getLongArray(key);
                List<BlockPos> list = new ArrayList<>(arr.length);
                for (long l : arr) list.add(BlockPos.of(l));
                this.componentPositions.put(key, list);
            }
        }
    }
}