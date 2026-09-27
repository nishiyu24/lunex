package com.nishiyu.lunex.datagen;

import com.nishiyu.lunex.Lunex;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.lang.reflect.Field;

public abstract class AutoLanguageProvider extends LanguageProvider {

    private final String locale;

    public AutoLanguageProvider(PackOutput output, String locale) {
        super(output, Lunex.MODID, locale);
        this.locale = locale;
    }

    // 外部から翻訳を追加しやすくするヘルパーメソッド
    public void addTranslation(String key, String en, String ja) {
        add(key, locale.equals("ja_jp") ? ja : en);
    }

    // 指定したクラス内の @Translatable が付いた DeferredHolder をスキャンする
    public void scanDeferredHolders(Class<?> targetClass) {
        for (Field field : targetClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Translatable.class)) {
                Translatable t = field.getAnnotation(Translatable.class);
                try {
                    Object value = field.get(null);
                    if (value instanceof DeferredHolder<?, ?> holder) {
                        // "block" や "item" などを取得
                        String registryName = holder.getKey().registry().getPath();
                        if (registryName.equals("entity_type")) registryName = "entity";

                        // キーの自動生成 (例: block.lunex.programmable_machine)
                        String translationKey = registryName + "." + holder.getId().getNamespace() + "." + holder.getId().getPath();

                        // 名前を追加
                        addTranslation(translationKey, t.en(), t.ja());

                        // 説明(desc)があれば、tooltip用として追加
                        if (!t.descEn().isEmpty() && !t.descJa().isEmpty()) {
                            addTranslation("tooltip." + holder.getId().getNamespace() + "." + holder.getId().getPath() + ".desc", t.descEn(), t.descJa());
                        }
                    }
                } catch (IllegalAccessException e) {
                    Lunex.LOGGER.error("Failed to collect translation from field: {}", field.getName(), e);
                }
            }
        }
    }
}