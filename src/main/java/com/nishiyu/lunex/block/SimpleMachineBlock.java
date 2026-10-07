package com.nishiyu.lunex.block;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.frame.CoreMachineVMCache;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import com.nishiyu.lunex.menu.MainframeOverviewMenu;
import com.nishiyu.lunex.menu.SimpleMachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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

    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public SimpleMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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

    @org.jetbrains.annotations.Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, net.minecraft.world.level.block.entity.@NotNull BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, com.nishiyu.lunex.Lunex.SIMPLE_MACHINE_BE.get(), SimpleMachineBlockEntity::tick);
    }

    @SuppressWarnings("unchecked")
    protected static <E extends BlockEntity, A extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<A> createTickerHelper(
            net.minecraft.world.level.block.entity.BlockEntityType<A> expected,
            net.minecraft.world.level.block.entity.BlockEntityType<E> actual,
            net.minecraft.world.level.block.entity.BlockEntityTicker<? super E> ticker) {
        return actual == expected ? (net.minecraft.world.level.block.entity.BlockEntityTicker<A>) ticker : null;
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
    }

    private void triggerDisassembly(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SimpleMachineBlockEntity machineEntity) {
            if (machineEntity.isMainframeMaster) {
                machineEntity.disassembleMainframe();
            } else if (machineEntity.getMasterPos() != null) {
                BlockEntity masterBe = level.getBlockEntity(machineEntity.getMasterPos());
                if (masterBe instanceof SimpleMachineBlockEntity master) {
                    master.disassembleMainframe();
                }
            }
        }
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SimpleMachineBlockEntity sm) {
            if (!level.isClientSide) {
                triggerDisassembly(level, pos);

                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(this.asItem());
                net.minecraft.nbt.CompoundTag beTag = sm.saveWithoutMetadata(level.registryAccess());
                net.minecraft.world.item.BlockItem.setBlockEntityData(stack, sm.getType(), beTag);

                net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                itemEntity.setDefaultPickUpDelay();
                level.addFreshEntity(itemEntity);

                level.levelEvent(player, 2001, pos, Block.getId(state));
                level.removeBlock(pos, false);
                return state;
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (!level.isClientSide) {
                triggerDisassembly(level, pos);

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SimpleMachineBlockEntity machineEntity) {
                    // ★修正: getCore() 経由に変更
                    if (machineEntity.getCore().machineId != null) {
                        CoreMachineVMCache.removeVM(machineEntity.getCore().machineId);
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
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> new MainframeOverviewMenu(id, inventory, pos),
                            Component.literal("Mainframe Overview")
                    ), pos);
                }
                return InteractionResult.CONSUME;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SimpleMachineBlockEntity) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> new SimpleMachineMenu(id, inventory, pos),
                            Component.literal("Core Terminal")
                    ), pos);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}