package com.nishiyu.lunex.api.mainframe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;

/**
 * アドオンがメインフレームに新しいリソース（マナ、ガス、熱など）のストレージや
 * バックエンド機能、自動処理などを動的に追加・結合するためのインターフェース。
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

    /**
     * メインフレームの毎Tick処理（更新処理）でマスターブロックから呼ばれます。
     * 自動クラフト、アイテムや液体の定期的な搬出入、継続的なエネルギー消費などの
     * サーバーサイドのバックエンド処理をここに実装します。
     *
     * ※実装が不要な拡張機能のために default メソッドとしています。
     *
     * @param level  現在のディメンション（Level）
     * @param master メインフレームのマスターブロックエンティティ
     */
    default void tick(Level level, SimpleMachineBlockEntity master) {
    }

    /**
     * クライアントのHUD（MainframeOverviewScreen 等）からカスタム操作パケットを受け取った際に呼ばれます。
     * ボタンのクリック、設定値の変更、モードの切り替えなどを処理するために使用します。
     *
     * ※実装が不要な拡張機能のために default メソッドとしています。
     *
     * @param action 実行されたアクションの名前や識別子（例: "toggle_mode", "set_filter"）
     * @param value  アクションに伴う付加的な文字列データ（例: "OUT", "minecraft:iron_ingot"）
     * @param master メインフレームのマスターブロックエンティティ
     */
    default void onActionReceived(String action, String value, SimpleMachineBlockEntity master) {
    }

    /**
     * アドオンで追加した独自リソース（ガス、マナなど）の現在使用量を map に追加してください。
     * @param usages 現在の各リソース使用量を格納するマップ
     */
    default void updateResourceUsages(java.util.Map<String, Long> usages) {
    }
}