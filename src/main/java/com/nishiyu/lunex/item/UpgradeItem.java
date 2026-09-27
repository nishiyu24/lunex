package com.nishiyu.lunex.item;

import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class UpgradeItem extends Item {

    private final UpgradeType type;
    private final int level;

    public UpgradeItem(Properties properties, UpgradeType type, int level) {
        super(properties);
        this.type = type;
        this.level = level;
    }

    public UpgradeType getUpgradeType() {
        return this.type;
    }

    public int getLevel() {
        return this.level;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.lunex.upgrade.desc").withStyle(ChatFormatting.GRAY));

            switch (this.type) {
                case EXECUTION ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.execution", this.level).withStyle(ChatFormatting.AQUA));
                case STORAGE ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.storage", this.level).withStyle(ChatFormatting.AQUA));
                case SPEED ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.speed", this.level).withStyle(ChatFormatting.AQUA));
                case EFFICIENCY ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.efficiency", this.level).withStyle(ChatFormatting.AQUA));
                case CAPACITY ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.capacity", this.level).withStyle(ChatFormatting.AQUA));
                case GENERATOR ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.generator", this.level).withStyle(ChatFormatting.AQUA));
                case COMMAND ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.command", this.level).withStyle(ChatFormatting.LIGHT_PURPLE));
                case ROUTER ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.router").withStyle(ChatFormatting.GOLD));
                case DISTANCE ->
                        tooltip.add(Component.translatable("tooltip.lunex.upgrade.distance", this.level).withStyle(ChatFormatting.AQUA));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.lunex.hold_shift").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        super.appendHoverText(stack, context, tooltip, flagIn);
    }

    public enum UpgradeType {
        EXECUTION, STORAGE, SPEED, EFFICIENCY, CAPACITY, GENERATOR, COMMAND, ROUTER, DISTANCE
    }

    // ★修正: 翻訳データを実態（エネルギー受け入れ量の増加）に合わせて変更
    public static class Translations implements ITranslationGatherer {
        @Override
        public void gatherTranslations(AutoLanguageProvider provider, String locale) {
            provider.addTranslation("tooltip.lunex.hold_shift", "<Hold Shift for details>", "<Shiftキーを押して詳細を表示>");
            provider.addTranslation("tooltip.lunex.upgrade.desc", "Insert into a Programmable Machine to enhance its performance.", "プログラマブルマシンに挿入して性能を強化できる。");
            provider.addTranslation("tooltip.lunex.upgrade.execution", "Expands the machine's hotbar capacity. (Mk%s)", "マシンの作業枠を拡張します。(Mk%s)");
            provider.addTranslation("tooltip.lunex.upgrade.storage", "Expands the machine's storage capacity. (Mk%s)", "マシンのストレージ枠を拡張します。(Mk%s)");
            provider.addTranslation("tooltip.lunex.upgrade.speed", "Reduces delay and smelting time. (Mk%s)", "クールタイムと精錬時間を短縮します。(Mk%s)");
            provider.addTranslation("tooltip.lunex.upgrade.efficiency", "Reduces the machine's energy consumption. (Mk%s)", "マシンの動作エネルギー消費量を抑えます。(Mk%s)");
            provider.addTranslation("tooltip.lunex.upgrade.capacity", "Increases the machine's maximum energy capacity. (Mk%s)", "マシンの最大エネルギー貯蔵量を増やします。(Mk%s)");

            // ★GENERATORの翻訳を修正[cite: 15]
            provider.addTranslation("tooltip.lunex.upgrade.generator", "Increases the energy receive rate from external sources. (Mk%s)", "外部からのエネルギー受け入れ量を増やします。(Mk%s)");

            provider.addTranslation("tooltip.lunex.upgrade.command", "Grants the machine god-like privileges, allowing execution of any command. (OP only)", "マシンに神の如き権限を与え、あらゆるコマンドの実行を可能にする。(OP専用)");
            provider.addTranslation("tooltip.lunex.upgrade.router", "Router Module: Assembles the machine as a Router.", "ルーターモジュール: マシンをルーターとしてアセンブルします");
            provider.addTranslation("tooltip.lunex.upgrade.distance", "Distance Module (Mk%s): Expands communication distance and chunk load range.", "距離拡張モジュール (Mk%s): 通信距離とチャンクロード範囲を拡張します");
        }
    }
}