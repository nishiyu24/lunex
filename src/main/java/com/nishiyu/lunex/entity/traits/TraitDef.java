package com.nishiyu.lunex.entity.traits;

import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Consumer;

public record TraitDef(
        String key,
        String englishName,
        String japaneseName,
        String englishDescription, // englishDesc から変更
        String japaneseDescription, // japaneseDesc から変更
        TraitCategory category,
        List<String> prerequisites, // 前提となるTraitのキー
        List<ItemRequirement> requirements, // アイテムと要求数のペア
        Consumer<TraitRegistry.MobStatusBuilder> statusModifier
) {
    public TraitDef(String key, String englishName, String japaneseName, String englishDescription, String japaneseDescription, TraitCategory category, List<String> prerequisites, List<ItemRequirement> requirements) {
        this(key, englishName, japaneseName, englishDescription, japaneseDescription, category, prerequisites, requirements, builder -> {
        });
    }

    // カテゴリーがステータス弱体化、または特性弱体化の場合にデメリットと判定する
    public boolean isNegative() {
        return this.category == TraitCategory.WEAKNESS_STAT || this.category == TraitCategory.WEAKNESS_TRAIT;
    }

    // 素材とその最大要求数を定義するレコード
    public record ItemRequirement(Item item, int count) {
    }
}