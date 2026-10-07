package com.nishiyu.lunex.chemistry;

import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChemicalRegistry {
    private static final Map<ResourceLocation, Molecule> dictionary = new HashMap<>();
    // ベース素材として登録されたアイテムIDを記録し、上書きを防止するリスト
    private static final Set<ResourceLocation> baseMaterials = new HashSet<>();

    // 基礎素材の手動登録（文字列指定）
    public static void registerBaseMaterial(String itemId, Molecule molecule) {
        registerBaseMaterial(ResourceLocation.parse(itemId), molecule);
    }

    // 基礎素材の手動登録（ResourceLocation指定）
    public static void registerBaseMaterial(ResourceLocation rl, Molecule molecule) {
        dictionary.put(rl, molecule);
        baseMaterials.add(rl); // 保護リストに追加
    }

    // 辞書への保存（計算エンジン用）
    public static void putMolecule(ResourceLocation itemId, Molecule molecule) {
        // ベース素材として保護されていない場合のみ、レシピ計算の結果で上書きする
        if (!baseMaterials.contains(itemId)) {
            dictionary.put(itemId, molecule);
        }
    }

    public static Molecule getMolecule(ResourceLocation itemId) {
        return dictionary.get(itemId);
    }

    public static void clear() {
        dictionary.clear();
        baseMaterials.clear();
    }

    public static Map<ResourceLocation, Molecule> getDictionary() {
        return dictionary;
    }
}