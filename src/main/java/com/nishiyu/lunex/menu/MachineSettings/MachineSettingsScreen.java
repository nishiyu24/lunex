package com.nishiyu.lunex.menu.MachineSettings;

import com.nishiyu.lunex.machine.IMachineContext;
import com.nishiyu.lunex.menu.utiles.IMachineTab;
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

import java.util.List;
import java.util.Objects;

public class MachineSettingsScreen extends AbstractContainerScreen<MachineSettingsMenu> {
    private final String[] topTabs = {"Dashboard", "Files", "Upgrades", "Settings"};
    private int activeTabIndex = 0;

    private long openTime;

    private IMachineTab currentTabInstance;
    private DashboardTab dashboardTab;
    private FileManagerTab fileManagerTab;
    private UpgradesTab upgradesTab;
    private SettingsTab settingsTab;

    public MachineSettingsScreen(MachineSettingsMenu menu, Inventory playerInventory, Component title) {
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

        if (this.dashboardTab == null) this.dashboardTab = new DashboardTab(this);
        if (this.fileManagerTab == null) this.fileManagerTab = new FileManagerTab(this);
        if (this.upgradesTab == null) this.upgradesTab = new UpgradesTab(this);
        if (this.settingsTab == null) this.settingsTab = new SettingsTab(this);

        this.clearWidgets();

        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        switchTabInstance(this.activeTabIndex);

        if (this.currentTabInstance != null) {
            this.currentTabInstance.init(leftPos, topPos);
        }
    }

    private void switchTabInstance(int index) {
        if (this.currentTabInstance != null) this.currentTabInstance.onClose();

        switch (index) {
            case 0 -> this.currentTabInstance = this.dashboardTab;
            case 1 -> this.currentTabInstance = this.fileManagerTab;
            case 2 -> this.currentTabInstance = this.upgradesTab;
            case 3 -> this.currentTabInstance = this.settingsTab;
        }
    }

    public <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> void addWidgetToScreen(T widget) {
        if (!this.renderables.contains(widget)) {
            this.addRenderableWidget(widget);
        }
    }

    public void setScreenFocused(GuiEventListener widget) {
        this.setFocused(widget);
    }

    public void sendCommand(String action, String data) {
        IMachineContext ctx = this.menu.getMachineContext();
        CompoundTag tag = new CompoundTag();
        tag.putString("command", action);
        tag.putString("arg", data != null ? data : "");

        if (ctx.isBlock() && ctx.getPos() != null) {
            tag.putLong("pos", ctx.getPos().asLong());
            PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "machine_command", tag));
        } else {
            PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "item_machine_command", tag));
        }
    }

    public IMachineContext getMachineContext() {
        return this.menu.getMachineContext();
    }

    public Font getFont() {
        return this.font;
    }

    public @NotNull MachineSettingsMenu getMenu() {
        return this.menu;
    }

    public int getImageWidth() {
        return this.imageWidth;
    }

    public int getImageHeight() {
        return this.imageHeight;
    }

    public void receiveFileContent(String content) {
        // 外部エディタを使用するため、GUI内ではテキストのセットを行いません
    }

    public void receiveItemFiles(List<String> files) {
        if (this.fileManagerTab != null) this.fileManagerTab.receiveItemFiles(files);
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

        // ★変更：全画面エディタが廃止されたため、常にGUIパーツを描画
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, GuiRenderUtils.COLOR_BG_MAIN);
        guiGraphics.renderOutline(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, GuiRenderUtils.COLOR_BORDER);

        int titleWidth = this.font.width("Machine Settings");
        guiGraphics.drawString(this.font, "Machine Settings", this.leftPos + (this.imageWidth - titleWidth) / 2, this.topPos - 12, 0xFFFFFF, false);

        int tabX = this.leftPos + 12;
        int tabY = this.topPos + 10;
        for (int i = 0; i < topTabs.length; i++) {
            boolean isActive = (i == activeTabIndex);
            int color = isActive ? GuiRenderUtils.COLOR_ITEM_SELECTED : 0xFF585B70;
            GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, tabX, tabY, 76, 16, topTabs[i], false, 1.0f, color, false);
            tabX += 80;
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
            for (int i = 0; i < topTabs.length; i++) {
                if (GuiRenderUtils.isHovered(mouseX, mouseY, tabX, tabY, 76, 16) && button == 0) {
                    this.activeTabIndex = i;
                    this.setFocused(null);

                    if (this.currentTabInstance != null) this.currentTabInstance.onClose();
                    switchTabInstance(this.activeTabIndex);
                    if (this.currentTabInstance != null) this.currentTabInstance.init(this.leftPos, this.topPos);

                    return true;
                }
                tabX += 80;
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