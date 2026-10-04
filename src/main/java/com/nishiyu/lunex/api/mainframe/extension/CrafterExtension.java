package com.nishiyu.lunex.api.mainframe.extension;

import com.nishiyu.lunex.api.mainframe.IMainframeExtension;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrafterExtension implements IMainframeExtension {

    private int tickCounter = 0;

    public CrafterExtension() { }

    @Override
    public void onAssembled(SimpleMachineBlockEntity master) {

    }

    @Override
    public void onDisassembled(SimpleMachineBlockEntity master) {

    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) { return new CompoundTag(); }

    @Override
    public void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) { }

    @Override
    public Object getCapabilityInstance() { return this; }

    @Override
    public void tick(Level level, SimpleMachineBlockEntity master) {
        if (level.isClientSide) return;

        tickCounter++;
        // ★修正: 毎tickのフラグ監視を撤廃。純粋に1秒に1回のオートクラフトのみを処理する
        if (tickCounter >= 20) {
            tickCounter = 0;

            List<BlockPos> crafters = master.componentPositions.get("minecraft:crafter");
            if (crafters == null || crafters.isEmpty()) return;

            for (BlockPos pos : crafters) {
                if (level.getBlockEntity(pos) instanceof MainframeAdapterBlockEntity adapter) {
                    if (adapter.getPersistentData().getBoolean("AutoCraftActive")) {
                        tryAutoCraft(master, adapter, level);
                    }
                }
            }
        }
    }

    // ★追加: イベント駆動で外部(ActionProvider)から即時実行するためのパブリックメソッド
    public void forceCraft(SimpleMachineBlockEntity master, MainframeAdapterBlockEntity crafterAdapter, Level level) {
        tryAutoCraft(master, crafterAdapter, level);
    }

    private void tryAutoCraft(SimpleMachineBlockEntity master, MainframeAdapterBlockEntity crafterAdapter, Level level) {
        CompoundTag recipeTag = crafterAdapter.getPersistentData().getCompound("CrafterRecipe");

        String outputId = recipeTag.getString("Slot_9");
        if (outputId.isEmpty()) return;

        Item outputItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(outputId));
        if (outputItem == Items.AIR) return;

        int count = recipeTag.contains("ResultCount") ? recipeTag.getInt("ResultCount") : 1;
        ItemStack recipeOutput = new ItemStack(outputItem, count);

        Map<Item, Integer> consolidatedReqs = new HashMap<>();

        for (int i = 0; i < 9; i++) {
            String ingredientId = recipeTag.getString("Slot_" + i);
            if (!ingredientId.isEmpty()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredientId));
                if (item != Items.AIR) {
                    consolidatedReqs.put(item, consolidatedReqs.getOrDefault(item, 0) + 1);
                }
            }
        }

        if (consolidatedReqs.isEmpty()) return;

        if (consumeIngredients(master, consolidatedReqs, true)) {
            consumeIngredients(master, consolidatedReqs, false);

            ItemStack remain = ItemHandlerHelper.insertItemStacked(master.mainframeStorage, recipeOutput, false);

            if (!remain.isEmpty()) {
                Block.popResource(level, master.getBlockPos().above(), remain);
            }

            master.updateResourceUsages();
            master.setChanged();
        }
    }

    private boolean consumeIngredients(SimpleMachineBlockEntity master, Map<Item, Integer> requirements, boolean simulate) {
        Map<Integer, Integer> extractionPlan = new HashMap<>();

        for (Map.Entry<Item, Integer> entry : requirements.entrySet()) {
            Item reqItem = entry.getKey();
            int needed = entry.getValue();

            for (int i = 0; i < master.mainframeStorage.getSlots() && needed > 0; i++) {
                ItemStack inSlot = master.mainframeStorage.getStackInSlot(i);
                int alreadyPlanned = extractionPlan.getOrDefault(i, 0);
                int available = inSlot.getCount() - alreadyPlanned;

                if (available > 0 && inSlot.is(reqItem)) {
                    int extract = Math.min(needed, available);
                    extractionPlan.put(i, alreadyPlanned + extract);
                    needed -= extract;
                }
            }

            if (needed > 0) return false;
        }

        if (!simulate) {
            for (Map.Entry<Integer, Integer> entry : extractionPlan.entrySet()) {
                master.mainframeStorage.extractItem(entry.getKey(), entry.getValue(), false);
            }
        }
        return true;
    }
}