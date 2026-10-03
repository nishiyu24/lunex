package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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

        // 1. プレイヤーインベントリのみ登録 (ストレージスロットはデジタル描画するため不要)
        // ※描画枠に合わせて+1ピクセルずらし、アイテムを枠の中央に配置します
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, j * 18 + 1, i * 18 + 1));
            }
        }
        // 2. ホットバー
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, k * 18 + 1, 3 * 18 + 4 + 1));
        }
    }

    public BlockPos getMasterPos() { return masterPos; }
    public Level getLevel() { return level; }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            // ★ 修正: Shift+クリックでプレイヤーの手持ちから直接メインフレームストレージへ挿入できるようにする
            if (index < 36) {
                if (!level.isClientSide) {
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(masterPos);
                    if (be instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity master) {
                        ItemStack remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(master.mainframeStorage, itemstack1, false);

                        if (remainder.getCount() != itemstack1.getCount()) {
                            itemstack1.setCount(remainder.getCount());
                            slot.setChanged();
                            master.setChanged();
                        }
                    }
                }

                // ストレージの容量がいっぱいで余った場合等は、通常のインベントリ移動へフォールバック
                if (!itemstack1.isEmpty()) {
                    if (index < 27) {
                        if (!this.moveItemStackTo(itemstack1, 27, 36, false)) return ItemStack.EMPTY;
                    } else if (index >= 27 && index < 36) {
                        if (!this.moveItemStackTo(itemstack1, 0, 27, false)) return ItemStack.EMPTY;
                    }
                }
            }

            if (itemstack1.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();

            if (itemstack1.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, itemstack1);
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, masterPos), player, Lunex.SIMPLE_MACHINE.get());
    }
}