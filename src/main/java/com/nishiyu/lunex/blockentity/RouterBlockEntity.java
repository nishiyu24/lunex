package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.item.UpgradeItem;
import com.nishiyu.lunex.machine.*;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.mcnet.MCNetUtil;
import com.nishiyu.lunex.mcnet.McNetManager;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RouterBlockEntity extends BlockEntity implements IMachineContext, IMCNetDevice, IDisguisable {

    public final MachineEnergyManager energyManager = new MachineEnergyManager();
    public final MachineUpgradeManager upgrades = new MachineUpgradeManager();

    public final VirtualStorage virtualStorage = new VirtualStorage(this);

    public final ItemStackHandler upgradeHandler = new ItemStackHandler(7) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2 | 4 | 8);
            }
        }
    };
    public final Map<String, BlockPos> localRoutes = new ConcurrentHashMap<>();
    public UUID machineId = null;
    public CompoundTag persistentData = new CompoundTag();

    // ★ 所有者のUUIDを保持
    public UUID ownerUUID = null;

    private boolean isPrivateMode = false;
    private boolean isRunningStatus = true; // 外部設定用の稼働ステータス

    public BlockState disguiseState = null;

    private boolean needsNetworkUpdate = true;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.ROUTER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RouterBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.upgrades.generatorLevel > 0) {
            entity.energyManager.energy = Math.min(
                    entity.energyManager.energy + (entity.upgrades.generatorLevel * 10),
                    entity.energyManager.getMaxEnergy(entity.upgrades)
            );
        }

        if (entity.needsNetworkUpdate) {
            entity.needsNetworkUpdate = false;
            entity.autoAssignDHCP();
        }

        entity.virtualStorage.tick(level);

        if (!entity.isRunningStatus) return;

        // 外部操作用ルーターとしての定期処理が必要であればここに記述
    }

    private void addToNetwork() {
        String wanIp = this.persistentData.getString("WanIP");
        if (wanIp != null && !wanIp.isEmpty() && !"0.0.0.0".equals(wanIp)) {
            // McNetManager.registerWanNode(wanIp, ...);
        }
    }

    private void removeFromNetwork() {
        String wanIp = this.persistentData.getString("WanIP");
        if (wanIp != null && !wanIp.isEmpty() && !"0.0.0.0".equals(wanIp)) {
            McNetManager.unregisterWanNode(wanIp);
        }
        this.localRoutes.clear();
    }

    public void requestNetworkUpdate() {
        this.needsNetworkUpdate = true;
    }

    public void autoAssignDHCP() {
        if (this.level == null || this.level.isClientSide) return;
        if (!this.persistentData.getBoolean("DHCPServerEnabled")) return;

        List<BlockPos> connected = MCNetUtil.getConnectedDevices(this.level, this.worldPosition, 2048);

        CompoundTag leases = this.persistentData.contains("DHCPLeases") ? this.persistentData.getCompound("DHCPLeases") : new CompoundTag();
        String baseIp = this.persistentData.getString("DHCPBaseIP");
        int start = this.persistentData.getInt("DHCPStartOctet");
        int size = this.persistentData.getInt("DHCPPoolSize");
        String subnet = this.persistentData.getString("DHCPSubnet");
        String gateway = this.persistentData.getString("DHCPGateway");

        boolean routerChanged = false;

        Set<String> connectedMacs = new HashSet<>();
        for (BlockPos pos : connected) {
            connectedMacs.add(pos.toShortString());
        }

        List<String> keysToRemove = new ArrayList<>();
        for (String mac : leases.getAllKeys()) {
            if (mac.length() == 36 && mac.split("-").length == 5) continue;
            if (mac.equals(this.worldPosition.toShortString())) continue;

            if (!connectedMacs.contains(mac)) {
                keysToRemove.add(mac);
            }
        }

        for (String mac : keysToRemove) {
            String ip = leases.getString(mac);
            leases.remove(mac);
            routerChanged = true;
            this.localRoutes.remove(ip);
        }

        Set<String> usedIps = new HashSet<>();
        for (String key : leases.getAllKeys()) {
            usedIps.add(leases.getString(key));
        }

        for (BlockPos pos : connected) {
            BlockEntity be = this.level.getBlockEntity(pos);
            if (be != null && MCNetUtil.isMCNetDevice(be)) {
                if (be == this) continue;

                CompoundTag beData;
                if (be instanceof AdvancedMachineBlockEntity machine) beData = machine.persistentData;
                else if (be instanceof RouterBlockEntity router) beData = router.persistentData;
                else beData = be.getPersistentData();

                String mac = pos.toShortString();
                String assignedIp = "";

                if (leases.contains(mac)) {
                    assignedIp = leases.getString(mac);
                } else {
                    for (int i = 0; i < size; i++) {
                        String testIp = baseIp + "." + (start + i);
                        if (!usedIps.contains(testIp)) {
                            assignedIp = testIp;
                            leases.putString(mac, testIp);
                            usedIps.add(testIp);
                            routerChanged = true;
                            break;
                        }
                    }
                }

                if (!assignedIp.isEmpty()) {
                    beData.putString("IPAddress", assignedIp);
                    beData.putString("SubnetMask", subnet);
                    beData.putString("DefaultGateway", gateway);
                    beData.putLong("RouterPos", this.worldPosition.asLong());

                    if (this.machineId != null) {
                        beData.putString("NetworkId", this.machineId.toString());
                    }

                    be.setChanged();
                    this.localRoutes.put(assignedIp, pos);

                    if (this.level != null) {
                        this.level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 2);
                    }
                }
            }
        }

        if (routerChanged) {
            this.persistentData.put("DHCPLeases", leases);
            this.setChanged();
            this.virtualStorage.rebuildNetworkCache(this.level);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.isRunningStatus) {
            addToNetwork();
        }
    }

    public void initializeFromFrame(MachineFrameBlockEntity frame) {
        if (this.machineId == null) {
            this.machineId = UUID.randomUUID();
        }

        for (int i = 0; i < frame.upgradeHandler.getSlots(); i++) {
            ItemStack stack = frame.upgradeHandler.getStackInSlot(i);
            this.upgradeHandler.setStackInSlot(i, stack.copy());
            if (stack.getItem() instanceof UpgradeItem upgrade) {
                switch (upgrade.getUpgradeType()) {
                    case EXECUTION -> this.upgrades.execLevel += upgrade.getLevel();
                    case STORAGE -> this.upgrades.storageLevel += upgrade.getLevel();
                    case SPEED -> this.upgrades.speedLevel += upgrade.getLevel();
                    case CAPACITY -> this.upgrades.capacityLevel += upgrade.getLevel();
                    case GENERATOR -> this.upgrades.generatorLevel += upgrade.getLevel();
                    default -> {}
                }
            }
        }

        this.generateWanIp();
        this.persistentData.putString("IPAddress", "192.168.1.1");
        this.persistentData.putBoolean("DHCPServerEnabled", true);
        this.persistentData.putString("DHCPBaseIP", "192.168.1");
        this.persistentData.putInt("DHCPStartOctet", 10);
        this.persistentData.putInt("DHCPPoolSize", 50);

        this.setRunning(true);
        this.setChanged();
    }

    @Override
    public void setRemoved() {
        removeFromNetwork();
        super.setRemoved();
    }

    public BlockPos resolveDevice(String targetStr) {
        return TargetUtil.resolveDevice(this, targetStr);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);
        tag.putBoolean("IsPrivateMode", this.isPrivateMode);
        tag.putBoolean("IsRunningStatus", this.isRunningStatus);

        // ★ 保存時に OwnerUUID を記録
        if (this.ownerUUID != null) tag.putUUID("OwnerUUID", this.ownerUUID);

        if (this.disguiseState != null) {
            tag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));
        }

        tag.put("PersistentData", this.persistentData);
        tag.put("UpgradeInventory", this.upgradeHandler.serializeNBT(registries));
        upgrades.save(tag);
        energyManager.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MachineId")) this.machineId = tag.getUUID("MachineId");
        if (tag.contains("IsPrivateMode")) this.isPrivateMode = tag.getBoolean("IsPrivateMode");
        if (tag.contains("IsRunningStatus")) this.isRunningStatus = tag.getBoolean("IsRunningStatus");

        // ★ 読み込み時に OwnerUUID を復元
        if (tag.contains("OwnerUUID")) this.ownerUUID = tag.getUUID("OwnerUUID");

        if (tag.contains("DisguiseState")) {
            this.disguiseState = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("DisguiseState"));
        } else {
            this.disguiseState = null;
        }

        if (tag.contains("PersistentData")) {
            this.persistentData = tag.getCompound("PersistentData");
        }

        if (tag.contains("MachineLabel")) {
            String oldLabel = tag.getString("MachineLabel");
            if (!oldLabel.isEmpty() && !this.persistentData.contains("NetworkTag")) {
                this.persistentData.putString("NetworkTag", oldLabel);
            }
        }

        if (tag.contains("UpgradeInventory")) {
            this.upgradeHandler.deserializeNBT(registries, tag.getCompound("UpgradeInventory"));
        }
        upgrades.load(tag);
        energyManager.load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (this.machineId != null) tag.putUUID("MachineId", this.machineId);
        tag.putBoolean("IsPrivateMode", this.isPrivateMode);
        tag.putBoolean("IsRunningStatus", this.isRunningStatus);
        tag.put("PersistentData", this.persistentData);

        // ★ クライアント（画面表示側）に OwnerUUID を同期
        if (this.ownerUUID != null) tag.putUUID("OwnerUUID", this.ownerUUID);

        if (this.disguiseState != null) {
            tag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));
        }

        tag.put("UpgradeInventory", this.upgradeHandler.serializeNBT(provider));

        return tag;
    }

    public String getWanIp() {
        return this.persistentData.contains("AssignedWanIP") ? this.persistentData.getString("AssignedWanIP") : "0.0.0.0";
    }

    public String generateWanIp() {
        if (this.level == null) return "0.0.0.0";
        if (this.persistentData.contains("AssignedWanIP")) return this.persistentData.getString("AssignedWanIP");

        int dim = 0;
        String dimName = this.level.dimension().location().getPath();
        if (dimName.contains("nether")) dim = 10;
        else if (dimName.contains("end")) dim = 20;
        else if (!dimName.contains("overworld")) dim = Math.abs(dimName.hashCode()) % 255;

        long time = System.currentTimeMillis();
        int octet2 = Math.abs(this.getBlockPos().getX() ^ (int) (time >> 8)) % 254 + 1;
        int octet3 = Math.abs(this.getBlockPos().getZ() ^ (int) (time >> 4)) % 254 + 1;
        int octet4 = (int) (time % 254) + 1;

        String newWanIp = dim + "." + octet2 + "." + octet3 + "." + octet4;
        this.persistentData.putString("AssignedWanIP", newWanIp);
        this.setChanged();
        return newWanIp;
    }

    @Override
    public boolean isBlock() { return true; }

    @Override
    public BlockPos getPos() { return this.worldPosition; }

    @Override
    public String getWorkspaceId() { return "routers"; }

    @Override
    public void setWorkspaceId(String id) {}

    @Override
    public String getMachineLabel() {
        String tag = this.persistentData.getString("NetworkTag");
        return tag.isEmpty() ? "Router" : tag;
    }

    @Override
    public void setMachineLabel(String label) {
        String current = this.persistentData.getString("NetworkTag");
        String newLabel = label != null ? label : "";
        if (!current.equals(newLabel)) {
            this.persistentData.putString("NetworkTag", newLabel);
            this.setChanged();
            this.sync();
            this.virtualStorage.rebuildNetworkCache(this.level);
        }
    }

    @Override
    public String getProgramName() { return ""; }

    @Override
    public void setProgramName(String name) {}

    @Override
    public boolean isPrivateMode() { return this.isPrivateMode; }

    @Override
    public void setPrivateMode(boolean privateMode) {
        this.isPrivateMode = privateMode;
        this.setChanged();
        this.sync();
    }

    @Override
    public boolean isWakeOnRedstone() { return false; }

    @Override
    public void setWakeOnRedstone(boolean wakeOnRedstone) {}

    @Override
    public boolean isDebugChat() { return false; }

    @Override
    public void setDebugChat(boolean debugChat) {}

    @Override
    public boolean isRunning() { return this.isRunningStatus; }

    @Override
    public void setRunning(boolean running) {
        boolean was = this.isRunningStatus;
        this.isRunningStatus = running;
        if (this.level != null && !this.level.isClientSide) {
            if (running && !was) {
                addToNetwork();
            } else if (!running && was) {
                removeFromNetwork();
            }
            this.sync();
        }
    }

    @Override
    public List<String> getInstalledPrograms() {
        return Collections.emptyList();
    }

    public int getDistanceUpgradeLevel() {
        int maxLevel = 0;
        for (int i = 0; i < this.upgradeHandler.getSlots(); i++) {
            ItemStack stack = this.upgradeHandler.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeItem upgrade && upgrade.getUpgradeType() == UpgradeItem.UpgradeType.DISTANCE) {
                maxLevel = Math.max(maxLevel, upgrade.getLevel());
            }
        }
        return maxLevel;
    }

    public boolean canCommunicateWith(Level targetLevel, BlockPos targetPos) {
        if (this.level == null) return false;
        int level = getDistanceUpgradeLevel();

        boolean isSameDim = this.level.dimension().equals(targetLevel.dimension());

        if (!isSameDim) {
            return level >= 4;
        }

        if (level >= 3) {
            return true;
        }

        double maxDist = switch (level) {
            case 1 -> 256.0;
            case 2 -> 1024.0;
            default -> 64.0;
        };

        return this.worldPosition.distSqr(targetPos) <= (maxDist * maxDist);
    }

    @Override
    public int getEnergy() { return this.energyManager.energy; }

    @Override
    public int getExecUpgradeLevel() { return this.upgrades.execLevel; }

    @Override
    public int getStorageUpgradeLevel() { return this.upgrades.storageLevel; }

    @Override
    public int getSpeedUpgradeLevel() { return this.upgrades.speedLevel; }

    @Override
    public int getEfficiencyUpgradeLevel() { return this.upgrades.efficiencyLevel; }

    @Override
    public int getCapacityUpgradeLevel() { return this.upgrades.capacityLevel; }

    @Override
    public int getGeneratorUpgradeLevel() { return this.upgrades.generatorLevel; }

    @Override
    public BlockState getDisguiseState() {
        return this.disguiseState;
    }

    @Override
    public void setDisguiseState(BlockState state) {
        this.disguiseState = state;
    }

    @Override
    public void applyDisguiseState(boolean isDisguised) {
        if (this.level != null && !this.level.isClientSide) {
            this.level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(com.nishiyu.lunex.block.RouterBlock.IS_DISGUISED, isDisguised));
            this.setChanged();
            this.sync();
        }
    }
}