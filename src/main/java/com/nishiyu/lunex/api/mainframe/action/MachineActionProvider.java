package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class MachineActionProvider implements IMainframeActionProvider<SimpleMachineBlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, SimpleMachineBlockEntity be, Level level, ServerPlayer player) {
        if (be.getCore() == null) return false;

        if ("start_vm".equals(action)) {
            be.getCore().setRunning(true);
            return true;
        } else if ("stop_vm".equals(action)) {
            be.getCore().setRunning(false);
            return true;
        }

        return false;
    }
}