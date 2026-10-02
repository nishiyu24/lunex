package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.blockentity.SpeakerBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public class SpeakerAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public SpeakerAPI() {}
    public SpeakerAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return MainframeConstants.API_SPEAKER; }

    @Override
    public String getRequiredFeature() { return MainframeConstants.FEATURE_SPEAKER; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new SpeakerAPI(vm);
    }

    @LuaFunction(
            value = "このスピーカーの音声の減衰距離を設定します（0～30、初期値10）。",
            args = {"num:maxDistance"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean setMaxSoundDistance(String targetStr, double maxDistance) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return false;

            BlockPos pos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (pos == null) return false;

            Level level = machine.getLevel();
            if (level != null && level.getBlockEntity(pos) instanceof SpeakerBlockEntity sbe) {
                sbe.setMaxDistance((float) maxDistance);
                return true;
            }
            return false;
        }, 50, false);
    }

    @LuaFunction(
            value = "このスピーカーからMinecraftのサウンドを再生します。",
            args = {"str:soundEventId", "num:pitch"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean playSound(String targetStr, String soundEventId, double pitch) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return false;

            BlockPos targetPos = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new).getActionTargetPos(targetStr);
            if (targetPos == null) return false;

            Level level = machine.getLevel();
            if (level == null || !(level.getBlockEntity(targetPos) instanceof SpeakerBlockEntity sbe)) return false;

            net.minecraft.sounds.SoundEvent soundEvent = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundEventId));
            if (soundEvent != null) {
                float mcVolume = sbe.getMaxDistance() / 16.0f;
                level.playSound(null, targetPos, soundEvent, SoundSource.RECORDS, mcVolume, (float) pitch);
                return true;
            }
            return false;
        }, 50, false);
    }
}