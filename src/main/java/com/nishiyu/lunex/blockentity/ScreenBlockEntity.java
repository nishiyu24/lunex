package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.ScreenBlock;
import com.nishiyu.lunex.machine.IMainframePart;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class ScreenBlockEntity extends BlockEntity implements IMCNetDevice, IMainframePart {
    public static final double RESOLUTION = 200.0;

    public BlockPos masterPos = null;
    public int screenWidth = 1;
    public int screenHeight = 1;
    public boolean isMaster = true;
    public int lodColor = 0xFF000000;

    public UUID networkId = null;

    public List<BlockPos> linkedSpeakers = new ArrayList<>();

    public BlockPos mainframeMasterPos = null;
    public int unlockTier = 0;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.SCREEN_BE.get(), pos, state);
    }

    @Override
    public void setMasterPos(BlockPos pos) { this.mainframeMasterPos = pos; this.setChanged(); }
    @Override
    public BlockPos getMasterPos() { return this.mainframeMasterPos; }

    public void updateUnlockTier(int tier) {
        if (!this.isMaster && this.masterPos != null && this.level != null) {
            if (this.level.getBlockEntity(this.masterPos) instanceof ScreenBlockEntity screenMaster) {
                screenMaster.updateUnlockTier(tier);
            }
        }
        this.unlockTier = tier;
        this.setChanged();
        this.sync();
    }

    public String getSessionId() {
        if (networkId != null) {
            return networkId.toString();
        }
        BlockPos pos = isMaster ? this.worldPosition : (masterPos != null ? masterPos : this.worldPosition);
        return "block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public void setLinkedSpeakers(List<BlockPos> speakers) {
        if (!isMaster && masterPos != null && level != null) {
            if (level.getBlockEntity(masterPos) instanceof ScreenBlockEntity master) master.setLinkedSpeakers(speakers);
            return;
        }
        this.linkedSpeakers = new ArrayList<>(speakers);
        this.sync();
    }

    public void setLODColor(int color) {
        if (!isMaster && masterPos != null && level != null) {
            if (level.getBlockEntity(masterPos) instanceof ScreenBlockEntity master) master.setLODColor(color);
            return;
        }
        this.lodColor = color;
        this.sync();
    }

    public void updateScreenNetwork() {
        if (level == null || level.isClientSide) return;
        BlockState myState = getBlockState();
        if (!(myState.getBlock() instanceof ScreenBlock)) return;

        Direction facing = myState.getValue(ScreenBlock.FACING);
        Direction screenRight = facing.getCounterClockWise();
        Direction screenLeft = facing.getClockWise();

        Set<BlockPos> connectedGroup = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(worldPosition);
        connectedGroup.add(worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : new Direction[]{Direction.UP, Direction.DOWN, screenLeft, screenRight}) {
                BlockPos next = current.relative(dir);
                if (!connectedGroup.contains(next) && isSameScreen(next, facing)) {
                    connectedGroup.add(next);
                    queue.add(next);
                }
            }
        }

        UUID inheritedNetworkId = this.networkId;
        Set<BlockPos> remaining = new HashSet<>(connectedGroup);
        boolean isFirstLargestRectangle = true;

        while (!remaining.isEmpty()) {
            Rectangle bestRect = null;
            for (BlockPos candidateOrigin : remaining) {
                for (int w = 1; ; w++) {
                    BlockPos widthCheck = candidateOrigin.relative(screenRight, w - 1);
                    if (!remaining.contains(widthCheck)) break;
                    for (int h = 1; ; h++) {
                        boolean valid = true;
                        for (int dx = 0; dx < w; dx++) {
                            for (int dy = 0; dy < h; dy++) {
                                if (!remaining.contains(candidateOrigin.relative(screenRight, dx).above(dy))) {
                                    valid = false;
                                    break;
                                }
                            }
                            if (!valid) break;
                        }
                        if (valid) {
                            Rectangle currentRect = new Rectangle(candidateOrigin, w, h);
                            if (bestRect == null || currentRect.isBetterThan(bestRect)) bestRect = currentRect;
                        } else break;
                    }
                }
            }
            if (bestRect == null) break;

            applyRectangle(bestRect, facing, screenRight, isFirstLargestRectangle ? inheritedNetworkId : null);
            isFirstLargestRectangle = false;

            for (int dx = 0; dx < bestRect.width; dx++) {
                for (int dy = 0; dy < bestRect.height; dy++) {
                    remaining.remove(bestRect.origin.relative(screenRight, dx).above(dy));
                }
            }
        }
    }

    private void applyRectangle(Rectangle rect, Direction facing, Direction screenRight, UUID assignedNetworkId) {
        for (int x = 0; x < rect.width; x++) {
            for (int y = 0; y < rect.height; y++) {
                BlockPos p = rect.origin.relative(screenRight, x).above(y);
                if (level.getBlockEntity(p) instanceof ScreenBlockEntity sbe) {

                    boolean structureChanged = (sbe.masterPos == null || !sbe.masterPos.equals(rect.origin))
                            || sbe.screenWidth != rect.width
                            || sbe.screenHeight != rect.height;

                    sbe.masterPos = rect.origin;
                    sbe.isMaster = p.equals(rect.origin);
                    sbe.screenWidth = rect.width;
                    sbe.screenHeight = rect.height;

                    if (structureChanged) {
                        sbe.networkId = assignedNetworkId;
                    }

                    if (sbe.unlockTier == 0 && this.unlockTier != 0) {
                        sbe.unlockTier = this.unlockTier;
                    }

                    sbe.setChanged();
                    sbe.sync();
                }

                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof ScreenBlock) {
                    BlockState newState = s.setValue(ScreenBlock.SCREEN_UP, y < rect.height - 1)
                            .setValue(ScreenBlock.SCREEN_DOWN, y > 0).setValue(ScreenBlock.SCREEN_LEFT, x > 0).setValue(ScreenBlock.SCREEN_RIGHT, x < rect.width - 1);
                    if (!s.equals(newState)) level.setBlock(p, newState, 2);
                }
            }
        }
    }

    private boolean isSameScreen(BlockPos pos, Direction facing) {
        if (level == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ScreenBlock && state.getValue(ScreenBlock.FACING) == facing;
    }

    public void sync() {
        this.setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(this.getBlockPos());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsMaster", isMaster);
        tag.putInt("ScreenWidth", screenWidth);
        tag.putInt("ScreenHeight", screenHeight);
        tag.putInt("LodColor", lodColor);
        tag.putInt("UnlockTier", unlockTier);

        if (masterPos != null) tag.putLong("MasterPos", masterPos.asLong());

        if (!linkedSpeakers.isEmpty()) {
            long[] posArray = new long[linkedSpeakers.size()];
            for (int i = 0; i < linkedSpeakers.size(); i++) posArray[i] = linkedSpeakers.get(i).asLong();
            tag.putLongArray("LinkedSpeakers", posArray);
        }
        if (networkId != null) tag.putUUID("NetworkId", networkId);
        if (mainframeMasterPos != null) tag.putLong("MainframeMasterPos", mainframeMasterPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isMaster = tag.getBoolean("IsMaster");
        this.screenWidth = tag.getInt("ScreenWidth");
        this.screenHeight = tag.getInt("ScreenHeight");

        if (tag.contains("LodColor")) this.lodColor = tag.getInt("LodColor");
        if (tag.contains("UnlockTier")) this.unlockTier = tag.getInt("UnlockTier");

        // ★ 修正: サーバー側でnullになったらクライアント側も確実にnullへ初期化する（ゴーストNBTバグの防止）
        if (tag.contains("MasterPos")) this.masterPos = BlockPos.of(tag.getLong("MasterPos"));
        else this.masterPos = null;

        this.linkedSpeakers.clear();
        if (tag.contains("LinkedSpeakers")) {
            long[] posArray = tag.getLongArray("LinkedSpeakers");
            for (long l : posArray) this.linkedSpeakers.add(BlockPos.of(l));
        }

        // ★ 修正: 同上（破片のUI残存バグの完全解決）
        if (tag.contains("NetworkId")) this.networkId = tag.getUUID("NetworkId");
        else this.networkId = null;

        if (tag.contains("MainframeMasterPos")) {
            this.mainframeMasterPos = BlockPos.of(tag.getLong("MainframeMasterPos"));
        } else {
            this.mainframeMasterPos = null;
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        String oldSessionId = this.getSessionId();
        int oldW = this.screenWidth;
        int oldH = this.screenHeight;

        loadAdditional(pkt.getTag(), registries);

        if (level != null && level.isClientSide) {
            String newSessionId = this.getSessionId();
            // ★ 修正: IDやサイズが本当に変わった場合（破壊・再構築）のみ古いUIを破棄する
            // むやみに破棄すると、スピーカーをリンクした瞬間に画面が真っ暗になる。
            if (!oldSessionId.equals(newSessionId) || oldW != this.screenWidth || oldH != this.screenHeight) {
                com.nishiyu.lunex.client.ClientScreenManager.clearSession(oldSessionId);
            }
        }
    }

    public record UIElement(String id, String type, int x, int y, int width, int height, String text, int color,
                            int bgColor, Map<String, String> events, int radius, float opacity, float translateX,
                            float translateY,
                            float scaleX, float scaleY, float rotate, int transitionDuration, String animDef) {
    }

    private record Rectangle(BlockPos origin, int width, int height) {
        int area() {
            return width * height;
        }
        boolean isBetterThan(Rectangle other) {
            return this.area() != other.area() ? this.area() > other.area() : this.width > other.width;
        }
    }
}