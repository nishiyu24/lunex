package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ProbeMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final Level level;

    public ProbeMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.PROBE_MENU.get(), containerId);
        this.pos = pos;
        this.level = playerInventory.player.level();
    }

    public BlockPos getPos() {
        return pos;
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
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), player, Lunex.PROBE_BLOCK.get());
    }
}