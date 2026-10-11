package com.nishiyu.lunex.chemistry.model;

public enum InkState {
    NONE("無"),
    SOLID("固体"),
    LIQUID("液体"),
    GAS("気体"),
    CHARGED("帯電");

    private final String localizedName;

    InkState(String localizedName) {
        this.localizedName = localizedName;
    }

    public String getLocalizedName() { return localizedName; }
}