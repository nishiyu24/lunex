package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RouterDashboardMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    // サーバー側で開く際に呼ばれるコンストラクタ
    public RouterDashboardMenu(int id, Inventory playerInventory, BlockPos pos) {
        super(Lunex.ROUTER_DASHBOARD_MENU.get(), id);
        this.pos = pos;
    }

    // クライアント側でネットワークからデータを受け取って開く際に呼ばれるコンストラクタ
    public RouterDashboardMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, extraData.readBlockPos());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // プレイヤーがアクセス可能かどうかの判定
        return player.level().getBlockEntity(this.pos) instanceof com.nishiyu.lunex.blockentity.RouterBlockEntity;
    }
}