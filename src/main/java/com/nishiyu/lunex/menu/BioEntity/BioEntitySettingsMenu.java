package com.nishiyu.lunex.menu.BioEntity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class BioEntitySettingsMenu extends AbstractContainerMenu {

    private final int entityId;
    private final Level level;

    // ★修正: クライアント側の状態に依存しないスタンドアロンなデータスロットに変更
    private final DataSlot isRunningSlot = DataSlot.standalone();

    public BioEntitySettingsMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, data.readInt());
    }

    public BioEntitySettingsMenu(int containerId, Inventory playerInventory, int entityId) {
        super(Lunex.BIO_MOB_SETTINGS_MENU.get(), containerId);
        this.entityId = entityId;
        this.level = playerInventory.player.level();

        this.addDataSlot(this.isRunningSlot);
    }

    // ★追加: サーバー側で毎ティック状態をチェックし、変更があればクライアントに送信する
    @Override
    public void broadcastChanges() {
        if (!this.level.isClientSide()) {
            CustomBioMobEntity mob = getBioMob();
            if (mob != null && mob.vm != null) {
                this.isRunningSlot.set(mob.vm.isRunning ? 1 : 0);
            } else {
                this.isRunningSlot.set(0);
            }
        }
        super.broadcastChanges();
    }

    public boolean isRunning() {
        return this.isRunningSlot.get() == 1;
    }

    public CustomBioMobEntity getBioMob() {
        net.minecraft.world.entity.Entity e = this.level.getEntity(this.entityId);
        return e instanceof CustomBioMobEntity ? (CustomBioMobEntity) e : null;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        CustomBioMobEntity mob = getBioMob();
        return mob != null && mob.isAlive();
    }
}