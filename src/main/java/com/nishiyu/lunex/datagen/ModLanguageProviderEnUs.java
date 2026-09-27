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

public class ModLanguageProviderEnUs extends AutoLanguageProvider {

    public ModLanguageProviderEnUs(PackOutput output) {
        super(output, "en_us");
    }

    @Override
    protected void addTranslations() {
        scanDeferredHolders(Lunex.class);

        new ModTraits().gatherTranslations(this, "en_us");
        new CustomBehaviorRegistry().gatherTranslations(this, "en_us");

        new UpgradeItem.Translations().gatherTranslations(this, "en_us");
        new ARGlassesItem.Translations().gatherTranslations(this, "en_us");
        new InactiveBookItem.Translations().gatherTranslations(this, "en_us");
        new PaintballItem.Translations().gatherTranslations(this, "en_us");
        new PortableScreenItem.Translations().gatherTranslations(this, "en_us");
        new ProgramDiskItem.Translations().gatherTranslations(this, "en_us");
        new TabletItem.Translations().gatherTranslations(this, "en_us");
        new ModMessages().gatherTranslations(this, "en_us");

        // Screenクラス等の @Translatable フィールドを自動収集
        scanTranslatableFields(AdvancedMachineScreen.class);
        scanTranslatableFields(RouterDashboardScreen.class);
        scanTranslatableFields(com.nishiyu.lunex.block.RouterBlock.class);

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