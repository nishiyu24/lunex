package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.lunex.menu.utiles.EditorLauncher;
import com.nishiyu.lunex.network.LocalWebSocketServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TurtleFileManagerTab implements ITurtleTab {
    private final TurtleSettingsScreen screen;
    private int leftPos, topPos;
    private int scrollOffset = 0;
    private String currentDirectory = "";
    private String selectedFile = null;
    private int syncTimer = 0;
    private boolean isLoadingFiles = false;
    private int loadingTimer = 0;
    private List<TreeItem> currentFiles = new ArrayList<>();
    private EditBox modalInput;
    private boolean showModal = false;
    private String modalTitle = "", modalAction = "", modalTarget = "";
    private long modalOpenTime;
    private long lastClickTime = 0;
    private TreeItem lastClickedItem = null;
    private ContextMenu contextMenu = null;

    public TurtleFileManagerTab(TurtleSettingsScreen screen) {
        this.screen = screen;
    }

    @Override
    public void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
        if (this.modalInput == null) {
            this.modalInput = new EditBox(this.screen.getFont(), 0, 0, 110, 10, Component.literal("Input Name"));
            this.modalInput.setMaxLength(30);
            this.modalInput.setBordered(false);
            this.modalInput.setTextColor(TurtleGuiUtils.COLOR_TEXT_PRIMARY);
        }
        this.screen.addWidgetToScreen(this.modalInput);
        this.modalInput.setVisible(false);
        this.refreshFileList();
    }

    public boolean isShowModal() { return this.showModal; }
    public ContextMenu getContextMenu() { return this.contextMenu; }

    public void receiveItemFiles(List<String> files) {
        this.currentFiles = buildFileTree(this.currentDirectory, files);
        this.isLoadingFiles = false;
        this.loadingTimer = 0;
    }

    public void refreshFileList() {
        this.isLoadingFiles = true;
        this.loadingTimer = 10;
        this.screen.sendCommand("sync_files", "");
    }

    private List<TreeItem> buildFileTree(String targetDir, List<String> paths) {
        List<TreeItem> tree = new ArrayList<>();
        List<String> sortedPaths = new ArrayList<>(paths);
        sortedPaths.sort(String::compareTo);
        List<String> dirs = new ArrayList<>();
        List<String> files = new ArrayList<>();
        String cur = targetDir;
        if (!cur.isEmpty() && !cur.endsWith("/")) cur += "/";

        for (String p : sortedPaths) {
            if (p.isEmpty() || p.endsWith(".lifespan")) continue;
            boolean isHidden = false;
            for (String part : p.split("/")) {
                if (part.startsWith(".")) { isHidden = true; break; }
            }
            if (isHidden) continue;

            if (p.startsWith(cur) && !p.equals(cur)) {
                String rel = p.substring(cur.length());
                int slashIdx = rel.indexOf('/');
                if (slashIdx == -1) files.add(rel);
                else {
                    String dirName = rel.substring(0, slashIdx);
                    if (!dirs.contains(dirName)) dirs.add(dirName);
                }
            }
        }
        if (!cur.isEmpty()) tree.add(new TreeItem("..", true));
        for (String d : dirs) tree.add(new TreeItem(d, true));
        for (String f : files) tree.add(new TreeItem(f, false));
        return tree;
    }

    @Override
    public void tick() {
        if (this.isLoadingFiles) {
            if (this.loadingTimer > 0) this.loadingTimer--;
            else {
                this.isLoadingFiles = false;
                this.currentFiles = buildFileTree(this.currentDirectory, this.screen.getMenu().getBlockEntity().getInstalledPrograms());
            }
        }
        syncTimer++;
        if (syncTimer % 40 == 0 && !this.isLoadingFiles) {
            this.screen.sendCommand("sync_files", "");
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderFileManager(guiGraphics, mouseX, mouseY);
        if (this.contextMenu != null) renderContextMenu(guiGraphics, mouseX, mouseY);
        if (this.showModal) renderModal(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderFileManager(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String displayDir = this.currentDirectory.isEmpty() ? "/" : "/" + this.currentDirectory;
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Path: " + displayDir, this.leftPos + 10, this.topPos + 32, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);

        int listX = this.leftPos + 10;
        int listY = this.topPos + 48;
        int itemWidth = 320;
        int itemHeight = 18;
        int maxVisible = 8;

        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Name", listX + 5, listY - 5, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Type", listX + 240, listY - 5, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        listY += 10;

        if (this.isLoadingFiles && this.loadingTimer > 0) {
            TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), "Loading...", listX + 5, listY + 10, TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
            return;
        }

        for (int i = 0; i < maxVisible; i++) {
            int index = this.scrollOffset + i;
            if (index >= this.currentFiles.size()) break;
            TreeItem item = this.currentFiles.get(index);
            int yPos = listY + (i * itemHeight);

            boolean isHovered = TurtleGuiUtils.isHovered(mouseX, mouseY, listX, yPos, itemWidth, itemHeight);
            if (this.contextMenu != null || this.showModal) isHovered = false;
            boolean isSelected = item.name.equals(this.selectedFile);

            int bgColor = isSelected ? TurtleGuiUtils.COLOR_ITEM_SELECTED : (isHovered ? TurtleGuiUtils.COLOR_ITEM_HOVER : 0x00000000);
            int textColor = isSelected ? TurtleGuiUtils.COLOR_TEXT_DARK : TurtleGuiUtils.COLOR_TEXT_PRIMARY;

            if (bgColor != 0x00000000) guiGraphics.fill(listX, yPos, listX + itemWidth, yPos + itemHeight, bgColor);

            ItemStack iconStack = item.name.equals("..") ? new ItemStack(Items.LADDER) : (item.isDir ? new ItemStack(Items.CHEST) : new ItemStack(Items.PAPER));
            guiGraphics.renderItem(iconStack, listX + 2, yPos + 1);

            TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), item.name, listX + 22, yPos + 5, textColor, 1.0f);

            String typeText;
            if (item.isDir) typeText = item.name.equals("..") ? "Up" : "Folder";
            else if (item.name.endsWith(".lua")) typeText = "Lua Script";
            else typeText = "File";
            TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), typeText, listX + 240, yPos + 5, isSelected ? TurtleGuiUtils.COLOR_TEXT_DARK : TurtleGuiUtils.COLOR_TEXT_MUTED, 1.0f);
        }
    }

    private void renderContextMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int cx = this.contextMenu.x;
        int cy = this.contextMenu.y;
        int w = 70;
        int itemH = 16;
        int h = this.contextMenu.options.size() * itemH + 2;

        guiGraphics.fill(cx, cy, cx + w, cy + h, TurtleGuiUtils.COLOR_BORDER);
        guiGraphics.fill(cx + 1, cy + 1, cx + w - 1, cy + h - 1, TurtleGuiUtils.COLOR_BG_HEADER);

        for (int i = 0; i < this.contextMenu.options.size(); i++) {
            String opt = this.contextMenu.options.get(i);
            int itemY = cy + 1 + (i * itemH);
            boolean hover = TurtleGuiUtils.isHovered(mouseX, mouseY, cx + 1, itemY, w - 2, itemH);
            if (hover) guiGraphics.fill(cx + 1, itemY, cx + w - 1, itemY + itemH, TurtleGuiUtils.COLOR_ITEM_HOVER);
            int color = opt.equals("Delete") ? 0xFFFFAAAA : TurtleGuiUtils.COLOR_TEXT_PRIMARY;
            TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), opt, cx + 5, itemY + 4, color, 1.0f);
        }
    }

    private void renderModal(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.screen.width, this.screen.height, 0xAA000000);
        int modalW = 150;
        int modalH = 80;
        int mx = this.leftPos + (this.screen.getImageWidth() - modalW) / 2;
        int my = this.topPos + (this.screen.getImageHeight() - modalH) / 2;

        guiGraphics.fill(mx, my, mx + modalW, my + modalH, TurtleGuiUtils.COLOR_BORDER);
        guiGraphics.fill(mx + 1, my + 1, mx + modalW - 1, my + modalH - 1, TurtleGuiUtils.COLOR_BG_HEADER);
        TurtleGuiUtils.drawScaledString(guiGraphics, this.screen.getFont(), this.modalTitle, mx + 5, my + 6, TurtleGuiUtils.COLOR_TEXT_PRIMARY, 1.0f);
        guiGraphics.fill(mx + 10, my + 25, mx + modalW - 10, my + 43, TurtleGuiUtils.COLOR_BORDER);
        guiGraphics.fill(mx + 11, my + 26, mx + modalW - 11, my + 42, TurtleGuiUtils.COLOR_BG_MAIN);

        if (this.modalInput != null) {
            this.modalInput.setX(mx + 15);
            this.modalInput.setY(my + 29);
            this.modalInput.setVisible(true);
            this.modalInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        String actionBtnText = this.modalAction.equals("rename") ? "Rename" : "Create";
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, mx + 15, my + 55, 55, 16, "Cancel", false, 1.0f, TurtleGuiUtils.COLOR_BTN_BG, false);
        TurtleGuiUtils.drawCustomButton(guiGraphics, this.screen.getFont(), mouseX, mouseY, mx + 80, my + 55, 55, 16, actionBtnText, false, 1.0f, TurtleGuiUtils.COLOR_ITEM_SELECTED, false);
    }

    private void launchEditorLinked(String targetFile) {
        BlockPos p = this.screen.getMenu().getBlockEntity().getBlockPos();
        String vmId = "turtle_" + p.getX() + "_" + p.getY() + "_" + p.getZ();
        LocalWebSocketServer.setActiveVm(vmId, this.screen.getMenu().getBlockEntity().getWorkspaceId(), "", targetFile);
        EditorLauncher.launchEditor("machine.html");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isLoadingFiles) return false;

        if (this.showModal) {
            int mx = this.leftPos + (this.screen.getImageWidth() - 150) / 2;
            int my = this.topPos + (this.screen.getImageHeight() - 80) / 2;
            if (button == 0) {
                if (TurtleGuiUtils.isHovered(mouseX, mouseY, mx + 15, my + 55, 55, 16)) {
                    this.showModal = false;
                    this.modalInput.setVisible(false);
                    this.screen.setScreenFocused(null);
                    return true;
                }
                if (TurtleGuiUtils.isHovered(mouseX, mouseY, mx + 80, my + 55, 55, 16)) {
                    executeModalAction();
                    return true;
                }
                if (this.modalInput != null && this.modalInput.mouseClicked(mouseX, mouseY, button)) {
                    this.screen.setScreenFocused(this.modalInput);
                    return true;
                }
            }
            return true;
        }

        if (this.contextMenu != null) {
            if (button == 0) {
                int cx = this.contextMenu.x;
                int cy = this.contextMenu.y;
                for (int i = 0; i < this.contextMenu.options.size(); i++) {
                    if (TurtleGuiUtils.isHovered(mouseX, mouseY, cx + 1, cy + 1 + (i * 16), 68, 16)) {
                        handleContextMenuAction(this.contextMenu.options.get(i), this.contextMenu.target);
                        this.contextMenu = null;
                        return true;
                    }
                }
            }
            this.contextMenu = null;
            return true;
        }

        int listX = this.leftPos + 10;
        int listY = this.topPos + 64;
        for (int i = 0; i < 8; i++) {
            int index = this.scrollOffset + i;
            if (index >= this.currentFiles.size()) break;
            int yPos = listY + (i * 18);
            if (TurtleGuiUtils.isHovered(mouseX, mouseY, listX, yPos, 320, 18)) {
                TreeItem item = this.currentFiles.get(index);
                if (button == 1) {
                    if (!item.name.equals("..")) {
                        this.selectedFile = item.name;
                        this.contextMenu = new ContextMenu((int) mouseX, (int) mouseY, item, false);
                    }
                    return true;
                } else if (button == 0) {
                    long now = System.currentTimeMillis();
                    boolean isDoubleClick = (item.equals(this.lastClickedItem) && (now - this.lastClickTime) < 300);
                    this.lastClickTime = now;
                    this.lastClickedItem = item;

                    if (isDoubleClick && !item.isDir && !item.name.equals("..")) {
                        launchEditorLinked(this.currentDirectory + item.name);
                        return true;
                    }

                    if (item.name.equals("..")) {
                        if (!this.currentDirectory.isEmpty()) {
                            String temp = this.currentDirectory.substring(0, this.currentDirectory.length() - 1);
                            int lastSlash = temp.lastIndexOf('/');
                            this.currentDirectory = (lastSlash == -1) ? "" : temp.substring(0, lastSlash + 1);
                        }
                        this.selectedFile = null;
                        this.scrollOffset = 0;
                        this.refreshFileList();
                    } else if (item.isDir) {
                        this.currentDirectory += item.name + "/";
                        this.selectedFile = null;
                        this.scrollOffset = 0;
                        this.refreshFileList();
                    } else {
                        this.selectedFile = item.name;
                    }
                    return true;
                }
            }
        }

        if (button == 1 && TurtleGuiUtils.isHovered(mouseX, mouseY, listX, listY, 320, 18 * 8)) {
            this.contextMenu = new ContextMenu((int) mouseX, (int) mouseY, null, false);
            return true;
        }
        return false;
    }

    private void executeModalAction() {
        if (this.modalInput == null) return;
        String val = this.modalInput.getValue().trim();
        if (!val.isEmpty()) {
            if (val.startsWith(".")) {
                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Error] Name cannot start with '.'§r"), true);
                return;
            }
            String path = this.currentDirectory + val;
            if (this.modalAction.equals("touch")) this.screen.sendCommand("touch", path);
            else if (this.modalAction.equals("mkdir")) this.screen.sendCommand("mkdir", path + "/");
            else if (this.modalAction.equals("rename") && this.modalTarget != null) {
                this.screen.sendCommand("rename", this.currentDirectory + this.modalTarget + " " + path);
            }
            this.refreshFileList();
        }
        this.showModal = false;
        this.modalInput.setVisible(false);
        this.modalTarget = null;
        this.screen.setScreenFocused(null);
    }

    private void handleContextMenuAction(String action, TreeItem item) {
        String path = this.currentDirectory + (item != null ? item.name : "");
        switch (action) {
            case "New File" -> openModal("Create New File", "touch", "New_File.lua");
            case "New Folder" -> openModal("Create New Folder", "mkdir", "New_Folder");
            case "Boot" -> {
                this.screen.sendCommand("set_startup", path);
                this.screen.sendCommand("boot", path);
                this.screen.getMenu().getBlockEntity().setProgramName(path);
                this.selectedFile = null;
            }
            case "Rename" -> {
                openModal("Rename Target", "rename", Objects.requireNonNull(item).name);
                this.modalTarget = item.name;
            }
            case "Delete" -> {
                this.screen.sendCommand("rm", path);
                if (Objects.requireNonNull(item).name.equals(this.selectedFile)) this.selectedFile = null;
                this.refreshFileList();
            }
            case "Open" -> {
                this.currentDirectory += Objects.requireNonNull(item).name + "/";
                this.selectedFile = null;
                this.scrollOffset = 0;
                this.refreshFileList();
            }
            case "Edit" -> launchEditorLinked(this.currentDirectory + Objects.requireNonNull(item).name);
        }
    }

    private void openModal(String title, String action, String defaultText) {
        this.showModal = true;
        this.modalTitle = title;
        this.modalAction = action;
        this.modalOpenTime = System.currentTimeMillis();
        if (this.modalInput != null) {
            this.modalInput.setValue(defaultText);
            this.modalInput.setVisible(true);
            this.screen.setScreenFocused(this.modalInput);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.showModal || this.contextMenu != null) return false;
        int maxScroll = Math.max(0, this.currentFiles.size() - 8);
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
        if (this.showModal) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                executeModalAction();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.showModal = false;
                this.modalInput.setVisible(false);
                this.screen.setScreenFocused(null);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        if (this.modalInput != null) this.modalInput.setVisible(false);
    }

    public record TreeItem(String name, boolean isDir) {}

    public static class ContextMenu {
        int x, y;
        TreeItem target;
        List<String> options;

        ContextMenu(int x, int y, TreeItem target, boolean isRouter) {
            this.x = x;
            this.y = y;
            this.target = target;
            this.options = new ArrayList<>();
            if (target == null) {
                this.options.add("New File");
                this.options.add("New Folder");
            } else {
                if (target.isDir) this.options.add("Open");
                else {
                    this.options.add("Edit");
                    this.options.add("Boot");
                }
                this.options.add("Rename");
                this.options.add("Delete");
            }
        }
    }
}