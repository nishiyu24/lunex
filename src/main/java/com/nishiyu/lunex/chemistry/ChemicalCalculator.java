package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.chemistry.model.Molecule;
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

public class ChemicalCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChemicalCalculator.class);

    public static void calculateAll(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        RegistryAccess registryAccess = server.registryAccess();

        LOGGER.info("[Lunex Chemistry] Starting molecule assignment calculation...");

        // 1. JSON (MaterialDataLoader) により登録された基礎素材の確認
        int jsonLoadedCount = ChemicalRegistry.getDictionary().size();
        LOGGER.info("[Lunex Chemistry] Loaded base materials from JSON/Datapacks: {}", jsonLoadedCount);


        // 2. レシピからの自動計算
        // 【重要】加工品・構造物・ツール（かまど、ドア、階段、チェスト等）は除外し、
        // 純粋な素材合成や中間材料のみに限定して計算する
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

                // 工作・成形加工品（かまど、ツール、家具など）は計算から除外
                if (isConstructOrProcessedItem(resultItem)) {
                    continue;
                }

                ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(resultItem.getItem());
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

        LOGGER.info("[Lunex Chemistry] Calculation complete! Iterations: {}", iterations);
        LOGGER.info("[Lunex Chemistry] Final registered items in dictionary: {}", ChemicalRegistry.getDictionary().size());
    }

    /**
     * 工業製品・構造物・道具・家具などの成形加工品かどうかを判定
     */
    private static boolean isConstructOrProcessedItem(ItemStack stack) {
        // 1. ツール・武器・防具
        if (stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem || stack.isDamageableItem()) {
            return true;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();

        // 2. ブロック成形品・家具・機械
        // (かまど、作業台、チェスト、ドア、階段、フェンス、トロッコ、ボート等)
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