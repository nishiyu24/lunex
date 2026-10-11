package com.nishiyu.lunex.chemistry.model;

public enum BaseInk {
    NONE("空枠", 0x555555),
    METALLIC("金属", 0xC0C0C0),
    SILICATE("ケイ酸", 0xE0C0A0),
    CARBONIC("炭素有機", 0x333333),
    BIOGENIC("生体", 0x40A040),
    AQUEOUS("溶媒", 0x4040FF),
    VOLATILE("揮発", 0xFF4040),
    CONDUCTIVE("導電", 0xFF4040),
    LUMINOUS("発光", 0xFFFF40),
    ASTRAL("星幽", 0x8040C0),
    ANOMALOUS("特異", 0xFF00FF);

    private final String localizedName;
    private final int color;

    BaseInk(String localizedName, int color) {
        this.localizedName = localizedName;
        this.color = color;
    }

    public String getLocalizedName() { return localizedName; }
    public int getColor() { return color; }
}