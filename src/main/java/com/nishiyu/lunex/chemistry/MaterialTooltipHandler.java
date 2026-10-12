package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.chemistry.model.BaseInk;
import com.nishiyu.lunex.chemistry.model.InkGene;
import com.nishiyu.lunex.chemistry.model.ItemDNA;
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

import java.util.EnumMap;
import java.util.Map;

@EventBusSubscriber(modid = Lunex.MODID, value = Dist.CLIENT)
public class MaterialTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        if (itemId.getNamespace().equals(Lunex.MODID)) {
            return;
        }

        ItemDNA dna = MaterialRegistry.getDNA(itemId);

        if (dna != null && !dna.isEmpty()) {
            Player player = event.getEntity();
            boolean wearingARGlasses = player != null && player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Lunex.AR_GLASSES.get();

            if (Screen.hasShiftDown() || wearingARGlasses) {
                event.getToolTip().add(Component.literal(""));

                // --- 1. 構成インクの合計数を表示 ---
                event.getToolTip().add(Component.literal("◆ 構成インク要素:").withStyle(ChatFormatting.AQUA));

                // DNA配列内にあるインクの種類ごとの数を集計
                Map<BaseInk, Integer> inkCounts = new EnumMap<>(BaseInk.class);
                for (int i = 0; i < ItemDNA.DNA_LENGTH; i++) {
                    InkGene gene = dna.getGene(i);
                    if (!gene.isEmpty()) {
                        inkCounts.put(gene.ink(), inkCounts.getOrDefault(gene.ink(), 0) + 1);
                    }
                }

                // 集計結果を表示
                for (Map.Entry<BaseInk, Integer> entry : inkCounts.entrySet()) {
                    BaseInk ink = entry.getKey();
                    int count = entry.getValue();

                    Style inkStyle = Style.EMPTY.withColor(TextColor.fromRgb(ink.getColor()));
                    MutableComponent inkName = Component.literal("  - " + ink.getLocalizedName()).withStyle(inkStyle);
                    MutableComponent countInfo = Component.literal(" : " + count).withStyle(ChatFormatting.WHITE);

                    event.getToolTip().add(inkName.append(countInfo));
                }

                // --- 2. 詳細なDNA配列（状態含む）の表示 (Ctrlキー押下またはARグラス装備時) ---
                if (Screen.hasControlDown() || wearingARGlasses) {
                    event.getToolTip().add(Component.literal("◆ 詳細DNA配列:").withStyle(ChatFormatting.YELLOW));

                    for (int i = 0; i < ItemDNA.DNA_LENGTH; i++) {
                        InkGene gene = dna.getGene(i);

                        if (gene.isEmpty()) {
                            event.getToolTip().add(Component.literal("  [" + (i + 1) + "] - 空枠 -").withStyle(ChatFormatting.DARK_GRAY));
                        } else {
                            BaseInk ink = gene.ink();
                            Style inkStyle = Style.EMPTY.withColor(TextColor.fromRgb(ink.getColor()));

                            MutableComponent index = Component.literal("  [" + (i + 1) + "] ").withStyle(ChatFormatting.GRAY);
                            MutableComponent inkName = Component.literal(ink.getLocalizedName()).withStyle(inkStyle);
                            MutableComponent stateInfo = Component.literal(" (" + gene.state().getLocalizedName() + ")").withStyle(ChatFormatting.WHITE);

                            event.getToolTip().add(index.append(inkName).append(stateInfo));
                        }
                    }
                } else {
                    event.getToolTip().add(Component.literal("  ▶ 状態と配列の詳細[Ctrl]").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
            } else {
                event.getToolTip().add(Component.literal("▶ インク要素を表示[Shift]").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }
}