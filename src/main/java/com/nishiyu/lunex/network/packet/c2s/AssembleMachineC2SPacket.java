package com.nishiyu.lunex.network.packet.c2s;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.MachineFrameBlockEntity;
import com.nishiyu.lunex.item.UpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AssembleMachineC2SPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<AssembleMachineC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "assemble_machine"));

    public static final StreamCodec<FriendlyByteBuf, AssembleMachineC2SPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AssembleMachineC2SPacket::pos,
            AssembleMachineC2SPacket::new
    );

    public static void handle(AssembleMachineC2SPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Level level = player.level();
            BlockPos pos = payload.pos();

            if (!level.isLoaded(pos)) return;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity frameBE) {

                boolean isRouter = false;
                boolean hasOtherUpgrades = false; // STORAGE以外のアップグレードが含まれているか判定

                // ★ アップグレードスロットを走査して状態を確認
                for (int i = 0; i < frameBE.upgradeHandler.getSlots(); i++) {
                    ItemStack stack = frameBE.upgradeHandler.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade) {
                        if (upgrade.getUpgradeType() == UpgradeItem.UpgradeType.ROUTER) {
                            isRouter = true;
                        }
                        if (upgrade.getUpgradeType() != UpgradeItem.UpgradeType.STORAGE) {
                            hasOtherUpgrades = true;
                        }
                    }
                }

                // ★ 判定結果によって設置するブロック(派生先)を分岐
                BlockState newState;
                if (isRouter) {
                    newState = Lunex.ROUTER_BLOCK.get().defaultBlockState();
                } else if (!hasOtherUpgrades) {
                    // STORAGE以外のアップグレードが存在しない場合（STORAGEのみ、または空の場合）
                    newState = Lunex.DATABASE_BLOCK.get().defaultBlockState();
                } else {
                    newState = Lunex.ADVANCED_MACHINE.get().defaultBlockState();
                }

                level.setBlockAndUpdate(pos, newState);

                // 置き換え後のブロックエンティティを取得して初期化
                BlockEntity newBE = level.getBlockEntity(pos);

                if (newBE instanceof com.nishiyu.lunex.blockentity.RouterBlockEntity routerBE) {
                    routerBE.initializeFromFrame(frameBE);
                    routerBE.ownerUUID = player.getUUID();
                    routerBE.setChanged();
                } else if (newBE instanceof DatabaseBlockEntity databaseBE) {
                    // ★ DatabaseBlockEntity の場合は、アイテムをそのままスロットに引き継ぐ
                    for (int i = 0; i < frameBE.upgradeHandler.getSlots(); i++) {
                        databaseBE.upgradeHandler.setStackInSlot(i, frameBE.upgradeHandler.getStackInSlot(i).copy());
                    }
                    databaseBE.setChanged();
                    // DatabaseBlockEntity に ownerUUID などが必要な場合はここで追加してください
                } else if (newBE instanceof AdvancedMachineBlockEntity machineBE) {
                    machineBE.initializeFromFrame(frameBE);
                    machineBE.ownerUUID = player.getUUID();
                    machineBE.setChanged();
                    machineBE.sync();
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}