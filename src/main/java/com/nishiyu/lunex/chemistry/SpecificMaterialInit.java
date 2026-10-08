package com.nishiyu.lunex.chemistry;

public class SpecificMaterialInit {

    public static void registerAll() {
        // ==========================================
        // 1. 液体・流体ブロック
        // ==========================================
        Molecule water = new Molecule(); water.addElement(Element.H, 20); water.addElement(Element.O, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:water_bucket", water);

        Molecule lava = new Molecule(); lava.addElement(Element.Si, 30); lava.addElement(Element.O, 60); lava.addElement(Element.Fe, 10); lava.addElement(Element.Mg, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:lava_bucket", lava);

        // ==========================================
        // 2. 動物ドロップ・モンスタードロップ
        // ==========================================
        Molecule meat = new Molecule(); meat.addElement(Element.C, 16); meat.addElement(Element.H, 30); meat.addElement(Element.O, 8); meat.addElement(Element.N, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:beef", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:porkchop", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:mutton", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:chicken", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:rabbit", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:cod", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:salmon", meat);

        Molecule egg = new Molecule(); egg.addElement(Element.Ca, 5); egg.addElement(Element.C, 10); egg.addElement(Element.H, 20); egg.addElement(Element.O, 8); egg.addElement(Element.N, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:egg", egg);

        Molecule bone = new Molecule(); bone.addElement(Element.Ca, 10); bone.addElement(Element.P, 6); bone.addElement(Element.O, 24); bone.addElement(Element.H, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:bone", bone);

        Molecule leather = new Molecule(); leather.addElement(Element.C, 25); leather.addElement(Element.H, 40); leather.addElement(Element.O, 10); leather.addElement(Element.N, 8);
        ChemicalRegistry.registerBaseMaterial("minecraft:leather", leather);
        ChemicalRegistry.registerBaseMaterial("minecraft:rabbit_hide", leather);

        Molecule feather = new Molecule(); feather.addElement(Element.C, 15); feather.addElement(Element.H, 25); feather.addElement(Element.O, 7); feather.addElement(Element.N, 5); feather.addElement(Element.S, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:feather", feather);

        Molecule slime = new Molecule(); slime.addElement(Element.C, 10); slime.addElement(Element.H, 20); slime.addElement(Element.O, 10); slime.addElement(Element.N, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:slime_ball", slime);

        Molecule string = new Molecule(); string.addElement(Element.C, 12); string.addElement(Element.H, 20); string.addElement(Element.O, 5); string.addElement(Element.N, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:string", string);
        ChemicalRegistry.registerBaseMaterial("minecraft:cobweb", string);

        Molecule gunpowder = new Molecule(); gunpowder.addElement(Element.K, 2); gunpowder.addElement(Element.N, 2); gunpowder.addElement(Element.O, 6); gunpowder.addElement(Element.S, 1); gunpowder.addElement(Element.C, 3);
        ChemicalRegistry.registerBaseMaterial("minecraft:gunpowder", gunpowder);

        Molecule spiderEye = new Molecule(); spiderEye.addElement(Element.C, 12); spiderEye.addElement(Element.H, 18); spiderEye.addElement(Element.O, 4); spiderEye.addElement(Element.N, 2); spiderEye.addElement(Element.P, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:spider_eye", spiderEye);

        Molecule ghastTear = new Molecule(); ghastTear.addElement(Element.Na, 4); ghastTear.addElement(Element.Cl, 4); ghastTear.addElement(Element.H, 10); ghastTear.addElement(Element.O, 5); ghastTear.addElement(Element.Ag, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:ghast_tear", ghastTear);

        Molecule phantomMembrane = new Molecule(); phantomMembrane.addElement(Element.C, 20); phantomMembrane.addElement(Element.H, 35); phantomMembrane.addElement(Element.O, 8); phantomMembrane.addElement(Element.N, 6); phantomMembrane.addElement(Element.Ca, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:phantom_membrane", phantomMembrane);

        // 【変更点】シュルカーは超重元素の力で浮遊している設定
        Molecule shulkerShell = new Molecule(); shulkerShell.addElement(Element.Lu, 5); shulkerShell.addElement(Element.Fm, 2); shulkerShell.addElement(Element.C, 10); shulkerShell.addElement(Element.O, 15);
        ChemicalRegistry.registerBaseMaterial("minecraft:shulker_shell", shulkerShell);

        Molecule inkSac = new Molecule(); inkSac.addElement(Element.C, 18); inkSac.addElement(Element.H, 10); inkSac.addElement(Element.N, 2); inkSac.addElement(Element.O, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:ink_sac", inkSac);

        Molecule glowInkSac = new Molecule(); glowInkSac.addElement(Element.C, 18); glowInkSac.addElement(Element.H, 10); glowInkSac.addElement(Element.N, 2); glowInkSac.addElement(Element.O, 4); glowInkSac.addElement(Element.P, 2); glowInkSac.addElement(Element.Cu, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:glow_ink_sac", glowInkSac);

        Molecule honeycomb = new Molecule(); honeycomb.addElement(Element.C, 15); honeycomb.addElement(Element.H, 30); honeycomb.addElement(Element.O, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:honeycomb", honeycomb);
        Molecule honey = new Molecule(); honey.addElement(Element.C, 12); honey.addElement(Element.H, 22); honey.addElement(Element.O, 11);
        ChemicalRegistry.registerBaseMaterial("minecraft:honey_bottle", honey);

        // ==========================================
        // 3. ネザー・エンド・海洋素材・固有設定
        // ==========================================
        Molecule netherrack = new Molecule(); netherrack.addElement(Element.Si, 10); netherrack.addElement(Element.O, 20); netherrack.addElement(Element.S, 5); netherrack.addElement(Element.Fe, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:netherrack", netherrack);

        // ネザー鉱石ブロックの追加
        Molecule netherGoldOre = new Molecule(netherrack); netherGoldOre.addElement(Element.Au, 11);
        ChemicalRegistry.registerBaseMaterial("minecraft:nether_gold_ore", netherGoldOre);
        Molecule netherQuartzOre = new Molecule(netherrack); netherQuartzOre.addElement(Element.Si, 10); netherQuartzOre.addElement(Element.O, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:nether_quartz_ore", netherQuartzOre);

        Molecule basalt = new Molecule(); basalt.addElement(Element.Si, 12); basalt.addElement(Element.O, 24); basalt.addElement(Element.Fe, 3); basalt.addElement(Element.Mg, 3); basalt.addElement(Element.Ca, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:basalt", basalt);
        ChemicalRegistry.registerBaseMaterial("minecraft:smooth_basalt", basalt);

        Molecule blackstone = new Molecule(); blackstone.addElement(Element.Si, 12); blackstone.addElement(Element.O, 24); blackstone.addElement(Element.Fe, 4); blackstone.addElement(Element.C, 4); blackstone.addElement(Element.Au, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:blackstone", blackstone);

        Molecule soulSand = new Molecule(); soulSand.addElement(Element.Si, 10); soulSand.addElement(Element.O, 20); soulSand.addElement(Element.Ca, 3); soulSand.addElement(Element.P, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:soul_sand", soulSand);
        ChemicalRegistry.registerBaseMaterial("minecraft:soul_soil", soulSand);

        Molecule glowstone = new Molecule(); glowstone.addElement(Element.P, 10); glowstone.addElement(Element.O, 15); glowstone.addElement(Element.Ne, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:glowstone_dust", glowstone);

        Molecule blazeRod = new Molecule(); blazeRod.addElement(Element.S, 15); blazeRod.addElement(Element.C, 10); blazeRod.addElement(Element.Fe, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:blaze_rod", blazeRod);

        // 【変更点】エンド系素材の異界化（希ガス・超重・アクチノイドを割り当て）
        Molecule endStone = new Molecule(); endStone.addElement(Element.Xe, 10); endStone.addElement(Element.Am, 5); endStone.addElement(Element.O, 20); endStone.addElement(Element.Si, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:end_stone", endStone);

        Molecule enderPearl = new Molecule(); enderPearl.addElement(Element.Bi, 5); enderPearl.addElement(Element.Es, 2); enderPearl.addElement(Element.Pm, 3); enderPearl.addElement(Element.Xe, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:ender_pearl", enderPearl);

        Molecule chorusFruit = new Molecule(); chorusFruit.addElement(Element.C, 10); chorusFruit.addElement(Element.H, 20); chorusFruit.addElement(Element.O, 10); chorusFruit.addElement(Element.Rn, 2); chorusFruit.addElement(Element.Po, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:chorus_fruit", chorusFruit);

        Molecule dragonEgg = new Molecule(); dragonEgg.addElement(Element.Cf, 10); dragonEgg.addElement(Element.U, 5); dragonEgg.addElement(Element.Ts, 5); dragonEgg.addElement(Element.O, 20); dragonEgg.addElement(Element.C, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:dragon_egg", dragonEgg);

        Molecule ancientDebris = new Molecule(); ancientDebris.addElement(Element.W, 8); ancientDebris.addElement(Element.Pt, 4); ancientDebris.addElement(Element.Au, 2); ancientDebris.addElement(Element.Si, 10); ancientDebris.addElement(Element.O, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:ancient_debris", ancientDebris);
        Molecule netheriteScrap = new Molecule(); netheriteScrap.addElement(Element.W, 8); netheriteScrap.addElement(Element.Pt, 4); netheriteScrap.addElement(Element.Au, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:netherite_scrap", netheriteScrap);

        Molecule prismarineShard = new Molecule(); prismarineShard.addElement(Element.Si, 10); prismarineShard.addElement(Element.O, 20); prismarineShard.addElement(Element.Cu, 3); prismarineShard.addElement(Element.Mg, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:prismarine_shard", prismarineShard);

        Molecule prismarineCrystals = new Molecule(); prismarineCrystals.addElement(Element.Si, 8); prismarineCrystals.addElement(Element.O, 16); prismarineCrystals.addElement(Element.P, 4); prismarineCrystals.addElement(Element.Be, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:prismarine_crystals", prismarineCrystals);

        Molecule coral = new Molecule(); coral.addElement(Element.Ca, 10); coral.addElement(Element.C, 10); coral.addElement(Element.O, 30);
        String[] coralTypes = {"tube", "brain", "bubble", "fire", "horn"};
        for (String type : coralTypes) {
            ChemicalRegistry.registerBaseMaterial("minecraft:" + type + "_coral", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:" + type + "_coral_block", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:" + type + "_coral_fan", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:dead_" + type + "_coral", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:dead_" + type + "_coral_block", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:dead_" + type + "_coral_fan", coral);
        }

        // ==========================================
        // 4. ハードコード保護・超レア素材
        // ==========================================
        Molecule tnt = new Molecule(); tnt.addElement(Element.C, 14); tnt.addElement(Element.H, 10); tnt.addElement(Element.N, 6); tnt.addElement(Element.O, 12);
        ChemicalRegistry.registerBaseMaterial("minecraft:tnt", tnt);

        Molecule obsidian = new Molecule(); obsidian.addElement(Element.Si, 15); obsidian.addElement(Element.O, 30); obsidian.addElement(Element.Mg, 5); obsidian.addElement(Element.Fe, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:obsidian", obsidian);
        MaterialRegistryUtil.registerCommonTag("obsidians", obsidian);

        Molecule cryObsidian = new Molecule(); cryObsidian.addElement(Element.Si, 15); cryObsidian.addElement(Element.O, 30); cryObsidian.addElement(Element.Mg, 5); cryObsidian.addElement(Element.Fe, 2); cryObsidian.addElement(Element.Nd, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:crying_obsidian", cryObsidian);

        Molecule netherStar = new Molecule(); netherStar.addElement(Element.C, 50); netherStar.addElement(Element.W, 10); netherStar.addElement(Element.Pt, 5); netherStar.addElement(Element.Ne, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:nether_star", netherStar);

        // 【変更点】ドラゴンの息を強酸・放射性ガスに
        Molecule dragonBreath = new Molecule(); dragonBreath.addElement(Element.Rn, 10); dragonBreath.addElement(Element.F, 15); dragonBreath.addElement(Element.Kr, 5); dragonBreath.addElement(Element.Og, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:dragon_breath", dragonBreath);

        // ==========================================
        // 5. 染料・顔料 (現実の化学組成に基づく)
        // ==========================================
        // 白: チタン白 (TiO2)
        Molecule whiteDye = new Molecule(); whiteDye.addElement(Element.Ti, 1); whiteDye.addElement(Element.O, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:white_dye", whiteDye);

        // オレンジ: クロムオレンジ (Pb2CrO5)
        Molecule orangeDye = new Molecule(); orangeDye.addElement(Element.Pb, 2); orangeDye.addElement(Element.Cr, 1); orangeDye.addElement(Element.O, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:orange_dye", orangeDye);

        // マゼンタ: キナクリドン (C20H12N2O2)
        Molecule magentaDye = new Molecule(); magentaDye.addElement(Element.C, 20); magentaDye.addElement(Element.H, 12); magentaDye.addElement(Element.N, 2); magentaDye.addElement(Element.O, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:magenta_dye", magentaDye);

        // ライトブルー: セルリアンブルー (CoSnO3)
        Molecule lightBlueDye = new Molecule(); lightBlueDye.addElement(Element.Co, 1); lightBlueDye.addElement(Element.Sn, 1); lightBlueDye.addElement(Element.O, 3);
        ChemicalRegistry.registerBaseMaterial("minecraft:light_blue_dye", lightBlueDye);

        // 黄: カドミウムイエロー (CdS)
        Molecule yellowDye = new Molecule(); yellowDye.addElement(Element.Cd, 1); yellowDye.addElement(Element.S, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:yellow_dye", yellowDye);

        // ライム: コバルト緑 (CoZnO2)
        Molecule limeDye = new Molecule(); limeDye.addElement(Element.Co, 1); limeDye.addElement(Element.Zn, 1); limeDye.addElement(Element.O, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:lime_dye", limeDye);

        // ピンク: 薔薇輝石/ロードナイト (MnSiO3)
        Molecule pinkDye = new Molecule(); pinkDye.addElement(Element.Mn, 1); pinkDye.addElement(Element.Si, 1); pinkDye.addElement(Element.O, 3);
        ChemicalRegistry.registerBaseMaterial("minecraft:pink_dye", pinkDye);

        // 灰: 閃亜鉛鉱 + 炭素 (ZnS + C)
        Molecule grayDye = new Molecule(); grayDye.addElement(Element.Zn, 1); grayDye.addElement(Element.S, 1); grayDye.addElement(Element.C, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:gray_dye", grayDye);

        // 薄灰: 酸化チタン + 炭素微量 (TiO2 + C)
        Molecule lightGrayDye = new Molecule(); lightGrayDye.addElement(Element.Ti, 1); lightGrayDye.addElement(Element.O, 2); lightGrayDye.addElement(Element.C, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:light_gray_dye", lightGrayDye);

        // シアン: フタロシアニンブルー (C32H16CuN8)
        Molecule cyanDye = new Molecule(); cyanDye.addElement(Element.C, 32); cyanDye.addElement(Element.H, 16); cyanDye.addElement(Element.Cu, 1); cyanDye.addElement(Element.N, 8);
        ChemicalRegistry.registerBaseMaterial("minecraft:cyan_dye", cyanDye);

        // 紫: 貝紫/ティリアンパープル (C16H8Br2N2O2)
        Molecule purpleDye = new Molecule(); purpleDye.addElement(Element.C, 16); purpleDye.addElement(Element.H, 8); purpleDye.addElement(Element.Br, 2); purpleDye.addElement(Element.N, 2); purpleDye.addElement(Element.O, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:purple_dye", purpleDye);

        // 青: コバルトブルー (CoAl2O4)
        Molecule blueDye = new Molecule(); blueDye.addElement(Element.Co, 1); blueDye.addElement(Element.Al, 2); blueDye.addElement(Element.O, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:blue_dye", blueDye);

        // 茶: ウンバー/褐鉄鉱 (Fe2MnO6H2)
        Molecule brownDye = new Molecule(); brownDye.addElement(Element.Fe, 2); brownDye.addElement(Element.Mn, 1); brownDye.addElement(Element.O, 6); brownDye.addElement(Element.H, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:brown_dye", brownDye);

        // 緑: ビリジアン (Cr2O5H4)
        Molecule greenDye = new Molecule(); greenDye.addElement(Element.Cr, 2); greenDye.addElement(Element.O, 5); greenDye.addElement(Element.H, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:green_dye", greenDye);

        // 赤: 辰砂/シンシャ (HgS)
        Molecule redDye = new Molecule(); redDye.addElement(Element.Hg, 1); redDye.addElement(Element.S, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:red_dye", redDye);

        // 黒: カーボンブラック (C)
        Molecule blackDye = new Molecule(); blackDye.addElement(Element.C, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:black_dye", blackDye);
    }
}