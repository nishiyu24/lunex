package com.nishiyu.lunex.datagen;

// 翻訳データを各クラス自身に提出させるためのインターフェース
public interface ITranslationGatherer {
    void gatherTranslations(AutoLanguageProvider provider, String locale);
}