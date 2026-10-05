package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class DatabaseActionProvider implements IMainframeActionProvider<DatabaseBlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, DatabaseBlockEntity be, Level level, ServerPlayer player) {
        if ("set_priority".equals(action)) {
            try {
                int priority = Integer.parseInt(payload);
                if (priority < 1 || priority > 10) priority = 1;
                be.getPersistentData().putInt("Priority", priority);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}