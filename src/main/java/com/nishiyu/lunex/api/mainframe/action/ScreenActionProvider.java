package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import net.minecraft.world.level.Level;

public class ScreenActionProvider implements IMainframeActionProvider<ScreenBlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, ScreenBlockEntity be, Level level) {
        if ("set_screen_mode".equals(action)) {
            be.getPersistentData().putString("DisplayMode", payload);
            be.setChanged();
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            return true;
        } else if ("set_screen_filter".equals(action)) {
            be.getPersistentData().putString("ScreenFilter", payload);
            be.setChanged();
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            return true;
        }
        return false;
    }
}