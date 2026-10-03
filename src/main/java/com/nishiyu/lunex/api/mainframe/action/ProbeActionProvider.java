package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ProbeActionProvider implements IMainframeActionProvider<ProbeBlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, ProbeBlockEntity be, Level level) {
        switch (action) {
            case "toggle_active":
                be.isDetected = !be.isDetected;
                be.setChanged();
                BlockState state = be.getBlockState();
                if (state.hasProperty(com.nishiyu.lunex.block.ProbeBlock.ACTIVE)) {
                    state = state.setValue(com.nishiyu.lunex.block.ProbeBlock.ACTIVE, be.isDetected);
                    level.setBlock(be.getBlockPos(), state, 3);
                }
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;

            case "toggle_mode":
                String currentMode = be.getPersistentData().getString("IOMode");
                be.getPersistentData().putString("IOMode", "OUT".equals(currentMode) ? "IN" : "OUT");
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;

            case "set_nbt_filter":
                be.getPersistentData().putString("NBTFilter", payload);
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;
        }
        return false;
    }
}