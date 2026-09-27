package com.nishiyu.lunex.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ItemMachineContext implements IMachineContext {

    private final ItemStack stack;
    private boolean isRunning = false;

    public ItemMachineContext(ItemStack stack) {
        this.stack = stack;
    }

    // キャッシュをやめ、常に最新のタグを取得する
    private CompoundTag getTag() {
        return this.stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private void saveTag(CompoundTag tag) {
        this.stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public boolean isBlock() {
        return false;
    }

    @Nullable
    @Override
    public BlockPos getPos() {
        return null;
    }

    @Override
    public String getWorkspaceId() {
        return getTag().getString("WorkspaceId");
    }

    @Override
    public void setWorkspaceId(String workspaceId) {
        CompoundTag tag = getTag();
        tag.putString("WorkspaceId", workspaceId);
        saveTag(tag);
    }

    @Override
    public String getMachineLabel() {
        return getTag().getString("MachineLabel");
    }

    @Override
    public void setMachineLabel(String label) {
        CompoundTag tag = getTag();
        if (label == null || label.isEmpty()) tag.remove("MachineLabel");
        else tag.putString("MachineLabel", label);
        saveTag(tag);
    }

    @Override
    public String getProgramName() {
        return getTag().getString("ProgramName");
    }

    @Override
    public void setProgramName(String name) {
        CompoundTag tag = getTag();
        if (name == null || name.isEmpty()) tag.remove("ProgramName");
        else tag.putString("ProgramName", name);
        saveTag(tag);
    }

    @Override
    public int getEnergy() {
        return getTag().getInt("Energy");
    }

    public int extractEnergy(int amount, boolean simulate) {
        CompoundTag tag = getTag();
        int current = tag.getInt("Energy");
        int extracted = Math.min(current, amount);
        if (!simulate) {
            tag.putInt("Energy", current - extracted);
            saveTag(tag);
        }
        return extracted;
    }

    @Override
    public boolean isPrivateMode() {
        return getTag().getBoolean("IsPrivateMode");
    }

    @Override
    public void setPrivateMode(boolean privateMode) {
        CompoundTag tag = getTag();
        tag.putBoolean("IsPrivateMode", privateMode);
        saveTag(tag);
    }

    @Override
    public boolean isWakeOnRedstone() {
        return getTag().getBoolean("WakeOnRedstone");
    }

    @Override
    public void setWakeOnRedstone(boolean wakeOnRedstone) {
        CompoundTag tag = getTag();
        tag.putBoolean("WakeOnRedstone", wakeOnRedstone);
        saveTag(tag);
    }

    @Override
    public boolean isDebugChat() {
        CompoundTag tag = getTag();
        return !tag.contains("DebugChat") || tag.getBoolean("DebugChat");
    }

    @Override
    public void setDebugChat(boolean debugChat) {
        CompoundTag tag = getTag();
        tag.putBoolean("DebugChat", debugChat);
        saveTag(tag);
    }

    @Override
    public int getExecUpgradeLevel() {
        return getTag().getInt("ExecUpgradeLevel");
    }

    @Override
    public int getStorageUpgradeLevel() {
        return getTag().getInt("StorageUpgradeLevel");
    }

    @Override
    public int getSpeedUpgradeLevel() {
        return getTag().getInt("SpeedUpgradeLevel");
    }

    @Override
    public int getEfficiencyUpgradeLevel() {
        return getTag().getInt("EfficiencyUpgradeLevel");
    }

    @Override
    public int getCapacityUpgradeLevel() {
        return getTag().getInt("CapacityUpgradeLevel");
    }

    @Override
    public int getGeneratorUpgradeLevel() {
        return getTag().getInt("GeneratorUpgradeLevel");
    }

    // ★ 追加: 距離アップグレードのレベルを取得
    @Override
    public int getDistanceUpgradeLevel() {
        return getTag().getInt("DistanceUpgradeLevel");
    }

    @Override
    public boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public ItemStack getItemStack() {
        return this.stack;
    }

    @Override
    public List<String> getInstalledPrograms() {
        List<String> list = new ArrayList<>();
        CompoundTag tag = getTag();
        if (tag.contains("InstalledPrograms")) {
            net.minecraft.nbt.ListTag listTag = tag.getList("InstalledPrograms", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(listTag.getString(i));
            }
        }
        return list;
    }
}