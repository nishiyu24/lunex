package com.nishiyu.lunex.block;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProbeBlock extends Block implements EntityBlock, IMCNetBlock {

    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty IS_DISGUISED = BooleanProperty.create("is_disguised");
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ProbeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(IS_DISGUISED, false)
                .setValue(ASSEMBLED, false)
                .setValue(ACTIVE, false)
        );
    }

    // ★追加: 削除してしまったメソッドを復活させます。ProbeScreen等から呼ばれます。
    public static BooleanProperty getPropertyByDirection(Direction dir) {
        return switch (dir) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST, IS_DISGUISED, ASSEMBLED, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(IS_DISGUISED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
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
    public @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);

            // 1. 合体時はマスターブロックに処理を委譲
            if (be instanceof com.nishiyu.lunex.machine.IMainframePart part) {
                InteractionResult delegateResult = part.delegateToMaster(level, player, hitResult);
                if (delegateResult != null) {
                    return delegateResult;
                }
            }

            // 2. 未合体時はプローブ本来のGUIを開く
            if (be instanceof com.nishiyu.lunex.blockentity.ProbeBlockEntity probe) {
                player.openMenu(probe, pos);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof ProbeBlockEntity probe) {
            return probe.redstoneOutputs.getOrDefault(direction.getOpposite(), 0);
        }
        return 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProbeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, Lunex.PROBE_BE.get(), ProbeBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    protected static <E extends BlockEntity, A extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<A> createTickerHelper(
            net.minecraft.world.level.block.entity.BlockEntityType<A> p_152133_,
            net.minecraft.world.level.block.entity.BlockEntityType<E> p_152134_,
            net.minecraft.world.level.block.entity.BlockEntityTicker<? super E> p_152135_) {
        return p_152134_ == p_152133_ ? (net.minecraft.world.level.block.entity.BlockEntityTicker<A>) p_152135_ : null;
    }
}