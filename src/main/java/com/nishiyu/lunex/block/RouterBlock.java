package com.nishiyu.lunex.block;

import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// ★修正: RouterDashboard などの単体機能を削除し、メインフレームパーツ専用ブロックとして実装。
public class RouterBlock extends Block implements EntityBlock {

    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public RouterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ASSEMBLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.nishiyu.lunex.machine.IMainframePart part) {
                InteractionResult delegateResult = part.delegateToMaster(level, player, hitResult);
                if (delegateResult != null) {
                    return delegateResult;
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMainframePart part && part.getMasterPos() != null) {
                BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
                // 構成ブロックが破壊されたらメインフレームを解体する
                if (masterBe instanceof SimpleMachineBlockEntity master) {
                    master.disassembleMainframe();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}