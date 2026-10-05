package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ProbeActionProvider implements IMainframeActionProvider<BlockEntity> {
    @Override
    public boolean handleAction(String action, String payload, BlockEntity be, Level level, ServerPlayer player) {
        switch (action) {
            case "toggle_active":
                if (be instanceof com.nishiyu.lunex.blockentity.ProbeBlockEntity probe) {
                    probe.isDetected = !probe.isDetected;
                    probe.getPersistentData().putBoolean("IsDetected", probe.isDetected);

                    BlockState state = probe.getBlockState();
                    if (state.hasProperty(com.nishiyu.lunex.block.ProbeBlock.ACTIVE)) {
                        state = state.setValue(com.nishiyu.lunex.block.ProbeBlock.ACTIVE, probe.isDetected);
                        level.setBlock(probe.getBlockPos(), state, 3);
                    }
                } else {
                    boolean isActive = be.getPersistentData().getBoolean("IsDetected");
                    be.getPersistentData().putBoolean("IsDetected", !isActive);
                }
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;

            case "toggle_mode":
                if (payload != null && !payload.isEmpty()) {
                    be.getPersistentData().putString("IOMode", payload);
                } else {
                    String currentMode = be.getPersistentData().getString("IOMode");
                    be.getPersistentData().putString("IOMode", "OUT".equals(currentMode) ? "IN" : "OUT");
                }
                be.setChanged();
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
                return true;

            case "toggle_target":
                be.getPersistentData().putString("TargetType", payload);
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