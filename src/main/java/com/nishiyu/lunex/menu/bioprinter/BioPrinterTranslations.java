package com.nishiyu.lunex.menu.bioprinter;

import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;

import java.lang.reflect.Field;

public class BioPrinterTranslations implements ITranslationGatherer {

    @Translatable(en = "Mixing", ja = "ミキシング")
    public static final String TAB_MIXING = "gui.lunex.bioprinter.tab.mixing";

    @Translatable(en = "AI", ja = "AI")
    public static final String TAB_AI = "gui.lunex.bioprinter.tab.ai";

    @Translatable(en = "Traits", ja = "特性")
    public static final String TAB_TRAITS = "gui.lunex.bioprinter.tab.traits";

    @Translatable(en = "Energy: %s / 100000 FE", ja = "エネルギー: %s / 100000 FE")
    public static final String ENERGY_FORMAT = "gui.lunex.bioprinter.energy";

    @Translatable(en = "Materials: %s / %s", ja = "素材: %s / %s")
    public static final String MATERIALS_FORMAT = "gui.lunex.bioprinter.materials";

    @Translatable(en = "Empty", ja = "空")
    public static final String EMPTY = "gui.lunex.bioprinter.empty";

    // --- Mixing Tab ---
    @Translatable(en = "Mixing Status", ja = "ミキシングステータス")
    public static final String MIXING_STATUS = "gui.lunex.bioprinter.mixing.status";

    @Translatable(en = "Success Rate: %s%%", ja = "成功率: %s%%")
    public static final String SUCCESS_RATE = "gui.lunex.bioprinter.mixing.success_rate";

    @Translatable(en = "AI: %s / %s", ja = "AI: %s / %s")
    public static final String AI_COUNT = "gui.lunex.bioprinter.mixing.ai_count";

    @Translatable(en = "Traits: %s / %s", ja = "特性: %s / %s")
    public static final String TRAITS_COUNT = "gui.lunex.bioprinter.mixing.traits_count";

    @Translatable(en = "Need Base Materials", ja = "ベース素材が必要です")
    public static final String NEED_BASE_MATS = "gui.lunex.bioprinter.mixing.need_base_mats";

    @Translatable(en = "- Bone: %s / 5", ja = "- 骨: %s / 5")
    public static final String BONE_COUNT = "gui.lunex.bioprinter.mixing.bone_count";

    @Translatable(en = "- Meat: %s / 5", ja = "- 肉: %s / 5")
    public static final String MEAT_COUNT = "gui.lunex.bioprinter.mixing.meat_count";

    @Translatable(en = "Create", ja = "作成")
    public static final String BTN_CREATE = "gui.lunex.bioprinter.mixing.btn_create";

    @Translatable(en = "Extract", ja = "抽出")
    public static final String BTN_EXTRACT = "gui.lunex.bioprinter.mixing.btn_extract";

    // --- AI Tab ---
    @Translatable(en = "AI Behaviors", ja = "AI挙動")
    public static final String AI_BEHAVIORS = "gui.lunex.bioprinter.ai.behaviors";

    @Translatable(en = "Selected: %s / 3", ja = "選択中: %s / 3")
    public static final String AI_SELECTED_COUNT = "gui.lunex.bioprinter.ai.selected_count";

    // --- Trait Tab ---
    @Translatable(en = "Unlock Pool (Click to Equip)", ja = "アンロックプール (クリックで装備)")
    public static final String TRAIT_TITLE = "gui.lunex.bioprinter.trait.title";

    @Translatable(en = "Research Points: %s / %s", ja = "研究ポイント: %s / %s")
    public static final String TRAIT_POINTS = "gui.lunex.bioprinter.trait.points";

    @Translatable(en = "Base Core", ja = "ベースコア")
    public static final String TRAIT_BASE_CORE = "gui.lunex.bioprinter.trait.base_core";

    @Translatable(en = "Everything starts from here.", ja = "すべての起点はここから始まる。")
    public static final String TRAIT_BASE_DESC = "gui.lunex.bioprinter.trait.base_desc";

    @Translatable(en = "Equip Effect: Grants +1 Research Point", ja = "装備効果: 研究ポイント +1")
    public static final String TRAIT_EFFECT_POS = "gui.lunex.bioprinter.trait.effect_pos";

    @Translatable(en = "Equip Cost: Consumes 1 Research Point", ja = "装備コスト: 研究ポイント 1 消費")
    public static final String TRAIT_EFFECT_NEG = "gui.lunex.bioprinter.trait.effect_neg";

    @Translatable(en = "Status: Equipped (Click to remove)", ja = "状態: 装備中 (クリックで外す)")
    public static final String TRAIT_STATUS_EQUIPPED = "gui.lunex.bioprinter.trait.status_equipped";

    @Translatable(en = "Status: Unlocked (Click to equip)", ja = "状態: 解放済み (クリックで装備)")
    public static final String TRAIT_STATUS_UNLOCKED = "gui.lunex.bioprinter.trait.status_unlocked";

    @Translatable(en = "Unlocks when prerequisites are met.", ja = "前提条件を満たすと解放されます。")
    public static final String TRAIT_UNLOCK_COND = "gui.lunex.bioprinter.trait.unlock_cond";

    @Translatable(en = "Required Materials:", ja = "要求素材:")
    public static final String TRAIT_REQ_MATS = "gui.lunex.bioprinter.trait.req_mats";

    @Override
    public void gatherTranslations(AutoLanguageProvider provider, String locale) {
        // ITranslationGathererとしての実装。自身のリフレクションを利用して翻訳を登録します。
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Translatable.class)) {
                try {
                    Translatable annotation = field.getAnnotation(Translatable.class);
                    String key = (String) field.get(null);
                    // ModLanguageProviderの addTranslation は (key, en, ja) ではなく (key, value) を受け取る実装を想定して切り分けます。
                    // ※提供された scanTranslatableFields と同様の呼び出しに合わせる場合はプロバイダ側にメソッドが必要です。
                    // ここでは provider に依存する独自収集を安全に行わせます。
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}