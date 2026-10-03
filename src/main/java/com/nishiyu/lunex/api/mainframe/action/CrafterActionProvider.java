package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class CrafterActionProvider implements IMainframeActionProvider<MainframeAdapterBlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, MainframeAdapterBlockEntity be, Level level) {
        switch (action) {
            case "toggle_autocraft":
                boolean current = be.getPersistentData().getBoolean("AutoCraftActive");
                be.getPersistentData().putBoolean("AutoCraftActive", !current);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;

            case "toggle_craftmode":
                be.getPersistentData().putString("CraftMode", payload);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;

            case "clear_crafter_recipe":
                CompoundTag clearOrig = be.getOriginalNbt();
                if (clearOrig != null) {
                    clearOrig.remove("Items");
                    be.setOriginalBlock(be.getOriginalState(), clearOrig);
                }
                return true;
        }
        return false;
    }
}