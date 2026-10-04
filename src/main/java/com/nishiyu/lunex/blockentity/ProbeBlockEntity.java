package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.IMainframeExtension;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.machine.MainframeCapabilityHandler;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.menu.ProbeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProbeBlockEntity extends BlockEntity implements MenuProvider, IMCNetDevice, IMainframePart {

    public final Map<Direction, Integer> redstoneOutputs = new ConcurrentHashMap<>();
    public boolean isDetected = true;
    public BlockState disguiseState = null;

    public BlockPos mainframeMasterPos = null;

    public ProbeBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.PROBE_BE.get(), pos, state);
        for (Direction dir : Direction.values()) {
            redstoneOutputs.put(dir, 0);
        }
    }

    // ★追加: 設置時やロード時に確実な初期値を NBT に持たせ、UI とのズレを防ぐ
    @Override
    public void onLoad() {
        super.onLoad();
        CompoundTag data = this.getPersistentData();
        if (!data.contains("IsDetected")) data.putBoolean("IsDetected", this.isDetected);
        if (!data.contains("IOMode")) data.putString("IOMode", "IN");
        if (!data.contains("TargetType")) data.putString("TargetType", "ALL");
        if (!data.contains("NBTFilter")) data.putString("NBTFilter", "");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProbeBlockEntity be) {
        // ★修正: be.getPersistentData() ではなく、確実な内部変数(isDetected)を見る
        if (!state.getValue(com.nishiyu.lunex.block.ProbeBlock.ASSEMBLED) || !be.isDetected || be.mainframeMasterPos == null) return;
        if (level.getGameTime() % 20L != 0L) return;

        BlockEntity masterBe = level.getBlockEntity(be.mainframeMasterPos);
        if (!(masterBe instanceof SimpleMachineBlockEntity master) || !master.isMainframeMaster) return;

        String mode = be.getPersistentData().getString("IOMode");
        if (mode.isEmpty()) mode = "IN";

        String targetType = be.getPersistentData().getString("TargetType");
        if (targetType.isEmpty()) targetType = "ALL";

        boolean processItem = "ALL".equals(targetType) || "ITEM".equals(targetType);
        boolean processEnergy = "ALL".equals(targetType) || "ENERGY".equals(targetType);

        String filter = be.getPersistentData().getString("NBTFilter");

        IItemHandler wrappedItemStorage = processItem ? new MainframeCapabilityHandler.ProbeItemHandlerWrapper(master.mainframeStorage, be) : null;
        IEnergyStorage wrappedEnergyStorage = processEnergy ? new MainframeCapabilityHandler.ProbeEnergyStorageWrapper(master.energyStorage, be) : null;

        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor == null || neighbor instanceof IMainframePart) continue;

            if (processItem && wrappedItemStorage != null) {
                IItemHandler neighborItemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(dir), dir.getOpposite());
                if (neighborItemHandler != null) {
                    if ("IN".equals(mode)) {
                        transferItems(neighborItemHandler, wrappedItemStorage, filter);
                    } else if ("OUT".equals(mode)) {
                        transferItems(wrappedItemStorage, neighborItemHandler, filter);
                    }
                }
            }

            if (processEnergy && wrappedEnergyStorage != null) {
                IEnergyStorage neighborEnergy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
                if (neighborEnergy != null) {
                    if ("IN".equals(mode)) {
                        int extractable = neighborEnergy.extractEnergy(10000, true);
                        if (extractable > 0) {
                            int accepted = wrappedEnergyStorage.receiveEnergy(extractable, false);
                            if (accepted > 0) neighborEnergy.extractEnergy(accepted, false);
                        }
                    } else if ("OUT".equals(mode)) {
                        int extractable = wrappedEnergyStorage.extractEnergy(10000, true);
                        if (extractable > 0) {
                            int accepted = neighborEnergy.receiveEnergy(extractable, false);
                            if (accepted > 0) wrappedEnergyStorage.extractEnergy(accepted, false);
                        }
                    }
                }
            }
        }
    }

    private static void transferItems(IItemHandler from, IItemHandler to, String filter) {
        boolean isNbtFilter = filter != null && filter.startsWith("{") && filter.endsWith("}");
        String searchStr = isNbtFilter ? filter.substring(1, filter.length() - 1) : filter;

        for (int i = 0; i < from.getSlots(); i++) {
            net.minecraft.world.item.ItemStack stackInSlot = from.getStackInSlot(i);
            if (stackInSlot.isEmpty()) continue;

            if (filter != null && !filter.isEmpty() && !filter.equals("{}")) {
                if (isNbtFilter) {
                    net.minecraft.world.item.component.CustomData customData = stackInSlot.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                    String nbtStr = customData.copyTag().toString();
                    if (!nbtStr.contains(searchStr)) continue;
                } else {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stackInSlot.getItem()).toString();
                    if (!id.contains(searchStr) && !stackInSlot.getHoverName().getString().contains(searchStr)) {
                        continue;
                    }
                }
            }

            net.minecraft.world.item.ItemStack extracted = from.extractItem(i, stackInSlot.getCount(), true);
            if (extracted.isEmpty()) continue;

            net.minecraft.world.item.ItemStack remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(to, extracted, true);
            int amountToMove = extracted.getCount() - remainder.getCount();

            if (amountToMove > 0) {
                net.minecraft.world.item.ItemStack actuallyExtracted = from.extractItem(i, amountToMove, false);
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(to, actuallyExtracted, false);
                break;
            }
        }
    }

    @Override public void setMasterPos(BlockPos pos) { this.mainframeMasterPos = pos; this.setChanged(); }
    @Override public BlockPos getMasterPos() { return this.mainframeMasterPos; }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getExtensionCapability(ResourceLocation extensionId) {
        if (this.mainframeMasterPos != null && this.level != null) {
            BlockEntity masterBe = this.level.getBlockEntity(this.mainframeMasterPos);
            if (masterBe instanceof SimpleMachineBlockEntity master && master.isMainframeMaster) {
                IMainframeExtension ext = master.getExtension(extensionId);
                if (ext != null) return (T) ext.getCapabilityInstance();
            }
        }
        return null;
    }

    public String getNetworkTag() { return this.getPersistentData().getString("NetworkTag"); }

    public void setNetworkTag(String tag) {
        String current = getNetworkTag();
        String newTag = tag != null ? tag : "";
        if (!current.equals(newTag)) {
            this.getPersistentData().putString("NetworkTag", newTag);
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
                this.notifyNetworkTagChanged();
            }
        }
    }

    public void notifyStorageChanged() {
        if (this.level == null || this.level.isClientSide) return;
        CompoundTag data = this.getPersistentData();
        if (data.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
            BlockEntity be = this.level.getBlockEntity(routerPos);
            if (be instanceof SimpleMachineBlockEntity master) {
                master.virtualStorage.onStorageChanged(this.level);
            }
        }
    }

    public void notifyNetworkTagChanged() {
        if (this.level == null || this.level.isClientSide) return;
        CompoundTag data = this.getPersistentData();
        if (data.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
            BlockEntity be = this.level.getBlockEntity(routerPos);
            if (be instanceof SimpleMachineBlockEntity master) {
                master.virtualStorage.addDeviceNode(this.getBlockPos(), this.level, false);
            }
        }
    }

    @Override
    public boolean isNetworkActive() { return this.isDetected; } // ★ここも isDetected を使う

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide) {
            CompoundTag data = this.getPersistentData();
            if (data.contains("RouterPos")) {
                BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
                if (this.level.isLoaded(routerPos)) {
                    BlockEntity be = this.level.getBlockEntity(routerPos);
                    if (be instanceof SimpleMachineBlockEntity master) {
                        master.virtualStorage.removeDeviceNode(this.getBlockPos());
                    }
                }
            }
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // ★修正: 保存する直前に、内部変数を PersistentData へ確実に同期させる
        this.getPersistentData().putBoolean("IsDetected", this.isDetected);

        tag.putBoolean("IsDetected", this.isDetected);
        if (this.disguiseState != null) tag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));

        CompoundTag rsTag = new CompoundTag();
        for (Direction dir : Direction.values()) rsTag.putInt(dir.getName(), redstoneOutputs.getOrDefault(dir, 0));
        tag.put("RedstoneOutputs", rsTag);

        tag.put("PersistentData", this.getPersistentData());
        if (this.mainframeMasterPos != null) tag.putLong("MainframeMasterPos", this.mainframeMasterPos.asLong());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("PersistentData")) this.getPersistentData().merge(tag.getCompound("PersistentData"));

        if (tag.contains("IsDetected")) {
            this.isDetected = tag.getBoolean("IsDetected");
            if (!this.getPersistentData().contains("IsDetected")) {
                this.getPersistentData().putBoolean("IsDetected", this.isDetected);
            }
        }

        if (tag.contains("DisguiseState"))
            this.disguiseState = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("DisguiseState"));
        else this.disguiseState = null;

        if (tag.contains("RedstoneOutputs")) {
            CompoundTag rsTag = tag.getCompound("RedstoneOutputs");
            for (Direction dir : Direction.values()) redstoneOutputs.put(dir, rsTag.getInt(dir.getName()));
        }

        if (tag.contains("MainframeMasterPos")) {
            this.mainframeMasterPos = BlockPos.of(tag.getLong("MainframeMasterPos"));
        } else {
            this.mainframeMasterPos = null;
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        tag.put("PersistentData", this.getPersistentData());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public @NotNull Component getDisplayName() { return Component.translatable("block.lunex.probe_block"); }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new ProbeMenu(containerId, playerInventory, this.worldPosition);
    }
}