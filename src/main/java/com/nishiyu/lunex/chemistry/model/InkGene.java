package com.nishiyu.lunex.chemistry.model;

/**
 * DNA配列の1単位（インク種類と状態のペア）を表すレコード
 */
public record InkGene(BaseInk ink, InkState state) {
    public static final InkGene EMPTY = new InkGene(BaseInk.NONE, InkState.NONE);

    public boolean isEmpty() {
        return this.ink == BaseInk.NONE;
    }

    @Override
    public String toString() {
        if (isEmpty()) return "NONE";
        return ink.name() + "(" + state.getLocalizedName() + ")";
    }
}