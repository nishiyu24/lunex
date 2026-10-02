package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
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

    private final TurtleBotBlockEntity blockEntity;
    private final BlockPos pos;
    private final DataSlot isRunningSlot;
    private final DataSlot energySlot;

    public TurtleBotMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        // ※Lunex.java の MENUS.register("turtle_bot_menu", ...) 等に後で登録が必要です
        super(Lunex.TURTLE_BOT_MENU.get(), containerId);
        this.pos = pos;
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof TurtleBotBlockEntity turtle) {
            this.blockEntity = turtle;
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        addMachineInventory();
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        this.isRunningSlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.isRunning() ? 1 : 0; }
            @Override
            public void set(int value) { blockEntity.setRunning(value == 1); }
        };
        this.addDataSlot(this.isRunningSlot);

        this.energySlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.energy; }
            @Override
            public void set(int value) { blockEntity.energy = value; }
        };
        this.addDataSlot(this.energySlot);
    }

    public boolean isRunning() { return this.isRunningSlot.get() == 1; }
    public int getEnergy() { return this.energySlot.get(); }
    public int getMaxEnergy() { return 100000; } // TurtleBotBlockEntity 側の DEFAULT_MAX_ENERGY

    private void addMachineInventory() {
        // タートルのインベントリは16スロット (4x4)
        int startX = 52;
        int startY = 17;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int index = (row * 4) + col;
                this.addSlot(new SlotItemHandler(this.blockEntity.itemHandler, index, startX + (col * 18), startY + (row * 18)));
            }
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 102 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 160));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().getBlockEntity(this.pos) == this.blockEntity;
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
            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    public TurtleBotBlockEntity getBlockEntity() { return this.blockEntity; }
    public BlockPos getPos() { return this.pos; }
}