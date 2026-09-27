package com.nishiyu.lunex.block;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.item.PaintballItem;
import com.nishiyu.lunex.item.TabletItem;
import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeScanner;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import com.nishiyu.lunex.menu.MachineSettings.MachineSettingsMenu;
import com.nishiyu.lunex.menu.AdvancedMachineMenu;
import com.nishiyu.lunex.util.WorkspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

public class AdvancedMachineBlock extends Block implements EntityBlock, IMCNetBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty IS_DISGUISED = BooleanProperty.create("is_disguised");

    // ★追加: メインフレーム接続判定用のプロパティ
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Random RANDOM = new Random();

    public AdvancedMachineBlock(Properties properties) {
        super(properties);
        // ★修正: デフォルトステートにすべてのプロパティを登録
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(IS_DISGUISED, false)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false));
    }

    // ★修正: プロパティの定義
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IS_DISGUISED, UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AdvancedMachineBlockEntity(pos, state);
    }

    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        if (state.getValue(IS_DISGUISED)) return RenderShape.INVISIBLE;
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof AdvancedMachineBlockEntity machine) {
                AdvancedMachineBlockEntity.tick(lvl, pos, st, machine);
            }
        };
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        this.updateNetworkOnPlace(state, level, pos, oldState);
        // ★追加: 設置時のスキャントリガー
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            MainframeScanner.attemptFormMainframe(level, pos);
        }
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AdvancedMachineBlockEntity machineBE) {

                if (machineBE.machineId == null) {
                    machineBE.machineId = UUID.randomUUID();
                    if (machineBE.getMachineLabel() == null || machineBE.getMachineLabel().isEmpty()) {
                        machineBE.setMachineLabel(String.format("Machine_%04d", RANDOM.nextInt(10000)));
                    }
                }

                if (placer instanceof Player player) {
                    machineBE.ownerUUID = player.getUUID();
                }

                if (machineBE.workspaceId == null || machineBE.workspaceId.isEmpty()) {
                    String safeLabel = machineBE.getMachineLabel().replaceAll("[^a-zA-Z0-9_\\-]", "");
                    if (safeLabel.isEmpty()) safeLabel = "Machine";
                    String shortId = machineBE.machineId.toString().substring(0, 8);
                    machineBE.workspaceId = safeLabel + "_" + shortId;
                }

                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.contains("WorkspaceId")) machineBE.workspaceId = tag.getString("WorkspaceId");
                if (tag.contains("OwnerUUID")) machineBE.ownerUUID = tag.getUUID("OwnerUUID");

                if (machineBE.workspaceId != null && !machineBE.workspaceId.isEmpty()) {
                    if (WorkspaceManager.requiresInitialization(level.getServer(), machineBE.workspaceId)) {
                        WorkspaceManager.initializeWorkspace(level.getServer(), machineBE.workspaceId);
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
            // ★追加: 破壊時の解体トリガー
            if (blockEntity instanceof IMainframePart part && part.getMasterPos() != null) {
                BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
                if (masterBe instanceof SimpleMachineBlockEntity master) {
                    master.disassembleMainframe();
                }
            }
            if (blockEntity instanceof AdvancedMachineBlockEntity machineEntity) {
                ItemStackHandler inventory = machineEntity.itemHandler;
                for (int i = 0; i < inventory.getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i));
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AdvancedMachineBlockEntity machineEntity) {
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

    protected void handleWrenchDestroy(Level level, BlockPos pos, BlockState state, AdvancedMachineBlockEntity machineEntity) {
        ItemStackHandler upgrades = machineEntity.upgradeHandler;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(i));
        }

        if (!level.isClientSide && machineEntity.workspaceId != null && !machineEntity.workspaceId.isEmpty()) {
            WorkspaceManager.deleteWorkspace(level.getServer(), machineEntity.workspaceId);
        }

        ItemStack frameStack = new ItemStack(Lunex.MACHINE_FRAME_ITEM.get());
        ItemEntity frameEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, frameStack);
        frameEntity.setDefaultPickUpDelay();
        level.addFreshEntity(frameEntity);
    }

    protected void handleNormalDestroy(Level level, BlockPos pos, BlockState state, AdvancedMachineBlockEntity machineEntity) {
        for (int i = 0; i < machineEntity.itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = machineEntity.itemHandler.getStackInSlot(i);
            if (!stackInSlot.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackInSlot);
                machineEntity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        ItemStack stack = new ItemStack(this.asItem());
        CompoundTag beTag = machineEntity.saveWithoutMetadata(level.registryAccess());
        BlockItem.setBlockEntityData(stack, machineEntity.getType(), beTag);

        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    private boolean canAccess(AdvancedMachineBlockEntity machine, Player player) {
        if (!machine.isPrivateMode()) return false;
        if (machine.ownerUUID == null) return false;
        return !player.getUUID().equals(machine.ownerUUID);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
        if (stack.getItem() instanceof PaintballItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AdvancedMachineBlockEntity machine) {
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
                                    (id, inventory, p) -> new MachineSettingsMenu(id, inventory, pos),
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
            if (blockEntity instanceof AdvancedMachineBlockEntity machineEntity) {
                if (canAccess(machineEntity, player)) {
                    player.displayClientMessage(Component.literal("§c[Security] Access Denied: This machine is private.§r"), true);
                    return InteractionResult.SUCCESS;
                }

                Component title = (machineEntity.getMachineLabel() != null && !machineEntity.getMachineLabel().isEmpty())
                        ? Component.literal(machineEntity.getMachineLabel())
                        : Component.translatable("block.lunex.programmable_machine");

                // ★追加: 組み込まれている場合はマスターのGUIを開かせるかを選択
                if (machineEntity instanceof IMainframePart part && part.getMasterPos() != null) {
                    BlockEntity masterBe = level.getBlockEntity(part.getMasterPos());
                    if (masterBe instanceof SimpleMachineBlockEntity master) {
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.openMenu(new SimpleMenuProvider(
                                    (id, inventory, p) -> new AdvancedMachineMenu(id, inventory, master.getBlockPos()),
                                    Component.literal("Mainframe Terminal")
                            ), master.getBlockPos());
                        }
                        return InteractionResult.SUCCESS;
                    }
                }

                if (player.isShiftKeyDown()) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.openMenu(new SimpleMenuProvider(
                                (id, inventory, p) -> new AdvancedMachineMenu(id, inventory, pos),
                                title
                        ), pos);
                    }
                } else {
                    if (machineEntity.vm.isRunning) {
                        machineEntity.vm.triggerEvent("on_click");
                    } else {
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.openMenu(new SimpleMenuProvider(
                                    (id, inventory, p) -> new AdvancedMachineMenu(id, inventory, pos),
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
            if (be instanceof AdvancedMachineBlockEntity machine) {
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
        if (be instanceof AdvancedMachineBlockEntity machine) {
            return machine.redstoneOutputs.getOrDefault(direction.getOpposite(), 0);
        }
        return 0;
    }

    @Override
    public int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}