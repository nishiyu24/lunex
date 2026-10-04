package com.nishiyu.lunex.block;

import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.item.TabletItem;
import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import com.nishiyu.lunex.menu.turtle.TurtleBotMenu;
import com.nishiyu.lunex.menu.turtle.TurtleSettingsMenu;
import com.nishiyu.lunex.util.WorkspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class TurtleBotBlock extends Block implements EntityBlock, IMCNetBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty IS_DISGUISED = BooleanProperty.create("is_disguised");

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D);
    private static final Random RANDOM = new Random();

    public TurtleBotBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(IS_DISGUISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IS_DISGUISED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TurtleBotBlockEntity(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        if (state.getValue(IS_DISGUISED)) return RenderShape.INVISIBLE;
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof TurtleBotBlockEntity machine) {
                TurtleBotBlockEntity.tick(lvl, pos, st, machine);
            }
        };
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TurtleBotBlockEntity machineBE) {

                // ▼ 修正箇所: getMachineLabel 等は machineBE から直接呼び出す
                if (machineBE.getMachineLabel() == null || machineBE.getMachineLabel().isEmpty()) {
                    machineBE.setMachineLabel(String.format("Turtle_%04d", RANDOM.nextInt(10000)));
                }

                if (placer instanceof Player player) {
                    machineBE.getCore().ownerUUID = player.getUUID();
                }

                if (machineBE.getWorkspaceId() == null || machineBE.getWorkspaceId().isEmpty()) {
                    String safeLabel = machineBE.getMachineLabel().replaceAll("[^a-zA-Z0-9_\\-]", "");
                    if (safeLabel.isEmpty()) safeLabel = "Turtle";
                    String shortId = machineBE.getCore().machineId.toString().substring(0, 8);
                    machineBE.getCore().workspaceId = safeLabel + "_" + shortId;
                }

                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.contains("WorkspaceId")) machineBE.getCore().workspaceId = tag.getString("WorkspaceId");
                if (tag.contains("OwnerUUID")) machineBE.getCore().ownerUUID = tag.getUUID("OwnerUUID");

                if (machineBE.getWorkspaceId() != null && !machineBE.getWorkspaceId().isEmpty()) {
                    if (WorkspaceManager.requiresInitialization(level.getServer(), machineBE.getWorkspaceId())) {
                        WorkspaceManager.initializeWorkspace(level.getServer(), machineBE.getWorkspaceId());
                        WorkspaceManager.cleanOldWorkspaces(level.getServer());
                    }
                }

                machineBE.setChanged();
                machineBE.sync();
            }
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        this.updateNetworkOnRemove(state, level, pos, newState);
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TurtleBotBlockEntity machineEntity) {
                if (!machineEntity.getCore().isRelocating) {
                    ItemStackHandler inventory = machineEntity.getCore().itemHandler;
                    for (int i = 0; i < inventory.getSlots(); i++) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i));
                    }
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TurtleBotBlockEntity machineEntity) {
            if (!level.isClientSide) {
                ItemStack tool = player.getMainHandItem();
                if (tool.getItem() instanceof WrenchItem && player.isShiftKeyDown()) {
                    handleWrenchDestroy(level, pos, state, machineEntity);
                } else {
                    handleNormalDestroy(level, pos, state, machineEntity);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    protected void handleWrenchDestroy(Level level, BlockPos pos, BlockState state, TurtleBotBlockEntity machineEntity) {
        machineEntity.getCore().dispose();

        if (!level.isClientSide && machineEntity.getWorkspaceId() != null && !machineEntity.getWorkspaceId().isEmpty()) {
            WorkspaceManager.deleteWorkspace(level.getServer(), machineEntity.getWorkspaceId());
        }

        ItemStack frameStack = new ItemStack(this.asItem());
        ItemEntity frameEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, frameStack);
        frameEntity.setDefaultPickUpDelay();
        level.addFreshEntity(frameEntity);

        ItemStack pistonStack = new ItemStack(net.minecraft.world.level.block.Blocks.PISTON);
        ItemEntity pistonEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, pistonStack);
        pistonEntity.setDefaultPickUpDelay();
        level.addFreshEntity(pistonEntity);
    }

    protected void handleNormalDestroy(Level level, BlockPos pos, BlockState state, TurtleBotBlockEntity machineEntity) {
        machineEntity.getCore().dispose();

        for (int i = 0; i < machineEntity.getCore().itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = machineEntity.getCore().itemHandler.getStackInSlot(i);
            if (!stackInSlot.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackInSlot);
                machineEntity.getCore().itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        ItemStack stack = new ItemStack(this.asItem());
        CompoundTag beTag = machineEntity.saveWithoutMetadata(level.registryAccess());
        BlockItem.setBlockEntityData(stack, machineEntity.getType(), beTag);

        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    private boolean canAccess(TurtleBotBlockEntity machine, Player player) {
        if (!machine.getCore().isPrivateMode) return false;
        if (machine.getCore().ownerUUID == null) return false;
        return !player.getUUID().equals(machine.getCore().ownerUUID);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TurtleBotBlockEntity machine) {
            if (player.isShiftKeyDown() && stack.getItem() instanceof WrenchItem) {
                if (canAccess(machine, player)) {
                    if (!level.isClientSide)
                        player.displayClientMessage(Component.literal("§c[Security] Access Denied: This machine is private.§r"), true);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }

                if (!level.isClientSide) {
                    handleNormalDestroy(level, pos, state, machine);
                    level.removeBlock(pos, false);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!player.isShiftKeyDown()) {
                if (stack.getItem() instanceof TabletItem) {
                    if (canAccess(machine, player)) {
                        if (!level.isClientSide)
                            player.displayClientMessage(Component.literal("§c[Security] Access Denied: This machine is private.§r"), true);
                        return ItemInteractionResult.sidedSuccess(level.isClientSide);
                    }

                    if (!level.isClientSide) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            Component title = (machine.getMachineLabel() != null && !machine.getMachineLabel().isEmpty())
                                    ? Component.literal(machine.getMachineLabel())
                                    : Component.translatable("block.lunex.machine_settings");

                            serverPlayer.openMenu(new SimpleMenuProvider(
                                    (id, inventory, p) -> new TurtleSettingsMenu(id, inventory, pos),
                                    title
                            ), pos);
                        }
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TurtleBotBlockEntity machineEntity) {
                if (canAccess(machineEntity, player)) {
                    player.displayClientMessage(Component.literal("§c[Security] Access Denied: This machine is private.§r"), true);
                    return InteractionResult.SUCCESS;
                }

                Component title = (machineEntity.getMachineLabel() != null && !machineEntity.getMachineLabel().isEmpty())
                        ? Component.literal(machineEntity.getMachineLabel())
                        : Component.translatable("block.lunex.programmable_machine");

                if (player.isShiftKeyDown()) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.openMenu(new SimpleMenuProvider(
                                (id, inventory, p) -> new TurtleBotMenu(id, inventory, pos),
                                title
                        ), pos);
                    }
                } else {
                    if (machineEntity.isRunning()) {
                        machineEntity.getCore().vm.triggerEvent("on_click");
                    } else {
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.openMenu(new SimpleMenuProvider(
                                    (id, inventory, p) -> new TurtleBotMenu(id, inventory, pos),
                                    title
                            ), pos);
                        }
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TurtleBotBlockEntity machine) {
                machine.onRedstoneUpdate(level.hasNeighborSignal(pos));
            }
        }
    }

    @Override
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getSignal(@NotNull BlockState state, BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TurtleBotBlockEntity machine) {
            return machine.getCore().redstoneOutputs.getOrDefault(direction.getOpposite(), 0);
        }
        return 0;
    }

    @Override
    public int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}