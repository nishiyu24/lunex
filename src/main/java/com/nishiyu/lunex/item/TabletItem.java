package com.nishiyu.lunex.item;

import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class TabletItem extends Item {

    @Translatable(en = "Right-click Target Machine: Open BIOS (Settings Menu)", ja = "対象のマシンを右クリック: BIOS(設定メニュー)を開く")
    public static final String TOOLTIP_RIGHT_CLICK = "tooltip.lunex.tablet.right_click";

    public TabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable(TOOLTIP_RIGHT_CLICK).withStyle(ChatFormatting.GRAY));
    }

    public static class Translations implements ITranslationGatherer {
        @Override
        public void gatherTranslations(AutoLanguageProvider provider, String locale) {
            provider.addTranslation(TOOLTIP_RIGHT_CLICK, "Right-click Target Machine: Open BIOS (Settings Menu)", "対象のマシンを右クリック: BIOS(設定メニュー)を開く");
        }
    }
}