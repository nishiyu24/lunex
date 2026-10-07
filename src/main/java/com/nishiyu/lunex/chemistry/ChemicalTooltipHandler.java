package com.nishiyu.lunex.chemistry;

import com.nishiyu.lunex.Lunex;
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

            // Lunexの「ARグラス」を頭に装備しているか判定
            boolean wearingARGlasses = player != null && player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Lunex.AR_GLASSES.get();

            if (Screen.hasShiftDown() || wearingARGlasses) {
                event.getToolTip().add(Component.literal("")); // 視認性向上のための空行

                // 元素ごとにフォーマットして表示
                for (Map.Entry<Element, Integer> entry : molecule.getElements().entrySet()) {
                    Element elem = entry.getKey();
                    int amount = entry.getValue();

                    // RGB値からカスタムカラーのスタイルを生成
                    Style elemStyle = Style.EMPTY.withColor(TextColor.fromRgb(elem.getColor()));

                    // デザイン: [ Fe ] 鉄 : 9
                    MutableComponent prefix = Component.literal("[ ").withStyle(ChatFormatting.DARK_GRAY);
                    // 元素記号にだけ細かく設定したRGBカラーを適用する
                    MutableComponent symbol = Component.literal(String.format("%-2s", elem.name())).withStyle(elemStyle);
                    MutableComponent suffix = Component.literal(" ] ").withStyle(ChatFormatting.DARK_GRAY);
                    MutableComponent name = Component.literal(elem.getLocalizedName() + " : ").withStyle(ChatFormatting.GRAY);
                    MutableComponent count = Component.literal(String.valueOf(amount)).withStyle(ChatFormatting.WHITE);

                    event.getToolTip().add(prefix.append(symbol).append(suffix).append(name).append(count));
                }
            } else {
                event.getToolTip().add(Component.literal("▶ 組成を表示[Shift]").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }
}