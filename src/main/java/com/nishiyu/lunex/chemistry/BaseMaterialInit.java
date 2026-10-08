package com.nishiyu.lunex.chemistry;

import net.minecraft.tags.ItemTags;

public class BaseMaterialInit {

    // 計算の基準値（インゴット＝9）
    public static final int INGOT = 9;

    public static void registerAll() {
        // ==========================================
        // 1. 自然地形・岩石・土壌系ブロック
        // ==========================================
        Molecule log = new Molecule(); log.addElement(Element.C, 18); log.addElement(Element.H, 20); log.addElement(Element.O, 10);
        MaterialRegistryUtil.registerTag(ItemTags.LOGS, log);
        MaterialRegistryUtil.registerTag(ItemTags.PLANKS, log);

        Molecule leaves = new Molecule(); leaves.addElement(Element.C, 3); leaves.addElement(Element.H, 5); leaves.addElement(Element.O, 2); leaves.addElement(Element.Mg, 1);
        MaterialRegistryUtil.registerTag(ItemTags.LEAVES, leaves);

        Molecule dirt = new Molecule(); dirt.addElement(Element.Si, 10); dirt.addElement(Element.O, 20); dirt.addElement(Element.Al, 4); dirt.addElement(Element.C, 3); dirt.addElement(Element.N, 1);
        MaterialRegistryUtil.registerTag(ItemTags.DIRT, dirt);

        Molecule mud = new Molecule(); mud.addElement(Element.Si, 10); mud.addElement(Element.O, 20); mud.addElement(Element.Al, 4); mud.addElement(Element.H, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:mud", mud);
        ChemicalRegistry.registerBaseMaterial("minecraft:packed_mud", mud);

        Molecule podzol = new Molecule(); podzol.addElement(Element.Si, 10); podzol.addElement(Element.O, 20); podzol.addElement(Element.C, 6); podzol.addElement(Element.P, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:podzol", podzol);

        Molecule sand = new Molecule(); sand.addElement(Element.Si, 12); sand.addElement(Element.O, 24);
        MaterialRegistryUtil.registerTag(ItemTags.SAND, sand);

        Molecule gravel = new Molecule(); gravel.addElement(Element.Si, 12); gravel.addElement(Element.O, 24); gravel.addElement(Element.Al, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:gravel", gravel);

        Molecule snow = new Molecule(); snow.addElement(Element.H, 10); snow.addElement(Element.O, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:snowball", snow);
        ChemicalRegistry.registerBaseMaterial("minecraft:snow", snow);
        ChemicalRegistry.registerBaseMaterial("minecraft:snow_block", snow);

        Molecule ice = new Molecule(); ice.addElement(Element.H, 20); ice.addElement(Element.O, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:ice", ice);
        ChemicalRegistry.registerBaseMaterial("minecraft:packed_ice", ice);
        ChemicalRegistry.registerBaseMaterial("minecraft:blue_ice", ice);

        // ※ 後の鉱石合成で使用するため、石と深層岩の変数は重要です
        Molecule stone = new Molecule(); stone.addElement(Element.Si, 15); stone.addElement(Element.O, 30); stone.addElement(Element.Al, 5); stone.addElement(Element.Ca, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:stone", stone);
        ChemicalRegistry.registerBaseMaterial("minecraft:cobblestone", stone);
        MaterialRegistryUtil.registerTag(ItemTags.STONE_CRAFTING_MATERIALS, stone);

        Molecule andesite = new Molecule(); andesite.addElement(Element.Si, 12); andesite.addElement(Element.O, 24); andesite.addElement(Element.Al, 4); andesite.addElement(Element.Ca, 2); andesite.addElement(Element.Na, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:andesite", andesite);
        Molecule diorite = new Molecule(); diorite.addElement(Element.Si, 10); diorite.addElement(Element.O, 20); diorite.addElement(Element.Al, 3); diorite.addElement(Element.Na, 2); diorite.addElement(Element.Ca, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:diorite", diorite);
        Molecule granite = new Molecule(); granite.addElement(Element.Si, 14); granite.addElement(Element.O, 28); granite.addElement(Element.Al, 4); granite.addElement(Element.K, 2); granite.addElement(Element.Na, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:granite", granite);

        Molecule deepslate = new Molecule(); deepslate.addElement(Element.Si, 15); deepslate.addElement(Element.O, 30); deepslate.addElement(Element.Al, 5); deepslate.addElement(Element.Fe, 3); deepslate.addElement(Element.Mg, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:cobbled_deepslate", deepslate);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate", deepslate);

        Molecule calcite = new Molecule(); calcite.addElement(Element.Ca, 10); calcite.addElement(Element.C, 10); calcite.addElement(Element.O, 30);
        ChemicalRegistry.registerBaseMaterial("minecraft:calcite", calcite);
        ChemicalRegistry.registerBaseMaterial("minecraft:pointed_dripstone", calcite);
        ChemicalRegistry.registerBaseMaterial("minecraft:dripstone_block", calcite);

        Molecule tuff = new Molecule(); tuff.addElement(Element.Si, 12); tuff.addElement(Element.O, 24); tuff.addElement(Element.Al, 4); tuff.addElement(Element.Fe, 2); tuff.addElement(Element.Ca, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:tuff", tuff);

        Molecule clay = new Molecule(); clay.addElement(Element.Al, 4); clay.addElement(Element.Si, 4); clay.addElement(Element.O, 18); clay.addElement(Element.H, 8);
        ChemicalRegistry.registerBaseMaterial("minecraft:clay_ball", clay);

        // ==========================================
        // 2. 鉱石・金属プロセス
        // ==========================================
        Molecule rawIron = new Molecule(); rawIron.addElement(Element.Fe, 14); rawIron.addElement(Element.O, 8);
        ChemicalRegistry.registerBaseMaterial("minecraft:raw_iron", rawIron);
        MaterialRegistryUtil.registerCommonTag("raw_materials/iron", rawIron);
        Molecule ironIngot = new Molecule(); ironIngot.addElement(Element.Fe, INGOT);
        ChemicalRegistry.registerBaseMaterial("minecraft:iron_ingot", ironIngot);
        MaterialRegistryUtil.registerCommonTag("ingots/iron", ironIngot);

        Molecule rawCopper = new Molecule(); rawCopper.addElement(Element.Cu, 14); rawCopper.addElement(Element.Fe, 3); rawCopper.addElement(Element.S, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:raw_copper", rawCopper);
        MaterialRegistryUtil.registerCommonTag("raw_materials/copper", rawCopper);
        Molecule copperIngot = new Molecule(); copperIngot.addElement(Element.Cu, INGOT);
        ChemicalRegistry.registerBaseMaterial("minecraft:copper_ingot", copperIngot);
        MaterialRegistryUtil.registerCommonTag("ingots/copper", copperIngot);

        Molecule rawGold = new Molecule(); rawGold.addElement(Element.Au, 11);
        ChemicalRegistry.registerBaseMaterial("minecraft:raw_gold", rawGold);
        MaterialRegistryUtil.registerCommonTag("raw_materials/gold", rawGold);
        Molecule goldIngot = new Molecule(); goldIngot.addElement(Element.Au, INGOT);
        ChemicalRegistry.registerBaseMaterial("minecraft:gold_ingot", goldIngot);
        MaterialRegistryUtil.registerCommonTag("ingots/gold", goldIngot);

        // ==========================================
        // 3. 宝石・特殊鉱物
        // ==========================================
        Molecule diamondGem = new Molecule(); diamondGem.addElement(Element.C, 50);
        ChemicalRegistry.registerBaseMaterial("minecraft:diamond", diamondGem);
        MaterialRegistryUtil.registerCommonTag("gems/diamond", diamondGem);

        Molecule emeraldGem = new Molecule(); emeraldGem.addElement(Element.Be, 6); emeraldGem.addElement(Element.Al, 4); emeraldGem.addElement(Element.Si, 12); emeraldGem.addElement(Element.O, 36); emeraldGem.addElement(Element.Cr, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:emerald", emeraldGem);
        MaterialRegistryUtil.registerCommonTag("gems/emerald", emeraldGem);

        Molecule lapis = new Molecule(); lapis.addElement(Element.Na, 6); lapis.addElement(Element.Ca, 2); lapis.addElement(Element.Al, 6); lapis.addElement(Element.Si, 6); lapis.addElement(Element.O, 24); lapis.addElement(Element.S, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:lapis_lazuli", lapis);
        MaterialRegistryUtil.registerCommonTag("gems/lapis", lapis);

        Molecule amethyst = new Molecule(); amethyst.addElement(Element.Si, 15); amethyst.addElement(Element.O, 30); amethyst.addElement(Element.Fe, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:amethyst_shard", amethyst);

        Molecule quartz = new Molecule(); quartz.addElement(Element.Si, 10); quartz.addElement(Element.O, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:quartz", quartz);
        MaterialRegistryUtil.registerCommonTag("gems/quartz", quartz);

        Molecule coalItem = new Molecule(); coalItem.addElement(Element.C, 18);
        MaterialRegistryUtil.registerTag(ItemTags.COALS, coalItem);
        ChemicalRegistry.registerBaseMaterial("minecraft:coal", coalItem);
        ChemicalRegistry.registerBaseMaterial("minecraft:charcoal", coalItem);

        Molecule redstoneDust = new Molecule(); redstoneDust.addElement(Element.Cu, 5); redstoneDust.addElement(Element.Si, 5); redstoneDust.addElement(Element.O, 10); redstoneDust.addElement(Element.Nd, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:redstone", redstoneDust);

        // ==========================================
        // 3.5 基礎鉱石ブロック (岩石 + 含有物の合成)
        // ==========================================
        Molecule ironOre = new Molecule(stone); ironOre.addMolecule(rawIron, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:iron_ore", ironOre);
        Molecule deepslateIronOre = new Molecule(deepslate); deepslateIronOre.addMolecule(rawIron, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_iron_ore", deepslateIronOre);

        Molecule copperOre = new Molecule(stone); copperOre.addMolecule(rawCopper, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:copper_ore", copperOre);
        Molecule deepslateCopperOre = new Molecule(deepslate); deepslateCopperOre.addMolecule(rawCopper, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_copper_ore", deepslateCopperOre);

        Molecule goldOre = new Molecule(stone); goldOre.addMolecule(rawGold, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:gold_ore", goldOre);
        Molecule deepslateGoldOre = new Molecule(deepslate); deepslateGoldOre.addMolecule(rawGold, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_gold_ore", deepslateGoldOre);

        Molecule coalOre = new Molecule(stone); coalOre.addMolecule(coalItem, 2); // 石炭はかさばるので多めに設定
        ChemicalRegistry.registerBaseMaterial("minecraft:coal_ore", coalOre);
        Molecule deepslateCoalOre = new Molecule(deepslate); deepslateCoalOre.addMolecule(coalItem, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_coal_ore", deepslateCoalOre);

        Molecule diamondOre = new Molecule(stone); diamondOre.addMolecule(diamondGem, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:diamond_ore", diamondOre);
        Molecule deepslateDiamondOre = new Molecule(deepslate); deepslateDiamondOre.addMolecule(diamondGem, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_diamond_ore", deepslateDiamondOre);

        Molecule emeraldOre = new Molecule(stone); emeraldOre.addMolecule(emeraldGem, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:emerald_ore", emeraldOre);
        Molecule deepslateEmeraldOre = new Molecule(deepslate); deepslateEmeraldOre.addMolecule(emeraldGem, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_emerald_ore", deepslateEmeraldOre);

        Molecule lapisOre = new Molecule(stone); lapisOre.addMolecule(lapis, 4); // 複数ドロップするため4倍
        ChemicalRegistry.registerBaseMaterial("minecraft:lapis_ore", lapisOre);
        Molecule deepslateLapisOre = new Molecule(deepslate); deepslateLapisOre.addMolecule(lapis, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_lapis_ore", deepslateLapisOre);

        Molecule redstoneOre = new Molecule(stone); redstoneOre.addMolecule(redstoneDust, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:redstone_ore", redstoneOre);
        Molecule deepslateRedstoneOre = new Molecule(deepslate); deepslateRedstoneOre.addMolecule(redstoneDust, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate_redstone_ore", deepslateRedstoneOre);

        // ==========================================
        // 4. 植物・農作物・海草
        // ==========================================
        Molecule wheat = new Molecule(); wheat.addElement(Element.C, 12); wheat.addElement(Element.H, 20); wheat.addElement(Element.O, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:wheat", wheat);

        Molecule potato = new Molecule(); potato.addElement(Element.C, 10); potato.addElement(Element.H, 18); potato.addElement(Element.O, 9); potato.addElement(Element.K, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:potato", potato);

        Molecule carrot = new Molecule(); carrot.addElement(Element.C, 8); carrot.addElement(Element.H, 14); carrot.addElement(Element.O, 7); carrot.addElement(Element.K, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:carrot", carrot);

        Molecule sugarCane = new Molecule(); sugarCane.addElement(Element.C, 6); sugarCane.addElement(Element.H, 11); sugarCane.addElement(Element.O, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:sugar_cane", sugarCane);

        Molecule sugar = new Molecule(); sugar.addElement(Element.C, 12); sugar.addElement(Element.H, 22); sugar.addElement(Element.O, 11);
        ChemicalRegistry.registerBaseMaterial("minecraft:sugar", sugar);

        Molecule apple = new Molecule(); apple.addElement(Element.C, 10); apple.addElement(Element.H, 18); apple.addElement(Element.O, 9);
        ChemicalRegistry.registerBaseMaterial("minecraft:apple", apple);

        Molecule melon = new Molecule(); melon.addElement(Element.C, 6); melon.addElement(Element.H, 12); melon.addElement(Element.O, 6); melon.addElement(Element.K, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:melon_slice", melon);

        Molecule pumpkin = new Molecule(); pumpkin.addElement(Element.C, 8); pumpkin.addElement(Element.H, 14); pumpkin.addElement(Element.O, 7); pumpkin.addElement(Element.Mg, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:pumpkin", pumpkin);

        Molecule cocoa = new Molecule(); cocoa.addElement(Element.C, 7); cocoa.addElement(Element.H, 8); cocoa.addElement(Element.N, 4); cocoa.addElement(Element.O, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:cocoa_beans", cocoa);

        Molecule berries = new Molecule(); berries.addElement(Element.C, 5); berries.addElement(Element.H, 10); berries.addElement(Element.O, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:sweet_berries", berries);
        Molecule glowBerries = new Molecule(); glowBerries.addElement(Element.C, 5); glowBerries.addElement(Element.H, 10); glowBerries.addElement(Element.O, 5); glowBerries.addElement(Element.P, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:glow_berries", glowBerries);

        Molecule kelp = new Molecule(); kelp.addElement(Element.C, 6); kelp.addElement(Element.H, 10); kelp.addElement(Element.O, 5); kelp.addElement(Element.I, 2); kelp.addElement(Element.K, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:kelp", kelp);
        ChemicalRegistry.registerBaseMaterial("minecraft:seagrass", kelp);

        Molecule cactus = new Molecule(); cactus.addElement(Element.C, 8); cactus.addElement(Element.H, 16); cactus.addElement(Element.O, 8); cactus.addElement(Element.K, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:cactus", cactus);

        Molecule mushroom = new Molecule(); mushroom.addElement(Element.C, 6); mushroom.addElement(Element.H, 10); mushroom.addElement(Element.O, 5); mushroom.addElement(Element.N, 2); mushroom.addElement(Element.P, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:brown_mushroom", mushroom);
        ChemicalRegistry.registerBaseMaterial("minecraft:red_mushroom", mushroom);

        Molecule netherWart = new Molecule(); netherWart.addElement(Element.C, 8); netherWart.addElement(Element.H, 12); netherWart.addElement(Element.O, 4); netherWart.addElement(Element.N, 2); netherWart.addElement(Element.S, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:nether_wart", netherWart);

        Molecule moss = new Molecule(); moss.addElement(Element.C, 5); moss.addElement(Element.H, 8); moss.addElement(Element.O, 4); moss.addElement(Element.N, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:moss_block", moss);
        ChemicalRegistry.registerBaseMaterial("minecraft:moss_carpet", moss);

        Molecule flower = new Molecule(); flower.addElement(Element.C, 10); flower.addElement(Element.H, 14); flower.addElement(Element.O, 6);
        MaterialRegistryUtil.registerTag(ItemTags.FLOWERS, flower);
    }
}