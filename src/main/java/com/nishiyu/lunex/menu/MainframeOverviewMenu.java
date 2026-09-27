package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class MainframeOverviewMenu extends AbstractContainerMenu {

    private final BlockPos masterPos;
    private final Level level;

    public MainframeOverviewMenu(int containerId, Inventory playerInventory, BlockPos masterPos) {
        super(Lunex.MAINFRAME_OVERVIEW_MENU.get(), containerId);
        this.masterPos = masterPos;
        this.level = playerInventory.player.level();
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    public Level getLevel() {
        return level;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        // ★修正: SIMPLE_MACHINE_BLOCK -> SIMPLE_MACHINE
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, masterPos), player, Lunex.SIMPLE_MACHINE.get());
    }
}