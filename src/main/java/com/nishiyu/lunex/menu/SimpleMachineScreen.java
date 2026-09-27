package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.blockentity.PrinterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.network.packet.c2s.SimpleMachineActionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SimpleMachineScreen extends AbstractContainerScreen<SimpleMachineMenu> {

    private EditBox inputField;
    private int scrollOffset = 0;
    private final int lineHeight = 10;
    private String currentSuggestion = "";

    private final List<String> pendingInputLines = new ArrayList<>();

    private static final List<String> commandHistory = new ArrayList<>();
    private static boolean historyLoaded = false;
    private int historyIndex = 0;

    private boolean showContextMenu = false;
    private int contextX, contextY;
    private final int contextWidth = 90;
    private List<String> currentMenuItems = new ArrayList<>();

    private boolean showExportDialog = false;
    private EditBox nameInputField;
    private Button confirmExportButton;
    private Button cancelExportButton;

    public SimpleMachineScreen(SimpleMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 160;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;

        loadHistory();
        this.historyIndex = commandHistory.size();

        this.inputField = new EditBox(this.font, this.leftPos + 14, this.topPos + 4, this.imageWidth - 18, 12, Component.empty());
        this.inputField.setMaxLength(1024);
        this.inputField.setBordered(false);
        this.inputField.setTextColor(0xFFFFFF);
        this.inputField.setCanLoseFocus(false);
        this.inputField.setFocused(true);
        this.addRenderableWidget(this.inputField);
        this.setInitialFocus(this.inputField);

        int dw = 160;
        int dh = 70;
        int dx = this.width / 2 - dw / 2;
        int dy = this.height / 2 - dh / 2;

        this.nameInputField = new EditBox(this.font, dx + 10, dy + 20, dw - 20, 16, Component.empty());
        this.nameInputField.setMaxLength(32);

        this.confirmExportButton = Button.builder(Component.literal("OK"), b -> confirmExport())
                .bounds(dx + 10, dy + 42, 65, 20).build();
        this.cancelExportButton = Button.builder(Component.literal("Cancel"), b -> closeExportDialog())
                .bounds(dx + dw - 75, dy + 42, 65, 20).build();

        this.addWidget(this.nameInputField);
        this.addWidget(this.confirmExportButton);
        this.addWidget(this.cancelExportButton);
    }

    private void loadHistory() {
        if (historyLoaded || this.minecraft == null) return;
        Path path = this.minecraft.gameDirectory.toPath().resolve("lunex_history.txt");
        if (Files.exists(path)) {
            try {
                commandHistory.clear();
                commandHistory.addAll(Files.readAllLines(path));
            } catch (Exception ignored) {}
        }
        historyLoaded = true;
    }

    private void saveHistory() {
        if (this.minecraft == null) return;
        Path path = this.minecraft.gameDirectory.toPath().resolve("lunex_history.txt");
        try {
            Files.write(path, commandHistory);
        } catch (Exception ignored) {}
    }

    private void openExportDialog() {
        this.showExportDialog = true;
        this.nameInputField.setValue("Program");
        this.setFocused(this.nameInputField);
        this.nameInputField.setFocused(true);
        this.inputField.setFocused(false);
    }

    private void closeExportDialog() {
        this.showExportDialog = false;
        this.setFocused(this.inputField);
        this.inputField.setFocused(true);
    }

    private void confirmExport() {
        String name = this.nameInputField.getValue().trim();
        if (name.isEmpty()) name = "Program";

        PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "export", name, ""));
        closeExportDialog();
    }

    private List<String> getSuggestions() {
        if (this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.blockPos);
            if (be instanceof SimpleMachineBlockEntity machineEntity) {
                return machineEntity.clientSuggestions;
            }
        }
        return new ArrayList<>();
    }

    private void updateSuggestion() {
        this.currentSuggestion = "";
        String text = this.inputField.getValue();

        // ★修正: text.startsWith("/") の条件を削除し、/ 系でもサジェストが出るように変更
        if (text.isEmpty()) return;

        String[] parts = text.split("[\\s]+");
        if (parts.length == 0) return;
        String lastWord = parts[parts.length - 1];

        for (String s : getSuggestions()) {
            if (s.toLowerCase().startsWith(lastWord.toLowerCase())) {
                if (!lastWord.contains(".") && s.contains(".")) {
                    this.currentSuggestion = s.substring(0, s.indexOf('.') + 1);
                } else {
                    this.currentSuggestion = s;
                }
                break;
            }
        }
    }

    private void buildContextMenu() {
        this.currentMenuItems.clear();
        this.currentMenuItems.add("Clear Screen");
        this.currentMenuItems.add("Wipe Memory");
        this.currentMenuItems.add("Copy Log");

        if (canExportProgram()) {
            this.currentMenuItems.add("Export Program");
        }
    }

    private boolean canExportProgram() {
        if (this.minecraft == null || this.minecraft.level == null) return false;
        BlockPos pos = this.menu.blockPos;
        for (Direction dir : Direction.values()) {
            BlockEntity be = this.minecraft.level.getBlockEntity(pos.relative(dir));
            if (be instanceof PrinterBlockEntity printer) {
                if (!printer.itemHandler.getStackInSlot(3).isEmpty()) continue;
                int iron = 0, redstone = 0, gold = 0;
                for (int i = 0; i < 3; i++) {
                    ItemStack stack = printer.itemHandler.getStackInSlot(i);
                    if (stack.getItem() == Items.IRON_INGOT) iron += stack.getCount();
                    if (stack.getItem() == Items.REDSTONE) redstone += stack.getCount();
                    if (stack.getItem() == Items.GOLD_INGOT) gold += stack.getCount();
                }
                if (iron >= 2 && redstone >= 5 && gold >= 1) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.showExportDialog) return true;
        if (scrollY > 0) this.scrollOffset++;
        else if (scrollY < 0) this.scrollOffset--;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.showExportDialog) {
            this.nameInputField.mouseClicked(mouseX, mouseY, button);
            this.confirmExportButton.mouseClicked(mouseX, mouseY, button);
            this.cancelExportButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (this.showContextMenu) {
            if (mouseX >= this.contextX && mouseX <= this.contextX + this.contextWidth) {
                int index = (int) (mouseY - this.contextY) / 15;
                if (index >= 0 && index < this.currentMenuItems.size()) {
                    String action = this.currentMenuItems.get(index);
                    if (action.equals("Clear Screen")) {
                        handleClientCommand("/clear");
                    } else if (action.equals("Wipe Memory")) {
                        handleClientCommand("/wipe");
                    } else if (action.equals("Copy Log")) {
                        List<String> cleanLog = new ArrayList<>();
                        for (String s : getTerminalLog()) {
                            if (s.equals("Simple OS v1.0") || s.equals("Type a command and press Enter.")) continue;
                            cleanLog.add(s);
                        }
                        if (this.minecraft != null) {
                            this.minecraft.keyboardHandler.setClipboard(String.join("\n", cleanLog));
                        }
                    } else if (action.equals("Export Program")) {
                        openExportDialog();
                    }
                }
            }
            this.showContextMenu = false;
            return true;
        }

        if (button == 1) {
            buildContextMenu();
            this.showContextMenu = true;
            this.contextX = (int) mouseX;
            this.contextY = (int) mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.showExportDialog) {
            return this.nameInputField.charTyped(codePoint, modifiers);
        }
        if (this.inputField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.showExportDialog) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeExportDialog();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmExport();
                return true;
            }
            return this.nameInputField.keyPressed(keyCode, scanCode, modifiers);
        }

        if (this.showContextMenu && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.showContextMenu = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB && !this.currentSuggestion.isEmpty()) {
            String text = this.inputField.getValue();
            String[] parts = text.split("(?<=\\s)|(?=\\s)");
            if (parts.length > 0) {
                parts[parts.length - 1] = this.currentSuggestion;
                this.inputField.setValue(String.join("", parts));
            } else {
                this.inputField.setValue(this.currentSuggestion);
            }
            this.inputField.setCursorPosition(this.inputField.getValue().length());
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (Screen.hasShiftDown()) {
                this.pendingInputLines.add(this.inputField.getValue());
                this.inputField.setValue("");
                this.scrollOffset = 0;
            } else {
                String current = this.inputField.getValue();
                if (this.pendingInputLines.isEmpty() && current.trim().isEmpty()) {
                    return true;
                }

                this.pendingInputLines.add(current);
                String command = String.join("\n", this.pendingInputLines);

                if (commandHistory.isEmpty() || !commandHistory.getLast().equals(command)) {
                    commandHistory.add(command);
                    saveHistory();
                }
                this.historyIndex = commandHistory.size();

                if (command.trim().startsWith("/")) {
                    handleClientCommand(command.trim());
                } else {
                    PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "execute", "", command));
                }

                this.pendingInputLines.clear();
                this.inputField.setValue("");
                this.scrollOffset = 0;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!commandHistory.isEmpty() && this.historyIndex > 0) {
                this.historyIndex--;
                loadHistoryToInput(commandHistory.get(this.historyIndex));
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!commandHistory.isEmpty() && this.historyIndex < commandHistory.size() - 1) {
                this.historyIndex++;
                loadHistoryToInput(commandHistory.get(this.historyIndex));
            } else if (this.historyIndex == commandHistory.size() - 1) {
                this.historyIndex++;
                this.pendingInputLines.clear();
                this.inputField.setValue("");
            }
            return true;
        }

        if (this.inputField.keyPressed(keyCode, scanCode, modifiers)) {
            updateSuggestion();
            return true;
        }

        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }

        boolean result = super.keyPressed(keyCode, scanCode, modifiers);
        updateSuggestion();
        return result;
    }

    private void handleClientCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "/exit":
                this.onClose();
                break;
            case "/clear":
                PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "clear", "", ""));
                break;
            case "/wipe":
                commandHistory.clear();
                saveHistory();
                this.historyIndex = 0;
                PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "wipe", "", ""));
                break;
            case "/help":
                PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "help", "", ""));
                break;
            case "/assemble":
                PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "assemble", "", ""));
                this.onClose(); // ★追加: assemble実行後に即座にメニューを閉じる
                break;
            default:
                PacketDistributor.sendToServer(new SimpleMachineActionC2SPacket(this.menu.blockPos, "unknown_cmd", "", parts[0]));
                break;
        }
    }

    private void loadHistoryToInput(String histCmd) {
        String[] lines = histCmd.split("\n");
        this.pendingInputLines.clear();
        for (int i = 0; i < lines.length - 1; i++) {
            this.pendingInputLines.add(lines[i]);
        }
        this.inputField.setValue(lines[lines.length - 1]);
        this.inputField.setCursorPosition(this.inputField.getValue().length());
    }

    private List<String> getTerminalLog() {
        if (this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.blockPos);
            if (be instanceof SimpleMachineBlockEntity machineEntity) {
                return machineEntity.clientTerminalLog;
            }
        }
        return new ArrayList<>();
    }

    private List<String> getProcessedTerminalLog() {
        List<String> raw = getTerminalLog();
        List<String> processed = new ArrayList<>();
        for (String s : raw) {
            if (s.contains("\n")) {
                String[] parts = s.split("\n");
                processed.add(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    if (parts[0].startsWith("> ")) {
                        processed.add("... " + parts[i]);
                    } else {
                        processed.add(parts[i]);
                    }
                }
            } else {
                processed.add(s);
            }
        }
        return processed;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        List<String> processedLog = getProcessedTerminalLog();
        int maxVisibleLines = (this.imageHeight - 16) / lineHeight;
        int totalLines = processedLog.size() + this.pendingInputLines.size();

        int maxScroll = Math.max(0, totalLines - maxVisibleLines);
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
        if (this.scrollOffset < 0) this.scrollOffset = 0;

        int startIndex = Math.max(0, totalLines - maxVisibleLines - this.scrollOffset);
        int endIndex = Math.min(totalLines, startIndex + maxVisibleLines);

        int inputRelativeY = 4 + (endIndex - startIndex) * lineHeight;
        if (totalLines >= maxVisibleLines || this.scrollOffset > 0) {
            inputRelativeY = 4 + maxVisibleLines * lineHeight;
        }
        this.inputField.setY(this.topPos + inputRelativeY);

        String prompt = this.pendingInputLines.isEmpty() ? "> " : "... ";
        int promptWidth = this.font.width(prompt);
        this.inputField.setX(this.leftPos + 4 + promptWidth);
        this.inputField.setWidth(this.imageWidth - 8 - promptWidth);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (!this.currentSuggestion.isEmpty() && this.inputField.getValue().length() > 0) {
            String[] parts = this.inputField.getValue().split("[\\s]+");
            if (parts.length > 0) {
                String lastWord = parts[parts.length - 1];
                if (this.currentSuggestion.toLowerCase().startsWith(lastWord.toLowerCase())) {
                    String remainingSuggestion = this.currentSuggestion.substring(lastWord.length());
                    int textWidth = this.font.width(this.inputField.getValue());
                    guiGraphics.drawString(this.font, remainingSuggestion, this.inputField.getX() + textWidth, this.inputField.getY(), 0xFF555555, false);
                }
            }
        }

        if (this.showContextMenu) {
            int menuHeight = this.currentMenuItems.size() * 15;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 400);

            guiGraphics.fill(this.contextX, this.contextY, this.contextX + this.contextWidth, this.contextY + menuHeight, 0xDD000000);
            guiGraphics.renderOutline(this.contextX, this.contextY, this.contextWidth, menuHeight, 0xFFFFFFFF);

            for (int i = 0; i < this.currentMenuItems.size(); i++) {
                int itemY = this.contextY + (i * 15);
                if (mouseX >= this.contextX && mouseX <= this.contextX + this.contextWidth && mouseY >= itemY && mouseY < itemY + 15) {
                    guiGraphics.fill(this.contextX + 1, itemY + 1, this.contextX + this.contextWidth - 1, itemY + 14, 0x55FFFFFF);
                }
                guiGraphics.drawString(this.font, this.currentMenuItems.get(i), this.contextX + 4, itemY + 4, 0xFFFFFF, false);
            }
            guiGraphics.pose().popPose();
        }

        if (this.showExportDialog) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);

            guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);
            int dw = 160;
            int dh = 70;
            int dx = this.width / 2 - dw / 2;
            int dy = this.height / 2 - dh / 2;

            guiGraphics.fill(dx, dy, dx + dw, dy + dh, 0xFF111111);
            guiGraphics.renderOutline(dx, dy, dw, dh, 0xFFFFFFFF);
            guiGraphics.drawCenteredString(this.font, "Enter Program Name", this.width / 2, dy + 6, 0xFFFFFF);

            this.nameInputField.render(guiGraphics, mouseX, mouseY, partialTick);
            this.confirmExportButton.render(guiGraphics, mouseX, mouseY, partialTick);
            this.cancelExportButton.render(guiGraphics, mouseX, mouseY, partialTick);

            guiGraphics.pose().popPose();
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF000000);
        guiGraphics.renderOutline(this.leftPos - 1, this.topPos - 1, this.imageWidth + 2, this.imageHeight + 2, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<String> processedLog = getProcessedTerminalLog();
        int logAreaHeight = this.imageHeight - 16;
        int maxVisibleLines = logAreaHeight / lineHeight;
        int totalLines = processedLog.size() + this.pendingInputLines.size();

        int maxScroll = Math.max(0, totalLines - maxVisibleLines);
        int startIndex = Math.max(0, totalLines - maxVisibleLines - this.scrollOffset);
        int endIndex = Math.min(totalLines, startIndex + maxVisibleLines);

        int y = 4;
        for (int i = startIndex; i < endIndex; i++) {
            String lineToDraw;
            if (i < processedLog.size()) {
                lineToDraw = processedLog.get(i);
            } else {
                lineToDraw = "... " + this.pendingInputLines.get(i - processedLog.size());
            }
            guiGraphics.drawString(this.font, lineToDraw, 4, y, 0xFFFFFF, false);
            y += lineHeight;
        }

        String prompt = this.pendingInputLines.isEmpty() ? "> " : "... ";
        int inputRelativeY = 4 + (endIndex - startIndex) * lineHeight;
        if (totalLines >= maxVisibleLines || this.scrollOffset > 0) {
            inputRelativeY = 4 + maxVisibleLines * lineHeight;
        }
        guiGraphics.drawString(this.font, prompt, 4, inputRelativeY + 2, 0xFFFFFF, false);

        if (totalLines > maxVisibleLines) {
            int scrollbarX = this.imageWidth - 6;
            int trackHeight = logAreaHeight - 8;
            int thumbHeight = Math.max(10, (int) ((float) maxVisibleLines / totalLines * trackHeight));
            float scrollRatio = 1.0f - ((float) this.scrollOffset / maxScroll);
            int thumbY = 4 + (int) (scrollRatio * (trackHeight - thumbHeight));

            guiGraphics.fill(scrollbarX, 4, scrollbarX + 2, 4 + trackHeight, 0xFF444444);
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }
}