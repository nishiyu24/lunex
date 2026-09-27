package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MachineFrameBlockEntity extends BlockEntity implements IMainframePart {
    // ★追加: メインフレーム連携用
    public BlockPos mainframeMasterPos = null;

    public final ItemStackHandler upgradeHandler = new ItemStackHandler(7) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public MachineFrameBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.MACHINE_FRAME_BE.get(), pos, state);
    }

    // ★追加: IMainframePartの実装
    @Override
    public void setMasterPos(BlockPos pos) { this.mainframeMasterPos = pos; this.setChanged(); }
    @Override
    public BlockPos getMasterPos() { return this.mainframeMasterPos; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("UpgradeInventory", upgradeHandler.serializeNBT(registries));
        if (this.mainframeMasterPos != null) tag.putLong("MainframeMasterPos", this.mainframeMasterPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("UpgradeInventory")) {
            upgradeHandler.deserializeNBT(registries, tag.getCompound("UpgradeInventory"));
        }
        if (tag.contains("MainframeMasterPos")) {
            this.mainframeMasterPos = BlockPos.of(tag.getLong("MainframeMasterPos"));
        } else {
            this.mainframeMasterPos = null;
        }
    }
}