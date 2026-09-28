package com.nishiyu.lunex.datagen;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBehaviorRegistry;
import com.nishiyu.lunex.entity.traits.ModTraits;
import com.nishiyu.lunex.item.*;
import com.nishiyu.lunex.jei.LunexJEIPlugin;
import com.nishiyu.lunex.menu.AdvancedMachineScreen;
import com.nishiyu.lunex.menu.RouterDashboardScreen;
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
        new UpgradeItem.Translations().gatherTranslations(this, "ja_jp");
        new ARGlassesItem.Translations().gatherTranslations(this, "ja_jp");
        new InactiveBookItem.Translations().gatherTranslations(this, "ja_jp");
        new PaintballItem.Translations().gatherTranslations(this, "ja_jp");
        new PortableScreenItem.Translations().gatherTranslations(this, "ja_jp");
        new ProgramDiskItem.Translations().gatherTranslations(this, "ja_jp");
        new TabletItem.Translations().gatherTranslations(this, "ja_jp");
        new ModMessages().gatherTranslations(this, "ja_jp");

        // 4. GUIスクリーンの @Translatable フィールドを自動収集
        scanTranslatableFields(AdvancedMachineScreen.class);
        scanTranslatableFields(RouterDashboardScreen.class);
        scanTranslatableFields(com.nishiyu.lunex.block.RouterBlock.class);
        scanTranslatableFields(com.nishiyu.lunex.menu.bioprinter.BioPrinterTranslations.class);

        // 【追加】JEIプラグインの @Translatable フィールドを自動収集
        scanTranslatableFields(LunexJEIPlugin.class);
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