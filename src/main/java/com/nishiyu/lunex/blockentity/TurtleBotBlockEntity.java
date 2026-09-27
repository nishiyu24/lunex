package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.AdvancedMachineBlock;
import com.nishiyu.lunex.program.server.ServerLuaVM;
// ★ 追加: タートル専用VMのインポート
import com.nishiyu.lunex.program.server.turtle.TurtleServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TurtleBotBlockEntity extends AdvancedMachineBlockEntity {

    public float renderOffsetX = 0, renderOffsetY = 0, renderOffsetZ = 0;
    public float renderRotDiff = 0;
    public long clientAnimStartTime = 0;

    public boolean hasPendingMove = false;
    public BlockPos pendingMoveTarget = null;
    public boolean hasPendingTurn = false;
    public Direction pendingTurnFacing = null;

    public TurtleBotBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.TURTLE_BOT_BE.get(), pos, state);

        // ★ 変更: 親クラスで初期化された通常のMachineServerLuaVMを、
        // タートル専用の TurtleServerLuaVM で上書きして参照を切り替える
        this.vm = new TurtleServerLuaVM(this);
    }

    public long getAnimationDurationMs() {
        return switch (this.upgrades.speedLevel) {
            case 3 -> 250;
            case 2 -> 500;
            case 1 -> 750;
            default -> 1000;
        };
    }

    public void startAnimation(float dx, float dy, float dz, float dRot) {
        this.renderOffsetX = dx;
        this.renderOffsetY = dy;
        this.renderOffsetZ = dz;
        this.renderRotDiff = dRot;
    }

    public void executePendingActions(net.minecraft.world.level.Level level, BlockPos pos) {
        if (this.isRemoved()) return;

        if (this.hasPendingTurn) {
            this.hasPendingTurn = false;
            BlockState currentState = this.getBlockState();
            BlockState newState = currentState.setValue(AdvancedMachineBlock.FACING, this.pendingTurnFacing);
            level.setBlock(pos, newState, 3);
            this.renderRotDiff = 0;
            level.sendBlockUpdated(pos, currentState, newState, 3);
        }

        if (this.hasPendingMove && this.pendingMoveTarget != null) {
            this.hasPendingMove = false;
            BlockPos targetPos = this.pendingMoveTarget;
            BlockState currentState = this.getBlockState();

            if (!level.getBlockState(targetPos).canBeReplaced()) {
                this.renderOffsetX = 0;
                this.renderOffsetY = 0;
                this.renderOffsetZ = 0;
                level.sendBlockUpdated(pos, currentState, currentState, 3);
                return;
            }

            CompoundTag tag = this.saveWithFullMetadata(level.registryAccess());
            tag.putBoolean("IsRunning", false);

            for (int i = 0; i < this.itemHandler.getSlots(); i++)
                this.itemHandler.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
            for (int i = 0; i < this.upgradeHandler.getSlots(); i++)
                this.upgradeHandler.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);

            ServerLuaVM activeVM = this.vm;
            activeVM.isRelocating = true;

            try {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(targetPos, currentState, 3);

                BlockEntity newBe = level.getBlockEntity(targetPos);
                if (newBe instanceof TurtleBotBlockEntity newTurtle) {
                    tag.putInt("x", targetPos.getX());
                    tag.putInt("y", targetPos.getY());
                    tag.putInt("z", targetPos.getZ());
                    newTurtle.loadWithComponents(tag, level.registryAccess());

                    newTurtle.vm.takeOverFrom(activeVM);
                    newTurtle.vm.isRelocating = false;
                    newTurtle.wasRunning = true;

                    newTurtle.renderOffsetX = 0;
                    newTurtle.renderOffsetY = 0;
                    newTurtle.renderOffsetZ = 0;
                    newTurtle.renderRotDiff = 0;
                    level.sendBlockUpdated(targetPos, Blocks.AIR.defaultBlockState(), currentState, 3);
                } else {
                    activeVM.isRelocating = false;
                    activeVM.stopProgram();
                }
            } finally {
                activeVM.isRelocating = false;
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("AnimDx", this.renderOffsetX);
        tag.putFloat("AnimDy", this.renderOffsetY);
        tag.putFloat("AnimDz", this.renderOffsetZ);
        tag.putFloat("AnimDRot", this.renderRotDiff);

        tag.putBoolean("PendingMove", this.hasPendingMove);
        if (this.pendingMoveTarget != null) tag.putLong("PendingTarget", this.pendingMoveTarget.asLong());
        tag.putBoolean("PendingTurn", this.hasPendingTurn);
        if (this.pendingTurnFacing != null) tag.putString("PendingTurnFacing", this.pendingTurnFacing.getName());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        float dx = tag.getFloat("AnimDx");
        float dy = tag.getFloat("AnimDy");
        float dz = tag.getFloat("AnimDz");
        float dRot = tag.getFloat("AnimDRot");

        if (dx != 0 || dy != 0 || dz != 0 || dRot != 0) {
            this.clientAnimStartTime = System.currentTimeMillis();
        }

        this.renderOffsetX = dx;
        this.renderOffsetY = dy;
        this.renderOffsetZ = dz;
        this.renderRotDiff = dRot;

        this.hasPendingMove = tag.getBoolean("PendingMove");
        if (tag.contains("PendingTarget")) this.pendingMoveTarget = BlockPos.of(tag.getLong("PendingTarget"));
        this.hasPendingTurn = tag.getBoolean("PendingTurn");
        if (tag.contains("PendingTurnFacing"))
            this.pendingTurnFacing = Direction.byName(tag.getString("PendingTurnFacing"));
    }

    @Override
    protected void saveSyncData(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveSyncData(tag, provider);
        tag.putFloat("AnimDx", this.renderOffsetX);
        tag.putFloat("AnimDy", this.renderOffsetY);
        tag.putFloat("AnimDz", this.renderOffsetZ);
        tag.putFloat("AnimDRot", this.renderRotDiff);
    }
}