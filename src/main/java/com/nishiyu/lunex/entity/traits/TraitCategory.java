package com.nishiyu.lunex.entity.traits;

public enum TraitCategory {
    BASE_ENHANCEMENT("Base Enhancement"),
    COMBAT_ABILITY("Combat Ability"),
    ELEMENTAL_CORE("Elemental Core"),
    MORPHOLOGY("Morphology"),
    ENVIRONMENTAL("Environmental"),
    UTILITY("Utility"),
    WEAKNESS_STAT("Stat Weakness"),
    WEAKNESS_TRAIT("Trait Weakness");

    private final String displayName;

    TraitCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}