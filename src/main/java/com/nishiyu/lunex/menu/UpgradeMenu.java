package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.MachineFrameBlockEntity;
import com.nishiyu.lunex.item.UpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class UpgradeMenu extends AbstractContainerMenu {

    private final MachineFrameBlockEntity blockEntity;
    private final BlockPos pos;

    public UpgradeMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.UPGRADE_MENU.get(), containerId);
        this.pos = pos;
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);

        if (be instanceof MachineFrameBlockEntity) {
            this.blockEntity = (MachineFrameBlockEntity) be;
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        // アップグレードスロット (上段のまま: y = 20)
        for (int i = 0; i < 7; ++i) {
            this.addSlot(new UpgradeSlot(this.blockEntity.upgradeHandler, i, 26 + i * 18, 20));
        }

        // プレイヤーインベントリ (1段分下げて y を 51 -> 69 に変更)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 8, 8 + j * 18, i * 18 + 69));
            }
        }

        // プレイヤーのホットバー (1段分下げて y を 109 -> 127 に変更)
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 127));
        }
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.pos) == this.blockEntity;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index < 7) {
                if (!this.moveItemStackTo(stackInSlot, 7, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(stackInSlot, 0, 7, false)) {
                return ItemStack.EMPTY;
            }
            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    static class UpgradeSlot extends SlotItemHandler {
        public UpgradeSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // ★同じ種類のアップグレードを複数入れられない制限を削除
            if (!(stack.getItem() instanceof UpgradeItem)) {
                return false;
            }
            return super.mayPlace(stack);
        }
    }
}