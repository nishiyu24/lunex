package com.nishiyu.lunex.machine;

import net.minecraft.core.BlockPos;

public interface IMainframePart {
    void setMasterPos(BlockPos pos);
    BlockPos getMasterPos();
}