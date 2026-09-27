package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.menu.MachineSettings.GuiRenderUtils;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RouterDashboardScreen extends AbstractContainerScreen<RouterDashboardMenu> {

    private final String[] topTabs = {"Dashboard", "Network", "Settings"};
    private int currentTab = 0;

    private EditBox labelInput;
    private EditBox selectedIpInput;
    private EditBox selectedPortInput;

    private int syncTimer = 0;
    private String selectedMac = "";
    private int networkScrollOffset = 0;

    private String labelSetButtonText = "Set";
    private int labelSetTick = 0;

    private final List<DhcpEntry> currentLeases = new ArrayList<>();
    private long openTime;

    public RouterDashboardScreen(RouterDashboardMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 340;
        this.imageHeight = 214;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = 10000;
        this.titleLabelX = 10000;
        this.openTime = System.currentTimeMillis();

        this.labelInput = new EditBox(this.font, this.leftPos + 18, this.topPos + 52, 100, 10, Component.literal("Machine Label"));
        this.labelInput.setMaxLength(30);
        this.labelInput.setBordered(false);
        this.labelInput.setTextColor(GuiRenderUtils.COLOR_TEXT_PRIMARY);
        this.addRenderableWidget(this.labelInput);

        this.selectedIpInput = new EditBox(this.font, this.leftPos + 60, this.topPos + 175, 90, 10, Component.literal("IP Address"));
        this.selectedIpInput.setMaxLength(15);
        this.selectedIpInput.setBordered(false);
        this.selectedIpInput.setTextColor(GuiRenderUtils.COLOR_TEXT_PRIMARY);
        this.addRenderableWidget(this.selectedIpInput);

        this.selectedPortInput = new EditBox(this.font, this.leftPos + 190, this.topPos + 175, 40, 10, Component.literal("Port"));
        this.selectedPortInput.setMaxLength(5);
        this.selectedPortInput.setBordered(false);
        this.selectedPortInput.setTextColor(GuiRenderUtils.COLOR_TEXT_PRIMARY);
        this.addRenderableWidget(this.selectedPortInput);

        if (this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.pos);
            if (be instanceof RouterBlockEntity router) {
                this.labelInput.setValue(router.getMachineLabel() != null ? router.getMachineLabel() : "");
            }
        }
        updateTabVisibility();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.syncTimer++;
        if (this.syncTimer % 20 == 0) {
            sendCommand("sync_router", "");
        }
        if (this.labelSetTick > 0) {
            this.labelSetTick--;
            if (this.labelSetTick == 0) this.labelSetButtonText = "Set";
        }
    }

    private void updateTabVisibility() {
        this.labelInput.setVisible(this.currentTab == 2);
        this.selectedIpInput.setVisible(this.currentTab == 1);
        this.selectedPortInput.setVisible(this.currentTab == 1);
    }

    private void updateLeasesList() {
        this.currentLeases.clear();
        if (this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.pos);
            if (be instanceof RouterBlockEntity router) {
                if (router.persistentData.contains("DHCPLeases")) {
                    CompoundTag leases = router.persistentData.getCompound("DHCPLeases");
                    CompoundTag pfTag = router.persistentData.contains("PortForwards") ? router.persistentData.getCompound("PortForwards") : new CompoundTag();

                    for (String mac : leases.getAllKeys()) {
                        String ip = leases.getString(mac);
                        String port = "";
                        for (String p : pfTag.getAllKeys()) {
                            if (pfTag.getString(p).equals(ip)) {
                                port = p;
                                break;
                            }
                        }

                        String displayMac = mac;
                        String[] parts = mac.split(",");
                        if (parts.length == 3) {
                            try {
                                int cx = Integer.parseInt(parts[0].trim());
                                int cy = Integer.parseInt(parts[1].trim());
                                int cz = Integer.parseInt(parts[2].trim());
                                net.minecraft.core.BlockPos clientPos = new net.minecraft.core.BlockPos(cx, cy, cz);
                                BlockEntity clientBe = this.minecraft.level.getBlockEntity(clientPos);

                                if (clientBe instanceof com.nishiyu.lunex.machine.IMachineContext ctx) {
                                    String label = ctx.getMachineLabel();
                                    if (label != null && !label.isEmpty()) {
                                        displayMac += " (" + label + ")";
                                    } else {
                                        displayMac += " (" + clientBe.getBlockState().getBlock().getName().getString() + ")";
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        this.currentLeases.add(new DhcpEntry(mac, ip, port, displayMac));
                    }
                }
            }
        }
    }

    private void sendCommand(String action, String data) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", this.menu.pos.asLong());
        tag.putString("command", action);
        tag.putString("arg", data != null ? data : "");
        PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "machine_command", tag));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
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

        int titleWidth = this.font.width("Router Settings");
        guiGraphics.drawString(this.font, "Router Settings", this.leftPos + (this.imageWidth - titleWidth) / 2, this.topPos - 12, 0xFFFFFF, false);

        int tabX = this.leftPos + 12;
        int tabY = this.topPos + 10;
        for (int i = 0; i < topTabs.length; i++) {
            boolean isActive = (i == currentTab);
            int color = isActive ? GuiRenderUtils.COLOR_ITEM_SELECTED : 0xFF585B70;
            GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, tabX, tabY, 76, 16, topTabs[i], false, 1.0f, color, false);
            tabX += 80;
        }

        RouterBlockEntity router = null;
        if (this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.pos);
            if (be instanceof RouterBlockEntity r) router = r;
        }

        if (router != null) {
            if (this.currentTab == 0) {
                renderDashboard(guiGraphics, mouseX, mouseY, router);
            } else if (this.currentTab == 1) {
                renderNetwork(guiGraphics, mouseX, mouseY, router);
            } else if (this.currentTab == 2) {
                renderSettings(guiGraphics, mouseX, mouseY, router);
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    private void renderDashboard(GuiGraphics guiGraphics, int mouseX, int mouseY, RouterBlockEntity router) {
        int col1 = this.leftPos + 15;
        int col2 = this.leftPos + 180;
        int y = this.topPos + 40;

        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Machine ID: #" + Math.abs(this.menu.pos.hashCode() % 10000), col1, y, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Pos: X=" + this.menu.pos.getX() + " Y=" + this.menu.pos.getY() + " Z=" + this.menu.pos.getZ(), col1, y + 16, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String lanIp = router.persistentData.contains("IPAddress") ? router.persistentData.getString("IPAddress") : "N/A";
        String wanIp = router.getWanIp();
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "LAN IP: " + lanIp, col1, y + 32, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "WAN IP: " + wanIp, col1, y + 48, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        String status = router.isRunning() ? "§aRunning§r" : "§cStopped§r";
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Status: " + status, col1, y + 64, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        int distLv = router.getDistanceUpgradeLevel();
        String distDesc = (distLv >= 4) ? "Cross Dimension" : (distLv == 3) ? "Same Dimension" : (distLv == 2) ? "1024m" : (distLv == 1) ? "256m" : "64m";
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Range: " + distDesc, col2, y, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

        int btnY = this.topPos + 180;
        if (router.isRunning()) {
            GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, col1, btnY, 55, 16, "■ Stop", false, 1.0f, GuiRenderUtils.COLOR_BTN_WARNING, false);
        } else {
            GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, col1, btnY, 55, 16, "▶ Start", false, 1.0f, GuiRenderUtils.COLOR_ITEM_SELECTED, false);
        }
        GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, col1 + 65, btnY, 55, 16, "Reboot", false, 1.0f, GuiRenderUtils.COLOR_BTN_WARNING, false);
    }

    private void renderNetwork(GuiGraphics guiGraphics, int mouseX, int mouseY, RouterBlockEntity router) {
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "DHCP Leases / Port Forwarding", this.leftPos + 15, this.topPos + 35, GuiRenderUtils.COLOR_TEXT_PRIMARY, 1.0f);

        int listX = this.leftPos + 14;
        int listY = this.topPos + 50;
        guiGraphics.fill(listX - 1, listY - 1, listX + 313, listY + 111, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(listX, listY, listX + 312, listY + 110, GuiRenderUtils.COLOR_BG_SIDEBAR);
        guiGraphics.fill(listX, listY, listX + 312, listY + 14, GuiRenderUtils.COLOR_BG_HEADER);

        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "MAC Address", listX + 5, listY + 4, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "IP Address", listX + 130, listY + 4, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Port", listX + 250, listY + 4, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);

        updateLeasesList();
        int itemY = listY + 15;
        for (int i = 0; i < 6; i++) {
            int index = this.networkScrollOffset + i;
            if (index >= this.currentLeases.size()) break;
            DhcpEntry entry = this.currentLeases.get(index);

            boolean isSelected = entry.mac().equals(this.selectedMac);
            boolean isHover = GuiRenderUtils.isHovered(mouseX, mouseY, listX, itemY, 312, 14);

            if (isSelected) {
                guiGraphics.fill(listX, itemY, listX + 312, itemY + 14, GuiRenderUtils.COLOR_ITEM_HOVER);
            } else if (isHover) {
                guiGraphics.fill(listX, itemY, listX + 312, itemY + 14, 0xFF222233);
            }

            String macDisp = entry.displayMac();
            if (this.font.width(macDisp) > 115) {
                macDisp = this.font.plainSubstrByWidth(macDisp, 110) + "..";
            }

            GuiRenderUtils.drawScaledString(guiGraphics, this.font, macDisp, listX + 5, itemY + 3, isSelected ? GuiRenderUtils.COLOR_TEXT_PRIMARY : GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
            GuiRenderUtils.drawScaledString(guiGraphics, this.font, entry.ip(), listX + 130, itemY + 3, isSelected ? GuiRenderUtils.COLOR_TEXT_PRIMARY : GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
            GuiRenderUtils.drawScaledString(guiGraphics, this.font, entry.port().isEmpty() ? "-" : entry.port(), listX + 250, itemY + 3, isSelected ? GuiRenderUtils.COLOR_TEXT_PRIMARY : GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
            itemY += 14;
        }

        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "IP Address:", this.leftPos + 15, this.topPos + 176, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        guiGraphics.fill(this.leftPos + 59, this.topPos + 172, this.leftPos + 151, this.topPos + 188, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(this.leftPos + 60, this.topPos + 173, this.leftPos + 150, this.topPos + 187, GuiRenderUtils.COLOR_BG_MAIN);
        this.selectedIpInput.render(guiGraphics, mouseX, mouseY, 0);

        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Port:", this.leftPos + 160, this.topPos + 176, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        guiGraphics.fill(this.leftPos + 189, this.topPos + 172, this.leftPos + 231, this.topPos + 188, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(this.leftPos + 190, this.topPos + 173, this.leftPos + 230, this.topPos + 187, GuiRenderUtils.COLOR_BG_MAIN);
        this.selectedPortInput.render(guiGraphics, mouseX, mouseY, 0);

        GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, this.leftPos + 240, this.topPos + 172, 45, 16, "Update", this.selectedMac.isEmpty(), 1.0f, GuiRenderUtils.COLOR_ITEM_SELECTED, false);
        GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, this.leftPos + 290, this.topPos + 172, 30, 16, "Del", this.selectedMac.isEmpty(), 1.0f, GuiRenderUtils.COLOR_BTN_DANGER, false);
    }

    private void renderSettings(GuiGraphics guiGraphics, int mouseX, int mouseY, RouterBlockEntity router) {
        int x = this.leftPos + 15;

        // Label
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Machine Label:", x, this.topPos + 35, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        guiGraphics.fill(x - 1, this.topPos + 49, x + 105, this.topPos + 65, GuiRenderUtils.COLOR_BORDER);
        guiGraphics.fill(x, this.topPos + 50, x + 104, this.topPos + 64, GuiRenderUtils.COLOR_BG_MAIN);
        if (this.labelInput.getValue().isEmpty() && !this.labelInput.isFocused()) {
            GuiRenderUtils.drawScaledString(guiGraphics, this.font, "Label name...", x + 3, this.topPos + 52, 0xFF666666, 1.0f);
        }
        this.labelInput.render(guiGraphics, mouseX, mouseY, 0);

        int btnColor1 = this.labelSetButtonText.equals("OK") ? GuiRenderUtils.COLOR_ITEM_SELECTED : GuiRenderUtils.COLOR_BTN_BG;
        GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, x + 110, this.topPos + 49, 35, 16, this.labelSetButtonText, false, 1.0f, btnColor1, false);
        GuiRenderUtils.drawCustomButton(guiGraphics, this.font, mouseX, mouseY, x + 150, this.topPos + 49, 45, 16, "Clear", false, 1.0f, GuiRenderUtils.COLOR_BTN_BG, false);

        // System Toggles
        GuiRenderUtils.drawScaledString(guiGraphics, this.font, "System Toggles:", x, this.topPos + 85, GuiRenderUtils.COLOR_TEXT_MUTED, 1.0f);
        boolean isPrivate = router.isPrivateMode();

        GuiRenderUtils.drawToggleButton(guiGraphics, this.font, mouseX, mouseY, x, this.topPos + 99, 55, 16, "Private", isPrivate, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabX = this.leftPos + 12;
            int tabY = this.topPos + 10;
            for (int i = 0; i < topTabs.length; i++) {
                if (GuiRenderUtils.isHovered(mouseX, mouseY, tabX, tabY, 76, 16)) {
                    this.currentTab = i;
                    this.setFocused(null);
                    updateTabVisibility();
                    return true;
                }
                tabX += 80;
            }

            if (this.currentTab == 0) {
                int col1 = this.leftPos + 15;
                int btnY = this.topPos + 180;
                if (GuiRenderUtils.isHovered(mouseX, mouseY, col1, btnY, 55, 16)) {
                    RouterBlockEntity router = null;
                    if (this.minecraft != null && this.minecraft.level != null) {
                        BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.pos);
                        if (be instanceof RouterBlockEntity r) router = r;
                    }
                    if (router != null && router.isRunning()) {
                        sendCommand("stop", "");
                    } else {
                        sendCommand("boot", "");
                    }
                    return true;
                }
                if (GuiRenderUtils.isHovered(mouseX, mouseY, col1 + 65, btnY, 55, 16)) {
                    sendCommand("reboot", "");
                    return true;
                }
            } else if (this.currentTab == 1) {
                int listY = this.topPos + 67;
                for (int i = 0; i < 6; i++) {
                    int index = this.networkScrollOffset + i;
                    if (index >= this.currentLeases.size()) break;
                    if (GuiRenderUtils.isHovered(mouseX, mouseY, this.leftPos + 14, listY, 312, 14)) {
                        DhcpEntry entry = this.currentLeases.get(index);
                        this.selectedMac = entry.mac();
                        this.selectedIpInput.setValue(entry.ip());
                        this.selectedPortInput.setValue(entry.port());
                        return true;
                    }
                    listY += 14;
                }
                if (GuiRenderUtils.isHovered(mouseX, mouseY, this.leftPos + 240, this.topPos + 172, 45, 16) && !this.selectedMac.isEmpty()) {
                    String newIp = this.selectedIpInput.getValue().trim();
                    String newPort = this.selectedPortInput.getValue().trim();
                    if (!newIp.isEmpty()) {
                        sendCommand("update_dhcp", this.selectedMac + "|" + newIp + "|" + newPort);
                        this.selectedMac = "";
                        this.selectedIpInput.setValue("");
                        this.selectedPortInput.setValue("");
                    }
                    return true;
                }
                if (GuiRenderUtils.isHovered(mouseX, mouseY, this.leftPos + 290, this.topPos + 172, 30, 16) && !this.selectedMac.isEmpty()) {
                    sendCommand("remove_dhcp", this.selectedMac);
                    this.selectedMac = "";
                    this.selectedIpInput.setValue("");
                    this.selectedPortInput.setValue("");
                    return true;
                }
                if (this.selectedIpInput.mouseClicked(mouseX, mouseY, button)) {
                    this.setFocused(this.selectedIpInput);
                    return true;
                }
                if (this.selectedPortInput.mouseClicked(mouseX, mouseY, button)) {
                    this.setFocused(this.selectedPortInput);
                    return true;
                }
            } else if (this.currentTab == 2) {
                int x = this.leftPos + 15;
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 110, this.topPos + 49, 35, 16)) {
                    sendCommand("label", "set " + this.labelInput.getValue().trim());
                    this.labelSetButtonText = "OK";
                    this.labelSetTick = 20;
                    return true;
                }
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x + 150, this.topPos + 49, 45, 16)) {
                    sendCommand("label", "clear");
                    this.labelInput.setValue("");
                    return true;
                }

                // Toggles
                if (GuiRenderUtils.isHovered(mouseX, mouseY, x, this.topPos + 99, 55, 16)) {
                    if (this.minecraft != null && this.minecraft.level != null) {
                        BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.pos);
                        if (be instanceof RouterBlockEntity router) {
                            sendCommand("toggle_private", String.valueOf(!router.isPrivateMode()));
                        }
                    }
                    return true;
                }

                if (this.labelInput.mouseClicked(mouseX, mouseY, button)) {
                    this.setFocused(this.labelInput);
                    return true;
                }
                this.setFocused(null);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.currentTab == 1) {
            int maxScroll = Math.max(0, this.currentLeases.size() - 6);
            if (scrollY > 0 && this.networkScrollOffset > 0) {
                this.networkScrollOffset--;
                return true;
            } else if (scrollY < 0 && this.networkScrollOffset < maxScroll) {
                this.networkScrollOffset++;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private record DhcpEntry(String mac, String ip, String port, String displayMac) {
    }
}