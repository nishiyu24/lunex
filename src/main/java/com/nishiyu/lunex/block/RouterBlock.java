package com.nishiyu.lunex.block;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.item.TabletItem;
import com.nishiyu.lunex.item.WrenchItem;
import com.nishiyu.lunex.mcnet.IMCNetBlock;
import com.nishiyu.lunex.menu.RouterDashboardMenu;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

public class RouterBlock extends Block implements EntityBlock, IMCNetBlock {

    // =========================================
    // 翻訳キーとアノテーションの登録
    // =========================================
    @Translatable(en = "§c[Security] Access Denied: This router is private.§r", ja = "§c[セキュリティ] アクセス拒否: このルーターはプライベート設定です。§r")
    public static final String MSG_ACCESS_DENIED = "message.lunex.router.access_denied";

    @Translatable(en = "[Router Dashboard]\n Status: %s\n LAN IP: %s\n WAN IP: %s\n * Please use a Tablet for configuration.", ja = "[ルーターダッシュボード]\n 状態: %s\n LAN IP: %s\n WAN IP: %s\n ※設定はタブレットを使用して行ってください。")
    public static final String MSG_DASHBOARD_INFO = "message.lunex.router.dashboard_info";

    @Translatable(en = "Running", ja = "稼働中")
    public static final String STATUS_RUNNING = "message.lunex.router.status.running";

    @Translatable(en = "Stopped", ja = "停止中")
    public static final String STATUS_STOPPED = "message.lunex.router.status.stopped";

    @Translatable(en = "Not Set", ja = "未設定")
    public static final String STATUS_NOT_SET = "message.lunex.router.status.not_set";

    @Translatable(en = "Router Dashboard", ja = "ルーターダッシュボード")
    public static final String MENU_TITLE_DASHBOARD = "menu.lunex.router_dashboard";


    private static final Random RANDOM = new Random();
    public static final BooleanProperty IS_DISGUISED = BooleanProperty.create("is_disguised");

    public RouterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(IS_DISGUISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IS_DISGUISED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (state.getValue(IS_DISGUISED)) {
            return RenderShape.INVISIBLE;
        }
        return super.getRenderShape(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof RouterBlockEntity router) {
                RouterBlockEntity.tick(lvl, pos, st, router);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RouterBlockEntity routerBE) {

                if (routerBE.machineId == null) {
                    routerBE.machineId = UUID.randomUUID();
                    if (routerBE.getMachineLabel() == null || routerBE.getMachineLabel().isEmpty() || routerBE.getMachineLabel().equals("Router")) {
                        routerBE.setMachineLabel(String.format("Router_%04d", RANDOM.nextInt(10000)));
                    }
                }

                // ★ 設置者を常に記録
                if (placer instanceof Player player) {
                    routerBE.ownerUUID = player.getUUID();
                }

                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.contains("OwnerUUID")) routerBE.ownerUUID = tag.getUUID("OwnerUUID");

                routerBE.generateWanIp();
                routerBE.persistentData.putString("IPAddress", "192.168.1.1");
                routerBE.persistentData.putBoolean("DHCPServerEnabled", true);
                routerBE.persistentData.putString("DHCPBaseIP", "192.168.1");
                routerBE.persistentData.putInt("DHCPStartOctet", 10);
                routerBE.persistentData.putInt("DHCPPoolSize", 50);

                routerBE.setChanged();
                routerBE.sync();
            }
        }
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

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RouterBlockEntity router) {
            if (!level.isClientSide) {
                ItemStack tool = player.getMainHandItem();

                if (tool.getItem() instanceof WrenchItem && player.isShiftKeyDown()) {
                    handleWrenchDestroy(level, pos, state, router);
                } else {
                    handleNormalDestroy(level, pos, state, router);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    protected void handleWrenchDestroy(Level level, BlockPos pos, BlockState state, RouterBlockEntity router) {
        ItemStackHandler upgrades = router.upgradeHandler;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(i));
        }

        ItemStack frameStack = new ItemStack(Lunex.MACHINE_FRAME_ITEM.get());
        ItemEntity frameEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, frameStack);
        frameEntity.setDefaultPickUpDelay();
        level.addFreshEntity(frameEntity);
    }

    protected void handleNormalDestroy(Level level, BlockPos pos, BlockState state, RouterBlockEntity router) {
        ItemStack stack = new ItemStack(this.asItem());
        CompoundTag beTag = router.saveWithoutMetadata(level.registryAccess());
        BlockItem.setBlockEntityData(stack, router.getType(), beTag);

        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    // ★ 権限チェック: PrivateModeが無効、または自分がOwnerならアクセス許可 (true を返す)
    private boolean canAccess(RouterBlockEntity router, Player player) {
        if (!router.isPrivateMode()) return true;
        if (router.ownerUUID == null) return true;
        return player.getUUID().equals(router.ownerUUID);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RouterBlockEntity router) {

            if (player.isShiftKeyDown() && stack.getItem() instanceof WrenchItem) {
                if (!canAccess(router, player)) {
                    if (!level.isClientSide)
                        player.displayClientMessage(Component.translatable(MSG_ACCESS_DENIED), true);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }

                if (!level.isClientSide) {
                    handleNormalDestroy(level, pos, state, router);
                    level.removeBlock(pos, false);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!player.isShiftKeyDown() && stack.getItem() instanceof TabletItem) {
                if (!canAccess(router, player)) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable(MSG_ACCESS_DENIED), true);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }

                if (!level.isClientSide) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        Component title = Component.translatable(MENU_TITLE_DASHBOARD);
                        serverPlayer.openMenu(new SimpleMenuProvider(
                                (id, inventory, p) -> new RouterDashboardMenu(id, inventory, pos),
                                title
                        ), pos);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RouterBlockEntity router) {
                if (!canAccess(router, player)) {
                    player.displayClientMessage(Component.translatable(MSG_ACCESS_DENIED), true);
                    return InteractionResult.SUCCESS;
                }

                String status = router.isRunning() ? Component.translatable(STATUS_RUNNING).getString() : Component.translatable(STATUS_STOPPED).getString();
                String lanIp = router.persistentData.contains("IPAddress") ? router.persistentData.getString("IPAddress") : Component.translatable(STATUS_NOT_SET).getString();
                String wanIp = router.generateWanIp();

                player.sendSystemMessage(Component.translatable(MSG_DASHBOARD_INFO, status, lanIp, wanIp));
            }
        }
        return InteractionResult.SUCCESS;
    }
}