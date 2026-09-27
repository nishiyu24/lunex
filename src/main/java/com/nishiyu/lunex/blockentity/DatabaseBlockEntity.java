package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.menu.DatabaseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseBlockEntity extends BlockEntity implements MenuProvider, IMainframePart {

    public static final int BASE_CAPACITY_BYTES = 2097152;

    public final Map<String, Long> itemCounts = new ConcurrentHashMap<>();
    public final Map<String, String> itemIds = new ConcurrentHashMap<>();
    public final Map<String, String> itemNbtStrings = new ConcurrentHashMap<>();

    public final Map<String, String> storedPrograms = new ConcurrentHashMap<>();
    public final Map<String, Long> fluidCounts = new ConcurrentHashMap<>();

    private int clientItemBytes = 0;
    private int clientFluidBytes = 0;
    private int clientProgramBytes = 0;
    private int clientMaxBytes = BASE_CAPACITY_BYTES;

    public BlockPos mainframeMasterPos = null;

    public final ItemStackHandler upgradeHandler = new ItemStackHandler(7) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            }
        }
    };

    public DatabaseBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.DATABASE_BE.get(), pos, state);
    }

    @Override
    public void setMasterPos(BlockPos pos) { this.mainframeMasterPos = pos; this.setChanged(); }
    @Override
    public BlockPos getMasterPos() { return this.mainframeMasterPos; }

    public int getMaxCapacityBytes() {
        return BASE_CAPACITY_BYTES;
    }

    public int getItemUsedBytes() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientItemBytes;
        }
        long bytes = 0;
        for (Map.Entry<String, Long> entry : itemCounts.entrySet()) {
            String key = entry.getKey();
            long count = entry.getValue();
            String nbt = itemNbtStrings.get(key);
            if (nbt != null && !nbt.isEmpty()) {
                bytes += count * 4096L;
            } else {
                bytes += count * 1024L;
            }
        }
        return (int) bytes;
    }

    public int getFluidUsedBytes() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientFluidBytes;
        }
        long bytes = 0;
        for (long mb : fluidCounts.values()) {
            bytes += (long) ((mb / 1000.0) * 2048.0);
        }
        return (int) bytes;
    }

    public int getProgramUsedBytes() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientProgramBytes;
        }
        return storedPrograms.size() * 8192;
    }

    public int getUsedBytes() {
        return getItemUsedBytes() + getFluidUsedBytes() + getProgramUsedBytes();
    }

    public String getStorageUsageMB() {
        int usedBytes = getUsedBytes();
        double used = usedBytes / 1048576.0;
        if (usedBytes > 0 && used < 0.01) {
            used = 0.01;
        }
        double max = getMaxCapacityBytes() / 1048576.0;
        return String.format("%.2f MB / %.2f MB", used, max);
    }

    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompoundTag tag = null;
        if (this.level != null && !stack.getComponentsPatch().isEmpty()) {
            tag = (CompoundTag) stack.saveOptional(this.level.registryAccess());
        }
        String nbtStr = tag != null ? tag.toString() : "";
        String key = id + "|" + nbtStr;

        int bytesPerItem = (nbtStr != null && !nbtStr.isEmpty()) ? 4096 : 1024;
        long totalAddedBytes = (long) stack.getCount() * bytesPerItem;
        int currentUsed = getUsedBytes();

        int insertCount = stack.getCount();
        if (currentUsed + totalAddedBytes > getMaxCapacityBytes()) {
            int remainingBytes = getMaxCapacityBytes() - currentUsed;
            insertCount = remainingBytes / bytesPerItem;
            if (insertCount <= 0) return stack.copy();
        }

        if (!simulate) {
            itemCounts.put(key, itemCounts.getOrDefault(key, 0L) + insertCount);
            itemIds.putIfAbsent(key, id);
            itemNbtStrings.putIfAbsent(key, nbtStr);
            this.setChanged();

            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
            }
        }

        if (insertCount == stack.getCount()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack remainder = stack.copy();
            remainder.shrink(insertCount);
            return remainder;
        }
    }

    public long insertFluid(String fluidId, long amountMb, boolean simulate) {
        if (amountMb <= 0) return 0;

        int currentUsed = getUsedBytes();
        long addedBytes = (long) ((amountMb / 1000.0) * 2048.0);

        long insertAmount = amountMb;
        if (currentUsed + addedBytes > getMaxCapacityBytes()) {
            int remainingBytes = getMaxCapacityBytes() - currentUsed;
            insertAmount = (long) ((remainingBytes / 2048.0) * 1000.0);
            if (insertAmount <= 0) return 0;
        }

        if (!simulate) {
            fluidCounts.put(fluidId, fluidCounts.getOrDefault(fluidId, 0L) + insertAmount);
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
            }
        }
        return insertAmount;
    }

    public ItemStack extractItem(String targetId, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;

        for (Map.Entry<String, String> entry : itemIds.entrySet()) {
            if (entry.getValue().equals(targetId)) {
                String key = entry.getKey();
                long currentCount = itemCounts.getOrDefault(key, 0L);
                if (currentCount > 0) {
                    int extractAmount = (int) Math.min(amount, currentCount);
                    if (!simulate) {
                        itemCounts.put(key, currentCount - extractAmount);
                        if (itemCounts.get(key) <= 0) {
                            itemCounts.remove(key);
                            itemIds.remove(key);
                            itemNbtStrings.remove(key);
                        }
                        this.setChanged();

                        if (this.level != null && !this.level.isClientSide) {
                            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
                        }
                    }

                    try {
                        String nbtStr = itemNbtStrings.get(key);
                        if (nbtStr != null && !nbtStr.isEmpty() && this.level != null) {
                            CompoundTag tag = TagParser.parseTag(nbtStr);
                            ItemStack parsed = ItemStack.parse(this.level.registryAccess(), tag).orElse(ItemStack.EMPTY);
                            parsed.setCount(extractAmount);
                            return parsed;
                        } else {
                            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(targetId));
                            return new ItemStack(item, extractAmount);
                        }
                    } catch (Exception e) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("PersistentData", this.getPersistentData());
        tag.put("UpgradeInventory", upgradeHandler.serializeNBT(registries));

        ListTag itemList = new ListTag();
        for (String key : itemCounts.keySet()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("Key", key);
            itemTag.putString("Id", itemIds.getOrDefault(key, ""));
            itemTag.putString("NbtStr", itemNbtStrings.getOrDefault(key, ""));
            itemTag.putLong("Count", itemCounts.get(key));
            itemList.add(itemTag);
        }
        tag.put("DatabaseItems", itemList);

        CompoundTag fluidTag = new CompoundTag();
        for (Map.Entry<String, Long> entry : fluidCounts.entrySet()) {
            fluidTag.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("DatabaseFluids", fluidTag);

        CompoundTag progTag = new CompoundTag();
        for (Map.Entry<String, String> entry : storedPrograms.entrySet()) {
            progTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("DatabasePrograms", progTag);

        if (this.mainframeMasterPos != null) tag.putLong("MainframeMasterPos", this.mainframeMasterPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("PersistentData")) {
            this.getPersistentData().merge(tag.getCompound("PersistentData"));
        }
        if (tag.contains("UpgradeInventory")) {
            upgradeHandler.deserializeNBT(registries, tag.getCompound("UpgradeInventory"));
        }

        itemCounts.clear();
        itemIds.clear();
        itemNbtStrings.clear();
        if (tag.contains("DatabaseItems")) {
            ListTag itemList = tag.getList("DatabaseItems", 10);
            for (int i = 0; i < itemList.size(); i++) {
                CompoundTag itemTag = itemList.getCompound(i);
                String key = itemTag.getString("Key");
                itemIds.put(key, itemTag.getString("Id"));
                itemNbtStrings.put(key, itemTag.getString("NbtStr"));
                itemCounts.put(key, itemTag.getLong("Count"));
            }
        }

        fluidCounts.clear();
        if (tag.contains("DatabaseFluids")) {
            CompoundTag fluidTag = tag.getCompound("DatabaseFluids");
            for (String key : fluidTag.getAllKeys()) {
                fluidCounts.put(key, fluidTag.getLong(key));
            }
        }

        storedPrograms.clear();
        if (tag.contains("DatabasePrograms")) {
            CompoundTag progTag = tag.getCompound("DatabasePrograms");
            for (String key : progTag.getAllKeys()) {
                storedPrograms.put(key, progTag.getString(key));
            }
        }

        if (tag.contains("MainframeMasterPos")) {
            this.mainframeMasterPos = BlockPos.of(tag.getLong("MainframeMasterPos"));
        } else {
            this.mainframeMasterPos = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.put("PersistentData", this.getPersistentData());
        tag.put("UpgradeInventory", upgradeHandler.serializeNBT(provider));
        tag.putInt("ClientItemBytes", this.getItemUsedBytes());
        tag.putInt("ClientFluidBytes", this.getFluidUsedBytes());
        tag.putInt("ClientProgramBytes", this.getProgramUsedBytes());
        tag.putInt("ClientMaxBytes", this.getMaxCapacityBytes());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        if (tag.contains("ClientItemBytes")) {
            this.clientItemBytes = tag.getInt("ClientItemBytes");
            this.clientProgramBytes = tag.getInt("ClientProgramBytes");
            this.clientMaxBytes = tag.getInt("ClientMaxBytes");
        }
        if (tag.contains("ClientFluidBytes")) {
            this.clientFluidBytes = tag.getInt("ClientFluidBytes");
        }
        if (tag.contains("PersistentData")) {
            this.getPersistentData().merge(tag.getCompound("PersistentData"));
        }
        if (tag.contains("UpgradeInventory")) {
            upgradeHandler.deserializeNBT(provider, tag.getCompound("UpgradeInventory"));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider provider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, provider);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lunex.database_block");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DatabaseMenu(containerId, playerInventory, this.worldPosition);
    }
}