package com.nishiyu.lunex.block;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class TurtleBotBlock extends AdvancedMachineBlock {

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D);

    public TurtleBotBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TurtleBotBlockEntity(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ▼ 追加: タートルボットをレンチで破壊した時の処理（親クラスの処理＋ピストン）
    @Override
    protected void handleWrenchDestroy(Level level, BlockPos pos, BlockState state, AdvancedMachineBlockEntity machineEntity) {
        super.handleWrenchDestroy(level, pos, state, machineEntity);

        // 追加でピストンを1つドロップさせる
        ItemStack pistonStack = new ItemStack(net.minecraft.world.level.block.Blocks.PISTON);
        net.minecraft.world.entity.item.ItemEntity pistonEntity = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, pistonStack);
        pistonEntity.setDefaultPickUpDelay();
        level.addFreshEntity(pistonEntity);
    }
}