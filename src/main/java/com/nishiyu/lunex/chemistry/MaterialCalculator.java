package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.chemistry.model.InkGene;
import com.nishiyu.lunex.chemistry.model.ItemDNA;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaterialCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialCalculator.class);

    public static void calculateAll(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        RegistryAccess registryAccess = server.registryAccess();

        LOGGER.info("[Lunex Chemistry] Starting ItemDNA assignment calculation...");

        // 1. JSONにより登録された基礎素材の確認
        int jsonLoadedCount = MaterialRegistry.getDictionary().size();
        LOGGER.info("[Lunex Chemistry] Loaded base materials from JSON/Datapacks: {}", jsonLoadedCount);

        boolean changed = true;
        int iterations = 0;
        int maxIterations = 20;

        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                Recipe<?> recipe = holder.value();
                ItemStack resultItem = recipe.getResultItem(registryAccess);

                if (resultItem.isEmpty()) continue;

                // 工作・成形加工品は計算から除外
                if (isConstructOrProcessedItem(resultItem)) continue;

                ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(resultItem.getItem());
                if (MaterialRegistry.getDNA(resultId) != null) continue;

                List<InkGene> collectedGenes = new ArrayList<>();
                boolean canCalculate = true;

                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) continue;

                    ItemStack[] items = ingredient.getItems();
                    if (items.length == 0) continue;

                    ResourceLocation ingredientId = BuiltInRegistries.ITEM.getKey(items[0].getItem());
                    ItemDNA ingDNA = MaterialRegistry.getDNA(ingredientId);

                    if (ingDNA == null) {
                        canCalculate = false;
                        break;
                    }

                    // 材料のDNAから空でない遺伝子を収集
                    for (InkGene gene : ingDNA.getSequence()) {
                        if (!gene.isEmpty()) {
                            collectedGenes.add(gene);
                        }
                    }
                }

                if (canCalculate && !collectedGenes.isEmpty()) {
                    ItemDNA newDNA = mixGenes(collectedGenes);
                    if (!newDNA.isEmpty()) {
                        MaterialRegistry.putDNA(resultId, newDNA);
                        changed = true;
                    }
                }
            }
        }

        LOGGER.info("[Lunex Chemistry] Calculation complete! Iterations: {}", iterations);
        LOGGER.info("[Lunex Chemistry] Final registered items in dictionary: {}", MaterialRegistry.getDictionary().size());
    }

    /**
     * 収集した遺伝子群から頻度を集計し、長さ6の新しいDNA配列を構築する
     */
    private static ItemDNA mixGenes(List<InkGene> collectedGenes) {
        Map<InkGene, Integer> frequencyMap = new HashMap<>();
        for (InkGene gene : collectedGenes) {
            frequencyMap.put(gene, frequencyMap.getOrDefault(gene, 0) + 1);
        }

        // 出現頻度順に降順ソート
        List<Map.Entry<InkGene, Integer>> sortedList = new ArrayList<>(frequencyMap.entrySet());
        sortedList.sort((a, b) -> {
            int freqCompare = b.getValue().compareTo(a.getValue());
            if (freqCompare != 0) return freqCompare;
            return a.getKey().ink().name().compareTo(b.getKey().ink().name());
        });

        ItemDNA resultDNA = new ItemDNA();
        int index = 0;

        // 頻度の高い遺伝子から優先してDNAの枠に割り当てる
        for (Map.Entry<InkGene, Integer> entry : sortedList) {
            // 全体に対する割合に応じて枠の占有数を決定（最低1枠）
            int countToAssign = Math.max(1, Math.round((float) entry.getValue() / collectedGenes.size() * ItemDNA.DNA_LENGTH));

            for (int i = 0; i < countToAssign && index < ItemDNA.DNA_LENGTH; i++) {
                resultDNA.setGene(index++, entry.getKey());
            }
            if (index >= ItemDNA.DNA_LENGTH) break;
        }

        return resultDNA;
    }

    private static boolean isConstructOrProcessedItem(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem || stack.isDamageableItem()) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return path.contains("furnace") || path.contains("workbench") || path.contains("crafting_table")
                || path.contains("chest") || path.contains("barrel") || path.contains("hopper")
                || path.contains("door") || path.contains("trapdoor") || path.contains("gate") || path.contains("fence")
                || path.contains("stairs") || path.contains("slab") || path.contains("wall")
                || path.contains("button") || path.contains("pressure_plate")
                || path.contains("boat") || path.contains("minecart") || path.contains("bed")
                || path.contains("piston") || path.contains("dispenser") || path.contains("dropper")
                || path.contains("anvil") || path.contains("stand") || path.contains("lantern")
                || path.contains("ladder") || path.contains("sign") || path.contains("torch");
    }
}