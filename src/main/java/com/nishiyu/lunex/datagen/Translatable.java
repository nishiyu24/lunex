package com.nishiyu.lunex.datagen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// フィールドに付与して、英語・日本語の翻訳を直接設定できるアノテーション
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Translatable {
    String en();

    String ja();

    String descEn() default "";

    String descJa() default "";
}