package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ToolAPIBase {
    protected static final int COST_BASE = 1000;
    private static final Map<ToolServerLuaVM, String> COST_MODES = new ConcurrentHashMap<>();
    private static final Map<ToolServerLuaVM, Integer> DEPOSIT_BALANCES = new ConcurrentHashMap<>();
    protected final ToolServerLuaVM vm;

    public ToolAPIBase(ToolServerLuaVM vm) {
        this.vm = vm;
    }

    public static void refundDeposit(ToolServerLuaVM vm) {
        Integer deposit = DEPOSIT_BALANCES.remove(vm);
        if (deposit != null && deposit > 0) {
            String type = COST_MODES.getOrDefault(vm, "energy");
            refundCostInternal(vm, type, deposit);
        }
    }

    private static void refundCostInternal(ToolServerLuaVM vm, String type, int amount) {
        if (vm.currentPlayer == null) return;

        if ("xp".equalsIgnoreCase(type) || "experience".equalsIgnoreCase(type)) {
            vm.currentPlayer.giveExperienceLevels(amount);
        } else if ("durability".equalsIgnoreCase(type) || "damage".equalsIgnoreCase(type)) {
            ItemStack stack = vm.currentPlayer.getMainHandItem();
            if (!com.nishiyu.lunex.event.ToolEventHandler.hasCartridge(stack))
                stack = vm.currentPlayer.getOffhandItem();

            if (!stack.isEmpty() && stack.isDamageableItem()) {
                int currentDamage = stack.getDamageValue();
                stack.setDamageValue(Math.max(0, currentDamage - amount));
            }
        } else {
            ItemStack stack = vm.currentPlayer.getMainHandItem();
            if (!com.nishiyu.lunex.event.ToolEventHandler.hasCartridge(stack))
                stack = vm.currentPlayer.getOffhandItem();

            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            int currentEnergy = tag.getInt("Energy");
            tag.putInt("Energy", currentEnergy + amount);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
        }
    }

    @LuaFunction(
            value = "使用するコストの種類(energy, durability, xp)と、大まかに先払いする量(デポジット)を指定します。イベント終了後に使わなかった分は全額返金されます。",
            en = "Specifies the type of cost to use (energy, durability, xp) and the approximate amount to prepay (deposit). Any unused amount will be fully refunded after the event ends.",
            args = {"str:type", "num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setToolCost(String type, int amount) {
        if (vm.currentPlayer == null) return false;

        refundDeposit(vm);
        COST_MODES.put(vm, type);

        if (amount > 0) {
            int effLevel = vm.context.getEfficiencyUpgradeLevel();
            double discountRate = Math.min(effLevel * 0.20, 0.80);
            int finalAmount = Math.max((int) (amount * (1.0 - discountRate)), 1);

            if (payCostInternal(type, finalAmount, "デポジット(先払い)")) {
                DEPOSIT_BALANCES.put(vm, finalAmount);
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    protected boolean consumeEnergy(int baseCost, String actionName) {
        if (vm.currentPlayer == null || vm.currentPlayer.isCreative()) return true;
        if (baseCost <= 0) return true;

        int effLevel = vm.context.getEfficiencyUpgradeLevel();
        double discountRate = Math.min(effLevel * 0.20, 0.80);
        int finalCost = Math.max((int) (baseCost * (1.0 - discountRate)), 1);

        Integer deposit = DEPOSIT_BALANCES.get(vm);
        if (deposit != null && deposit > 0) {
            if (deposit >= finalCost) {
                DEPOSIT_BALANCES.put(vm, deposit - finalCost);
                return true;
            } else {
                int shortage = finalCost - deposit;
                String type = COST_MODES.getOrDefault(vm, "energy");
                if (payCostInternal(type, shortage, actionName)) {
                    DEPOSIT_BALANCES.put(vm, 0);
                    return true;
                }
                return false;
            }
        } else {
            String type = COST_MODES.getOrDefault(vm, "energy");
            return payCostInternal(type, finalCost, actionName);
        }
    }

    private boolean payCostInternal(String type, int amount, String action) {
        if (vm.currentPlayer == null || vm.currentPlayer.isCreative()) return true;

        if ("xp".equalsIgnoreCase(type) || "experience".equalsIgnoreCase(type)) {
            if (vm.currentPlayer.experienceLevel >= amount) {
                vm.currentPlayer.giveExperienceLevels(-amount);
                return true;
            } else {
                chat("§c[コスト不足] 経験値レベルが足りません: " + action + " (必要: " + amount + "Lv)");
                return false;
            }
        } else if ("durability".equalsIgnoreCase(type) || "damage".equalsIgnoreCase(type)) {
            ItemStack stack = vm.currentPlayer.getMainHandItem();
            if (!com.nishiyu.lunex.event.ToolEventHandler.hasCartridge(stack))
                stack = vm.currentPlayer.getOffhandItem();

            if (!stack.isEmpty() && stack.isDamageableItem()) {
                int maxDamage = stack.getMaxDamage();
                int currentDamage = stack.getDamageValue();
                if (maxDamage - currentDamage > amount) {
                    stack.setDamageValue(currentDamage + amount);
                    return true;
                } else {
                    chat("§c[コスト不足] ツールの耐久値が足りません: " + action);
                    return false;
                }
            } else {
                chat("§c[エラー] 手のアイテムに耐久値がありません。");
                return false;
            }
        } else {
            if (vm.context.getEnergy() >= amount) {
                vm.context.extractEnergy(amount, false);
                return true;
            } else {
                chat("§c[コスト不足] エネルギーが足りません: " + action + " (必要: " + amount + " FE)");
                return false;
            }
        }
    }

    protected ServerLevel getServerLevel() {
        if (vm.currentPlayer != null && vm.currentPlayer.level() instanceof ServerLevel sl) return sl;
        return null;
    }

    protected LivingEntity getTargetLiving() {
        if (vm.currentTarget instanceof LivingEntity le) return le;
        return null;
    }

    protected void chat(String message) {
        if (vm.currentPlayer != null) vm.currentPlayer.sendSystemMessage(Component.literal("§e[Tool] §f" + message));
    }
}