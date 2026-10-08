package com.nishiyu.lunex.chemistry;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;

public class ChemicalCalculator {

    public static void calculateAll(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        RegistryAccess registryAccess = server.registryAccess();

        System.out.println("[Lunex Chemistry] Starting molecule assignment calculation based on base material counts...");

        // Initialize categorized materials
        BaseMaterialInit.registerAll();
        SpecificMaterialInit.registerAll();

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

                // [Loop Prevention] Skip calculation if the item is already registered in the dictionary
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

        System.out.println("[Lunex Chemistry] Calculation complete! Iterations: " + iterations);
        System.out.println("[Lunex Chemistry] Assigned items: " + ChemicalRegistry.getDictionary().size());
    }
}