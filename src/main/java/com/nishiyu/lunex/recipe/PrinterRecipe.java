package com.nishiyu.lunex.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record PrinterRecipe(NonNullList<Ingredient> ingredients, ItemStack result, int energyPerTick, int processingTime) implements Recipe<PrinterRecipeInput> {

    @Override
    public boolean matches(@NotNull PrinterRecipeInput input, @NotNull Level level) {
        if (input.isEmpty()) return false;

        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) inputs.add(stack);
        }

        if (inputs.size() != ingredients.size()) return false;

        // 簡単な順不同マッチング（入力アイテムとレシピの材料が一致するか）
        boolean[] used = new boolean[inputs.size()];
        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < inputs.size(); i++) {
                if (!used[i] && ingredient.test(inputs.get(i))) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull PrinterRecipeInput input, HolderLookup.@NotNull Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return result;
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return Type.INSTANCE;
    }

    // JSONシリアライザー（KubeJS対応用）
    public static class Serializer implements RecipeSerializer<PrinterRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        public static final MapCodec<PrinterRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(
                        ingredients -> {
                            Ingredient[] array = ingredients.toArray(Ingredient[]::new);
                            if (array.length == 0 || array.length > 3) {
                                return com.mojang.serialization.DataResult.error(() -> "Printer recipe must have 1 to 3 ingredients");
                            }
                            return com.mojang.serialization.DataResult.success(NonNullList.of(Ingredient.EMPTY, array));
                        },
                        com.mojang.serialization.DataResult::success
                ).forGetter(PrinterRecipe::ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(PrinterRecipe::result),
                Codec.INT.optionalFieldOf("energyPerTick", 10).forGetter(PrinterRecipe::energyPerTick),
                Codec.INT.optionalFieldOf("processingTime", 100).forGetter(PrinterRecipe::processingTime)
        ).apply(inst, PrinterRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PrinterRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                        list -> NonNullList.of(Ingredient.EMPTY, list.toArray(Ingredient[]::new)),
                        list -> list
                ), PrinterRecipe::ingredients,
                ItemStack.STREAM_CODEC, PrinterRecipe::result,
                ByteBufCodecs.INT, PrinterRecipe::energyPerTick,
                ByteBufCodecs.INT, PrinterRecipe::processingTime,
                PrinterRecipe::new
        );

        @Override
        public @NotNull MapCodec<PrinterRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, PrinterRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public static class Type implements RecipeType<PrinterRecipe> {
        public static final Type INSTANCE = new Type();
        public static final String ID = "printer";
    }
}