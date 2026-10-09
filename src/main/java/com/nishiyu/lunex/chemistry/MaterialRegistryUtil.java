package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.chemistry.model.Molecule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class MaterialRegistryUtil {

    public static void registerTag(TagKey<Item> tagKey, Molecule molecule) {
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            ChemicalRegistry.registerBaseMaterial(id, molecule);
        }
    }

    public static void registerCommonTag(String tagPath, Molecule molecule) {
        TagKey<Item> tagKey = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:" + tagPath));
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            ChemicalRegistry.registerBaseMaterial(id, molecule);
        }
    }
}