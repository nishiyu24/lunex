package com.nishiyu.lunex.jei;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.datagen.Translatable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class LunexJEIPlugin implements IModPlugin {

    // =========================================
    // JEI用 翻訳キーとアノテーションの登録
    // =========================================
    @Translatable(
            en = "Can be crafted by placing specific items in the Machine Frame and clicking 'Assemble'.",
            ja = "マシンフレームに特定のアイテムを入れて「組み立て」をクリックすることで作成できます。"
    )
    public static final String INFO_MACHINE_ASSEMBLE = "jei.lunex.machine_assemble.info";

    @Translatable(
            en = "Can be crafted using the Printer.",
            ja = "プリンターを使用して作成できます。"
    )
    public static final String INFO_PRINTER_CRAFT = "jei.lunex.printer_craft.info";

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Programmable Machine (マシンフレームでの組み立て)
        registration.addIngredientInfo(
                new ItemStack(Lunex.ADVANCED_MACHINE_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable(INFO_MACHINE_ASSEMBLE)
        );

        // Router (マシンフレームでの組み立て)
        registration.addIngredientInfo(
                new ItemStack(Lunex.ROUTER_BLOCK_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable(INFO_MACHINE_ASSEMBLE)
        );

        // Program Disk (プリンターでの作成)
        registration.addIngredientInfo(
                new ItemStack(Lunex.PROGRAM_DISK.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable(INFO_PRINTER_CRAFT)
        );

        // Inactive Book (プリンターでの作成)
        registration.addIngredientInfo(
                new ItemStack(Lunex.INACTIVE_BOOK.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable(INFO_PRINTER_CRAFT)
        );
    }
}