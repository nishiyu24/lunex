package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CrafterActionProvider implements IMainframeActionProvider<BlockEntity> {

    @Override
    public boolean handleAction(String action, String payload, BlockEntity be, Level level) {
        switch (action) {
            case "toggle_autocraft":
                CompoundTag recipeCheck = be.getPersistentData().getCompound("CrafterRecipe");
                if (!recipeCheck.getString("Slot_9").isEmpty()) {
                    boolean current = be.getPersistentData().getBoolean("AutoCraftActive");
                    be.getPersistentData().putBoolean("AutoCraftActive", !current);
                    be.setChanged();
                    level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                    notifyMaster(be, level);
                }
                return true;

            // ★変更: NBTフラグを立てるのをやめ、直接Extensionのメソッドを呼び出す（イベント式）
            case "force_craft":
                if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getMasterPos() != null) {
                    BlockEntity masterBe = level.getBlockEntity(adapter.getMasterPos());
                    if (masterBe instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity master) {
                        // マスター機に登録されているCrafterExtensionを取得して実行
                        com.nishiyu.lunex.api.mainframe.IMainframeExtension ext = master.getExtension(ResourceLocation.parse("lunex:crafter"));
                        if (ext instanceof com.nishiyu.lunex.api.mainframe.extension.CrafterExtension crafterExt) {
                            crafterExt.forceCraft(master, adapter, level);
                        }
                    }
                }
                return true;

            case "set_crafter_recipe":
                int colonIndex = payload.indexOf(':');
                if (colonIndex >= 0) {
                    try {
                        int slot = Integer.parseInt(payload.substring(0, colonIndex));
                        String itemId = payload.substring(colonIndex + 1);

                        CompoundTag recipeTag = be.getPersistentData().getCompound("CrafterRecipe");
                        if (itemId.isEmpty()) {
                            recipeTag.remove("Slot_" + slot);
                        } else {
                            recipeTag.putString("Slot_" + slot, itemId);
                        }

                        updateRecipeResult(recipeTag, level);

                        be.getPersistentData().putBoolean("AutoCraftActive", false);

                        be.getPersistentData().put("CrafterRecipe", recipeTag);
                        be.setChanged();
                        level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                        notifyMaster(be, level);
                    } catch (NumberFormatException ignored) {}
                }
                return true;

            case "clear_crafter_recipe":
                if (be instanceof MainframeAdapterBlockEntity adapter) {
                    CompoundTag clearOrig = adapter.getOriginalNbt();
                    if (clearOrig != null) {
                        clearOrig.remove("Items");
                        adapter.setOriginalBlock(adapter.getOriginalState(), clearOrig);
                        adapter.setChanged();
                        notifyMaster(adapter, level);
                    }
                }
                return true;
        }
        return false;
    }

    private void updateRecipeResult(CompoundTag recipeTag, Level level) {
        List<ItemStack> inputItems = new ArrayList<>();
        boolean isEmpty = true;

        for (int i = 0; i < 9; i++) {
            String itemId = recipeTag.getString("Slot_" + i);
            if (!itemId.isEmpty()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                if (item != Items.AIR) {
                    inputItems.add(new ItemStack(item));
                    isEmpty = false;
                    continue;
                }
            }
            inputItems.add(ItemStack.EMPTY);
        }

        if (isEmpty) {
            recipeTag.remove("Slot_9");
            recipeTag.remove("ResultCount");
            return;
        }

        CraftingInput input = CraftingInput.of(3, 3, inputItems);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);

        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
            if (!result.isEmpty()) {
                String resultId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
                recipeTag.putString("Slot_9", resultId);
                recipeTag.putInt("ResultCount", result.getCount());
                return;
            }
        }
        recipeTag.remove("Slot_9");
        recipeTag.remove("ResultCount");
    }

    private void notifyMaster(BlockEntity be, Level level) {
        if (be instanceof com.nishiyu.lunex.machine.IMainframePart part && part.getMasterPos() != null) {
            BlockEntity master = level.getBlockEntity(part.getMasterPos());
            if (master != null) {
                master.setChanged();
            }
        }
    }
}