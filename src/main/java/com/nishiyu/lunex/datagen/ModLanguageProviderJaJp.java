package com.nishiyu.lunex.datagen;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBehaviorRegistry;
import com.nishiyu.lunex.entity.traits.ModTraits;
import com.nishiyu.lunex.item.*;
import net.minecraft.data.PackOutput;

import java.lang.reflect.Field;

public class ModLanguageProviderJaJp extends AutoLanguageProvider {

    public ModLanguageProviderJaJp(PackOutput output) {
        super(output, "ja_jp");
    }

    @Override
    protected void addTranslations() {
        // 1. Lunexクラス内のアノテーションを自動収集
        scanDeferredHolders(Lunex.class);

        // 2. 各クラスに分散された翻訳データを収集
        new ModTraits().gatherTranslations(this, "ja_jp");
        new CustomBehaviorRegistry().gatherTranslations(this, "ja_jp");

        // 3. アイテム固有、システムメッセージ等の収集
        new ARGlassesItem.Translations().gatherTranslations(this, "ja_jp");
        new InactiveBookItem.Translations().gatherTranslations(this, "ja_jp");
        new PortableScreenItem.Translations().gatherTranslations(this, "ja_jp");
        new ProgramDiskItem.Translations().gatherTranslations(this, "ja_jp");
        new TabletItem.Translations().gatherTranslations(this, "ja_jp");
        new ModMessages().gatherTranslations(this, "ja_jp");

        scanTranslatableFields(com.nishiyu.lunex.block.RouterBlock.class);
        scanTranslatableFields(com.nishiyu.lunex.menu.bioprinter.BioPrinterTranslations.class);

    }

    /**
     * 指定されたクラス内の @Translatable アノテーションが付与された public static final String を自動で収集します
     */
    private void scanTranslatableFields(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Translatable.class)) {
                try {
                    Translatable annotation = field.getAnnotation(Translatable.class);
                    String key = (String) field.get(null);
                    this.addTranslation(key, annotation.en(), annotation.ja());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}