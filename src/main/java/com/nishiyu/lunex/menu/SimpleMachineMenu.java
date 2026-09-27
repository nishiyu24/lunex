package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class SimpleMachineMenu extends AbstractContainerMenu {

    public final BlockPos blockPos;

    public SimpleMachineMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.SIMPLE_MACHINE_MENU.get(), containerId); // レジストリに合わせて変更してください
        this.blockPos = pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // インベントリを持たないため処理なし
    }

    @Override
    public boolean stillValid(Player player) {
        // プレイヤーがブロックから離れすぎていないかチェック
        return player.distanceToSqr(this.blockPos.getX() + 0.5, this.blockPos.getY() + 0.5, this.blockPos.getZ() + 0.5) <= 64.0;
    }
}