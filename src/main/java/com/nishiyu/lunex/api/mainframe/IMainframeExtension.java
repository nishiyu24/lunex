package com.nishiyu.lunex.api.mainframe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import java.util.Map;

/**
 * アドオンがメインフレームの「サーバーサイドのバックエンド機能」
 * （自動クラフト、リソース管理、外部ストレージ連携など）を動的に追加するためのインターフェース。
 */
public interface IMainframeExtension {
    /** メインフレームが合体・再構築された際に呼ばれます。 */
    void onAssembled(SimpleMachineBlockEntity master);

    /** メインフレームが解体された際に呼ばれます。 */
    void onDisassembled(SimpleMachineBlockEntity master);

    /** 保存処理 */
    CompoundTag serializeNBT(HolderLookup.Provider provider);

    /** 読み込み処理 */
    void deserializeNBT(CompoundTag tag, HolderLookup.Provider provider);

    /**
     * ポート等から要求された際に提供するインスタンス（Capabilityの中身等）を返します。
     */
    Object getCapabilityInstance();

    /**
     * 毎Tick処理（自動クラフトや定期搬出入など）。
     */
    default void tick(Level level, SimpleMachineBlockEntity master) {}

    /**
     * 拡張機能専用のカスタムパケットを受け取った際の処理。
     * （※ブロック単位の処理は ActionProvider で行い、こちらは「マシン全体」の処理用です）
     */
    default void onActionReceived(String action, String value, SimpleMachineBlockEntity master) {}

    /**
     * 独自リソース（ガス、マナなど）の現在使用量を報告します。
     */
    default void updateResourceUsages(Map<String, Long> usages) {}
}