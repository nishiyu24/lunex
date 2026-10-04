package com.nishiyu.lunex.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class MainframeItemHandler implements IItemHandler {
    private final NonNullList<ItemStack> stacks = NonNullList.create();
    private int capacity = 0;

    public void updateCapacity(int capacity) { this.capacity = capacity; }
    public int getCapacity() { return capacity; }

    public int getTotalItems() {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    public NonNullList<ItemStack> getStacks() {
        return stacks;
    }

    @Override
    public int getSlots() {
        return stacks.size() + 1;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (slot >= 0 && slot < stacks.size()) return stacks.get(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        int currentTotal = getTotalItems();
        if (currentTotal >= capacity) return stack;

        // 全体容量に収まる分だけを挿入対象とする
        int insertableAmount = Math.min(stack.getCount(), capacity - currentTotal);
        if (insertableAmount <= 0) return stack;

        ItemStack toInsert = stack.copyWithCount(insertableAmount);
        ItemStack remainder = stack.copyWithCount(stack.getCount() - insertableAmount);

        // 既存のスロットに対して、バニラの最大スタック数(64等)の空き容量分だけ埋める
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack existing = stacks.get(i);
            if (ItemStack.isSameItemSameComponents(existing, toInsert)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int addAmount = Math.min(space, toInsert.getCount());
                    if (!simulate) {
                        existing.grow(addAmount);
                        onContentsChanged(i);
                    }
                    toInsert.shrink(addAmount);
                    if (toInsert.isEmpty()) {
                        return remainder; // 全て挿入完了
                    }
                }
            }
        }

        // 既存のスロットに入りきらなかった分を、新しいスロットとして追加（最大スタック数で分割）
        while (!toInsert.isEmpty()) {
            int addAmount = Math.min(toInsert.getMaxStackSize(), toInsert.getCount());
            if (!simulate) {
                stacks.add(toInsert.copyWithCount(addAmount));
                onContentsChanged(stacks.size() - 1);
            }
            toInsert.shrink(addAmount);
        }

        return remainder;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;

        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int extracted = Math.min(amount, existing.getCount());
        ItemStack result = existing.copyWithCount(extracted);

        if (!simulate) {
            existing.shrink(extracted);
            if (existing.isEmpty()) stacks.remove(slot);
            onContentsChanged(slot);
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return getTotalItems() < capacity;
    }

    protected void onContentsChanged(int slot) {}

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) list.add(stack.saveOptional(provider));
        }
        nbt.put("Items", list);
        nbt.putInt("Capacity", capacity);
        return nbt;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        stacks.clear();
        if (nbt.contains("Items")) {
            ListTag list = nbt.getList("Items", 10);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(provider, list.getCompound(i));
                if (!stack.isEmpty()) stacks.add(stack);
            }
        }
        if (nbt.contains("Capacity")) capacity = nbt.getInt("Capacity");
    }
}