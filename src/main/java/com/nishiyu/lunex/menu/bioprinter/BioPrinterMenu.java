package com.nishiyu.lunex.menu.bioprinter;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.BioPrinterBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class BioPrinterMenu extends AbstractContainerMenu {
    public final BioPrinterBlockEntity blockEntity;
    public final ContainerData data;

    public int activeTab = 0;

    public BioPrinterMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(16));
    }

    public BioPrinterMenu(int id, Inventory playerInventory, BlockEntity entity, ContainerData data) {
        super(Lunex.BIO_PRINTER_MENU.get(), id);
        this.blockEntity = (BioPrinterBlockEntity) entity;
        this.data = data;

        this.addSlot(new SlotItemHandler(this.blockEntity.itemHandler, 0, 216, 142) {
            @Override
            public boolean isActive() {
                return activeTab == 0;
            }
        });

        int leftPos = 11;
        int topPos = 136;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, leftPos + j * 18, topPos + i * 18) {
                    @Override
                    public boolean isActive() {
                        return activeTab == 0;
                    }
                });
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, leftPos + i * 18, 194) {
                @Override
                public boolean isActive() {
                    return activeTab == 0;
                }
            });
        }

        this.addDataSlots(this.data);
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id == 0) {
            this.blockEntity.forceGenerate();
            return true;
        } else if (id == 1) {
            this.blockEntity.extractMaterials(player);
            return true;
        }
        if (id >= 100 && id < 200) {
            this.blockEntity.toggleBehavior(id - 100);
            return true;
        }
        if (id >= 200 && id < 300) {
            this.blockEntity.toggleTrait(id - 200);
            return true;
        }
        return false;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(this.blockEntity.getBlockPos()) instanceof BioPrinterBlockEntity;
    }
}