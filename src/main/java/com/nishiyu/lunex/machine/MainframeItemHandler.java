package com.nishiyu.lunex.machine;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MainframeItemHandler extends ItemStackHandler {

    private int maxCapacityBytes = 0;

    public MainframeItemHandler() {
        super(1); // 初期状態
    }

    public void updateCapacity(int totalBytes) {
        this.maxCapacityBytes = totalBytes;
        // 簡易的な計算: 1スロットあたり約1000バイトとしてスロット数を拡張
        int requiredSlots = Math.max(1, totalBytes / 1000);

        NonNullList<ItemStack> newStacks = NonNullList.withSize(requiredSlots, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(this.stacks.size(), requiredSlots); i++) {
            newStacks.set(i, this.stacks.get(i));
        }
        this.stacks = newStacks;
    }

    public int getMaxCapacityBytes() {
        return this.maxCapacityBytes;
    }

    // すべてのアイテムを取得（解体時の分散用）
    public NonNullList<ItemStack> getAllItems() {
        return this.stacks;
    }
}