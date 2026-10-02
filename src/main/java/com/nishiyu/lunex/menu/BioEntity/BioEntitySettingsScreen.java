package com.nishiyu.lunex.menu.BioEntity;

import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.menu.utiles.GuiRenderUtils;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BioEntitySettingsScreen extends AbstractContainerScreen<BioEntitySettingsMenu> {

    private final List<String> availableTabs = new ArrayList<>();
    private final List<IMachineTab> availableTabInstances = new ArrayList<>();
    private int activeTabIndex = 0;

    private long openTime;

    private IMachineTab currentTabInstance;
    private BioDashboardTab dashboardTab;
    private BioStatusTab statusTab;
    private BioModelTab modelTab;
    private BioFileManagerTab fileManagerTab;
    private BioSettingsTab settingsTab;

    // ★追加: サーバーから取得した情報を保持する変数
    public String lastError = "No recent errors.";
    public boolean isPrivateMode = false;
    public boolean isDebugLog = false;

    public BioEntitySettingsScreen(BioEntitySettingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 340;
        this.imageHeight = 214;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 10000;
        this.inventoryLabelY = 10000;
        this.openTime = System.currentTimeMillis();

        if (this.dashboardTab == null) this.dashboardTab = new BioDashboardTab(this);
        if (this.statusTab == null) this.statusTab = new BioStatusTab(this);
        if (this.modelTab == null) this.modelTab = new BioModelTab(this);
        if (this.fileManagerTab == null) this.fileManagerTab = new BioFileManagerTab(this);
        if (this.settingsTab == null) this.settingsTab = new BioSettingsTab(this);

        this.clearWidgets();

        CustomBioMobEntity mob = this.menu.getBioMob();
        boolean isMechanical = (mob != null && mob.behaviors.contains("mechanical"));
        boolean isShapeshifter = (mob != null && mob.traits.contains("shapeshifter"));

        this.availableTabs.clear();
        this.availableTabInstances.clear();

        this.availableTabs.add("Dashboard");
        this.availableTabInstances.add(this.dashboardTab);

        this.availableTabs.add("Status");
        this.availableTabInstances.add(this.statusTab);

        if (isShapeshifter) {
            this.availableTabs.add("Model");
            this.availableTabInstances.add(this.modelTab);
        }

        if (isMechanical) {
            this.availableTabs.add("Files");
            this.availableTabInstances.add(this.fileManagerTab);
        }

        this.availableTabs.add("Settings");
        this.availableTabInstances.add(this.settingsTab);

        if (this.activeTabIndex >= this.availableTabs.size()) {
            this.activeTabIndex = 0;
        }

        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        switchTabInstance(this.activeTabIndex);

        if (this.currentTabInstance != null) {
            this.currentTabInstance.init(leftPos, topPos);
        }
    }

    private void switchTabInstance(int index) {
        if (this.currentTabInstance != null) this.currentTabInstance.onClose();

        if (index >= 0 && index < this.availableTabInstances.size()) {
            this.currentTabInstance = this.availableTabInstances.get(index);
        }
    }

    public <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> void addWidgetToScreen(T widget) {
        if (!this.renderables.contains(widget)) {
            this.addRenderableWidget(widget);
        }
    }

    public void setScreenFocused(GuiEventListener widget) {
        this.setFocused(widget);
    }

    public void sendCommand(String action, String data) {
        if (this.menu.getBioMob() == null) return;

        CompoundTag tag = new CompoundTag();
        tag.putString("command", action);
        tag.putString("arg", data != null ? data : "");
        tag.putInt("entityId", this.menu.getBioMob().getId());
        PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "bio_mob_command", tag));
    }

    public Font getFont() {
        return this.font;
    }

    public @NotNull BioEntitySettingsMenu getMenu() {
        return this.menu;
    }

    public int getImageWidth() {
        return this.imageWidth;
    }

    public int getImageHeight() {
        return this.imageHeight;
    }

    public void receiveItemFiles(List<String> files) {
        if (this.fileManagerTab != null) this.fileManagerTab.receiveItemFiles(files);
    }

    // ★追加: S2Cパケットで情報を受け取った際に呼び出されるメソッド
    public void receiveBioInfo(CompoundTag tag) {
        if (tag.contains("lastError")) {
            this.lastError = tag.getString("lastError");
        } else {
            this.lastError = "No recent errors.";
        }
        if (tag.contains("PrivateMode")) {
            this.isPrivateMode = tag.getBoolean("PrivateMode");
        }
        if (tag.contains("DebugLog")) {
            this.isDebugLog = tag.getBoolean("DebugLog");
        }
        if (tag.contains("programName")) {
            CustomBioMobEntity mob = this.menu.getBioMob();
            if (mob != null) {
                mob.programName = tag.getString("programName");
            }
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        long elapsed = System.currentTimeMillis() - this.openTime;
        float progress = Math.min(1.0f, elapsed / 250.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 4);

        guiGraphics.pose().pushPose();
        if (ease < 1.0f) {
            guiGraphics.pose().translate(this.width / 2.0f, this.height / 2.0f, 0);
            guiGraphics.pose().scale(0.95f + 0.05f * ease, 0.95f + 0.05f * ease, 1.0f);
            guiGraphics.pose().translate(0, 10 * (1.0f - ease), 0);
            guiGraphics.pose().translate(-this.width / 2.0f, -this.height / 2.0f, 0);
        }

        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, GuiRenderUtils.COLOR_BG_MAIN);
        guiGraphics.renderOutline(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, GuiRenderUtils.COLOR_BORDER);

        int titleWidth = this.font.width("Bio Mob Settings");
        guiGraphics.drawString(this.font, "Bio Mob Settings", this.leftPos + (this.imageWidth - titleWidth) / 2, this.topPos - 12, 0xFFFFFF, false);

        int tabX = this.leftPos + 12;
        int tabY = this.topPos + 10;
        int tabWidth = (this.imageWidth - 24) / this.availableTabs.size();

        for (int i = 0; i < this.availableTabs.size(); i++) {
            boolean isActive = (i == activeTabIndex);
            int color = isActive ? GuiRenderUtils.COLOR_ITEM_SELECTED : 0xFF585B70;
            GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, tabX, tabY, tabWidth - 4, 16, this.availableTabs.get(i), false, 1.0f, color, false);
            tabX += tabWidth;
        }

        if (this.currentTabInstance != null) {
            this.currentTabInstance.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.currentTabInstance != null) {
            this.currentTabInstance.tick();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean isModalOrContext = (this.fileManagerTab != null && (this.fileManagerTab.isShowModal() || this.fileManagerTab.getContextMenu() != null));

        if (!isModalOrContext) {
            int tabX = this.leftPos + 12;
            int tabY = this.topPos + 10;
            int tabWidth = (this.imageWidth - 24) / this.availableTabs.size();

            for (int i = 0; i < this.availableTabs.size(); i++) {
                if (GuiRenderUtils.isHovered(mouseX, mouseY, tabX, tabY, tabWidth - 4, 16) && button == 0) {
                    this.activeTabIndex = i;
                    this.setFocused(null);

                    if (this.currentTabInstance != null) this.currentTabInstance.onClose();
                    switchTabInstance(this.activeTabIndex);
                    if (this.currentTabInstance != null) this.currentTabInstance.init(this.leftPos, this.topPos);

                    return true;
                }
                tabX += tabWidth;
            }
        }

        if (this.currentTabInstance != null && this.currentTabInstance.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.currentTabInstance != null && this.currentTabInstance.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Objects.requireNonNull(this.minecraft).options.keyInventory.matches(keyCode, scanCode)) return true;
        if (this.currentTabInstance != null && this.currentTabInstance.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}