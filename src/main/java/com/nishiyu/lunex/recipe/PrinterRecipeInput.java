package com.nishiyu.lunex.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public record PrinterRecipeInput(IItemHandler itemHandler) implements RecipeInput {
    @Override
    public @NotNull ItemStack getItem(int index) {
        return itemHandler.getStackInSlot(index);
    }

    @Override
    public int size() {
        return 3; // 入力スロットは3つ
    }
}