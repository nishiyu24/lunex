package com.nishiyu.lunex.machine;

import net.minecraft.nbt.CompoundTag;

public class MachineEnergyManager {
    public int energy = 0;

    public int getMaxEnergy(MachineUpgradeManager upgrades) {
        if (upgrades.capacityLevel >= 3) return 1000000;
        if (upgrades.capacityLevel == 2) return 500000;
        if (upgrades.capacityLevel == 1) return 200000;
        return 100000;
    }

    // ★ 追加：GENERATORアップグレードを「エネルギー受け入れ量の増加」として利用
    public int getReceiveRate(MachineUpgradeManager upgrades) {
        if (upgrades.generatorLevel >= 3) return 10000; // 毎ティックの最大受け入れ量
        if (upgrades.generatorLevel == 2) return 5000;
        if (upgrades.generatorLevel == 1) return 2000;
        return 1000; // デフォルト
    }

    public int getVmMaintainCost(MachineUpgradeManager upgrades) {
        if (upgrades.efficiencyLevel >= 3) return 175;
        if (upgrades.efficiencyLevel == 2) return 280;
        if (upgrades.efficiencyLevel == 1) return 390;
        return 500;
    }

    public double getEnergyCostMultiplier(MachineUpgradeManager upgrades) {
        return Math.max(0.1, 1.0 - (upgrades.efficiencyLevel * 20 / 100.0));
    }

    public boolean consume(int baseCost, MachineUpgradeManager upgrades) {
        int actualCost = (int) Math.ceil(baseCost * getEnergyCostMultiplier(upgrades));
        if (this.energy >= actualCost) {
            this.energy -= actualCost;
            return true;
        }
        return false;
    }

    public void save(CompoundTag tag) {
        tag.putInt("Energy", energy);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Energy")) energy = tag.getInt("Energy");
    }
}