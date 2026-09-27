package com.nishiyu.lunex.block;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.machine.SimpleMachineVMCache;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import com.nishiyu.lunex.menu.SimpleMachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public class SimpleMachineBlock extends Block implements EntityBlock, IMCNetBlock {

    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // JSONで要求されている6方向のプロパティを追加
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public SimpleMachineBlock(Properties properties) {
        super(properties);
        // 全てのプロパティの初期値を設定
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // ビルダーに全てのプロパティを登録
        builder.add(ASSEMBLED, FACING, UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(ASSEMBLED, false)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SimpleMachineBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (!level.isClientSide) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SimpleMachineBlockEntity machineEntity) {
                    if (machineEntity.isMainframeMaster) {
                        machineEntity.disassembleMainframe();
                    }
                    else if (machineEntity.getMasterPos() != null) {
                        BlockEntity masterBe = level.getBlockEntity(machineEntity.getMasterPos());
                        if (masterBe instanceof SimpleMachineBlockEntity master) {
                            master.disassembleMainframe();
                        }
                    }

                    if (machineEntity.machineId != null) {
                        SimpleMachineVMCache.removeVM(machineEntity.machineId);
                    }
                }
            }
            this.updateNetworkOnRemove(state, level, pos, newState);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (state.getValue(ASSEMBLED)) {
                InteractionResult result = MainframeScanner.tryOpenMainframeTerminal(level, pos, player);
                if (result.consumesAction()) return result;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SimpleMachineBlockEntity) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new SimpleMachineMenu(id, inventory, pos),
                            Component.literal("CLI Terminal")
                    ), pos);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}