package com.nishiyu.lunex.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public class DataGenerators {

    // アノテーションを削除し、メインクラスから直接呼び出せるようにする
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();

        // クライアント側のデータ生成が有効な場合に追加
        if (event.includeClient()) {
            generator.addProvider(true, new ModLanguageProviderJaJp(packOutput));
            generator.addProvider(true, new ModLanguageProviderEnUs(packOutput));
        }
    }
}