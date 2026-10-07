package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.machine.frame.IMainframePart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

public class FurnaceActionProvider implements IMainframeActionProvider<BlockEntity> {

    @Override
    public boolean handleAction(String action, String payload, BlockEntity be, Level level, ServerPlayer player) {
        switch (action) {
            case "toggle_autosmelt":
                CompoundTag dataCheck = be.getPersistentData();
                if (!dataCheck.getString("SmeltTarget").isEmpty()) {
                    boolean current = dataCheck.getBoolean("AutoSmeltActive");
                    dataCheck.putBoolean("AutoSmeltActive", !current);
                    be.setChanged();
                    level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                }
                return true;

            case "set_furnace_target":
                String itemId = payload;
                CompoundTag data = be.getPersistentData();

                data.remove("SmeltTarget");
                data.remove("CookTimeTotal");
                data.remove("SmeltResult");
                data.remove("SmeltResultCount");
                data.putInt("CookTime", 0);

                if (!itemId.isEmpty()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    if (item != Items.AIR) {
                        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
                        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);

                        if (recipe.isPresent()) {
                            data.putString("SmeltTarget", itemId);
                            data.putInt("CookTimeTotal", recipe.get().value().getCookingTime());
                            ItemStack resultStack = recipe.get().value().assemble(input, level.registryAccess());
                            data.putString("SmeltResult", BuiltInRegistries.ITEM.getKey(resultStack.getItem()).toString());
                            data.putInt("SmeltResultCount", resultStack.getCount());
                        }
                    }
                }

                data.putBoolean("AutoSmeltActive", false);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                notifyMaster(be, level);
                return true;
        }
        return false;
    }

    private void notifyMaster(BlockEntity be, Level level) {
        if (be instanceof IMainframePart part && part.getMasterPos() != null) {
            BlockEntity master = level.getBlockEntity(part.getMasterPos());
            if (master != null) {
                master.setChanged();
            }
        }
    }
}