package com.nishiyu.lunex.machine.turtle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class TurtleTaskManager {
    public boolean isBreakingBlock = false;
    public BlockPos breakingPos = null;
    public int breakingProgress = -1;
    public int breakingTimeTotal = 0;
    public int breakingTimeCurrent = 0;
    public int breakingSlot = 0;
    public String breakingMode = "normal";
    public boolean breakingCollect = false;

    public void resetBreakingState() {
        this.isBreakingBlock = false;
        this.breakingPos = null;
        this.breakingProgress = -1;
        this.breakingTimeTotal = 0;
        this.breakingTimeCurrent = 0;
        this.breakingMode = "normal";
        this.breakingCollect = false;
    }

    public void save(CompoundTag tag) {
        CompoundTag taskTag = new CompoundTag();
        taskTag.putBoolean("IsBreaking", isBreakingBlock);
        if (breakingPos != null) taskTag.putLong("BreakPos", breakingPos.asLong());
        taskTag.putInt("BreakProg", breakingProgress);
        taskTag.putInt("BreakTot", breakingTimeTotal);
        taskTag.putInt("BreakCur", breakingTimeCurrent);
        taskTag.putInt("BreakSlot", breakingSlot);
        taskTag.putString("BreakMode", breakingMode);
        taskTag.putBoolean("BreakCol", breakingCollect);
        tag.put("Tasks", taskTag);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Tasks")) return;
        CompoundTag taskTag = tag.getCompound("Tasks");

        isBreakingBlock = taskTag.getBoolean("IsBreaking");
        if (taskTag.contains("BreakPos")) breakingPos = BlockPos.of(taskTag.getLong("BreakPos"));
        breakingProgress = taskTag.getInt("BreakProg");
        breakingTimeTotal = taskTag.getInt("BreakTot");
        breakingTimeCurrent = taskTag.getInt("BreakCur");
        breakingSlot = taskTag.getInt("BreakSlot");
        breakingMode = taskTag.contains("BreakMode") ? taskTag.getString("BreakMode") : "normal";
        breakingCollect = taskTag.getBoolean("BreakCol");
    }
}