package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PrinterMenu extends AbstractContainerMenu {
    public final PrinterBlockEntity blockEntity;
    private final ContainerData dataAccess;

    public PrinterMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.PRINTER_MENU.get(), windowId);
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof PrinterBlockEntity printer) {
            this.blockEntity = printer;
            this.dataAccess = printer.dataAccess;
        } else {
            throw new IllegalStateException("Incorrect block entity class");
        }

        // 入力スロット群
        this.addSlot(new SlotItemHandler(blockEntity.itemHandler, 0, 35, 48));
        this.addSlot(new SlotItemHandler(blockEntity.itemHandler, 1, 53, 48));
        this.addSlot(new SlotItemHandler(blockEntity.itemHandler, 2, 71, 48));

        // 出力スロット
        this.addSlot(new SlotItemHandler(blockEntity.itemHandler, 3, 125, 48) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        // プレイヤーインベントリ
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }

        // データの同期を登録
        this.addDataSlots(this.dataAccess);
    }

    // --- GUI描画用にデータを取得するメソッド ---
    public int getEnergy() {
        return this.dataAccess.get(0);
    }

    public int getMaxEnergy() {
        return this.dataAccess.get(1);
    }

    public int getProgress() {
        return this.dataAccess.get(2);
    }

    public int getMaxProgress() {
        return this.dataAccess.get(3);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < 4) {
                if (!this.moveItemStackTo(itemstack1, 4, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 3, false)) {
                return ItemStack.EMPTY;
            }
            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos()), player, Lunex.PRINTER.get());
    }
}