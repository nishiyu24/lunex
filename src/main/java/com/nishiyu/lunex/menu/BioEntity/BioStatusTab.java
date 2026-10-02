package com.nishiyu.lunex.menu.BioEntity;

import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.entity.traits.TraitDef;
import com.nishiyu.lunex.entity.traits.TraitRegistry;
import com.nishiyu.lunex.menu.utiles.GuiRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

public class BioStatusTab implements IMachineTab {
    private static final int MAX_VISIBLE = 8;
    private static final int ITEM_HEIGHT = 14;

    // 1.21.1用のGUIスプライト指定
    private static final ResourceLocation HEART_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation ARMOR_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");

    private final BioEntitySettingsScreen screen;
    private final List<TraitDef> currentTraits = new ArrayList<>();
    private int leftPos, topPos;
    private int scrollOffset = 0;

    public BioStatusTab(BioEntitySettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;

        this.currentTraits.clear();
        this.scrollOffset = 0;

        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        if (mob != null) {
            for (String traitKey : mob.traits) {
                for (TraitDef def : TraitRegistry.TRAITS) {
                    if (def.key().equals(traitKey)) {
                        this.currentTraits.add(def);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        if (mob == null) return;

        int startX = this.leftPos + 15;
        int startY = this.topPos + 35;

        // --- ステータス描画 (1.21.1版 横並びアイコン) ---
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Base Status:", startX, startY, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        int statusY = startY + 12;
        int currentX = startX;
        Minecraft mc = Minecraft.getInstance();

        // 1. ハート (Health)
        String hpStr = String.format("%.1f/%.1f", mob.getHealth(), mob.getMaxHealth());
        guiGraphics.blitSprite(HEART_SPRITE, currentX, statusY, 9, 9);
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), hpStr, currentX + 11, statusY + 1, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        currentX += 11 + this.screen.getFont().width(hpStr) + 8;

        // 2. シールド (Armor)
        String defStr = String.format("%.1f", mob.getAttributeValue(Attributes.ARMOR));
        guiGraphics.blitSprite(ARMOR_SPRITE, currentX, statusY, 9, 9);
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), defStr, currentX + 11, statusY + 1, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        currentX += 11 + this.screen.getFont().width(defStr) + 8;

        // 3. 攻撃力 (Attack Damage)
        String atkStr = String.format("%.1f", mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
        TextureAtlasSprite atkSprite = mc.getMobEffectTextures().get(MobEffects.DAMAGE_BOOST);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(currentX, statusY, 0);
        guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
        guiGraphics.blit(0, 0, 0, 18, 18, atkSprite);
        guiGraphics.pose().popPose();
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), atkStr, currentX + 11, statusY + 1, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        currentX += 11 + this.screen.getFont().width(atkStr) + 8;

        // 4. 移動速度 (Speed)
        String spdStr = String.format("%.2f", mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        TextureAtlasSprite spdSprite = mc.getMobEffectTextures().get(MobEffects.MOVEMENT_SPEED);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(currentX, statusY, 0);
        guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
        guiGraphics.blit(0, 0, 0, 18, 18, spdSprite);
        guiGraphics.pose().popPose();
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), spdStr, currentX + 11, statusY + 1, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);


        // --- Trait(特性)リスト描画 ---
        int listY = startY + 28;
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Active Traits (" + this.currentTraits.size() + "):", startX, listY, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
        listY += 12;

        if (this.currentTraits.isEmpty()) {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "No traits found.", startX + 5, listY, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
            return;
        }

        TraitDef hoveredTrait = null;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int index = this.scrollOffset + i;
            if (index >= this.currentTraits.size()) break;

            TraitDef def = this.currentTraits.get(index);
            int yPos = listY + (i * ITEM_HEIGHT);

            // 表示名を Component (翻訳ファイル) 経由で取得するように修正
            String traitText = "◆ " + Component.translatable("trait.lunex." + def.key() + ".name").getString();
            int textWidth = this.screen.getFont().width(traitText);

            boolean isHovered = GuiRenderUtils.isHovered(mouseX, mouseY, startX + 5, yPos, textWidth + 5, ITEM_HEIGHT);
            int textColor = isHovered ? GuiRenderUtils.COLOR_TEXT_PRIMARY : GuiRenderUtils.COLOR_ITEM_SELECTED;

            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), traitText, startX + 5, yPos, textColor, 1.0f);

            if (isHovered) {
                hoveredTrait = def;
            }
        }

        if (hoveredTrait != null) {
            // 説明文も Component (翻訳ファイル) 経由で取得するように修正
            guiGraphics.renderTooltip(this.screen.getFont(), Component.translatable("trait.lunex." + hoveredTrait.key() + ".desc"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, this.currentTraits.size() - MAX_VISIBLE);
        if (scrollY > 0 && this.scrollOffset > 0) {
            this.scrollOffset--;
            return true;
        } else if (scrollY < 0 && this.scrollOffset < maxScroll) {
            this.scrollOffset++;
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public void onClose() {
    }
}