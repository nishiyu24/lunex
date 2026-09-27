package com.nishiyu.lunex.block;

import com.mojang.serialization.MapCodec;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ScreenBlock extends BaseEntityBlock implements IMCNetBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty SCREEN_UP = BooleanProperty.create("screen_up");
    public static final BooleanProperty SCREEN_DOWN = BooleanProperty.create("screen_down");
    public static final BooleanProperty SCREEN_LEFT = BooleanProperty.create("screen_left");
    public static final BooleanProperty SCREEN_RIGHT = BooleanProperty.create("screen_right");

    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);

    public ScreenBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SCREEN_UP, false).setValue(SCREEN_DOWN, false).setValue(SCREEN_LEFT, false).setValue(SCREEN_RIGHT, false)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(ASSEMBLED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCREEN_UP, SCREEN_DOWN, SCREEN_LEFT, SCREEN_RIGHT, UP, DOWN, NORTH, SOUTH, EAST, WEST, ASSEMBLED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (state.getValue(ASSEMBLED)) {
                InteractionResult result = MainframeScanner.tryOpenMainframeTerminal(level, pos, player);
                if (result.consumesAction()) return result;
            }
        }

        Direction facing = state.getValue(FACING);
        if (hitResult.getDirection() == facing) {
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            // ★ 修正: 隣接するブロックが「ScreenBlock」だった場合のみ再構築処理を走らせる
            // （土やレッドストーンなどを置いた時の無駄な再計算ラグを防ぐ）
            if (block instanceof ScreenBlock) {
                if (level.getBlockEntity(pos) instanceof ScreenBlockEntity sbe) sbe.updateScreenNetwork();
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ScreenBlockEntity sbe) sbe.updateScreenNetwork();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMainframePart part && part.getMasterPos() != null) {
                BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
                if (masterBe instanceof SimpleMachineBlockEntity master) {
                    master.disassembleMainframe();
                }
            }
        }
        this.updateNetworkOnRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }
}