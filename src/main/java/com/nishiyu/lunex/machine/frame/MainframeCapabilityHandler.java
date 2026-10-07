package com.nishiyu.lunex.machine.frame;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class MainframeCapabilityHandler {

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        BlockEntityType<?>[] validPartTypes = new BlockEntityType<?>[]{ Lunex.PROBE_BE.get() };
        for (BlockEntityType<?> type : validPartTypes) {
            registerForType(event, type);
        }
    }

    @SuppressWarnings("unchecked")
    private static <BE extends BlockEntity> void registerForType(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        BlockEntityType<BE> typed = (BlockEntityType<BE>) type;

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, typed, (be, side) -> {
            if (be instanceof ProbeBlockEntity probe) {
                SimpleMachineBlockEntity master = getMaster(probe);
                if (master != null) return new ProbeItemHandlerWrapper(master.mainframeStorage, probe);
            }
            return null;
        });

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, typed, (be, side) -> {
            if (be instanceof ProbeBlockEntity probe) {
                SimpleMachineBlockEntity master = getMaster(probe);
                if (master != null) return new ProbeEnergyStorageWrapper(master.energyStorage, probe);
            }
            return null;
        });
    }

    private static SimpleMachineBlockEntity getMaster(BlockEntity be) {
        if (be instanceof SimpleMachineBlockEntity master && master.isMainframeMaster) return master;
        if (be instanceof IMainframePart part && part.getMasterPos() != null && be.getLevel() != null) {
            BlockEntity masterBe = be.getLevel().getBlockEntity(part.getMasterPos());
            if (masterBe instanceof SimpleMachineBlockEntity master && master.isMainframeMaster) return master;
        }
        return null;
    }

    public static class ProbeItemHandlerWrapper implements IItemHandler {
        private final IItemHandler masterStorage;
        private final ProbeBlockEntity probe;

        public ProbeItemHandlerWrapper(IItemHandler masterStorage, ProbeBlockEntity probe) {
            this.masterStorage = masterStorage;
            this.probe = probe;
        }

        private boolean isValidState() {
            if (!probe.isDetected) return false; // ★修正: NBTではなくプローブの実際の状態を直接見る
            String targetType = probe.getPersistentData().getString("TargetType");
            return targetType.isEmpty() || "ALL".equals(targetType) || "ITEM".equals(targetType);
        }

        private boolean canInsertItemToProbe() {
            if (!isValidState()) return false;
            String mode = probe.getPersistentData().getString("IOMode");
            return mode.isEmpty() || "IN".equals(mode);
        }

        private boolean canExtractItemFromProbe() {
            if (!isValidState()) return false;
            return "OUT".equals(probe.getPersistentData().getString("IOMode"));
        }

        private boolean passesFilter(ItemStack stack) {
            String filter = probe.getPersistentData().getString("NBTFilter");
            if (filter.isEmpty() || filter.equals("{}")) return true;

            boolean isNbtFilter = filter.startsWith("{") && filter.endsWith("}");
            String searchStr = isNbtFilter ? filter.substring(1, filter.length() - 1) : filter;

            if (isNbtFilter) {
                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                return customData.copyTag().toString().contains(searchStr);
            } else {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                return id.contains(searchStr) || stack.getHoverName().getString().contains(searchStr);
            }
        }

        @Override public int getSlots() { return masterStorage.getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return masterStorage.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return masterStorage.getSlotLimit(slot); }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!canInsertItemToProbe() || !passesFilter(stack)) return stack;
            return masterStorage.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!canExtractItemFromProbe()) return ItemStack.EMPTY;
            ItemStack stackInSlot = masterStorage.getStackInSlot(slot);
            if (!stackInSlot.isEmpty() && !passesFilter(stackInSlot)) return ItemStack.EMPTY;
            return masterStorage.extractItem(slot, amount, simulate);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return masterStorage.isItemValid(slot, stack) && canInsertItemToProbe() && passesFilter(stack);
        }
    }

    public static class ProbeEnergyStorageWrapper implements IEnergyStorage {
        private final IEnergyStorage masterEnergy;
        private final ProbeBlockEntity probe;

        public ProbeEnergyStorageWrapper(IEnergyStorage masterEnergy, ProbeBlockEntity probe) {
            this.masterEnergy = masterEnergy;
            this.probe = probe;
        }

        private boolean isValidState() {
            if (!probe.isDetected) return false; // ★修正: NBTではなくプローブの実際の状態を直接見る
            String targetType = probe.getPersistentData().getString("TargetType");
            return targetType.isEmpty() || "ALL".equals(targetType) || "ENERGY".equals(targetType);
        }

        private boolean canReceiveEnergyFromProbe() {
            if (!isValidState()) return false;
            String mode = probe.getPersistentData().getString("IOMode");
            return mode.isEmpty() || "IN".equals(mode);
        }

        private boolean canExtractEnergyFromProbe() {
            if (!isValidState()) return false;
            return "OUT".equals(probe.getPersistentData().getString("IOMode"));
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!canReceiveEnergyFromProbe()) return 0;
            return masterEnergy.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!canExtractEnergyFromProbe()) return 0;
            return masterEnergy.extractEnergy(maxExtract, simulate);
        }

        @Override public int getEnergyStored() { return masterEnergy.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return masterEnergy.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return canExtractEnergyFromProbe() && masterEnergy.canExtract(); }
        @Override public boolean canReceive() { return canReceiveEnergyFromProbe() && masterEnergy.canReceive(); }
    }
}