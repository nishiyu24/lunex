package com.nishiyu.lunex.entity.goals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GoalActionDef {
    String value();

    String desc() default "";

    String descEn() default ""; // 英語用の説明文を追加

    String[] args() default {};

    String[] requiredTraits() default {};
}