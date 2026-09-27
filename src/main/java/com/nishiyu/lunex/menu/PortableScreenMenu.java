package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PortableScreenMenu extends AbstractContainerMenu {
    private final String ip;

    public PortableScreenMenu(int containerId, Inventory playerInventory, String ip) {
        super(Lunex.PORTABLE_SCREEN_MENU.get(), containerId);
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // アイテムを手に持っている間だけGUIを開き続ける
        return player.getMainHandItem().getItem() == Lunex.PORTABLE_SCREEN.get() ||
                player.getOffhandItem().getItem() == Lunex.PORTABLE_SCREEN.get();
    }
}