package com.nishiyu.lunex.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MachineUpgradeManager {
    public int execLevel = 0;
    public int storageLevel = 0;
    public int speedLevel = 0;
    public int efficiencyLevel = 0;
    public int capacityLevel = 0;
    public int generatorLevel = 0;
    // ★ 追加: 距離アップグレードのレベルを保持
    public int distanceLevel = 0;

    public void update(Level level, BlockPos pos, ItemStackHandler upgradeHandler, ItemStackHandler itemHandler, MachineEnergyManager energyManager) {
        int oldExec = this.execLevel;
        int oldStorage = this.storageLevel;

        int exec = 0, storage = 0, speed = 0, eff = 0, cap = 0, gen = 0, dist = 0; // ★ dist追加

        for (int i = 0; i < upgradeHandler.getSlots(); i++) {
            ItemStack stack = upgradeHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof com.nishiyu.lunex.item.UpgradeItem upgrade) {
                int lvl = upgrade.getLevel();
                switch (upgrade.getUpgradeType()) {
                    case EXECUTION -> exec = Math.max(exec, lvl);
                    case STORAGE -> storage = Math.max(storage, lvl);
                    case SPEED -> speed = Math.max(speed, lvl);
                    case EFFICIENCY -> eff = Math.max(eff, lvl);
                    case CAPACITY -> cap = Math.max(cap, lvl);
                    case GENERATOR -> gen = Math.max(gen, lvl);
                    case DISTANCE -> dist = Math.max(dist, lvl); // ★ 追加
                    default -> {
                    }
                }
            }
        }

        this.execLevel = exec;
        this.storageLevel = storage;
        this.speedLevel = speed;
        this.efficiencyLevel = eff;
        this.capacityLevel = cap;
        this.generatorLevel = gen;
        this.distanceLevel = dist; // ★ 追加

        if (level != null && !level.isClientSide()) {
            if (exec < oldExec) {
                int keepSlots = (exec >= 3) ? 9 : (exec == 2) ? 6 : (exec == 1) ? 3 : 0;
                for (int i = keepSlots; i < 9; i++) dropItem(level, pos, itemHandler, i);
            }
            if (storage < oldStorage) {
                int keepRows = (storage >= 3) ? 3 : (storage == 2) ? 2 : (storage == 1) ? 1 : 0;
                for (int i = 9; i < 36; i++) {
                    if ((i - 9) / 9 >= keepRows) dropItem(level, pos, itemHandler, i);
                }
            }
            int currentMax = energyManager.getMaxEnergy(this);
            if (energyManager.energy > currentMax) energyManager.energy = currentMax;
        }
    }

    private void dropItem(Level level, BlockPos pos, ItemStackHandler handler, int slot) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    public void save(CompoundTag tag) {
        tag.putInt("ExecUp", execLevel);
        tag.putInt("StorageUp", storageLevel);
        tag.putInt("SpeedUp", speedLevel);
        tag.putInt("EffUp", efficiencyLevel);
        tag.putInt("CapUp", capacityLevel);
        tag.putInt("GenUp", generatorLevel);
        tag.putInt("DistUp", distanceLevel); // ★ 追加
    }

    public void load(CompoundTag tag) {
        execLevel = tag.getInt("ExecUp");
        storageLevel = tag.getInt("StorageUp");
        speedLevel = tag.getInt("SpeedUp");
        efficiencyLevel = tag.getInt("EffUp");
        capacityLevel = tag.getInt("CapUp");
        generatorLevel = tag.getInt("GenUp");
        distanceLevel = tag.getInt("DistUp"); // ★ 追加
    }
}