package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.machine.turtle.TurtleCore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class TurtleBotMenu extends AbstractContainerMenu {

    private final TurtleCore core;
    private final DataSlot isRunningSlot;
    private final DataSlot energySlot;

    public TurtleBotMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.TURTLE_BOT_MENU.get(), containerId);
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof TurtleBotBlockEntity turtle) {
            this.core = turtle.getCore();
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        addMachineInventory();
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        this.isRunningSlot = new DataSlot() {
            @Override public int get() { return core.vm != null && core.vm.isRunning ? 1 : 0; }
            @Override public void set(int value) { if (core.getBoundEntity() != null) core.getBoundEntity().setRunning(value == 1); }
        };
        this.addDataSlot(this.isRunningSlot);

        this.energySlot = new DataSlot() {
            @Override public int get() { return core.energy; }
            @Override public void set(int value) { core.energy = value; }
        };
        this.addDataSlot(this.energySlot);
    }

    public boolean isRunning() { return this.isRunningSlot.get() == 1; }
    public int getEnergy() { return this.energySlot.get(); }
    public int getMaxEnergy() { return 100000; }
    public TurtleBotBlockEntity getBlockEntity() { return this.core.getBoundEntity(); }

    private void addMachineInventory() {
        int startX = 53, startY = 51;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int index = (row * 4) + col;
                this.addSlot(new SlotItemHandler(this.core.itemHandler, index, startX + (col * 18), startY + (row * 18)));
            }
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 140 + i * 18));
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 198));
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        // Coreが破棄されておらず、かつバインド先のエンティティがプレイヤーの近くにあれば有効
        if (this.core.isDisposed() || this.core.getBoundEntity() == null) return false;
        BlockPos currentPos = this.core.getBoundEntity().getBlockPos();
        return player.distanceToSqr(currentPos.getX() + 0.5D, currentPos.getY() + 0.5D, currentPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();
            if (index < 16) {
                if (!this.moveItemStackTo(stackInSlot, 16, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stackInSlot, 0, 16, false)) return ItemStack.EMPTY;
            }
            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return itemstack;
    }
}