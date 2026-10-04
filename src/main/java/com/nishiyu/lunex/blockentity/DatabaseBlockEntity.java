package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseBlockEntity extends BlockEntity implements IMainframePart {

    // 最大容量（アイテム個数ベース）
    public static final int BASE_CAPACITY = 1000;

    public final Map<String, Long> itemCounts = new ConcurrentHashMap<>();
    public final Map<String, String> itemIds = new ConcurrentHashMap<>();
    public final Map<String, String> itemNbtStrings = new ConcurrentHashMap<>();

    public final Map<String, String> storedPrograms = new ConcurrentHashMap<>();
    public final Map<String, Long> fluidCounts = new ConcurrentHashMap<>();

    private int clientItemCount = 0;
    private int clientFluidCount = 0;
    private int clientProgramCount = 0;
    private int clientMaxCount = BASE_CAPACITY;

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

    public int getMaxCapacity() {
        return BASE_CAPACITY;
    }

    public int getItemUsedCount() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientItemCount;
        }
        long count = 0;
        for (long c : itemCounts.values()) {
            count += c;
        }
        return (int) count;
    }

    public int getFluidUsedCount() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientFluidCount;
        }
        long count = 0;
        for (long mb : fluidCounts.values()) {
            count += mb; // 1mB = 1アイテム
        }
        return (int) count;
    }

    public int getProgramUsedCount() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientProgramCount;
        }
        return storedPrograms.size() * 5; // 1プログラム = 5アイテム
    }

    public int getUsedCount() {
        return getItemUsedCount() + getFluidUsedCount() + getProgramUsedCount();
    }

    public String getStorageUsageString() {
        return String.format("%d / %d", getUsedCount(), getMaxCapacity());
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

        int currentUsed = getUsedCount();
        int insertCount = stack.getCount();

        if (currentUsed + insertCount > getMaxCapacity()) {
            insertCount = getMaxCapacity() - currentUsed;
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

        int currentUsed = getUsedCount();
        long insertAmount = amountMb;

        if (currentUsed + insertAmount > getMaxCapacity()) {
            insertAmount = getMaxCapacity() - currentUsed;
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
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
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
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
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
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.put("PersistentData", this.getPersistentData());
        tag.put("UpgradeInventory", upgradeHandler.serializeNBT(provider));
        tag.putInt("ClientItemCount", this.getItemUsedCount());
        tag.putInt("ClientFluidCount", this.getFluidUsedCount());
        tag.putInt("ClientProgramCount", this.getProgramUsedCount());
        tag.putInt("ClientMaxCount", this.getMaxCapacity());
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        super.handleUpdateTag(tag, provider);
        if (tag.contains("ClientItemCount")) {
            this.clientItemCount = tag.getInt("ClientItemCount");
            this.clientProgramCount = tag.getInt("ClientProgramCount");
            this.clientMaxCount = tag.getInt("ClientMaxCount");
        }
        if (tag.contains("ClientFluidCount")) {
            this.clientFluidCount = tag.getInt("ClientFluidCount");
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
    public void onDataPacket(net.minecraft.network.@NotNull Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = pkt.getTag();
        handleUpdateTag(tag, provider);
    }
}