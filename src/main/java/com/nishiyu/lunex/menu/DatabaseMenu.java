package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class DatabaseMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Level level;

    // ★ GUI開閉時のリアルタイム同期用データ
    private final ContainerData data;

    public DatabaseMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(Lunex.DATABASE_MENU.get(), containerId);
        this.pos = pos;
        this.level = playerInventory.player.level();

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DatabaseBlockEntity db && !level.isClientSide) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    long totalItems = 0;
                    if (index >= 8 && index <= 11) {
                        for (long count : db.itemCounts.values()) {
                            totalItems += count;
                        }
                    }

                    return switch (index) {
                        // 容量の同期 (0〜7)
                        case 0 -> db.getItemUsedBytes() >> 16;
                        case 1 -> db.getItemUsedBytes() & 0xFFFF;
                        case 2 -> db.getProgramUsedBytes() >> 16;
                        case 3 -> db.getProgramUsedBytes() & 0xFFFF;
                        // ★ 追加: 液体の使用容量
                        case 4 -> db.getFluidUsedBytes() >> 16;
                        case 5 -> db.getFluidUsedBytes() & 0xFFFF;
                        case 6 -> db.getMaxCapacityBytes() >> 16;
                        case 7 -> db.getMaxCapacityBytes() & 0xFFFF;

                        // ★ アイテムの総数 (long型なので4分割して送信)
                        case 8 -> (int) ((totalItems >> 48) & 0xFFFF);
                        case 9 -> (int) ((totalItems >> 32) & 0xFFFF);
                        case 10 -> (int) ((totalItems >> 16) & 0xFFFF);
                        case 11 -> (int) (totalItems & 0xFFFF);

                        // ★ プログラムの個数 (12〜13)
                        case 12 -> db.storedPrograms.size() >> 16;
                        case 13 -> db.storedPrograms.size() & 0xFFFF;

                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                // ★ 同期するデータの数を 14 に変更
                @Override
                public int getCount() { return 14; }
            };
        } else {
            // クライアント側: 送られてきたデータを受信するバッファ (サイズを 14 に変更)
            this.data = new SimpleContainerData(14);
        }

        this.addDataSlots(this.data);
    }

    public int getItemBytes() {
        return (this.data.get(0) << 16) | (this.data.get(1) & 0xFFFF);
    }

    public int getProgramBytes() {
        return (this.data.get(2) << 16) | (this.data.get(3) & 0xFFFF);
    }

    public int getFluidBytes() {
        return (this.data.get(4) << 16) | (this.data.get(5) & 0xFFFF);
    }

    public int getMaxBytes() {
        int max = (this.data.get(6) << 16) | (this.data.get(7) & 0xFFFF);
        return max <= 0 ? DatabaseBlockEntity.BASE_CAPACITY_BYTES : max;
    }

    public long getTotalItemCount() {
        return ((long) (this.data.get(8) & 0xFFFF) << 48) |
                ((long) (this.data.get(9) & 0xFFFF) << 32) |
                ((long) (this.data.get(10) & 0xFFFF) << 16) |
                ((long) (this.data.get(11) & 0xFFFF));
    }

    public int getProgramCount() {
        return (this.data.get(12) << 16) | (this.data.get(13) & 0xFFFF);
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
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), player, Lunex.DATABASE_BLOCK.get());
    }
}