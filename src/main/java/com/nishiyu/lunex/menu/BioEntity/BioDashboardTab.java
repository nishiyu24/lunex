package com.nishiyu.lunex.menu.BioEntity;

import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.menu.utiles.GuiRenderUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public class BioDashboardTab implements IMachineTab {
    private final BioEntitySettingsScreen screen;
    private int leftPos, topPos;

    public BioDashboardTab(BioEntitySettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;

        this.screen.sendCommand("request_bio_info", "");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        if (mob == null) return;

        boolean isMechanical = mob.behaviors.contains("mechanical");

        int col1 = this.leftPos + 15;
        int y = this.topPos + 40;

        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Entity ID: " + mob.getId(), col1, y, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String mobName = mob.hasCustomName() ? Objects.requireNonNull(mob.getCustomName()).getString() : "Biological Construct";
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Name: " + mobName, col1, y + 16, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String healthInfo = String.format("Health: %.1f / %.1f", mob.getHealth(), mob.getMaxHealth());
        GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), healthInfo, col1, y + 36, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

        if (isMechanical) {
            String status = this.screen.getMenu().isRunning() ? "§aRunning§r" : "§cStopped§r";
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Status: " + status, col1, y + 56, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

            // ★ 修正: programNameが空かどうかの判定を正確に行う
            String rawProgram = (mob.programName != null && !mob.programName.isEmpty()) ? mob.programName : "";
            String displayProgram = rawProgram.isEmpty() ? "None" : rawProgram;

            if (this.screen.getMenu().isRunning()) {
                displayProgram = "§a" + displayProgram + " (Running)§r";
            }
            GuiRenderUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Boot Program: " + displayProgram, col1, y + 76, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

            int btnY = this.topPos + 180;
            boolean canBoot = !rawProgram.isEmpty();

            // ★ 修正: プログラムが未設定の時はボタンの色を暗くして無効化をアピール
            if (this.screen.getMenu().isRunning()) {
                GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1, btnY, 55, 16, "■ Stop", false, 1.0f, GuiRenderUtils.COLOR_BTN_WARNING, false);
            } else {
                int startColor = canBoot ? GuiRenderUtils.COLOR_ITEM_SELECTED : GuiRenderUtils.COLOR_BTN_BG;
                GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1, btnY, 55, 16, "▶ Start", false, 1.0f, startColor, false);
            }

            int rebootColor = canBoot ? GuiRenderUtils.COLOR_BTN_WARNING : GuiRenderUtils.COLOR_BTN_BG;
            GuiRenderUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, col1 + 65, btnY, 55, 16, "Reboot", false, 1.0f, rebootColor, false);
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CustomBioMobEntity mob = this.screen.getMenu().getBioMob();
        if (mob != null && mob.behaviors.contains("mechanical")) {
            if (button == 0) {
                int col1 = this.leftPos + 15;
                int btnY = this.topPos + 180;

                // ★ 修正: 起動すべきプログラム名を取得
                String prog = (mob.programName != null && !mob.programName.isEmpty()) ? mob.programName : "";

                if (GuiRenderUtils.isHovered(mouseX, mouseY, col1, btnY, 55, 16)) {
                    if (this.screen.getMenu().isRunning()) {
                        this.screen.sendCommand("stop", "");
                    } else if (!prog.isEmpty()) {
                        // ★ 修正: メモリにロードされていない場合に備え、実行前にファイルを同期してプログラム名を明示的に送る
                        this.screen.sendCommand("sync_files", "");
                        this.screen.sendCommand("boot", prog);
                    }
                    return true;
                }
                if (GuiRenderUtils.isHovered(mouseX, mouseY, col1 + 65, btnY, 55, 16)) {
                    if (!prog.isEmpty()) {
                        this.screen.sendCommand("sync_files", "");
                        this.screen.sendCommand("reboot", prog);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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