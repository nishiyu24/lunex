package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class SpeakerBlockEntity extends BlockEntity implements IMCNetDevice {
    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.SPEAKER_BE.get(), pos, state);
    }

    public float getMaxDistance() {
        if (!this.getPersistentData().contains("SpeakerMaxDistance")) {
            this.getPersistentData().putFloat("SpeakerMaxDistance", 10.0f);
        }
        return this.getPersistentData().getFloat("SpeakerMaxDistance");
    }

    public void setMaxDistance(float distance) {
        float clamped = Math.clamp(distance, 0.0f, 30.0f);
        this.getPersistentData().putFloat("SpeakerMaxDistance", clamped);

        // インターフェースの更新処理を呼び出す
        this.sync();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = new CompoundTag();
        super.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(@NotNull Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(pkt.getTag(), registries);
    }
}