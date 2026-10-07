package com.nishiyu.lunex.chemistry;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;

public class ChemicalCalculator {

    // 計算の基準値（インゴット＝9）
    private static final int INGOT = 9;

    public static void calculateAll(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        RegistryAccess registryAccess = server.registryAccess();

        System.out.println("[Lunex Chemistry] 基礎素材の絶対数に基づく分子割り当て計算を開始します...");

        initBaseMaterials();

        boolean changed = true;
        int iterations = 0;
        int maxIterations = 50;

        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                Recipe<?> recipe = holder.value();
                ItemStack resultItem = recipe.getResultItem(registryAccess);

                if (resultItem.isEmpty()) continue;
                ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(resultItem.getItem());

                // 【ループ防止】既に辞書に登録されているアイテムは計算をスキップ
                if (ChemicalRegistry.getMolecule(resultId) != null) {
                    continue;
                }

                Molecule currentMolecule = new Molecule();
                boolean canCalculate = true;

                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) continue;

                    ItemStack[] items = ingredient.getItems();
                    if (items.length == 0) continue;

                    ResourceLocation ingredientId = BuiltInRegistries.ITEM.getKey(items[0].getItem());
                    Molecule ingMolecule = ChemicalRegistry.getMolecule(ingredientId);

                    if (ingMolecule == null) {
                        canCalculate = false;
                        break;
                    }
                    currentMolecule.addMolecule(ingMolecule, 1);
                }

                if (canCalculate && !currentMolecule.isEmpty()) {
                    currentMolecule.divideBy(resultItem.getCount());

                    if (!currentMolecule.isEmpty()) {
                        ChemicalRegistry.putMolecule(resultId, currentMolecule);
                        changed = true;
                    }
                }
            }
        }

        System.out.println("[Lunex Chemistry] 計算完了! 反復回数: " + iterations);
        System.out.println("[Lunex Chemistry] 割り当て済みアイテム数: " + ChemicalRegistry.getDictionary().size());
    }

    private static void registerTag(TagKey<Item> tagKey, Molecule molecule) {
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            ChemicalRegistry.registerBaseMaterial(id, molecule);
        }
    }

    private static void registerCommonTag(String tagPath, Molecule molecule) {
        TagKey<Item> tagKey = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:" + tagPath));
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            ChemicalRegistry.registerBaseMaterial(id, molecule);
        }
    }

    private static void initBaseMaterials() {
        // ==========================================
        // 1. 自然地形・岩石系ブロック
        // ==========================================
        Molecule log = new Molecule(); log.addElement(Element.C, 18); log.addElement(Element.H, 20); log.addElement(Element.O, 10);
        registerTag(ItemTags.LOGS, log);
        registerTag(ItemTags.PLANKS, log); // 念のため板材ベースも

        Molecule leaves = new Molecule(); leaves.addElement(Element.C, 3); leaves.addElement(Element.H, 5); leaves.addElement(Element.O, 2); leaves.addElement(Element.Mg, 1);
        registerTag(ItemTags.LEAVES, leaves);

        Molecule dirt = new Molecule(); dirt.addElement(Element.Si, 10); dirt.addElement(Element.O, 20); dirt.addElement(Element.Al, 4); dirt.addElement(Element.C, 3); dirt.addElement(Element.N, 1);
        registerTag(ItemTags.DIRT, dirt);

        Molecule sand = new Molecule(); sand.addElement(Element.Si, 12); sand.addElement(Element.O, 24);
        registerTag(ItemTags.SAND, sand);

        // 基本の石
        Molecule stone = new Molecule(); stone.addElement(Element.Si, 15); stone.addElement(Element.O, 30); stone.addElement(Element.Al, 5); stone.addElement(Element.Ca, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:stone", stone);
        ChemicalRegistry.registerBaseMaterial("minecraft:cobblestone", stone);
        registerTag(ItemTags.STONE_CRAFTING_MATERIALS, stone);

        // 安山岩・閃緑岩・花崗岩
        Molecule andesite = new Molecule(); andesite.addElement(Element.Si, 12); andesite.addElement(Element.O, 24); andesite.addElement(Element.Al, 4); andesite.addElement(Element.Ca, 2); andesite.addElement(Element.Na, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:andesite", andesite);
        Molecule diorite = new Molecule(); diorite.addElement(Element.Si, 10); diorite.addElement(Element.O, 20); diorite.addElement(Element.Al, 3); diorite.addElement(Element.Na, 2); diorite.addElement(Element.Ca, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:diorite", diorite);
        Molecule granite = new Molecule(); granite.addElement(Element.Si, 14); granite.addElement(Element.O, 28); granite.addElement(Element.Al, 4); granite.addElement(Element.K, 2); granite.addElement(Element.Na, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:granite", granite);

        // 深層岩 (Deepslate)
        Molecule deepslate = new Molecule(); deepslate.addElement(Element.Si, 15); deepslate.addElement(Element.O, 30); deepslate.addElement(Element.Al, 5); deepslate.addElement(Element.Fe, 3); deepslate.addElement(Element.Mg, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:cobbled_deepslate", deepslate);
        ChemicalRegistry.registerBaseMaterial("minecraft:deepslate", deepslate);

        // 方解石 (Calcite) & 凝灰岩 (Tuff) & 鍾乳石 (Dripstone)
        Molecule calcite = new Molecule(); calcite.addElement(Element.Ca, 10); calcite.addElement(Element.C, 10); calcite.addElement(Element.O, 30); // 炭酸カルシウム
        ChemicalRegistry.registerBaseMaterial("minecraft:calcite", calcite);
        ChemicalRegistry.registerBaseMaterial("minecraft:pointed_dripstone", calcite);
        ChemicalRegistry.registerBaseMaterial("minecraft:dripstone_block", calcite);

        Molecule tuff = new Molecule(); tuff.addElement(Element.Si, 12); tuff.addElement(Element.O, 24); tuff.addElement(Element.Al, 4); tuff.addElement(Element.Fe, 2); tuff.addElement(Element.Ca, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:tuff", tuff);

        Molecule clay = new Molecule(); clay.addElement(Element.Al, 4); clay.addElement(Element.Si, 4); clay.addElement(Element.O, 18); clay.addElement(Element.H, 8);
        ChemicalRegistry.registerBaseMaterial("minecraft:clay_ball", clay);

        // ==========================================
        // 2. 液体
        // ==========================================
        Molecule water = new Molecule(); water.addElement(Element.H, 20); water.addElement(Element.O, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:water_bucket", water);

        Molecule lava = new Molecule(); lava.addElement(Element.Si, 30); lava.addElement(Element.O, 60); lava.addElement(Element.Fe, 10); lava.addElement(Element.Mg, 10);
        ChemicalRegistry.registerBaseMaterial("minecraft:lava_bucket", lava);

        // ==========================================
        // 3. 鉱石・金属プロセス
        // ==========================================
        Molecule rawIron = new Molecule(); rawIron.addElement(Element.Fe, 14); rawIron.addElement(Element.O, 8);
        ChemicalRegistry.registerBaseMaterial("minecraft:raw_iron", rawIron);
        registerCommonTag("raw_materials/iron", rawIron);
        Molecule ironIngot = new Molecule(); ironIngot.addElement(Element.Fe, INGOT);
        ChemicalRegistry.registerBaseMaterial("minecraft:iron_ingot", ironIngot);
        registerCommonTag("ingots/iron", ironIngot);

        Molecule rawCopper = new Molecule(); rawCopper.addElement(Element.Cu, 14); rawCopper.addElement(Element.Fe, 3); rawCopper.addElement(Element.S, 5);
        ChemicalRegistry.registerBaseMaterial("minecraft:raw_copper", rawCopper);
        registerCommonTag("raw_materials/copper", rawCopper);
        Molecule copperIngot = new Molecule(); copperIngot.addElement(Element.Cu, INGOT);
        ChemicalRegistry.registerBaseMaterial("minecraft:copper_ingot", copperIngot);
        registerCommonTag("ingots/copper", copperIngot);

        Molecule rawGold = new Molecule(); rawGold.addElement(Element.Au, 11);
        ChemicalRegistry.registerBaseMaterial("minecraft:raw_gold", rawGold);
        registerCommonTag("raw_materials/gold", rawGold);
        Molecule goldIngot = new Molecule(); goldIngot.addElement(Element.Au, INGOT);
        ChemicalRegistry.registerBaseMaterial("minecraft:gold_ingot", goldIngot);
        registerCommonTag("ingots/gold", goldIngot);

        // ==========================================
        // 4. 宝石・特殊鉱物
        // ==========================================
        Molecule diamondGem = new Molecule(); diamondGem.addElement(Element.C, 50);
        ChemicalRegistry.registerBaseMaterial("minecraft:diamond", diamondGem);
        registerCommonTag("gems/diamond", diamondGem);

        Molecule emeraldGem = new Molecule(); emeraldGem.addElement(Element.Be, 6); emeraldGem.addElement(Element.Al, 4); emeraldGem.addElement(Element.Si, 12); emeraldGem.addElement(Element.O, 36); emeraldGem.addElement(Element.Cr, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:emerald", emeraldGem);
        registerCommonTag("gems/emerald", emeraldGem);

        Molecule lapis = new Molecule(); lapis.addElement(Element.Na, 6); lapis.addElement(Element.Ca, 2); lapis.addElement(Element.Al, 6); lapis.addElement(Element.Si, 6); lapis.addElement(Element.O, 24); lapis.addElement(Element.S, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:lapis_lazuli", lapis);
        registerCommonTag("gems/lapis", lapis);

        Molecule amethyst = new Molecule(); amethyst.addElement(Element.Si, 15); amethyst.addElement(Element.O, 30); amethyst.addElement(Element.Fe, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:amethyst_shard", amethyst);

        Molecule quartz = new Molecule(); quartz.addElement(Element.Si, 10); quartz.addElement(Element.O, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:quartz", quartz);
        registerCommonTag("gems/quartz", quartz);

        Molecule coalItem = new Molecule(); coalItem.addElement(Element.C, 18);
        registerTag(ItemTags.COALS, coalItem);
        ChemicalRegistry.registerBaseMaterial("minecraft:coal", coalItem);
        ChemicalRegistry.registerBaseMaterial("minecraft:charcoal", coalItem);

        Molecule redstoneDust = new Molecule(); redstoneDust.addElement(Element.Cu, 5); redstoneDust.addElement(Element.Si, 5); redstoneDust.addElement(Element.O, 10); redstoneDust.addElement(Element.Nd, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:redstone", redstoneDust);

        // ==========================================
        // 5. 植物・農作物・キノコ・海草
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

        Molecule flower = new Molecule(); flower.addElement(Element.C, 10); flower.addElement(Element.H, 14); flower.addElement(Element.O, 6);
        registerTag(ItemTags.FLOWERS, flower);

        // ==========================================
        // 6. 動物ドロップ・モンスタードロップ
        // ==========================================
        Molecule meat = new Molecule(); meat.addElement(Element.C, 16); meat.addElement(Element.H, 30); meat.addElement(Element.O, 8); meat.addElement(Element.N, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:beef", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:porkchop", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:mutton", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:chicken", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:rabbit", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:cod", meat);
        ChemicalRegistry.registerBaseMaterial("minecraft:salmon", meat);

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

        Molecule shulkerShell = new Molecule(); shulkerShell.addElement(Element.Ca, 8); shulkerShell.addElement(Element.C, 16); shulkerShell.addElement(Element.O, 24); shulkerShell.addElement(Element.N, 4); shulkerShell.addElement(Element.Mg, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:shulker_shell", shulkerShell);

        Molecule inkSac = new Molecule(); inkSac.addElement(Element.C, 18); inkSac.addElement(Element.H, 10); inkSac.addElement(Element.N, 2); inkSac.addElement(Element.O, 4);
        ChemicalRegistry.registerBaseMaterial("minecraft:ink_sac", inkSac);

        Molecule glowInkSac = new Molecule(); glowInkSac.addElement(Element.C, 18); glowInkSac.addElement(Element.H, 10); glowInkSac.addElement(Element.N, 2); glowInkSac.addElement(Element.O, 4); glowInkSac.addElement(Element.P, 2); glowInkSac.addElement(Element.Cu, 1);
        ChemicalRegistry.registerBaseMaterial("minecraft:glow_ink_sac", glowInkSac);

        // ==========================================
        // 7. ネザー・エンド・海洋素材
        // ==========================================
        Molecule netherrack = new Molecule(); netherrack.addElement(Element.Si, 10); netherrack.addElement(Element.O, 20); netherrack.addElement(Element.S, 5); netherrack.addElement(Element.Fe, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:netherrack", netherrack);

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

        Molecule endStone = new Molecule(); endStone.addElement(Element.Si, 12); endStone.addElement(Element.O, 24); endStone.addElement(Element.Ca, 5); endStone.addElement(Element.Al, 3);
        ChemicalRegistry.registerBaseMaterial("minecraft:end_stone", endStone);

        Molecule enderPearl = new Molecule(); enderPearl.addElement(Element.Be, 5); enderPearl.addElement(Element.Bi, 3); enderPearl.addElement(Element.Si, 10); enderPearl.addElement(Element.O, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:ender_pearl", enderPearl);

        Molecule blazeRod = new Molecule(); blazeRod.addElement(Element.S, 15); blazeRod.addElement(Element.C, 10); blazeRod.addElement(Element.Fe, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:blaze_rod", blazeRod);

        // ネザライト系（古代の残骸）
        Molecule ancientDebris = new Molecule(); ancientDebris.addElement(Element.W, 8); ancientDebris.addElement(Element.Pt, 4); ancientDebris.addElement(Element.Au, 2); ancientDebris.addElement(Element.Si, 10); ancientDebris.addElement(Element.O, 20);
        ChemicalRegistry.registerBaseMaterial("minecraft:ancient_debris", ancientDebris);
        Molecule netheriteScrap = new Molecule(); netheriteScrap.addElement(Element.W, 8); netheriteScrap.addElement(Element.Pt, 4); netheriteScrap.addElement(Element.Au, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:netherite_scrap", netheriteScrap);

        // 海洋素材（プリズマリン・海結晶など）
        Molecule prismarineShard = new Molecule(); prismarineShard.addElement(Element.Si, 10); prismarineShard.addElement(Element.O, 20); prismarineShard.addElement(Element.Cu, 3); prismarineShard.addElement(Element.Mg, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:prismarine_shard", prismarineShard);

        Molecule prismarineCrystals = new Molecule(); prismarineCrystals.addElement(Element.Si, 8); prismarineCrystals.addElement(Element.O, 16); prismarineCrystals.addElement(Element.P, 4); prismarineCrystals.addElement(Element.Be, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:prismarine_crystals", prismarineCrystals);

        // サンゴ類（タグエラー回避のため全種を個別に登録）
        Molecule coral = new Molecule(); coral.addElement(Element.Ca, 10); coral.addElement(Element.C, 10); coral.addElement(Element.O, 30);
        String[] coralTypes = {"tube", "brain", "bubble", "fire", "horn"};
        for (String type : coralTypes) {
            ChemicalRegistry.registerBaseMaterial("minecraft:" + type + "_coral", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:" + type + "_coral_block", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:" + type + "_coral_fan", coral);
            // 死んだサンゴ類
            ChemicalRegistry.registerBaseMaterial("minecraft:dead_" + type + "_coral", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:dead_" + type + "_coral_block", coral);
            ChemicalRegistry.registerBaseMaterial("minecraft:dead_" + type + "_coral_fan", coral);
        }

        // ==========================================
        // 8. ハードコード保護・その他の特殊アイテム
        // ==========================================
        Molecule tnt = new Molecule(); tnt.addElement(Element.C, 14); tnt.addElement(Element.H, 10); tnt.addElement(Element.N, 6); tnt.addElement(Element.O, 12);
        ChemicalRegistry.registerBaseMaterial("minecraft:tnt", tnt);

        Molecule obsidian = new Molecule(); obsidian.addElement(Element.Si, 15); obsidian.addElement(Element.O, 30); obsidian.addElement(Element.Mg, 5); obsidian.addElement(Element.Fe, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:obsidian", obsidian);
        registerCommonTag("obsidians", obsidian);

        Molecule cryObsidian = new Molecule(); cryObsidian.addElement(Element.Si, 15); cryObsidian.addElement(Element.O, 30); cryObsidian.addElement(Element.Mg, 5); cryObsidian.addElement(Element.Fe, 2); cryObsidian.addElement(Element.Nd, 2);
        ChemicalRegistry.registerBaseMaterial("minecraft:crying_obsidian", cryObsidian);
    }
}