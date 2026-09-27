package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AdvancedMachineMenu extends AbstractContainerMenu {

    private final AdvancedMachineBlockEntity blockEntity;
    private final BlockPos pos;
    private final DataSlot isRunningSlot;
    private final DataSlot energySlot;
    private final DataSlot execLevelSlot;
    private final DataSlot storageLevelSlot;

    public AdvancedMachineMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.ADVANCED_MACHINE_MENU.get(), containerId);
        this.pos = pos;
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof AdvancedMachineBlockEntity) {
            this.blockEntity = (AdvancedMachineBlockEntity) be;
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        this.execLevelSlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.upgrades.execLevel; }
            @Override
            public void set(int value) { blockEntity.upgrades.execLevel = value; }
        };
        this.addDataSlot(this.execLevelSlot);

        this.storageLevelSlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.upgrades.storageLevel; }
            @Override
            public void set(int value) { blockEntity.upgrades.storageLevel = value; }
        };
        this.addDataSlot(this.storageLevelSlot);

        addMachineInventory();
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        this.isRunningSlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.vm.isRunning ? 1 : 0; }
            @Override
            public void set(int value) { blockEntity.vm.isRunning = (value == 1); }
        };
        this.addDataSlot(this.isRunningSlot);

        this.energySlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.energyManager.energy; }
            @Override
            public void set(int value) { blockEntity.energyManager.energy = value; }
        };
        this.addDataSlot(this.energySlot);

        // 燃焼時間関係のスロットは削除
    }

    public boolean isRunning() {
        return this.isRunningSlot.get() == 1;
    }

    public int getEnergy() {
        return this.energySlot.get();
    }

    public int getMaxEnergy() {
        return this.blockEntity.energyManager.getMaxEnergy(this.blockEntity.upgrades);
    }

    private void addMachineInventory() {
        int startY_exec = 51;
        for (int i = 0; i < 9; i++) {
            this.addSlot(new UpgradableSlot(blockEntity.itemHandler, i, 8 + (i * 18), startY_exec));
        }

        int startY_internal = 72;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + (row * 9) + col;
                this.addSlot(new UpgradableSlot(blockEntity.itemHandler, index, 8 + (col * 18), startY_internal + (row * 18)));
            }
        }
        // 燃料スロット（FuelSlot）の追加は削除
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 140 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 198));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.pos) == this.blockEntity;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // 37 を 36 に変更 (機械インベントリが 0~35 になるため)
            if (index < 36) {
                if (!this.moveItemStackTo(stackInSlot, 36, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stackInSlot, 0, 36, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    public AdvancedMachineBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    class UpgradableSlot extends SlotItemHandler {
        public UpgradableSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return isActive() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(@NotNull Player playerIn) {
            return isActive() && super.mayPickup(playerIn);
        }

        @Override
        public boolean isActive() {
            int idx = this.getSlotIndex();
            if (idx < 9) {
                int execLevel = execLevelSlot.get();
                if (execLevel >= 3) return true;
                if (execLevel == 2 && idx < 6) return true;
                return execLevel == 1 && idx < 3;
            }
            else if (idx < 36) {
                int storageLevel = storageLevelSlot.get();
                if (storageLevel >= 3) return true;
                if (storageLevel == 2 && idx < 27) return true;
                return storageLevel == 1 && idx < 18;
            }
            return true;
        }
    }
}