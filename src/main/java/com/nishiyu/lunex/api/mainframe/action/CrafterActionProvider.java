// CrafterActionProvider.java の修正
package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CrafterActionProvider implements IMainframeActionProvider<BlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, BlockEntity be, Level level) {
        switch (action) {
            case "toggle_autocraft":
                boolean current = be.getPersistentData().getBoolean("AutoCraftActive");
                be.getPersistentData().putBoolean("AutoCraftActive", !current);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                notifyMaster(be, level);
                return true;

            case "toggle_craftmode":
                be.getPersistentData().putString("CraftMode", payload);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                notifyMaster(be, level); // ★追加
                return true;

            case "clear_crafter_recipe":
                if (be instanceof MainframeAdapterBlockEntity adapter) {
                    CompoundTag clearOrig = adapter.getOriginalNbt();
                    if (clearOrig != null) {
                        clearOrig.remove("Items");
                        adapter.setOriginalBlock(adapter.getOriginalState(), clearOrig);
                        adapter.setChanged();
                        notifyMaster(adapter, level); // ★追加
                    }
                }
                return true;
        }
        return false;
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