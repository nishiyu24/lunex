package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.frame.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// ★修正: ルーター機能が SimpleMachine に統合されたため、
// ネットワークロジックや VirtualStorage を削除し、単なるパーツとして定義。
public class RouterBlockEntity extends BlockEntity implements IMainframePart {

    public BlockPos mainframeMasterPos = null;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.ROUTER_BE.get(), pos, state);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.mainframeMasterPos != null) {
            tag.putLong("MainframeMasterPos", this.mainframeMasterPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MainframeMasterPos")) {
            this.mainframeMasterPos = BlockPos.of(tag.getLong("MainframeMasterPos"));
        } else {
            this.mainframeMasterPos = null;
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (this.mainframeMasterPos != null) {
            tag.putLong("MainframeMasterPos", this.mainframeMasterPos.asLong());
        }
        return tag;
    }
}