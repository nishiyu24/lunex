package com.nishiyu.lunex.datagen;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBehaviorRegistry;
import com.nishiyu.lunex.entity.traits.ModTraits;
import com.nishiyu.lunex.item.*;
import net.minecraft.data.PackOutput;

import java.lang.reflect.Field;

public class ModLanguageProviderEnUs extends AutoLanguageProvider {

    public ModLanguageProviderEnUs(PackOutput output) {
        super(output, "en_us");
    }

    @Override
    protected void addTranslations() {
        scanDeferredHolders(Lunex.class);

        new ModTraits().gatherTranslations(this, "en_us");
        new CustomBehaviorRegistry().gatherTranslations(this, "en_us");

        new ARGlassesItem.Translations().gatherTranslations(this, "en_us");
        new InactiveBookItem.Translations().gatherTranslations(this, "en_us");
        new PortableScreenItem.Translations().gatherTranslations(this, "en_us");
        new ProgramDiskItem.Translations().gatherTranslations(this, "en_us");
        new TabletItem.Translations().gatherTranslations(this, "en_us");
        new ModMessages().gatherTranslations(this, "en_us");

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