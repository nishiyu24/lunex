package com.nishiyu.lunex.api.mainframe.extension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;

/**
 * アドオンがメインフレームに新しいリソース（マナ、ガス、熱など）のストレージや
 * バックエンド機能を動的に追加・結合するためのインターフェース。
 */
public interface IMainframeExtension {
    /**
     * メインフレームが合体・再構築された際に呼ばれます。
     * master.componentCounts などを参照し、構成ブロックに応じて容量を計算・拡張します。
     */
    void onAssembled(SimpleMachineBlockEntity master);

    /**
     * メインフレームが解体された際に呼ばれます。
     * 内容物を周囲に分散させたり、初期化する処理を行います。
     */
    void onDisassembled(SimpleMachineBlockEntity master);

    /**
     * データの保存処理
     */
    CompoundTag serializeNBT(HolderLookup.Provider provider);

    /**
     * データの読み込み処理
     */
    void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider);

    /**
     * この拡張がポート等から要求された際に提供するインスタンス（Capabilityの中身等）を返します。
     * 呼び出し側（アドオン）でキャストして利用します。
     */
    Object getCapabilityInstance();
}