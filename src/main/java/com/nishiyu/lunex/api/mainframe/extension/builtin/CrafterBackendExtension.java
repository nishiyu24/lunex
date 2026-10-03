package com.nishiyu.lunex.api.mainframe.extension.builtin;

import com.nishiyu.lunex.api.mainframe.extension.IMainframeExtension;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CrafterBackendExtension implements IMainframeExtension {

    private static final int CRAFT_COOLDOWN_TICKS = 20;

    @Override
    public void onAssembled(SimpleMachineBlockEntity master) {}

    @Override
    public void onDisassembled(SimpleMachineBlockEntity master) {}

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) { return new CompoundTag(); }

    @Override
    public void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) {}

    @Override
    public Object getCapabilityInstance() { return null; }

    @Override
    public void tick(Level level, SimpleMachineBlockEntity master) {
        if (level.getGameTime() % CRAFT_COOLDOWN_TICKS != 0) return;

        for (BlockPos pos : master.mainframeParts) {
            if (level.getBlockEntity(pos) instanceof MainframeAdapterBlockEntity adapter) {
                if (adapter.getOriginalState() != null && adapter.getOriginalState().is(Blocks.CRAFTER)) {
                    if (adapter.getPersistentData().getBoolean("AutoCraftActive")) {
                        processAutoCraft(level, master, adapter);
                    }
                }
            }
        }
    }

    private void processAutoCraft(Level level, SimpleMachineBlockEntity master, MainframeAdapterBlockEntity crafter) {
        CompoundTag originalNbt = crafter.getOriginalNbt();
        if (originalNbt == null || !originalNbt.contains("Items")) return;

        // Crafter内のアイテムをクラフトのレシピ設計図として読み取る
        NonNullList<ItemStack> blueprintItems = NonNullList.withSize(9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(originalNbt, blueprintItems, level.registryAccess());

        boolean isEmpty = true;
        for (ItemStack stack : blueprintItems) {
            if (!stack.isEmpty()) {
                isEmpty = false;
                break;
            }
        }
        if (isEmpty) return;

        CraftingInput input = CraftingInput.of(3, 3, blueprintItems);
        Optional<RecipeHolder<CraftingRecipe>> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) return;

        RecipeHolder<CraftingRecipe> recipeHolder = match.get();
        IItemHandler storage = master.mainframeStorage;

        List<Integer> extractSlots = new ArrayList<>();
        List<ItemStack> requiredItems = new ArrayList<>();

        // 1. マスターのストレージから消費できる素材があるか検索
        for (ItemStack required : blueprintItems) {
            if (required.isEmpty()) continue;

            boolean found = false;
            for (int i = 0; i < storage.getSlots(); i++) {
                ItemStack stored = storage.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(required, stored)) {
                    long alreadyCounted = requiredItems.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stored)).count();
                    // すでに消費予定とした分を差し引いても残っているか確認
                    if (stored.getCount() > alreadyCounted) {
                        extractSlots.add(i);
                        requiredItems.add(stored);
                        found = true;
                        break;
                    }
                }
            }
            // 必要な素材が一つでも足りなければクラフトを中止
            if (!found) return;
        }

        // 2. クラフト結果のアイテムを取得し、ストレージに空きがあるかシミュレーション
        ItemStack result = recipeHolder.value().assemble(input, level.registryAccess());
        ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(storage, result.copy(), true);
        if (!simulatedRemainder.isEmpty()) return; // 容量不足で入らないため中止

        // 3. 実際に素材を消費
        for (int slot : extractSlots) {
            storage.extractItem(slot, 1, false);
        }

        // 4. 完成品をマスターのストレージに格納
        ItemHandlerHelper.insertItemStacked(storage, result, false);
    }
}