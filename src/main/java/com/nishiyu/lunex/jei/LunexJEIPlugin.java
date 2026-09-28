package com.nishiyu.lunex.jei;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.recipe.PrinterRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JeiPlugin
public class LunexJEIPlugin implements IModPlugin {

    @Translatable(
            en = "Can be crafted by placing specific items in the Machine Frame and clicking 'Assemble'.",
            ja = "マシンフレームに特定のアイテムを入れて「組み立て」をクリックすることで作成できます。"
    )
    public static final String INFO_MACHINE_ASSEMBLE = "jei.lunex.machine_assemble.info";

    // 変更：枠に収まるように文章を短縮
    @Translatable(
            en = "Requires a Machine.",
            ja = "マシンと連携して使用"
    )
    public static final String INFO_MACHINE_LINK = "jei.lunex.machine_link.info";

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "jei_plugin");
    }

    // =========================================
    // 1. レシピカテゴリの登録
    // =========================================
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PrinterRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    // =========================================
    // 2. レシピデータと情報の登録
    // =========================================
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // マシン組み立ての情報表示
        registration.addIngredientInfo(new ItemStack(Lunex.ADVANCED_MACHINE_ITEM.get()), VanillaTypes.ITEM_STACK, Component.translatable(INFO_MACHINE_ASSEMBLE));
        registration.addIngredientInfo(new ItemStack(Lunex.ROUTER_BLOCK_ITEM.get()), VanillaTypes.ITEM_STACK, Component.translatable(INFO_MACHINE_ASSEMBLE));

        // --- JSON(KubeJS)で追加されたレシピを取得 ---
        RecipeManager manager = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getRecipeManager() : null;
        List<PrinterRecipe> recipes = Objects.requireNonNull(manager).getAllRecipesFor(PrinterRecipe.Type.INSTANCE)
                .stream()
                .map(RecipeHolder::value)
                .toList();

        List<PrinterRecipe> allRecipes = new ArrayList<>(recipes);

        // --- ハードコードされている既存機能の「ダミーレシピ」をJEI用に追加 ---

        // ダミー1: エンチャント本のコピー
        allRecipes.add(new PrinterRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.ENCHANTED_BOOK), Ingredient.of(new ItemStack(Items.INK_SAC, 10)), Ingredient.of(new ItemStack(Items.EXPERIENCE_BOTTLE, 10))),
                new ItemStack(Lunex.INACTIVE_BOOK.get()), 10, 100
        ));

        // ダミー2: プログラムディスクの作成
        allRecipes.add(new PrinterRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(new ItemStack(Items.IRON_INGOT, 2)), Ingredient.of(new ItemStack(Items.REDSTONE, 5)), Ingredient.of(new ItemStack(Items.GOLD_INGOT, 1))),
                new ItemStack(Lunex.PROGRAM_DISK.get()), 10, 100
        ));

        // ダミー3: 本の印刷
        allRecipes.add(new PrinterRecipe(
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(new ItemStack(Items.INK_SAC, 5)), Ingredient.of(Items.BOOK)),
                new ItemStack(Items.WRITTEN_BOOK), 10, 100
        ));

        // プリンターのカテゴリへすべてのレシピ（JSON + ダミー）を流し込む
        registration.addRecipes(PrinterRecipeCategory.TYPE, allRecipes);
    }

    // =========================================
    // 3. 作業台（プリンターブロック自体）の登録
    // =========================================
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Lunex.PRINTER.get()), PrinterRecipeCategory.TYPE);
    }
}