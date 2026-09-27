package com.nishiyu.lunex.block;

import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import com.nishiyu.lunex.mcnet.MCNetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class LANCableBlock extends Block implements IMCNetBlock {

    public static final EnumProperty<CableState> NORTH = EnumProperty.create("north", CableState.class);
    public static final EnumProperty<CableState> EAST = EnumProperty.create("east", CableState.class);
    public static final EnumProperty<CableState> SOUTH = EnumProperty.create("south", CableState.class);
    public static final EnumProperty<CableState> WEST = EnumProperty.create("west", CableState.class);
    public static final EnumProperty<CableState> UP = EnumProperty.create("up", CableState.class);
    public static final EnumProperty<CableState> DOWN = EnumProperty.create("down", CableState.class);
    public static final Map<Direction, EnumProperty<CableState>> PROPERTY_BY_DIRECTION = Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST,
            Direction.SOUTH, SOUTH, Direction.WEST, WEST,
            Direction.UP, UP, Direction.DOWN, DOWN
    );
    protected final VoxelShape[] shapeByIndex;

    public LANCableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, CableState.NONE).setValue(EAST, CableState.NONE)
                .setValue(SOUTH, CableState.NONE).setValue(WEST, CableState.NONE)
                .setValue(UP, CableState.NONE).setValue(DOWN, CableState.NONE));
        this.shapeByIndex = this.makeShapes();
    }

    private VoxelShape[] makeShapes() {
        VoxelShape[] shapes = new VoxelShape[64];
        float min = 6.0F;
        float max = 10.0F;
        VoxelShape core = Block.box(min, min, min, max, max, max);
        VoxelShape up = Block.box(min, max, min, max, 16.0F, max);
        VoxelShape down = Block.box(min, 0.0F, min, max, min, max);
        VoxelShape north = Block.box(min, min, 0.0F, max, max, min);
        VoxelShape south = Block.box(min, min, max, max, max, 16.0F);
        VoxelShape west = Block.box(0.0F, min, min, min, max, max);
        VoxelShape east = Block.box(max, min, min, 16.0F, max, max);

        for (int i = 0; i < 64; i++) {
            VoxelShape shape = core;
            if ((i & 1) != 0) shape = Shapes.or(shape, down);
            if ((i & 2) != 0) shape = Shapes.or(shape, up);
            if ((i & 4) != 0) shape = Shapes.or(shape, north);
            if ((i & 8) != 0) shape = Shapes.or(shape, south);
            if ((i & 16) != 0) shape = Shapes.or(shape, west);
            if ((i & 32) != 0) shape = Shapes.or(shape, east);
            shapes[i] = shape;
        }
        return shapes;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = 0;
        if (state.getValue(DOWN) == CableState.CONNECTED) index |= 1;
        if (state.getValue(UP) == CableState.CONNECTED) index |= 2;
        if (state.getValue(NORTH) == CableState.CONNECTED) index |= 4;
        if (state.getValue(SOUTH) == CableState.CONNECTED) index |= 8;
        if (state.getValue(WEST) == CableState.CONNECTED) index |= 16;
        if (state.getValue(EAST) == CableState.CONNECTED) index |= 32;
        return this.shapeByIndex[index];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return this.defaultBlockState()
                .setValue(DOWN, connectsTo(level, pos.below()) ? CableState.CONNECTED : CableState.NONE)
                .setValue(UP, connectsTo(level, pos.above()) ? CableState.CONNECTED : CableState.NONE)
                .setValue(NORTH, connectsTo(level, pos.north()) ? CableState.CONNECTED : CableState.NONE)
                .setValue(EAST, connectsTo(level, pos.east()) ? CableState.CONNECTED : CableState.NONE)
                .setValue(SOUTH, connectsTo(level, pos.south()) ? CableState.CONNECTED : CableState.NONE)
                .setValue(WEST, connectsTo(level, pos.west()) ? CableState.CONNECTED : CableState.NONE);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        CableState currentState = state.getValue(PROPERTY_BY_DIRECTION.get(direction));

        if (currentState == CableState.BLOCKED) {
            return state;
        }

        if (neighborState.getBlock() instanceof LANCableBlock) {
            CableState neighborFacingUs = neighborState.getValue(PROPERTY_BY_DIRECTION.get(direction.getOpposite()));
            if (neighborFacingUs == CableState.BLOCKED) {
                return state.setValue(PROPERTY_BY_DIRECTION.get(direction), CableState.NONE);
            }
        }

        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, neighborPos) ? CableState.CONNECTED : CableState.NONE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof WrenchItem) {
            if (!level.isClientSide()) {
                Vec3 hitLoc = hitResult.getLocation();
                double dx = hitLoc.x() - pos.getX() - 0.5;
                double dy = hitLoc.y() - pos.getY() - 0.5;
                double dz = hitLoc.z() - pos.getZ() - 0.5;
                double max = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));

                Direction targetDir = hitResult.getDirection();
                if (max == Math.abs(dx)) targetDir = dx > 0 ? Direction.EAST : Direction.WEST;
                else if (max == Math.abs(dy)) targetDir = dy > 0 ? Direction.UP : Direction.DOWN;
                else if (max == Math.abs(dz)) targetDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;

                EnumProperty<CableState> prop = PROPERTY_BY_DIRECTION.get(targetDir);
                CableState current = state.getValue(prop);

                CableState nextState;
                if (current == CableState.BLOCKED) {
                    nextState = connectsTo(level, pos.relative(targetDir)) ? CableState.CONNECTED : CableState.NONE;
                    player.displayClientMessage(Component.literal("§aConnection Restored: " + targetDir.name()), true);
                } else {
                    nextState = CableState.BLOCKED;
                    player.displayClientMessage(Component.literal("§cConnection Blocked: " + targetDir.name()), true);
                }

                level.setBlockAndUpdate(pos, state.setValue(prop, nextState));

                BlockPos neighborPos = pos.relative(targetDir);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof LANCableBlock) {
                    EnumProperty<CableState> nProp = PROPERTY_BY_DIRECTION.get(targetDir.getOpposite());
                    CableState nNextState = (nextState == CableState.BLOCKED) ? CableState.BLOCKED :
                            (((LANCableBlock) neighborState.getBlock()).connectsTo(level, pos) ? CableState.CONNECTED : CableState.NONE);
                    level.setBlockAndUpdate(neighborPos, neighborState.setValue(nProp, nNextState));
                }

                MCNetUtil.triggerNetworkUpdate(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public boolean connectsTo(BlockGetter level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        BlockEntity be = level.getBlockEntity(pos);
        return block instanceof LANCableBlock || MCNetUtil.isMCNetDevice(be);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        this.updateNetworkOnRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    public enum CableState implements StringRepresentable {
        NONE("none"),
        CONNECTED("connected"),
        BLOCKED("blocked");

        private final String name;

        CableState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}