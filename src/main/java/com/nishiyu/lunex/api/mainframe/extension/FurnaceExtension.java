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

import java.util.List;

public class FurnaceExtension implements IMainframeExtension {

    // 1秒(20tick)ごとにまとめてエネルギーを消費する (10/tick * 20 = 200)
    private static final int ENERGY_PER_SECOND = 200;
    private int tickCounter = 0;

    public FurnaceExtension() { }

    @Override
    public void onAssembled(SimpleMachineBlockEntity master) { }

    @Override
    public void onDisassembled(SimpleMachineBlockEntity master) { }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) { return new CompoundTag(); }

    @Override
    public void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) { }

    @Override
    public Object getCapabilityInstance() { return this; }

    @Override
    public void tick(Level level, SimpleMachineBlockEntity master) {
        if (level.isClientSide) return;

        // ★真の解決策: 毎tickの処理を廃止し、Crafterと同様に1秒(20tick)に1回のバッチ処理にする
        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        List<BlockPos> furnaces = master.componentPositions.get("minecraft:furnace");
        if (furnaces == null || furnaces.isEmpty()) return;

        for (BlockPos pos : furnaces) {
            if (level.getBlockEntity(pos) instanceof MainframeAdapterBlockEntity adapter) {
                CompoundTag data = adapter.getPersistentData();

                // ターゲットアイテムが設定されていない間は処理を完全にスキップ
                if (data.getString("SmeltTarget").isEmpty()) continue;

                if (data.getBoolean("AutoSmeltActive")) {
                    processSmelting(master, adapter, level);
                }
            }
        }
    }

    private void processSmelting(SimpleMachineBlockEntity master, MainframeAdapterBlockEntity adapter, Level level) {
        CompoundTag data = adapter.getPersistentData();
        String targetId = data.getString("SmeltTarget");
        String resultId = data.getString("SmeltResult");

        if (targetId.isEmpty()) {
            resetProgress(adapter, level);
            return;
        }

        int cookTime = data.getInt("CookTime");
        int cookTimeTotal = data.getInt("CookTimeTotal");
        if (cookTimeTotal <= 0) cookTimeTotal = 200;

        if (cookTime == 0) {
            Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(targetId));
            if (targetItem == Items.AIR) return;

            boolean extracted = false;
            for (int i = 0; i < master.mainframeStorage.getSlots(); i++) {
                ItemStack stackInSlot = master.mainframeStorage.getStackInSlot(i);
                if (!stackInSlot.isEmpty() && stackInSlot.is(targetItem)) {
                    ItemStack extractedStack = master.mainframeStorage.extractItem(i, 1, false);
                    if (!extractedStack.isEmpty()) {
                        extracted = true;
                        break;
                    }
                }
            }

            if (!extracted) return; // 素材がない時は静かに待機する

            cookTime = 1; // タイマースタート
            data.putInt("CookTime", cookTime);
        }

        // ★エネルギーを1秒分まとめて消費する
        // 毎tick消費すると SimpleMachineBlockEntity 内で毎tick sync() が呼ばれ、UIが猛烈にチカチカするため。
        boolean hasEnergy = master.consumeActionEnergy(ENERGY_PER_SECOND);
        if (!hasEnergy) return;

        // 1秒分(20tick)進める
        cookTime += 20;

        if (cookTime >= cookTimeTotal) {
            Item resultItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(resultId));
            int resultCount = data.contains("SmeltResultCount") ? data.getInt("SmeltResultCount") : 1;

            if (resultItem != Items.AIR) {
                ItemStack resultStack = new ItemStack(resultItem, resultCount);
                ItemStack remain = ItemHandlerHelper.insertItemStacked(master.mainframeStorage, resultStack, false);
                if (!remain.isEmpty()) {
                    Block.popResource(level, master.getBlockPos().above(), remain);
                }
            }

            data.putInt("CookTime", 0);
            master.updateResourceUsages();
            master.setChanged();

            adapter.setChanged();
            level.sendBlockUpdated(adapter.getBlockPos(), adapter.getBlockState(), adapter.getBlockState(), 3);
        } else {
            data.putInt("CookTime", cookTime);

            // プログレスバー更新 (1秒に1回のみフラグ2で安全に部分更新する)
            adapter.setChanged();
            level.sendBlockUpdated(adapter.getBlockPos(), adapter.getBlockState(), adapter.getBlockState(), 2);
        }
    }

    private void resetProgress(MainframeAdapterBlockEntity adapter, Level level) {
        if (adapter.getPersistentData().getInt("CookTime") != 0) {
            adapter.getPersistentData().putInt("CookTime", 0);
            adapter.setChanged();
            level.sendBlockUpdated(adapter.getBlockPos(), adapter.getBlockState(), adapter.getBlockState(), 3);
        }
    }
}