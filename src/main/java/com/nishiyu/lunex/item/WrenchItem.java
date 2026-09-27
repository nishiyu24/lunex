package com.nishiyu.lunex.item;

import com.nishiyu.lunex.Lunex;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        // ★ プログラマブルマシン、タートルボット、データベースブロックの場合
        if (state.is(Lunex.ADVANCED_MACHINE.get()) || state.is(Lunex.TURTLE_BOT_BLOCK.get()) || state.is(Lunex.DATABASE_BLOCK.get())) {
            // 誤破壊を防ぐため、15.0F（一瞬）から 2.0F（適度な遅さ）に変更
            return 2.0F;
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        // ★ 該当のブロックであればドロップを許可する
        if (state.is(Lunex.ADVANCED_MACHINE.get()) || state.is(Lunex.TURTLE_BOT_BLOCK.get()) || state.is(Lunex.DATABASE_BLOCK.get())) {
            return true;
        }
        return super.isCorrectToolForDrops(stack, state);
    }

    // スニーク（Shift）中であっても、対象ブロックの右クリック処理を呼び出すための設定
    @Override
    public boolean doesSneakBypassUse(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return true;
    }
}