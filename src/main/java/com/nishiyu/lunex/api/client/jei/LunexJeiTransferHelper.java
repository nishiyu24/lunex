package com.nishiyu.lunex.api.client.jei;

import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.api.client.ui.panel.AbstractRightPanel;
import com.nishiyu.lunex.menu.MainframeOverviewMenu;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class LunexJeiTransferHelper {

    public interface TransferAction {
        @Nullable
        IRecipeTransferError transfer(BlockPos pos, BlockEntity be, IRecipeSlotsView recipeSlots, boolean doTransfer, mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper helper);
    }

    public static <T, U extends AbstractRightPanel> void register(
            IRecipeTransferRegistration reg,
            mezz.jei.api.recipe.RecipeType<T> type,
            Class<U> targetUIClass,
            TransferAction action) {

        reg.addRecipeTransferHandler(new IRecipeTransferHandler<MainframeOverviewMenu, T>() {
            @Override
            public @NotNull Class<MainframeOverviewMenu> getContainerClass() { return MainframeOverviewMenu.class; }

            @Override
            public @NotNull Optional<net.minecraft.world.inventory.MenuType<MainframeOverviewMenu>> getMenuType() { return Optional.empty(); }

            @Override
            public mezz.jei.api.recipe.@NotNull RecipeType<T> getRecipeType() { return type; }

            @SuppressWarnings("removal")
            @Override
            public @Nullable IRecipeTransferError transferRecipe(@NotNull MainframeOverviewMenu container, @NotNull T recipe, @NotNull IRecipeSlotsView recipeSlots, @NotNull Player player, boolean maxTransfer, boolean doTransfer) {
                BlockPos pos = MainframeOverviewScreen.lastSelectedPos;
                if (pos == null) return reg.getTransferHelper().createUserErrorWithTooltip(Component.literal("Select a machine first"));

                BlockEntity be = container.getLevel().getBlockEntity(pos);
                if (be == null) return reg.getTransferHelper().createInternalError();

                AbstractRightPanel extension = MainframeUIRegistry.createRightPanel(pos, be);
                if (!targetUIClass.isInstance(extension)) {
                    return reg.getTransferHelper().createUserErrorWithTooltip(Component.literal("Selected machine UI does not match the recipe."));
                }

                return action.transfer(pos, be, recipeSlots, doTransfer, reg.getTransferHelper());
            }
        }, type);
    }
}