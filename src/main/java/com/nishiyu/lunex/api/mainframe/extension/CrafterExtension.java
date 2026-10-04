package com.nishiyu.lunex.api.mainframe.extension;

import com.nishiyu.lunex.api.mainframe.IMainframeExtension;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrafterExtension implements IMainframeExtension {

    // テスト用の固定レシピ (鉄インゴット9個 -> 鉄ブロック1個)
    // ※後でCrafterUIExtensionから動的に設定できるように拡張できます
    private final List<ItemStack> recipeIngredients = new ArrayList<>();
    private ItemStack recipeOutput = new ItemStack(Items.IRON_BLOCK, 1);

    private int tickCounter = 0;

    public CrafterExtension() {
        for(int i = 0; i < 9; i++) {
            recipeIngredients.add(new ItemStack(Items.IRON_INGOT, 1));
        }
    }

    @Override
    public void onAssembled(SimpleMachineBlockEntity master) {
    }

    @Override
    public void onDisassembled(SimpleMachineBlockEntity master) {
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!recipeOutput.isEmpty()) {
            tag.put("RecipeOutput", recipeOutput.saveOptional(provider));
        }
        CompoundTag ingredientsTag = new CompoundTag();
        ingredientsTag.putInt("Size", recipeIngredients.size());
        for (int i = 0; i < recipeIngredients.size(); i++) {
            ingredientsTag.put("Item" + i, recipeIngredients.get(i).saveOptional(provider));
        }
        tag.put("RecipeIngredients", ingredientsTag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("RecipeOutput")) {
            recipeOutput = ItemStack.parseOptional(provider, tag.getCompound("RecipeOutput"));
        }
        recipeIngredients.clear();
        if (tag.contains("RecipeIngredients")) {
            CompoundTag ingredientsTag = tag.getCompound("RecipeIngredients");
            int size = ingredientsTag.getInt("Size");
            for (int i = 0; i < size; i++) {
                recipeIngredients.add(ItemStack.parseOptional(provider, ingredientsTag.getCompound("Item" + i)));
            }
        }
    }

    @Override
    public Object getCapabilityInstance() {
        return this;
    }

    @Override
    public void tick(Level level, SimpleMachineBlockEntity master) {
        if (level.isClientSide) return;

        tickCounter++;
        // 20tick(1秒)ごとにクラフト処理を実行
        if (tickCounter >= 20) {
            tickCounter = 0;

            // ネットワーク内の全パーツからCrafterを探し、個別にON/OFFを判定して実行する
            for (BlockPos pos : master.mainframeParts) {
                if (level.getBlockEntity(pos) instanceof MainframeAdapterBlockEntity adapter) {
                    if (adapter.getOriginalState() != null && adapter.getOriginalState().is(net.minecraft.world.level.block.Blocks.CRAFTER)) {
                        // このCrafterがONに設定されている場合のみクラフト試行
                        if (adapter.getPersistentData().getBoolean("AutoCraftActive")) {
                            tryAutoCraft(master);
                        }
                    }
                }
            }
        }
    }

    private void tryAutoCraft(SimpleMachineBlockEntity master) {
        if (recipeOutput.isEmpty() || recipeIngredients.isEmpty()) return;

        // 1. 完成品がストレージに収納できるかシミュレート
        ItemStack remain = ItemHandlerHelper.insertItemStacked(master.mainframeStorage, recipeOutput.copy(), true);
        if (!remain.isEmpty()) return;

        // 2. 素材がインベントリから引き出せるかシミュレート
        if (consumeIngredients(master, true)) {
            // 3. 実際に素材を消費
            consumeIngredients(master, false);
            // 4. 完成品をストレージに挿入
            ItemHandlerHelper.insertItemStacked(master.mainframeStorage, recipeOutput.copy(), false);
            // 5. 容量・アイテム数の更新をトリガー
            master.updateResourceUsages();
            master.setChanged();
        }
    }

    private boolean consumeIngredients(SimpleMachineBlockEntity master, boolean simulate) {
        List<ItemStack> requirements = new ArrayList<>();
        for (ItemStack req : recipeIngredients) {
            if (!req.isEmpty()) requirements.add(req.copy());
        }

        Map<Integer, Integer> extractionPlan = new HashMap<>();

        for (ItemStack req : requirements) {
            int needed = req.getCount();
            for (int i = 0; i < master.mainframeStorage.getSlots() && needed > 0; i++) {
                ItemStack inSlot = master.mainframeStorage.getStackInSlot(i);
                int alreadyPlanned = extractionPlan.getOrDefault(i, 0);
                int available = inSlot.getCount() - alreadyPlanned;

                if (available > 0 && ItemStack.isSameItemSameComponents(inSlot, req)) {
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