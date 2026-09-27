package com.nishiyu.lunex.menu.BioEntity;

import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.menu.MachineSettings.GuiRenderUtils;
import com.nishiyu.lunex.menu.utiles.IMachineTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public class BioModelTab implements IMachineTab {
    private static final int MAX_VISIBLE = 9;
    private final BioEntitySettingsScreen screen;
    private final List<String> availableAppearances = new ArrayList<>();
    private final List<String> availableSkins = new ArrayList<>();
    private int leftPos, topPos;
    private int scrollOffset = 0;
    private String errorMessage = "";
    private int errorTick = 0;

    private int skinIndex = 0;
    private String currentSkin = "default";

    private String skinSetButtonText = "Set";
    private int skinSetTick = 0;

    public BioModelTab(BioEntitySettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
        this.scrollOffset = 0;

        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();

        // Appearance(モデル)のロード
        this.availableAppearances.clear();
        this.availableAppearances.add("default");
        this.availableAppearances.add("zombie");
        this.availableAppearances.add("skeleton");

        if (ModList.get().isLoaded("geckolib")) {
            net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .listResources("geo", loc -> loc.getPath().endsWith(".geo.json"))
                    .forEach((loc, res) -> {
                        if (loc.getNamespace().equals("lunex")) {
                            String name = loc.getPath().replace("geo/", "").replace(".geo.json", "");
                            this.availableAppearances.add("gecko:" + name);
                        }
                    });
        }

        // スキンのロード
        this.availableSkins.clear();
        this.availableSkins.addAll(com.nishiyu.lunex.util.SkinLoader.getAvailableSkins());

        // 現在設定されているスキンを取得して保持
        this.currentSkin = (mob != null && mob.getCustomSkinName() != null) ? mob.getCustomSkinName() : "default";
        this.skinIndex = this.availableSkins.indexOf(this.currentSkin);
        if (this.skinIndex == -1) this.skinIndex = 0;
    }

    @Override
    public void tick() {
        if (this.errorTick > 0) {
            this.errorTick--;
            if (this.errorTick == 0) this.errorMessage = "";
        }
        if (this.skinSetTick > 0) {
            this.skinSetTick--;
            if (this.skinSetTick == 0) this.skinSetButtonText = "Set";
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        String currentApp = (mob != null && mob.getAppearance() != null && !mob.getAppearance().isEmpty()) ? mob.getAppearance() : "default";

        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Select Appearance:", this.leftPos + 15, this.topPos + 35, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

        // 1. モデルリストの描画
        int listX = this.leftPos + 15;
        int listY = this.topPos + 48;
        int itemWidth = 130;
        int itemHeight = 16;

        guiGraphics.fill(listX - 1, listY - 1, listX + itemWidth + 1, listY + (MAX_VISIBLE * itemHeight) + 1, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(listX, listY, listX + itemWidth, listY + (MAX_VISIBLE * itemHeight), GuiRenderUtils.COLOR_BG_MAIN);

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = this.scrollOffset + i;
            if (idx >= this.availableAppearances.size()) break;

            String app = this.availableAppearances.get(idx);
            int itemY = listY + (i * itemHeight);

            boolean isSelected = app.equals(currentApp);
            boolean isHovered = GuiRenderUtils.isHovered(mouseX, mouseY, listX, itemY, itemWidth, itemHeight);

            int bgColor = isSelected ? GuiRenderUtils.COLOR_ITEM_SELECTED : (isHovered ? GuiRenderUtils.COLOR_ITEM_HOVER : 0x00000000);
            int textColor = isSelected ? GuiRenderUtils.COLOR_TEXT_DARK : GuiRenderUtils.COLOR_TEXT_PRIMARY;

            if (bgColor != 0x00000000) {
                guiGraphics.fill(listX, itemY, listX + itemWidth, itemY + itemHeight, bgColor);
            }

            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), app, listX + 5, itemY + 4, textColor, 1.0f);
        }

        // 2. プレビューとスキンの描画
        if (mob != null) {
            int previewLeft = this.leftPos + 155;
            int previewTop = this.topPos + 48;
            int previewRight = this.leftPos + 325;
            int previewBottom = "default".equals(currentApp) ? this.topPos + 172 : this.topPos + 192;

            guiGraphics.fill(previewLeft, previewTop, previewRight, previewBottom, 0x33000000);
            guiGraphics.renderOutline(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop, GuiRenderUtils.COLOR_BORDER);

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    guiGraphics,
                    previewLeft, previewTop,
                    previewRight, previewBottom,
                    45,
                    0.0f,
                    (float) mouseX, (float) mouseY,
                    mob
            );

            // スティーブ(default)時のみスキン選択UIを表示
            if ("default".equals(currentApp) && !this.availableSkins.isEmpty()) {
                int skinY = this.topPos + 180;
                int skinX = this.leftPos + 155;
                GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Skin:", skinX, skinY + 4, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

                GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, skinX + 25, skinY, 14, 16, "<", false, 1.0f, GuiRenderUtils.COLOR_BTN_BG, false);

                int nameWidth = 80;
                guiGraphics.fill(skinX + 41, skinY, skinX + 41 + nameWidth, skinY + 16, GuiRenderUtils.COLOR_BG_MAIN);
                guiGraphics.renderOutline(skinX + 41, skinY, nameWidth, 16, GuiRenderUtils.COLOR_BORDER);

                String dispSkin = this.availableSkins.get(this.skinIndex);
                String truncatedSkin = this.screen.getFont().plainSubstrByWidth(dispSkin, nameWidth - 8);
                guiGraphics.drawString(this.screen.getFont(), truncatedSkin, skinX + 45, skinY + 4, GuiRenderUtils.COLOR_TEXT_PRIMARY, false);

                GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, skinX + 43 + nameWidth, skinY, 14, 16, ">", false, 1.0f, GuiRenderUtils.COLOR_BTN_BG, false);

                int btnColor = this.skinSetButtonText.equals("OK") ? GuiRenderUtils.COLOR_ITEM_SELECTED : GuiRenderUtils.COLOR_BTN_BG;
                GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, skinX + 59 + nameWidth, skinY, 28, 16, this.skinSetButtonText, false, 1.0f, btnColor, false);
            }
        }

        if (!this.errorMessage.isEmpty()) {
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), this.errorMessage, this.leftPos + 15, this.topPos + 198, 0xFFFF5555, 1.0f);
        }
    }

    private void applyAppearance(String app, CustomBioMobEntity mob) {
        if (app.isEmpty()) app = "default";

        if (app.startsWith("gecko:") && !ModList.get().isLoaded("geckolib")) {
            this.errorMessage = "GeckoLib is NOT installed!";
            this.errorTick = 60;
            return;
        }

        if (mob != null && net.minecraft.client.Minecraft.getInstance().level != null) {
            net.minecraft.world.entity.Entity realMob = net.minecraft.client.Minecraft.getInstance().level.getEntity(mob.getId());
            if (realMob instanceof CustomBioMobEntity customMob) {
                customMob.setAppearance(app);
            }
            mob.setAppearance(app);
        }

        this.screen.sendCommand("appearance", app);
    }

    private void applySkin(String skinName, CustomBioMobEntity mob) {
        // サーバーへ送信（実際の確定処理）
        this.screen.sendCommand("skin", skinName);

        if (mob != null) {
            mob.setCustomSkinName(skinName);
            // 実際のワールド上のMobにも適用
            if (net.minecraft.client.Minecraft.getInstance().level != null) {
                net.minecraft.world.entity.Entity realMob = net.minecraft.client.Minecraft.getInstance().level.getEntity(mob.getId());
                if (realMob instanceof CustomBioMobEntity customMob) {
                    customMob.setCustomSkinName(skinName);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = this.leftPos + 15;
            int listY = this.topPos + 48;
            int itemWidth = 130;
            int itemHeight = 16;
            CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
            String currentApp = (mob != null && mob.getAppearance() != null) ? mob.getAppearance() : "default";

            for (int i = 0; i < MAX_VISIBLE; i++) {
                int idx = this.scrollOffset + i;
                if (idx >= this.availableAppearances.size()) break;

                int itemY = listY + (i * itemHeight);
                if (GuiRenderUtils.isHovered(mouseX, mouseY, listX, itemY, itemWidth, itemHeight)) {
                    String selectedApp = this.availableAppearances.get(idx);
                    applyAppearance(selectedApp, mob);
                    return true;
                }
            }

            if ("default".equals(currentApp) && !this.availableSkins.isEmpty()) {
                int skinY = this.topPos + 180;
                int skinX = this.leftPos + 155;
                int nameWidth = 80;

                // ★修正: < ボタン (押した瞬間にプレビューのみ変更)
                if (GuiRenderUtils.isHovered(mouseX, mouseY, skinX + 25, skinY, 14, 16)) {
                    this.skinIndex--;
                    if (this.skinIndex < 0) this.skinIndex = this.availableSkins.size() - 1;
                    if (mob != null) mob.setCustomSkinName(this.availableSkins.get(this.skinIndex));
                    return true;
                }

                // ★修正: > ボタン (押した瞬間にプレビューのみ変更)
                if (GuiRenderUtils.isHovered(mouseX, mouseY, skinX + 43 + nameWidth, skinY, 14, 16)) {
                    this.skinIndex++;
                    if (this.skinIndex >= this.availableSkins.size()) this.skinIndex = 0;
                    if (mob != null) mob.setCustomSkinName(this.availableSkins.get(this.skinIndex));
                    return true;
                }

                // Set ボタン (プレビュー中の見た目をサーバーに確定送信)
                if (GuiRenderUtils.isHovered(mouseX, mouseY, skinX + 59 + nameWidth, skinY, 28, 16)) {
                    this.currentSkin = this.availableSkins.get(this.skinIndex);
                    applySkin(this.currentSkin, mob);
                    this.skinSetButtonText = "OK";
                    this.skinSetTick = 20;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, this.availableAppearances.size() - MAX_VISIBLE);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public void onClose() {
        // ★追加: Setを押さずにGUIを閉じたり別タブに切り替えた場合、元のスキンに戻す
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        if (mob != null) {
            mob.setCustomSkinName(this.currentSkin);
        }
    }
}