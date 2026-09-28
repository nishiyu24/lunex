package com.nishiyu.lunex.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

public class MobFakePlayerContext {
    private final CustomBioMobEntity mob;
    private FakePlayer fakePlayer;
    private BlockPos currentMiningPos = null;
    private float miningProgress = 0.0f;
    private UUID fakePlayerUuid = null;

    private int lastSwingTick = 0;

    public MobFakePlayerContext(CustomBioMobEntity mob) {
        this.mob = mob;
    }

    public FakePlayer getFakePlayer() {
        if (mob.level() instanceof ServerLevel serverLevel) {
            if (this.fakePlayer == null) {
                if (this.fakePlayerUuid == null) {
                    this.fakePlayerUuid = UUID.randomUUID();
                }
                GameProfile profile = new GameProfile(this.fakePlayerUuid, "[Bot]" + mob.getName().getString());
                this.fakePlayer = FakePlayerFactory.get(serverLevel, profile);
            }
            this.fakePlayer.setPos(mob.getX(), mob.getY(), mob.getZ());
            this.fakePlayer.setYRot(mob.getYRot());
            this.fakePlayer.setXRot(mob.getXRot());
            this.fakePlayer.setYHeadRot(mob.getYHeadRot());

            this.fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, mob.getItemInHand(InteractionHand.MAIN_HAND));
            this.fakePlayer.setItemInHand(InteractionHand.OFF_HAND, mob.getItemInHand(InteractionHand.OFF_HAND));
            return this.fakePlayer;
        }
        return null;
    }

    public boolean tickMining(BlockPos pos) {
        if (mob.level().isClientSide) return false;

        FakePlayer player = this.getFakePlayer();
        if (player == null) return false;

        if (!pos.equals(this.currentMiningPos)) {
            if (this.currentMiningPos != null) {
                mob.level().destroyBlockProgress(mob.getId(), this.currentMiningPos, -1);
            }
            this.currentMiningPos = pos;
            this.miningProgress = 0.0f;
        }

        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir()) {
            this.currentMiningPos = null;
            mob.level().destroyBlockProgress(mob.getId(), pos, -1);
            return true;
        }

        float destroySpeed = state.getDestroyProgress(player, mob.level(), pos) * mob.traitManager.getMiningSpeedMultiplier();
        this.miningProgress += destroySpeed;

        mob.level().destroyBlockProgress(mob.getId(), pos, (int) (this.miningProgress * 10.0F));

        if (mob.tickCount - this.lastSwingTick >= 4) {
            mob.swing(InteractionHand.MAIN_HAND, true);
            this.lastSwingTick = mob.tickCount;
        }

        if (mob.tickCount % 4 == 0) {
            net.minecraft.world.level.block.SoundType soundtype = state.getSoundType(mob.level(), pos, player);
            mob.playSound(soundtype.getHitSound(), (soundtype.getVolume() + 1.0F) / 8.0F, soundtype.getPitch() * 0.5F);
        }

        if (this.miningProgress >= 1.0f) {
            player.gameMode.destroyBlock(pos);
            mob.setItemInHand(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND));

            this.currentMiningPos = null;
            this.miningProgress = 0.0f;
            mob.level().destroyBlockProgress(mob.getId(), pos, -1);
            return true;
        }
        return false;
    }

    public void resetMining() {
        if (this.currentMiningPos != null && !mob.level().isClientSide) {
            mob.level().destroyBlockProgress(mob.getId(), this.currentMiningPos, -1);
            this.currentMiningPos = null;
        }
        this.miningProgress = 0.0f;
        this.lastSwingTick = 0;
    }
}