package com.nishiyu.lunex.api.client;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 特定のBlockEntityに対して、UI拡張（IMainframeUIExtension）を提供するプロバイダー。
 * アドオンはこれを実装してレジストリに登録します。
 */
public interface IMainframeUIExtensionProvider {
    /**
     * 与えられたBlockEntityに対して、このプロバイダーがUIを提供できるか判定し、
     * 提供可能な場合は IMainframeUIExtension のインスタンスを返します。
     * 提供しない場合は null を返します。
     */
    IMainframeUIExtension<?> getExtension(BlockEntity be);
}