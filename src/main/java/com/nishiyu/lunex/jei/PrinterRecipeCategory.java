package com.nishiyu.lunex.jei;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.recipe.PrinterRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

public class PrinterRecipeCategory implements IRecipeCategory<PrinterRecipe> {

    public static final RecipeType<PrinterRecipe> TYPE = RecipeType.create(Lunex.MODID, "printer", PrinterRecipe.class);
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/printer.png");

    private final IDrawable background;
    private final IDrawable slotTexture;
    private final IDrawable icon;
    private final IDrawableAnimated arrow;

    public PrinterRecipeCategory(IGuiHelper helper) {
        // テキストが収まるように、幅200・高さ45の透明な背景枠を作成
        this.background = helper.createBlankDrawable(200, 45);

        // 実際のスロット画像は別で切り取っておく (幅118, 高さ26)
        this.slotTexture = helper.createDrawable(TEXTURE, 29, 43, 118, 26);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Lunex.PRINTER.get()));

        IDrawableStatic staticArrow = helper.createDrawable(TEXTURE, 176, 0, 24, 17);
        this.arrow = helper.createAnimatedDrawable(staticArrow, 100, IDrawableAnimated.StartDirection.LEFT, false);
    }

    @Override
    public @NotNull RecipeType<PrinterRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("block.lunex.printer");
    }

    @Override
    public @NotNull IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return this.background.getWidth();
    }

    @Override
    public int getHeight() {
        return this.background.getHeight();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull PrinterRecipe recipe, @NotNull IFocusGroup focuses) {
        // 透明枠(幅200)の中央にGUI(幅118)を寄せるためのXオフセット: (200 - 118) / 2 = 41
        int offsetX = 41;

        // 入力スロット (最大3つ)
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            int x = offsetX + 6 + (i * 18);
            builder.addSlot(RecipeIngredientRole.INPUT, x, 5)
                    .addIngredients(recipe.ingredients().get(i));
        }

        // 出力スロット
        builder.addSlot(RecipeIngredientRole.OUTPUT, offsetX + 96, 5)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(@NotNull PrinterRecipe recipe, @NotNull IRecipeSlotsView recipeSlotsView, @NotNull GuiGraphics guiGraphics, double mouseX, double mouseY) {
        int offsetX = 41;

        // スロット画像を中央に手動描画
        this.slotTexture.draw(guiGraphics, offsetX, 0);

        // 白い矢印の描画
        arrow.draw(guiGraphics, offsetX + 66, 6);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.processingTime() + " t", offsetX + 68, 24, 0xFF808080, false);

        // ★完成品が「プログラムディスク」か「記入済みの本(WRITTEN_BOOK)」の場合のみ、テキストを下に描画
        if (recipe.result().getItem() == Lunex.PROGRAM_DISK.get() || recipe.result().getItem() == Items.WRITTEN_BOOK) {
            Component text = Component.translatable(LunexJEIPlugin.INFO_MACHINE_LINK);

            // テキストを中央揃えにして描画 (Y座標はスロット画像の下あたり)
            int textWidth = font.width(text);
            int textX = (getWidth() - textWidth) / 2;
            guiGraphics.drawString(font, text, textX, 32, 0xFF555555, false);
        }
    }
}