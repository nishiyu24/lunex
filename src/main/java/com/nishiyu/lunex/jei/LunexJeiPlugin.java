package com.nishiyu.lunex.jei;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import com.nishiyu.lunex.api.client.jei.LunexJeiTransferHelper;
import com.nishiyu.lunex.api.client.ui.extensions.CrafterUIExtension;
import com.nishiyu.lunex.api.client.ui.extensions.FurnaceUIExtension;
import com.nishiyu.lunex.api.client.ui.tabs.SystemStorageBottomTab;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class LunexJeiPlugin implements IModPlugin {

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.parse("lunex:jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {

        registration.addGuiContainerHandler(MainframeOverviewScreen.class, new IGuiContainerHandler<>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull MainframeOverviewScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                IdeScreenFramework fw = screen.getUiFramework();
                IdeScreenFramework.IBottomTab activeTab = fw.getActiveBottomTab();

                if (activeTab instanceof SystemStorageBottomTab sysTab) {
                    int tabY = Math.max(0, screen.height - fw.bottomHeight);

                    areas.add(new Rect2i(0, 0, Math.max(0, screen.width), tabY));

                    // 動的に計算された配置X座標を取得
                    int storageX = sysTab.getCalculatedStorageX(screen.width);
                    int storageWidth = sysTab.getStorageAreaWidth(screen.width, storageX);
                    int tabHeaderHeight = 24;

                    int jeiAreaX = storageX + storageWidth;
                    int jeiAreaWidth = Math.max(0, screen.width - jeiAreaX);

                    if (jeiAreaWidth > 0) {
                        areas.add(new Rect2i(jeiAreaX, tabY, jeiAreaWidth, tabHeaderHeight));
                    }

                    areas.add(new Rect2i(0, tabY, Math.max(0, jeiAreaX), Math.max(0, fw.bottomHeight)));

                } else {
                    areas.add(new Rect2i(0, 0, Math.max(0, screen.width), Math.max(0, screen.height)));
                }

                return areas;
            }
        });

        registration.addGhostIngredientHandler(MainframeOverviewScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> @NotNull List<Target<I>> getTargetsTyped(@NotNull MainframeOverviewScreen screen, @NotNull ITypedIngredient<I> ingredient, boolean doStart) {
                List<Target<I>> targets = new ArrayList<>();
                if (ingredient.getIngredient() instanceof ItemStack stack) {
                    for (GuiEventListener child : screen.children()) {
                        if (child instanceof CrafterUIExtension.GhostSlotWidget ghostWidget) {
                            targets.add(new Target<>() {
                                @Override
                                public @NotNull Rect2i getArea() {
                                    return new Rect2i(ghostWidget.getX(), ghostWidget.getY(), ghostWidget.getWidth(), ghostWidget.getHeight());
                                }
                                @Override
                                public void accept(@NotNull I ing) {
                                    ghostWidget.acceptDrop(stack);
                                }
                            });
                        } else if (child instanceof FurnaceUIExtension.TargetSlotWidget targetWidget) {
                            targets.add(new Target<>() {
                                @Override
                                public @NotNull Rect2i getArea() {
                                    return new Rect2i(targetWidget.getX(), targetWidget.getY(), targetWidget.getWidth(), targetWidget.getHeight());
                                }
                                @Override
                                public void accept(@NotNull I ing) {
                                    targetWidget.acceptDrop(stack);
                                }
                            });
                        }
                    }
                }
                return targets;
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(@NotNull IRecipeTransferRegistration registration) {
        registerTransfer(registration, mezz.jei.api.constants.RecipeTypes.CRAFTING, true);
        registerTransfer(registration, mezz.jei.api.constants.RecipeTypes.SMELTING, false);
    }

    private <T> void registerTransfer(IRecipeTransferRegistration reg, mezz.jei.api.recipe.RecipeType<T> type, boolean isCrafting) {
        if (isCrafting) {
            LunexJeiTransferHelper.register(reg, type, CrafterUIExtension.class, (pos, be, recipeSlots, doTransfer, helper) -> {
                if (be.getPersistentData().getBoolean("AutoCraftActive")) {
                    return helper.createUserErrorWithTooltip(Component.literal("Disable Auto-Crafting first"));
                }

                if (doTransfer) {
                    List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
                    for (int i = 0; i < Math.min(9, inputs.size()); i++) {
                        ItemStack stack = inputs.get(i).getDisplayedItemStack().orElse(ItemStack.EMPTY);
                        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_crafter_recipe", i + ":" + itemId));
                    }

                    List<IRecipeSlotView> outputs = recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT);
                    if (!outputs.isEmpty()) {
                        ItemStack stack = outputs.getFirst().getDisplayedItemStack().orElse(ItemStack.EMPTY);
                        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_crafter_recipe", "9:" + itemId));
                    }

                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return null;
            });
        } else {
            LunexJeiTransferHelper.register(reg, type, FurnaceUIExtension.class, (pos, be, recipeSlots, doTransfer, helper) -> {
                if (be.getPersistentData().getBoolean("AutoSmeltActive")) {
                    return helper.createUserErrorWithTooltip(Component.literal("Disable Auto-Smelting first"));
                }

                if (doTransfer) {
                    List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
                    if (!inputs.isEmpty()) {
                        ItemStack stack = inputs.getFirst().getDisplayedItemStack().orElse(ItemStack.EMPTY);
                        String itemId = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_furnace_target", itemId));
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                }
                return null;
            });
        }
    }
}