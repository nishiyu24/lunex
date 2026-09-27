package com.nishiyu.lunex.menu.MachineSettings;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMachineContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MachineSettingsMenu extends AbstractContainerMenu {

    private final IMachineContext machineContext;
    private final DataSlot isRunningSlot;

    // ブロック用コンストラクタ（既存の呼び出し元から使用）
    public MachineSettingsMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.MACHINE_SETTINGS_MENU.get(), containerId);

        net.minecraft.world.level.block.entity.BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof IMachineContext context) {
            this.machineContext = context;
        } else {
            throw new IllegalStateException("Incorrect block entity or context");
        }

        this.isRunningSlot = setupSyncSlot();
    }

    // アイテム用コンストラクタ（タブレット用に追加）
    public MachineSettingsMenu(int containerId, Inventory playerInventory, IMachineContext itemContext) {
        super(Lunex.MACHINE_SETTINGS_MENU.get(), containerId);
        this.machineContext = itemContext;
        this.isRunningSlot = setupSyncSlot();
    }

    private DataSlot setupSyncSlot() {
        DataSlot slot = new DataSlot() {
            @Override
            public int get() {
                return machineContext.isRunning() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                machineContext.setRunning(value == 1);
            }
        };
        this.addDataSlot(slot);
        return slot;
    }

    public boolean isRunning() {
        return this.isRunningSlot.get() == 1;
    }

    public IMachineContext getMachineContext() {
        return this.machineContext;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (this.machineContext.isBlock()) {
            BlockPos pos = this.machineContext.getPos();
            if (pos == null) return false;
            return player.level().getBlockEntity(pos) instanceof IMachineContext;
        } else {
            // アイテムの場合は、プレイヤーが対象のアイテム(タブレット等)を持っているか判定
            return player.getMainHandItem().getItem() instanceof com.nishiyu.lunex.item.TabletItem;
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        // サーバー側かつ、対象がアイテムの場合
        if (!player.level().isClientSide && this.machineContext != null && !this.machineContext.isBlock()) {
            if (this.machineContext instanceof com.nishiyu.lunex.machine.ItemMachineContext itemCtx) {
                player.getInventory().placeItemBackInInventory(itemCtx.getItemStack());
            }
        }
    }
}