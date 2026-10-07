package com.nishiyu.lunex.block;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.machine.frame.IMainframePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class DatabaseBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public DatabaseBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(ASSEMBLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UP, DOWN, NORTH, SOUTH, EAST, WEST, ASSEMBLED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DatabaseBlockEntity(pos, state);
    }

    // ★追加: 重複していた解体処理を共通メソッドとして切り出し
    private void triggerDisassembly(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMainframePart part && part.getMasterPos() != null) {
            BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
            if (masterBe instanceof SimpleMachineBlockEntity master) {
                master.disassembleMainframe();
            }
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            // 切り出した共通メソッドを呼び出す（爆発などで消滅した際の備え）
            triggerDisassembly(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DatabaseBlockEntity db) {
            if (!level.isClientSide) {
                // 切り出した共通メソッドでマスターの解体を先に行い、安全にネットワークから切り離す
                triggerDisassembly(level, pos);

                ItemStack tool = player.getMainHandItem();
                // Wrenchを持った状態でのスニーク破壊はパーツのばらまき処理、それ以外はNBT保持ドロップ
                if (tool.getItem() instanceof WrenchItem && player.isShiftKeyDown()) {
                    handleWrenchDestroy(level, pos, state, db);
                } else {
                    handleNormalDestroy(level, pos, state, db);
                }

                // クリエイティブでも確実に出現させ、バニラドロップを防ぐためにブロックを即座に消去する
                level.levelEvent(player, 2001, pos, Block.getId(state));
                level.removeBlock(pos, false);
                return state;
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    protected void handleWrenchDestroy(Level level, BlockPos pos, BlockState state, DatabaseBlockEntity db) {
        ItemStackHandler upgrades = db.upgradeHandler;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(i));
        }
        ItemStack frameStack = new ItemStack(Lunex.MACHINE_FRAME_ITEM.get());
        ItemEntity frameEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, frameStack);
        frameEntity.setDefaultPickUpDelay();
        level.addFreshEntity(frameEntity);
    }

    protected void handleNormalDestroy(Level level, BlockPos pos, BlockState state, DatabaseBlockEntity db) {
        ItemStack stack = new ItemStack(this.asItem());
        CompoundTag beTag = db.saveWithoutMetadata(level.registryAccess());
        BlockItem.setBlockEntityData(stack, db.getType(), beTag);

        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DatabaseBlockEntity db) {
            if (player.isShiftKeyDown() && stack.getItem() instanceof WrenchItem) {
                if (!level.isClientSide) {
                    handleNormalDestroy(level, pos, state, db);
                    level.removeBlock(pos, false);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.nishiyu.lunex.machine.frame.IMainframePart part) {
                InteractionResult delegateResult = part.delegateToMaster(level, player, hit);
                if (delegateResult != null) {
                    return delegateResult;
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}