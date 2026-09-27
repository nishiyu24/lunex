package com.nishiyu.lunex.program.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LuaFunction {
    // ブロックツールチップとして表示される説明文 (日本語)
    String value() default "";

    // ★追加: ブロックツールチップとして表示される説明文 (英語)
    String en() default "";

    // 引数の定義 ("型:名前" の形式。 例: "str:target", "num:slot")
    String[] args() default {};

    // 戻り値の定義 ("型:名前" の形式。 例: "bool:success", "num:count")
    String[] rets() default {};

    // 非同期(フローノード)か、同期/純粋関数(ブロックノード)か
    boolean isAsync() default false;

    // 実行入力ピン(In)を持つかどうか (falseなら開始イベントブロック)
    boolean hasExecIn() default true;

    // 実行出力ピンのリスト (デフォルトは "Out"。コールバックがある場合は複数指定)
    String[] execOuts() default {"Out"};
}