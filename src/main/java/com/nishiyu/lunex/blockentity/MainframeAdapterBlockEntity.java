package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class MainframeAdapterBlockEntity extends BlockEntity implements IMainframePart {

    private BlockPos masterPos;
    private BlockState originalState;
    private CompoundTag originalNbt;

    public MainframeAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.MAINFRAME_ADAPTER_BE.get(), pos, state);
    }

    @Override
    public void setMasterPos(BlockPos pos) {
        this.masterPos = pos;
        this.setChanged();
    }

    @Override
    public BlockPos getMasterPos() {
        return this.masterPos;
    }

    public CompoundTag getOriginalNbt() {
        return this.originalNbt;
    }

    public void setOriginalBlock(BlockState state, CompoundTag nbt) {
        this.originalState = state;
        this.originalNbt = nbt;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public BlockState getOriginalState() {
        return this.originalState;
    }

    public void restoreOriginalBlock() {
        if (this.level != null && !this.level.isClientSide && this.originalState != null) {
            // 元のバニラブロックに戻す
            this.level.setBlock(this.worldPosition, this.originalState, 3);

            // アイテムなどの内部データを復元する
            if (this.originalNbt != null) {
                BlockEntity restoredBe = this.level.getBlockEntity(this.worldPosition);
                if (restoredBe != null) {
                    restoredBe.loadWithComponents(this.originalNbt, this.level.registryAccess());
                }
            }
        }
    }

    // エラーが出ていた解体通知メソッドを実装
    public void notifyMasterDisassembly() {
        if (this.level != null && !this.level.isClientSide && this.masterPos != null) {
            BlockEntity masterBe = this.level.getBlockEntity(this.masterPos);
            if (masterBe instanceof SimpleMachineBlockEntity master) {
                master.disassembleMainframe();
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.masterPos != null) {
            tag.putLong("MasterPos", this.masterPos.asLong());
        }
        if (this.originalState != null) {
            tag.put("OriginalState", NbtUtils.writeBlockState(this.originalState));
        }
        if (this.originalNbt != null) {
            tag.put("OriginalNbt", this.originalNbt);
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MasterPos")) {
            this.masterPos = BlockPos.of(tag.getLong("MasterPos"));
        }
        if (tag.contains("OriginalState")) {
            this.originalState = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("OriginalState"));
        }
        if (tag.contains("OriginalNbt")) {
            this.originalNbt = tag.getCompound("OriginalNbt");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }
}