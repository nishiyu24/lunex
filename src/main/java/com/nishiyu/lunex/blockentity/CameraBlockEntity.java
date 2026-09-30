package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class CameraBlockEntity extends BlockEntity implements IMCNetDevice {

    private float cameraYaw = 0.0f;
    private float cameraPitch = 0.0f;
    public CompoundTag persistentData = new CompoundTag();

    // 写真と動画のデータを保持
    public List<String> photoList = new ArrayList<>();
    public String videoData = "";

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.CAMERA_BE.get(), pos, state);
    }

    @Override
    public String getDeviceType() {
        return "camera";
    }

    @Override
    public boolean isNetworkActive() {
        return true;
    }

    public void setRotation(float yaw, float pitch) {
        this.cameraYaw = yaw;
        this.cameraPitch = pitch;
        this.setChanged();
        this.sync();
    }

    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("CameraYaw", this.cameraYaw);
        tag.putFloat("CameraPitch", this.cameraPitch);

        ListTag listTag = new ListTag();
        for (String photo : photoList) {
            listTag.add(StringTag.valueOf(photo));
        }
        tag.put("PhotoList", listTag);
        tag.putString("VideoData", videoData);

        // ネットワークAPI(Lua等)から参照できるよう persistentData にも同期
        this.persistentData.put("PhotoList", listTag.copy());
        this.persistentData.putString("VideoData", videoData);
        tag.put("NetworkData", this.persistentData);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.cameraYaw = tag.getFloat("CameraYaw");
        this.cameraPitch = tag.getFloat("CameraPitch");

        photoList.clear();
        if (tag.contains("PhotoList")) {
            ListTag listTag = tag.getList("PhotoList", 8);
            for (int i = 0; i < listTag.size(); i++) {
                photoList.add(listTag.getString(i));
            }
        }
        videoData = tag.getString("VideoData");

        if (tag.contains("NetworkData")) {
            this.persistentData = tag.getCompound("NetworkData");
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
}