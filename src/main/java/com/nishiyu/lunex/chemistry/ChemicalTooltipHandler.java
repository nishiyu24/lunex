package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.chemistry.model.Compound;
import com.nishiyu.lunex.chemistry.model.Element;
import com.nishiyu.lunex.chemistry.model.Molecule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Map;

@EventBusSubscriber(modid = Lunex.MODID, value = Dist.CLIENT)
public class ChemicalTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        Molecule molecule = ChemicalRegistry.getMolecule(itemId);

        if (molecule != null && !molecule.isEmpty()) {
            Player player = event.getEntity();
            boolean wearingARGlasses = player != null && player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Lunex.AR_GLASSES.get();

            if (Screen.hasShiftDown() || wearingARGlasses) {
                event.getToolTip().add(Component.literal("")); // 視認性のための空行

                // 1. 化合物（鉱物・分子）の表示 (例: - 水 [H₂O] : 5)
                if (!molecule.getCompounds().isEmpty()) {
                    event.getToolTip().add(Component.literal("◆ 構成化合物 / 鉱物:").withStyle(ChatFormatting.AQUA));
                    for (Map.Entry<Compound, Integer> entry : molecule.getCompounds().entrySet()) {
                        Compound comp = entry.getKey();
                        int count = entry.getValue();

                        MutableComponent line = Component.literal("  - " + comp.getLocalizedName() + " ")
                                .withStyle(ChatFormatting.GRAY);

                        // 下付き文字の化学式部分を緑色で強調
                        MutableComponent formulaComp = Component.literal("[" + comp.getFormulaString() + "] ")
                                .withStyle(ChatFormatting.DARK_GREEN);

                        MutableComponent countComp = Component.literal(": " + count)
                                .withStyle(ChatFormatting.WHITE);

                        event.getToolTip().add(line.append(formulaComp).append(countComp));
                    }
                }

                // 2. 元素レベルの表示 (Ctrlキー押下またはARグラス装備時)
                if (Screen.hasControlDown() || wearingARGlasses) {
                    event.getToolTip().add(Component.literal("◆ 総元素内訳:").withStyle(ChatFormatting.YELLOW));
                    for (Map.Entry<Element, Integer> entry : molecule.getElements().entrySet()) {
                        Element elem = entry.getKey();
                        int amount = entry.getValue();

                        Style elemStyle = Style.EMPTY.withColor(TextColor.fromRgb(elem.getColor()));
                        MutableComponent prefix = Component.literal("    [ ").withStyle(ChatFormatting.DARK_GRAY);
                        MutableComponent symbol = Component.literal(String.format("%-2s", elem.name())).withStyle(elemStyle);
                        MutableComponent suffix = Component.literal(" ] ").withStyle(ChatFormatting.DARK_GRAY);
                        MutableComponent name = Component.literal(elem.getLocalizedName() + " : ").withStyle(ChatFormatting.GRAY);
                        MutableComponent count = Component.literal(String.valueOf(amount)).withStyle(ChatFormatting.WHITE);

                        event.getToolTip().add(prefix.append(symbol).append(suffix).append(name).append(count));
                    }
                } else {
                    event.getToolTip().add(Component.literal("  ▶ 元素の詳細[Ctrl]").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
            } else {
                event.getToolTip().add(Component.literal("▶ 化学組成を表示[Shift]").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }
}