package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TurtleSettingsMenu extends AbstractContainerMenu {
    private final TurtleBotBlockEntity blockEntity;
    private final DataSlot isRunningSlot;

    public TurtleSettingsMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.TURTLE_SETTINGS_MENU.get(), containerId);
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof TurtleBotBlockEntity turtle) {
            this.blockEntity = turtle;
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        this.isRunningSlot = new DataSlot() {
            @Override
            public int get() { return blockEntity.isRunning() ? 1 : 0; }
            @Override
            public void set(int value) { blockEntity.setRunning(value == 1); }
        };
        this.addDataSlot(this.isRunningSlot);
    }

    public boolean isRunning() { return this.isRunningSlot.get() == 1; }
    public TurtleBotBlockEntity getBlockEntity() { return this.blockEntity; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.blockEntity.getBlockPos()) == this.blockEntity;
    }
}