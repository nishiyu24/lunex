package com.nishiyu.lunex.menu.bioprinter;

import com.nishiyu.lunex.entity.BioMobGenerator;
import com.nishiyu.lunex.entity.traits.TraitRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MixingTab extends AbstractPrinterTab {

    private static final ResourceLocation HEART_SPRITE = ResourceLocation.parse("minecraft:hud/heart/full");
    private static final ResourceLocation ARMOR_SPRITE = ResourceLocation.parse("minecraft:hud/armor_full");
    private static final ResourceLocation EFFECT_STRENGTH = ResourceLocation.parse("minecraft:textures/mob_effect/strength.png");
    private static final ResourceLocation EFFECT_SPEED = ResourceLocation.parse("minecraft:textures/mob_effect/speed.png");

    public MixingTab(BioPrinterScreen screen, BioPrinterMenu menu) {
        super(screen, menu);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int leftPos, int topPos) {
        guiGraphics.fill(leftPos + 10, topPos + 30, leftPos + 265, topPos + 125, 0xFF11111B);
        guiGraphics.renderOutline(leftPos + 10, topPos + 30, 255, 95, 0xFF45475A);

        Map<String, Integer> mats = this.menu.blockEntity.getMaterialCounts();
        boolean hasBaseMats = hasRequiredBaseMaterials(mats);

        if (hasBaseMats) {
            // ★ ハードコードの修正
            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.MIXING_STATUS).getString(), leftPos + 15, topPos + 35, 0xFFCDD6F4, false);

            List<String> previewTraits = new ArrayList<>();
            List<Integer> selectedTraits = this.menu.blockEntity.getSelectedTraits();
            for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
                if (selectedTraits.contains(i)) {
                    previewTraits.add(TraitRegistry.TRAITS.get(i).key());
                }
            }

            int successRate = 100 - this.menu.data.get(7);
            int aiCount = this.menu.blockEntity.getSelectedBehaviors().size();
            int maxAi = this.menu.blockEntity.getSelectedBehaviors().contains(19) ? 1 : 3;

            int currentPoints = TraitRegistry.getConsumedPoints(selectedTraits);
            int maxTraits = TraitRegistry.getMaxPoints(mats, selectedTraits);

            // ★ 翻訳用パラメータに対応
            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.SUCCESS_RATE, successRate).getString(), leftPos + 15, topPos + 55, successRate < 50 ? 0xFFF38BA8 : 0xFFA6E3A1, false);
            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.AI_COUNT, aiCount, maxAi).getString(), leftPos + 120, topPos + 55, 0xFFA6ADC8, false);
            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.TRAITS_COUNT, currentPoints, maxTraits).getString(), leftPos + 175, topPos + 55, 0xFFA6ADC8, false);

            double baseHp = 2.0;
            double baseArmor = 0.0;
            double baseAtk = 1.0;
            double baseSpd = 0.5;

            BioMobGenerator.MobStatus finalStatus = BioMobGenerator.calculateStatus(mats, previewTraits);
            double traitHp = finalStatus.maxHealth() - baseHp;
            double traitArmor = finalStatus.armor() - baseArmor;
            double traitAtk = finalStatus.attackDamage() - baseAtk;
            double traitSpd = finalStatus.speed() - baseSpd;

            String hpStr = String.format("%.1f %s", baseHp, formatBonus(traitHp, false));
            String armorStr = String.format("%.1f %s", baseArmor, formatBonus(traitArmor, false));
            String atkStr = String.format("%.1f %s", baseAtk, formatBonus(traitAtk, false));
            String spdStr = String.format("%.3f %s", baseSpd, formatBonus(traitSpd, true));

            drawIconAndText(guiGraphics, HEART_SPRITE, true, leftPos + 15, topPos + 75, hpStr);
            drawIconAndText(guiGraphics, ARMOR_SPRITE, true, leftPos + 140, topPos + 75, armorStr);
            drawIconAndText(guiGraphics, EFFECT_STRENGTH, false, leftPos + 15, topPos + 95, atkStr);
            drawIconAndText(guiGraphics, EFFECT_SPEED, false, leftPos + 140, topPos + 95, spdStr);

        } else {
            // ★ ハードコードの修正
            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.NEED_BASE_MATS).getString(), leftPos + 15, topPos + 35, 0xFFF38BA8, false);
            int bones = mats == null ? 0 : BioMobGenerator.getCount(mats, Items.BONE);
            int meats = mats == null ? 0 : (BioMobGenerator.getCount(mats, Items.ROTTEN_FLESH) + BioMobGenerator.getCount(mats, Items.BEEF) + BioMobGenerator.getCount(mats, Items.PORKCHOP) + BioMobGenerator.getCount(mats, Items.CHICKEN) + BioMobGenerator.getCount(mats, Items.MUTTON) + BioMobGenerator.getCount(mats, Items.RABBIT));

            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.BONE_COUNT, bones).getString(), leftPos + 20, topPos + 55, bones >= 5 ? 0xFFA6E3A1 : 0xFFF38BA8, false);
            guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.MEAT_COUNT, meats).getString(), leftPos + 20, topPos + 70, meats >= 5 ? 0xFFA6E3A1 : 0xFFF38BA8, false);
        }

        guiGraphics.fill(leftPos + 10, topPos + 135, leftPos + 174, topPos + 213, 0xFF11111B);
        guiGraphics.renderOutline(leftPos + 10, topPos + 134, 164, 78, 0xFF45475A);
        guiGraphics.fill(leftPos + 10, topPos + 191, leftPos + 174, topPos + 192, 0xFF45475A);
        guiGraphics.fill(leftPos + 215, topPos + 141, leftPos + 233, topPos + 159, 0xFF11111B);
        guiGraphics.renderOutline(leftPos + 215, topPos + 141, 18, 18, 0xFF45475A);

        int energy = this.menu.data.get(0);
        int currentTotal = this.menu.data.get(1);
        int maxMaterials = this.menu.data.get(2);
        boolean canCreate = (currentTotal >= maxMaterials && currentTotal > 0) && (energy >= 100000) && hasBaseMats;
        boolean canExtract = currentTotal > 0;

        // ★ ボタン名の翻訳対応
        screen.drawTabBtn(guiGraphics, mouseX, mouseY, leftPos + 192, topPos + 174, 64, 16, Component.translatable(BioPrinterTranslations.BTN_CREATE).getString(), canCreate ? 0xFF89B4FA : 0xFF313244, canCreate);
        screen.drawTabBtn(guiGraphics, mouseX, mouseY, leftPos + 192, topPos + 196, 64, 16, Component.translatable(BioPrinterTranslations.BTN_EXTRACT).getString(), canExtract ? 0xFFE0AF68 : 0xFF313244, canExtract);
    }

    private String formatBonus(double val, boolean isSpeed) {
        String format = isSpeed ? "%.3f" : "%.1f";
        if (val >= 0) {
            return "+ " + String.format(format, val);
        }
        return "- " + String.format(format, Math.abs(val));
    }

    private void drawIconAndText(GuiGraphics guiGraphics, ResourceLocation icon, boolean isSprite, int x, int y, String text) {
        if (isSprite) {
            guiGraphics.blitSprite(icon, x, y, 9, 9);
        } else {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            guiGraphics.pose().scale(0.5f, 0.5f, 1.0f);
            guiGraphics.blit(icon, 0, 0, 0, 0, 18, 18, 18, 18);
            guiGraphics.pose().popPose();
        }
        guiGraphics.drawString(screen.getFont(), text, x + 12, y + 1, 0xFFA6ADC8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int leftPos, int topPos) {
        if (button == 0) {
            int energy = this.menu.data.get(0);
            int currentTotal = this.menu.data.get(1);
            int maxMaterials = this.menu.data.get(2);
            Map<String, Integer> mats = this.menu.blockEntity.getMaterialCounts();
            boolean hasBaseMats = hasRequiredBaseMaterials(mats);

            boolean canCreate = (currentTotal >= maxMaterials && currentTotal > 0) && (energy >= 100000) && hasBaseMats;
            boolean canExtract = currentTotal > 0;

            if (canCreate && screen.isHovered(mouseX, mouseY, leftPos + 192, topPos + 174, 64, 16)) {
                screen.handleButtonClick(0);
                return true;
            }
            if (canExtract && screen.isHovered(mouseX, mouseY, leftPos + 192, topPos + 196, 64, 16)) {
                screen.handleButtonClick(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int leftPos, int topPos) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}