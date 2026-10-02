package com.nishiyu.lunex.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public interface IMainframePart {
    void setMasterPos(BlockPos pos);
    BlockPos getMasterPos();

    /**
     * マスターのクラスに一切依存せず、Minecraft標準の仕組みを使って右クリック処理を委譲します。
     */
    default InteractionResult delegateToMaster(Level level, Player player, BlockHitResult hitResult) {
        BlockPos masterPos = getMasterPos();
        // マスターが存在し、かつ自分自身ではない場合のみ処理を委譲
        if (masterPos != null && !masterPos.equals(hitResult.getBlockPos())) {
            BlockState masterState = level.getBlockState(masterPos);
            // クリックした座標をマスターの位置に偽装して、対象ブロックの右クリックメソッドをそのまま実行させる
            return masterState.useWithoutItem(level, player, hitResult.withPosition(masterPos));
        }
        return null; // マスターがない（未合体）場合は null を返す
    }
}