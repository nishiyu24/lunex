package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.machine.TurtleCore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class TurtleSettingsMenu extends AbstractContainerMenu {
    private final TurtleCore core;
    private final DataSlot isRunningSlot;

    public TurtleSettingsMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.TURTLE_SETTINGS_MENU.get(), containerId);
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof TurtleBotBlockEntity turtle) {
            this.core = turtle.getCore();
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        this.isRunningSlot = new DataSlot() {
            @Override
            public int get() { return core.vm != null && core.vm.isRunning ? 1 : 0; }
            @Override
            public void set(int value) { if (core.getBoundEntity() != null) core.getBoundEntity().setRunning(value == 1); }
        };
        this.addDataSlot(this.isRunningSlot);
    }

    public boolean isRunning() { return this.isRunningSlot.get() == 1; }
    public TurtleBotBlockEntity getBlockEntity() { return this.core.getBoundEntity(); }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (this.core.isDisposed() || this.core.getBoundEntity() == null) return false;
        BlockPos currentPos = this.core.getBoundEntity().getBlockPos();
        return player.distanceToSqr(currentPos.getX() + 0.5D, currentPos.getY() + 0.5D, currentPos.getZ() + 0.5D) <= 64.0D;
    }
}