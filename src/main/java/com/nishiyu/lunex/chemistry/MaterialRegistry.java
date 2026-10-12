package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.chemistry.model.ItemDNA;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

public class MaterialRegistry {
    private static final Map<ResourceLocation, ItemDNA> dictionary = new HashMap<>();

    // 基礎素材の手動登録（文字列指定）
    public static void registerBaseMaterial(String itemId, ItemDNA dna) {
        registerBaseMaterial(ResourceLocation.parse(itemId), dna);
    }

    // 基礎素材の手動登録（ResourceLocation指定）
    public static void registerBaseMaterial(ResourceLocation rl, ItemDNA dna) {
        if (rl == null || rl.getNamespace().equals(Lunex.MODID)) {
            return;
        }
        dictionary.put(rl, dna);
    }

    public static ItemDNA getDNA(ResourceLocation itemId) {
        if (itemId == null || itemId.getNamespace().equals(Lunex.MODID)) {
            return null;
        }
        return dictionary.get(itemId);
    }

    public static void clear() {
        dictionary.clear();
    }

    public static Map<ResourceLocation, ItemDNA> getDictionary() {
        return dictionary;
    }
}