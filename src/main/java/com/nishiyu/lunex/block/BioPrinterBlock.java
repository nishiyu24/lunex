package com.nishiyu.lunex.block;

import com.nishiyu.lunex.blockentity.BioPrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction; // 追加
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext; // 追加
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition; // 追加
import net.minecraft.world.level.block.state.properties.BlockStateProperties; // 追加
import net.minecraft.world.level.block.state.properties.DirectionProperty; // 追加
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BioPrinterBlock extends Block implements EntityBlock {

    // 向きのプロパティを定義 (水平方向の東西南北)
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 高さ15ピクセルの当たり判定
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public BioPrinterBlock(Properties properties) {
        super(properties);
        // デフォルトのステート（状態）を北向きに設定
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ブロックを設置する際の処理（プレイヤーが向いている方向の逆＝プレイヤー側を正面にする）
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ブロックステートに「向き(FACING)」のプロパティを登録する
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BioPrinterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof BioPrinterBlockEntity printer) {
                BioPrinterBlockEntity.tick(lvl, pos, st, printer);
            }
        };
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BioPrinterBlockEntity printer) {
                player.openMenu(printer, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}