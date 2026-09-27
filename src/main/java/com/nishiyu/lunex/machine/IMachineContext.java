package com.nishiyu.lunex.machine;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public interface IMachineContext {
    boolean isBlock();

    @Nullable
    BlockPos getPos();

    String getWorkspaceId();

    // ▼ 追加: GUIからの変更をクライアント側でも保持するためのSetter
    void setWorkspaceId(String workspaceId);

    String getMachineLabel();

    void setMachineLabel(String label);

    String getProgramName();

    void setProgramName(String name);

    int getEnergy();

    boolean isPrivateMode();

    void setPrivateMode(boolean privateMode);

    boolean isWakeOnRedstone();

    void setWakeOnRedstone(boolean wakeOnRedstone);

    boolean isDebugChat();

    void setDebugChat(boolean debugChat);

    int getExecUpgradeLevel();

    int getStorageUpgradeLevel();

    int getSpeedUpgradeLevel();

    int getEfficiencyUpgradeLevel();

    int getCapacityUpgradeLevel();

    int getGeneratorUpgradeLevel();

    int getDistanceUpgradeLevel();

    boolean isRunning();

    void setRunning(boolean running);

    java.util.List<String> getInstalledPrograms();
}