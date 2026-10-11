package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.chemistry.model.ItemDNA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class MaterialRegistryUtil {

    public static void registerTag(TagKey<Item> tagKey, ItemDNA dna) {
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            MaterialRegistry.registerBaseMaterial(id, dna);
        }
    }

    public static void registerCommonTag(String tagPath, ItemDNA dna) {
        TagKey<Item> tagKey = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:" + tagPath));
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            MaterialRegistry.registerBaseMaterial(id, dna);
        }
    }
}