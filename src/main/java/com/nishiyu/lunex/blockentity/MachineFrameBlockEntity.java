package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class MachineFrameBlockEntity extends BlockEntity implements IMainframePart {

    public BlockPos mainframeMasterPos = null;

    public MachineFrameBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.MACHINE_FRAME_BE.get(), pos, state);
    }

    @Override
    public void setMasterPos(BlockPos pos) {
        this.mainframeMasterPos = pos;
        this.setChanged();
    }

    @Override
    public BlockPos getMasterPos() {
        return this.mainframeMasterPos;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.mainframeMasterPos != null) {
            tag.putLong("MainframeMasterPos", this.mainframeMasterPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MainframeMasterPos")) {
            this.mainframeMasterPos = BlockPos.of(tag.getLong("MainframeMasterPos"));
        } else {
            this.mainframeMasterPos = null;
        }
    }
}