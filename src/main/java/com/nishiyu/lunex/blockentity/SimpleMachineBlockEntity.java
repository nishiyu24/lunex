package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeItemHandler;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.machine.SimpleMachineVMCache;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.program.server.machine.SimpleMachineServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SimpleMachineBlockEntity extends AdvancedMachineBlockEntity implements IMainframePart, IMCNetDevice {

    public final List<String> clientTerminalLog = new ArrayList<>();
    public final List<String> clientSuggestions = new ArrayList<>();

    public boolean isMainframeMaster = false;
    public BlockPos masterPos = null;
    public final List<BlockPos> mainframeParts = new ArrayList<>();
    public final List<BlockPos> mainframeDatabases = new ArrayList<>();

    public int mainframeMachines = 1;
    public int mainframeTotalCapacityBytes = 0;

    // ★追加: 容量の内訳
    public int mainframeUsedItemBytes = 0;
    public int mainframeUsedFluidBytes = 0;
    public int mainframeUsedProgramBytes = 0;

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
    }

    @Override
    public boolean isNetworkActive() {
        return true;
    }

    @Override
    public void setMasterPos(BlockPos pos) { this.masterPos = pos; this.setChanged(); }
    @Override
    public BlockPos getMasterPos() { return this.masterPos; }

    // ★追加: アイテム、流体、プログラムの使用容量を計算・更新する
    public void updateCapacityUsage() {
        if (level == null || level.isClientSide) return;
        int fluidB = 0, progB = 0, maxB = 0;
        for (BlockPos dbPos : mainframeDatabases) {
            if (level.getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                fluidB += db.getFluidUsedBytes();
                progB += db.getProgramUsedBytes();
                maxB += db.getMaxCapacityBytes();
            }
        }
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
        this.mainframeUsedFluidBytes = fluidB;
        this.mainframeUsedProgramBytes = progB;
        this.mainframeTotalCapacityBytes = maxB;
    }

    public void rebuildMainframe() {
        if (level == null || level.isClientSide) return;

        // 仮で容量を確保
        int tempMax = 0;
        for (BlockPos dbPos : mainframeDatabases) {
            if (level.getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                tempMax += db.getMaxCapacityBytes();
            }
        }
        this.mainframeStorage.updateCapacity(tempMax);

        List<ItemStack> collectedItems = new ArrayList<>();
        for (BlockPos dbPos : mainframeDatabases) {
            if (level.getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                for (String key : db.itemCounts.keySet()) {
                    String id = db.itemIds.get(key);
                    int amount = db.itemCounts.get(key).intValue();
                    ItemStack extracted = db.extractItem(id, amount, false);
                    if (!extracted.isEmpty()) {
                        collectedItems.add(extracted);
                    }
                }
            }
        }

        for (ItemStack stack : collectedItems) {
            for (int i = 0; i < this.mainframeStorage.getSlots(); i++) {
                stack = this.mainframeStorage.insertItem(i, stack, false);
                if (stack.isEmpty()) break;
            }
        }

        updateCapacityUsage();

        for (BlockPos pos : this.mainframeParts) {
            if (level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
                screen.updateUnlockTier(this.mainframeMachines);
            }
        }

        if (this.vm instanceof SimpleMachineServerLuaVM svm) {
            svm.terminalLog.add("[System] Mainframe assembled.");
            svm.terminalLog.add("[System] CPUs: " + this.mainframeMachines + ", Capacity: " + (this.mainframeTotalCapacityBytes / 1048576) + "MB");
            svm.syncClient();
        }
        this.setChanged();
        this.sync();
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    public void disassembleMainframe() {
        if (level == null || level.isClientSide || !isMainframeMaster) return;

        List<ItemStack> itemsToDistribute = new ArrayList<>();
        for (ItemStack stack : this.mainframeStorage.getAllItems()) {
            if (!stack.isEmpty()) {
                itemsToDistribute.add(stack.copy());
            }
        }

        for (BlockPos dbPos : mainframeDatabases) {
            if (level.getBlockEntity(dbPos) instanceof DatabaseBlockEntity db) {
                for (int i = 0; i < itemsToDistribute.size(); i++) {
                    ItemStack stack = itemsToDistribute.get(i);
                    if (stack.isEmpty()) continue;

                    ItemStack remainder = db.insertItem(stack, false);
                    itemsToDistribute.set(i, remainder);
                }
            }
        }

        if (this.vm instanceof com.nishiyu.lunex.program.server.machine.SimpleMachineServerLuaVM svm) {
            svm.terminalLog.add("[System] CRITICAL: Mainframe connection lost. Disassembled.");
            svm.syncClient();
        }

        List<BlockPos> previousParts = new ArrayList<>(this.mainframeParts);

        for (BlockPos pos : mainframeParts) {
            if (level.getBlockEntity(pos) instanceof IMainframePart part) {
                if (part instanceof ScreenBlockEntity screen) {
                    screen.updateUnlockTier(0);
                }
                part.setMasterPos(null);
            }
        }

        this.isMainframeMaster = false;
        this.mainframeParts.clear();
        this.mainframeDatabases.clear();
        this.mainframeTotalCapacityBytes = 0;
        this.mainframeUsedItemBytes = 0;
        this.mainframeUsedFluidBytes = 0;
        this.mainframeUsedProgramBytes = 0;
        this.mainframeMachines = 1;
        this.mainframeStorage.updateCapacity(0);
        this.setChanged();
        this.sync();
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);

        MainframeScanner.updateMainframeVisuals(level, previousParts, false);
    }

    @Override
    public void onLoad() {
        this.updateChunkLoading();
        if (this.level != null && !this.level.isClientSide) {
            if (this.machineId == null) {
                this.machineId = UUID.randomUUID();
            }
            this.vm = SimpleMachineVMCache.getOrCreateVM(this.machineId, this);

            if (this.vm instanceof SimpleMachineServerLuaVM svm) {
                svm.syncClient();
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SimpleMachineBlockEntity entity) {
        if (level.isClientSide || entity.vm == null) return;
        entity.vm.tick();
        if (entity.vm.isRunning) {
            entity.vm.triggerEvent("on_tick");
        }
    }

    @Override
    public void setRemoved() {
        if (this.vm != null) {
            this.vm.stopProgram();
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsMainframeMaster", this.isMainframeMaster);
        tag.putInt("MainframeMachines", this.mainframeMachines);
        tag.putInt("MainframeTotalCapacityBytes", this.mainframeTotalCapacityBytes);
        tag.putInt("MainframeUsedItemBytes", this.mainframeUsedItemBytes);
        tag.putInt("MainframeUsedFluidBytes", this.mainframeUsedFluidBytes);
        tag.putInt("MainframeUsedProgramBytes", this.mainframeUsedProgramBytes);
        if (this.masterPos != null) tag.putLong("MasterPos", this.masterPos.asLong());
        tag.put("MainframeStorage", this.mainframeStorage.serializeNBT(registries));

        if (!this.mainframeParts.isEmpty()) {
            long[] partsArray = new long[this.mainframeParts.size()];
            for (int i = 0; i < this.mainframeParts.size(); i++) partsArray[i] = this.mainframeParts.get(i).asLong();
            tag.putLongArray("MainframeParts", partsArray);
        }
        if (!this.mainframeDatabases.isEmpty()) {
            long[] dbArray = new long[this.mainframeDatabases.size()];
            for (int i = 0; i < this.mainframeDatabases.size(); i++) dbArray[i] = this.mainframeDatabases.get(i).asLong();
            tag.putLongArray("MainframeDatabases", dbArray);
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        updateDataFromTag(tag);
        if (tag.contains("MasterPos")) this.masterPos = BlockPos.of(tag.getLong("MasterPos"));
        if (tag.contains("MainframeStorage")) {
            this.mainframeStorage.deserializeNBT(registries, tag.getCompound("MainframeStorage"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);

        tag.putBoolean("IsMainframeMaster", this.isMainframeMaster);
        tag.putInt("MainframeMachines", this.mainframeMachines);
        tag.putInt("MainframeTotalCapacityBytes", this.mainframeTotalCapacityBytes);
        tag.putInt("MainframeUsedItemBytes", this.mainframeUsedItemBytes);
        tag.putInt("MainframeUsedFluidBytes", this.mainframeUsedFluidBytes);
        tag.putInt("MainframeUsedProgramBytes", this.mainframeUsedProgramBytes);
        tag.put("PersistentData", this.persistentData);
        tag.put("MainframeStorage", this.mainframeStorage.serializeNBT(provider));

        if (!this.mainframeParts.isEmpty()) {
            long[] partsArray = new long[this.mainframeParts.size()];
            for (int i = 0; i < this.mainframeParts.size(); i++) partsArray[i] = this.mainframeParts.get(i).asLong();
            tag.putLongArray("MainframeParts", partsArray);
        }
        if (!this.mainframeDatabases.isEmpty()) {
            long[] dbArray = new long[this.mainframeDatabases.size()];
            for (int i = 0; i < this.mainframeDatabases.size(); i++) dbArray[i] = this.mainframeDatabases.get(i).asLong();
            tag.putLongArray("MainframeDatabases", dbArray);
        }

        if (this.vm instanceof SimpleMachineServerLuaVM svm) {
            ListTag logTag = new ListTag();
            for (String line : svm.terminalLog) {
                logTag.add(StringTag.valueOf(line));
            }
            tag.put("TerminalLog", logTag);

            ListTag suggestTag = new ListTag();
            for (String s : svm.suggestions) {
                suggestTag.add(StringTag.valueOf(s));
            }
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
        }
    }

    private void updateDataFromTag(CompoundTag tag) {
        if (tag.contains("TerminalLog")) {
            this.clientTerminalLog.clear();
            ListTag logTag = tag.getList("TerminalLog", 8);
            for (int i = 0; i < logTag.size(); i++) {
                this.clientTerminalLog.add(logTag.getString(i));
            }
        }
        if (tag.contains("Suggestions")) {
            this.clientSuggestions.clear();
            ListTag suggestTag = tag.getList("Suggestions", 8);
            for (int i = 0; i < suggestTag.size(); i++) {
                this.clientSuggestions.add(suggestTag.getString(i));
            }
        }

        if (tag.contains("PersistentData")) {
            this.persistentData.merge(tag.getCompound("PersistentData"));
        }

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
        if (tag.contains("MainframeDatabases")) {
            this.mainframeDatabases.clear();
            for (long l : tag.getLongArray("MainframeDatabases")) this.mainframeDatabases.add(BlockPos.of(l));
        }
    }
}