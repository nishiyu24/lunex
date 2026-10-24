package com.nishiyu.lunex.menu.turtle;

import com.nishiyu.enderframelib.client.ClientWebManager;
import com.nishiyu.enderframelib.client.gui.AbstractWebContainerScreen;
import com.nishiyu.enderframelib.client.gui.AbstractWebScreen;
import com.nishiyu.enderframelib.client.renderer.WebCanvasContext;
import com.nishiyu.enderframelib.client.renderer.WebRenderCore;
import com.nishiyu.enderframelib.core.model.UIElement;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.menu.utiles.EditorLauncher;
import com.nishiyu.lunex.network.LocalWebSocketServer;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class TurtleSettingsScreen extends AbstractWebContainerScreen<TurtleSettingsMenu> {

    private static final ResourceLocation IDE_HTML_LOC = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "ui/turtle_ide.html");
    private static final int ENERGY_HISTORY_SIZE = 40;
    private static final int MAX_ENERGY = 100000;

    // ファイルツリー＆エクスプローラー状態
    private String currentDirectory = "";
    private String selectedFile = null;
    private boolean isLoadingFiles = false;
    private int loadingTimer = 0;
    private int syncTimer = 0;
    private List<TreeItem> currentFiles = new ArrayList<>();
    private long lastClickTime = 0;
    private String lastClickedItem = null;

    // マルチタブ・コードエディタ状態
    private String openedFilePath = "";
    private String currentCodeContent = "";
    private boolean isFileDirty = false;
    private final Map<String, OpenTabBuffer> openTabs = new LinkedHashMap<>();

    // テレメトリ・リアルタイムグラフ履歴
    private final float[] energyHistory = new float[ENERGY_HISTORY_SIZE];
    private int lastTickEnergy = -1;
    private int smoothedEnergyDelta = 0;

    // モーダル・通知タイマー状態
    private boolean showModal = false;
    private String modalAction = "";
    private String modalTarget = "";
    private int labelSetTick = 0;
    private int saveFeedbackTick = 0;
    private String currentLastError = "No recent errors. Ready.";

    public record TreeItem(String name, boolean isDir) {}

    private static class OpenTabBuffer {
        final String path;
        String content;
        boolean dirty;

        OpenTabBuffer(String path, String content, boolean dirty) {
            this.path = path;
            this.content = content;
            this.dirty = dirty;
        }
    }

    public TurtleSettingsScreen(TurtleSettingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
                "turtle_ide_" + (menu.getBlockEntity() != null ? menu.getBlockEntity().getBlockPos().asLong() : 0L));
    }

    @Override
    protected String buildInitialHtml() {
        return AbstractWebScreen.loadHtmlResource(IDE_HTML_LOC);
    }

    @Override
    protected void init() {
        super.init();

        TurtleBotBlockEntity be = this.menu.getBlockEntity();
        int initialEnergy = be != null ? be.getCore().energy : 0;
        Arrays.fill(this.energyHistory, Math.clamp((float) initialEnergy / MAX_ENERGY, 0f, 1f));
        this.lastTickEnergy = initialEnergy;

        initDefaultStateStore();
        syncDynamicDataToStore(true);
        refreshFileList();

        if (be != null) {
            setInputValue("input_label", be.getMachineLabel());
        }

        if (be != null && be.getCore().programName != null && !be.getCore().programName.isEmpty() && openedFilePath.isEmpty()) {
            requestOpenFileInIde(be.getCore().programName);
        } else if (!openedFilePath.isEmpty()) {
            syncEditorContentToDom();
        } else {
            setEditorWelcomePlaceholder();
        }
    }

    private void initDefaultStateStore() {
        this.state.set("machine_id", "#0000 [default]")
                .set("is_running", false)
                .set("status_text", "○ STOPPED")
                .set("run_btn_text", "▶ Run Boot")
                .set("current_path", "/")
                .set("save_btn_text", "Save (Ctrl+S)")
                .set("save_ok", false)
                .set("editor_status", "Ready")
                .set("cursor_pos", "Ln 1, Col 1")
                .set("error_log", "No recent errors. Ready.")
                .set("error_color", "#6a9955")
                .set("pos_text", "X:0 Y:0 Z:0")
                .set("ip_text", "Offline")
                .set("boot_prog", "None")
                .set("energy_text", "0 FE (0%)")
                .set("energy_val", 0)
                .set("energy_max", MAX_ENERGY)
                .set("energy_rate", "0 FE/t")
                .set("label_btn_text", "Set")
                .set("label_ok", false)
                .set("opt_private", false)
                .set("opt_wake", false)
                .set("opt_debug", false);
        this.state.forceSync();
    }

    // ==========================================
    // サーバー通信＆ファイル送受信メソッド
    // ==========================================
    public void sendCommand(String action, String data) {
        if (this.menu.getBlockEntity() == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("command", action);
        tag.putString("arg", data != null ? data : "");
        tag.putLong("pos", this.menu.getBlockEntity().getBlockPos().asLong());
        PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "machine_command", tag));
    }

    public void requestOpenFileInIde(String fullPath) {
        if (this.menu.getBlockEntity() == null || fullPath == null || fullPath.isEmpty()) return;

        if (this.openTabs.containsKey(fullPath)) {
            switchToTab(fullPath);
            return;
        }

        saveActiveBufferToTabMap();
        this.openedFilePath = fullPath;
        this.isFileDirty = false;
        this.currentCodeContent = "-- Fetching " + fullPath + " from server...";
        this.openTabs.put(fullPath, new OpenTabBuffer(fullPath, this.currentCodeContent, false));

        this.state.set("editor_status", "Loading " + fullPath + "...");
        syncTabsToStore();
        syncEditorContentToDom();

        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", this.menu.getBlockEntity().getBlockPos().asLong());
        tag.putString("programName", fullPath);
        PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "ide_request_file", tag));
    }

    public void receiveFileContent(String programName, String content) {
        if (programName != null && !programName.isEmpty()) {
            this.openedFilePath = programName;
        }
        String normalized = (content != null && !content.isEmpty())
                ? content.replace("\r\n", "\n")
                : "-- " + this.openedFilePath + "\n";

        this.currentCodeContent = normalized;
        this.isFileDirty = false;
        this.openTabs.put(this.openedFilePath, new OpenTabBuffer(this.openedFilePath, normalized, false));

        this.state.set("editor_status", "Loaded " + this.openedFilePath);
        syncTabsToStore();
        syncEditorContentToDom();
        rebuildFileListStore();
    }

    public void saveCurrentFileToServer() {
        if (this.openedFilePath.isEmpty() || this.menu.getBlockEntity() == null) return;

        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", this.menu.getBlockEntity().getBlockPos().asLong());
        tag.putString("programName", this.openedFilePath);
        tag.putString("code", this.currentCodeContent);
        PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "sync_program", tag));

        markFileDirty(false);
        this.saveFeedbackTick = 30;
        this.state.set("save_btn_text", "✓ Saved")
                .set("save_ok", true)
                .set("editor_status", "Saved " + this.openedFilePath);
    }

    public void receiveItemFiles(List<String> files) {
        this.currentFiles = buildFileTree(this.currentDirectory, files);
        this.isLoadingFiles = false;
        this.loadingTimer = 0;
        rebuildFileListStore();
    }

    public void refreshFileList() {
        this.isLoadingFiles = true;
        this.loadingTimer = 10;
        sendCommand("sync_files", "");
        rebuildFileListStore();
    }

    // ==========================================
    // Tick & リアルタイムWebStateStore / Canvas同期
    // ==========================================
    @Override
    public void containerTick() {
        super.containerTick();
        TurtleBotBlockEntity be = this.menu.getBlockEntity();

        if (this.isLoadingFiles && be != null) {
            if (this.loadingTimer > 0) {
                this.loadingTimer--;
            } else {
                this.isLoadingFiles = false;
                this.currentFiles = buildFileTree(this.currentDirectory, be.getCore().installedPrograms);
                rebuildFileListStore();
            }
        }

        syncTimer++;
        if (syncTimer % 60 == 0 && !this.isLoadingFiles) {
            sendCommand("sync_files", "");
        }

        if (this.labelSetTick > 0) {
            this.labelSetTick--;
            if (this.labelSetTick == 0) {
                this.state.set("label_btn_text", "Set").set("label_ok", false);
            }
        }

        if (this.saveFeedbackTick > 0) {
            this.saveFeedbackTick--;
            if (this.saveFeedbackTick == 0) {
                this.state.set("save_btn_text", "Save (Ctrl+S)").set("save_ok", false);
            }
        }

        updateCursorPosBadge();
        syncDynamicDataToStore(false);
        updateTelemetryCanvas();
    }

    private void syncDynamicDataToStore(boolean force) {
        TurtleBotBlockEntity be = this.menu.getBlockEntity();
        if (be == null) return;

        BlockPos pos = be.getBlockPos();
        String ws = (be.getWorkspaceId() != null && !be.getWorkspaceId().isEmpty()) ? be.getWorkspaceId() : "default";
        this.state.set("machine_id", "#" + Math.abs(pos.hashCode() % 10000) + " [" + ws + "]")
                .set("pos_text", "X:" + pos.getX() + " Y:" + pos.getY() + " Z:" + pos.getZ());

        String lanIp = be.getCore().persistentData.contains("IPAddress")
                ? be.getCore().persistentData.getString("IPAddress") : "Offline";
        this.state.set("ip_text", lanIp);

        boolean running = this.menu.isRunning();
        this.state.set("is_running", running)
                .set("status_text", running ? "● RUNNING" : "○ STOPPED")
                .set("run_btn_text", running ? "■ Stop VM" : "▶ Run Boot");

        int energy = be.getCore().energy;
        int pct = Math.round((energy / (float) MAX_ENERGY) * 100);
        this.state.set("energy_text", energy + " FE (" + pct + "%)")
                .set("energy_val", energy)
                .set("energy_max", MAX_ENERGY);

        if (this.lastTickEnergy >= 0) {
            int delta = energy - this.lastTickEnergy;
            this.smoothedEnergyDelta = (int) Math.round(this.smoothedEnergyDelta * 0.6 + delta * 0.4);
        }
        this.lastTickEnergy = energy;
        String rateStr = (this.smoothedEnergyDelta > 0 ? "+" : "") + this.smoothedEnergyDelta + " FE/t";
        this.state.set("energy_rate", rateStr);

        System.arraycopy(this.energyHistory, 1, this.energyHistory, 0, ENERGY_HISTORY_SIZE - 1);
        this.energyHistory[ENERGY_HISTORY_SIZE - 1] = Math.clamp((float) energy / MAX_ENERGY, 0f, 1f);

        String prog = (be.getCore().programName == null || be.getCore().programName.isEmpty())
                ? "None" : be.getCore().programName;
        this.state.set("boot_prog", prog);

        String lastErr = be.getCore().persistentData.contains("LastError")
                ? be.getCore().persistentData.getString("LastError") : "No recent errors. Ready.";
        this.currentLastError = lastErr;
        boolean hasErr = !lastErr.startsWith("No recent errors");
        this.state.set("error_log", lastErr)
                .set("error_color", hasErr ? "#f48771" : "#6a9955");

        this.state.set("opt_private", be.getCore().isPrivateMode)
                .set("opt_wake", be.getCore().wakeOnRedstone)
                .set("opt_debug", be.getCore().debugChat);

        if (force) {
            this.state.forceSync();
        }
    }

    private void updateTelemetryCanvas() {
        WebCanvasContext ctx = getCanvas("canvas_telemetry");
        ctx.clear();

        float w = 190.0f;
        float h = 38.0f;

        ctx.drawLine(0, h * 0.5f, w, h * 0.5f, 1.0f, 0x22569CD6);
        ctx.drawLine(w * 0.5f, 0, w * 0.5f, h, 1.0f, 0x22569CD6);

        boolean discharging = this.smoothedEnergyDelta < 0;
        int lineCol = discharging ? 0xFFCE9178 : 0xFF569CD6;
        int fillCol = discharging ? 0x28CE9178 : 0x28569CD6;
        ctx.drawSparkline(this.energyHistory, 2.0f, 4.0f, w - 4.0f, h - 6.0f, 1.2f, lineCol, fillCol);

        float latestPct = Math.round(this.energyHistory[ENERGY_HISTORY_SIZE - 1] * 100.0f);
        ctx.drawText("PWR " + (int) latestPct + "%", 4.0f, 3.0f, 0x99D4D4D4, 0.75f);
    }

    private void updateCursorPosBadge() {
        if (this.openedFilePath.isEmpty()) {
            this.state.set("cursor_pos", "Ln -, Col -");
            return;
        }
        String textareaRuntimeId = "code_editor_textarea";
        int cursorIdx = Math.clamp(
                WebRenderCore.textareaCursorIndex.getOrDefault(textareaRuntimeId, this.currentCodeContent.length()),
                0,
                this.currentCodeContent.length()
        );
        int line = 1;
        int col = 1;
        for (int i = 0; i < cursorIdx; i++) {
            if (this.currentCodeContent.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        this.state.set("cursor_pos", "Ln " + line + ", Col " + col);
    }

    // ==========================================
    // マルチタブ＆ネイティブ <textarea> コード編集管理
    // ==========================================
    private void saveActiveBufferToTabMap() {
        if (!this.openedFilePath.isEmpty() && this.openTabs.containsKey(this.openedFilePath)) {
            OpenTabBuffer buf = this.openTabs.get(this.openedFilePath);
            buf.content = this.currentCodeContent;
            buf.dirty = this.isFileDirty;
        }
    }

    private void switchToTab(String path) {
        if (!this.openTabs.containsKey(path)) return;
        saveActiveBufferToTabMap();

        OpenTabBuffer target = this.openTabs.get(path);
        this.openedFilePath = target.path;
        this.currentCodeContent = target.content;
        this.isFileDirty = target.dirty;

        this.state.set("editor_status", "Editing " + this.openedFilePath);
        syncTabsToStore();
        syncEditorContentToDom();
        rebuildFileListStore();
    }

    private void closeTab(String path) {
        if (!this.openTabs.containsKey(path)) return;
        this.openTabs.remove(path);

        if (path.equals(this.openedFilePath)) {
            if (!this.openTabs.isEmpty()) {
                String nextPath = this.openTabs.keySet().iterator().next();
                OpenTabBuffer next = this.openTabs.get(nextPath);
                this.openedFilePath = next.path;
                this.currentCodeContent = next.content;
                this.isFileDirty = next.dirty;
                syncEditorContentToDom();
            } else {
                this.openedFilePath = "";
                this.currentCodeContent = "";
                this.isFileDirty = false;
                setEditorWelcomePlaceholder();
            }
        }
        syncTabsToStore();
        rebuildFileListStore();
    }

    private void syncTabsToStore() {
        List<Map<String, String>> tabItems = new ArrayList<>();
        if (this.openTabs.isEmpty()) {
            Map<String, String> welcomeTab = new HashMap<>();
            welcomeTab.put("path", "");
            welcomeTab.put("title", "Welcome");
            welcomeTab.put("tab_class", "editor-tab editor-tab-active");
            tabItems.add(welcomeTab);
        } else {
            for (OpenTabBuffer tab : this.openTabs.values()) {
                Map<String, String> item = new HashMap<>();
                String shortName = tab.path.contains("/") ? tab.path.substring(tab.path.lastIndexOf('/') + 1) : tab.path;
                item.put("path", tab.path);
                item.put("title", shortName + (tab.dirty ? " ●" : ""));
                item.put("tab_class", tab.path.equals(this.openedFilePath) ? "editor-tab editor-tab-active" : "editor-tab");
                tabItems.add(item);
            }
        }
        this.state.setList("open_tabs", tabItems);
    }

    private void syncEditorContentToDom() {
        setTextareaValue("code_editor", this.currentCodeContent);
    }

    private void setEditorWelcomePlaceholder() {
        String welcome = "-- Welcome to Turtle Studio\n"
                + "-- Select a Lua script from Explorer on the left, or click '+File' to create one.\n"
                + "-- Shortcuts: Ctrl+S (Save), F5 (Save & Run)\n";
        setTextareaValue("code_editor", welcome);
        syncTabsToStore();
    }

    private void markFileDirty(boolean dirty) {
        this.isFileDirty = dirty;
        if (!this.openedFilePath.isEmpty() && this.openTabs.containsKey(this.openedFilePath)) {
            this.openTabs.get(this.openedFilePath).dirty = dirty;
            this.openTabs.get(this.openedFilePath).content = this.currentCodeContent;
        }
        syncTabsToStore();
    }

    private void insertCodeSnippet(String snippetType) {
        if (this.openedFilePath.isEmpty()) return;
        String snippet = switch (snippetType) {
            case "forward" -> "turtle.forward()";
            case "dig" -> "turtle.dig()";
            case "loop" -> "for i = 1, 10 do\n    turtle.forward()\nend";
            case "func" -> "local function step()\n    \nend";
            default -> "";
        };
        if (snippet.isEmpty()) return;

        String textareaRuntimeId = "code_editor_textarea";
        int cursor = Math.clamp(
                WebRenderCore.textareaCursorIndex.getOrDefault(textareaRuntimeId, this.currentCodeContent.length()),
                0,
                this.currentCodeContent.length()
        );
        this.currentCodeContent = this.currentCodeContent.substring(0, cursor)
                + snippet
                + this.currentCodeContent.substring(cursor);
        WebRenderCore.textareaCursorIndex.put(textareaRuntimeId, cursor + snippet.length());

        markFileDirty(true);
        syncEditorContentToDom();
    }

    // ==========================================
    // AbstractWebContainerScreen フック実装
    // ==========================================
    @Override
    protected void onWebInputChanged(String elementId, String newText) {
        if ("code_editor".equals(elementId)) {
            if (!this.openedFilePath.isEmpty()) {
                this.currentCodeContent = newText != null ? newText : "";
                markFileDirty(true);
            }
        }
    }

    @Override
    protected void onWebInputSubmitted(String elementId, String text) {
        if ("modal_input".equals(elementId) && this.showModal) {
            executeModalAction();
        } else if ("input_label".equals(elementId)) {
            applyMachineLabel();
            unfocusWebInput();
        } else if (!"code_editor".equals(elementId)) {
            unfocusWebInput();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double webX = toWebX(mouseX);
        double webY = toWebY(mouseY);

        ClientWebManager.UIHitResult hit = ClientWebManager.getHitElement(sessionId, webX, webY);
        UIElement clicked = hit != null ? hit.element() : null;
        String action = (clicked != null && clicked.events() != null) ? clicked.events().get("onclick") : null;

        if (button == 1 && !this.showModal) {
            if (action != null && action.startsWith("file_item:")) {
                String itemName = action.substring("file_item:".length());
                if (!"..".equals(itemName)) {
                    this.selectedFile = itemName;
                    rebuildFileListStore();
                    TreeItem target = findTreeItem(itemName);
                    if (target != null) {
                        openContextMenu((int) webX, (int) webY, target);
                    }
                }
                return true;
            } else if (webX >= 0 && webX <= 184 && webY >= 32 && webY <= this.virtualHeight) {
                openContextMenu((int) webX, (int) webY, null);
                return true;
            }
        }

        if (action == null || !action.startsWith("ctx:")) {
            hideContextMenu();
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.showModal && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeModal();
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
            saveCurrentFileToServer();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            saveAndRunCurrentFile();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onWebAction(String action, UIElement clickedElement, int mouseButton) {
        if (mouseButton != 0) return;
        TurtleBotBlockEntity be = this.menu.getBlockEntity();

        switch (action) {
            case "close_ide" -> this.onClose();
            case "toggle_run" -> {
                if (this.menu.isRunning()) {
                    sendCommand("stop", "");
                } else {
                    String pName = be != null ? be.getCore().programName : "";
                    if ((pName == null || pName.isEmpty()) && !openedFilePath.isEmpty()) {
                        pName = openedFilePath;
                    }
                    sendCommand("boot", pName != null ? pName : "");
                }
            }
            case "reboot" -> sendCommand("reboot", "");

            case "new_file" -> openModal("Create New File", "touch", "main.lua", "");
            case "new_folder" -> openModal("Create New Folder", "mkdir", "lib", "");
            case "refresh_files" -> refreshFileList();

            case "save_file" -> saveCurrentFileToServer();
            case "run_current_file" -> saveAndRunCurrentFile();
            case "set_boot_current" -> {
                if (!openedFilePath.isEmpty()) {
                    sendCommand("set_startup", openedFilePath);
                    if (be != null) be.setProgramName(openedFilePath);
                    this.state.set("editor_status", "Boot set to " + openedFilePath);
                    syncDynamicDataToStore(false);
                }
            }
            case "open_external_editor" -> {
                String target = !openedFilePath.isEmpty() ? openedFilePath : (selectedFile != null ? currentDirectory + selectedFile : "main.lua");
                launchEditorLinked(target);
            }

            case "modal_cancel" -> closeModal();
            case "modal_submit" -> executeModalAction();

            case "label_set" -> applyMachineLabel();
            case "label_clear" -> {
                sendCommand("label", "clear");
                if (be != null) be.setMachineLabel("");
                setInputValue("input_label", "");
            }
            case "error_clear" -> {
                sendCommand("clear_error", "");
                if (be != null) be.getCore().persistentData.remove("LastError");
                this.currentLastError = "No recent errors. Ready.";
                syncDynamicDataToStore(false);
            }
            case "error_copy" -> {
                if (!this.currentLastError.startsWith("No recent errors")) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.currentLastError);
                    this.state.set("editor_status", "Error copied to clipboard");
                }
            }
            case "toggle_private" -> {
                if (be != null) {
                    boolean val = !be.getCore().isPrivateMode;
                    sendCommand("toggle_private", String.valueOf(val));
                    be.getCore().isPrivateMode = val;
                    syncDynamicDataToStore(false);
                }
            }
            case "toggle_wake" -> {
                if (be != null) {
                    boolean val = !be.getCore().wakeOnRedstone;
                    sendCommand("toggle_wake", String.valueOf(val));
                    be.getCore().wakeOnRedstone = val;
                    syncDynamicDataToStore(false);
                }
            }
            case "toggle_debug" -> {
                if (be != null) {
                    boolean val = !be.getCore().debugChat;
                    sendCommand("toggle_debug", String.valueOf(val));
                    be.getCore().debugChat = val;
                    syncDynamicDataToStore(false);
                }
            }
            case "wipe_memory" -> {
                sendCommand("wipe_memory", "");
                this.state.set("editor_status", "Persistent memory wiped");
            }

            default -> {
                if (action.startsWith("file_item:")) {
                    handleFileItemLeftClick(action.substring("file_item:".length()));
                } else if (action.startsWith("tab_select:")) {
                    String path = action.substring("tab_select:".length());
                    if (!path.isEmpty()) switchToTab(path);
                } else if (action.startsWith("tab_close:")) {
                    String path = action.substring("tab_close:".length());
                    if (!path.isEmpty()) closeTab(path);
                } else if (action.startsWith("snippet:")) {
                    insertCodeSnippet(action.substring("snippet:".length()));
                } else if (action.startsWith("ctx:")) {
                    handleContextMenuAction(action.substring("ctx:".length()));
                    hideContextMenu();
                }
            }
        }
    }

    private void saveAndRunCurrentFile() {
        if (this.openedFilePath.isEmpty()) return;
        saveCurrentFileToServer();
        sendCommand("set_startup", this.openedFilePath);
        sendCommand("boot", this.openedFilePath);
        if (this.menu.getBlockEntity() != null) {
            this.menu.getBlockEntity().setProgramName(this.openedFilePath);
        }
        this.state.set("editor_status", "Running " + this.openedFilePath);
        syncDynamicDataToStore(false);
    }

    private void applyMachineLabel() {
        String lbl = getInputValue("input_label").trim();
        sendCommand("label", "set " + lbl);
        if (this.menu.getBlockEntity() != null) {
            this.menu.getBlockEntity().setMachineLabel(lbl);
        }
        this.state.set("label_btn_text", "✓").set("label_ok", true);
        this.labelSetTick = 20;
    }

    // ==========================================
    // ファイルツリー操作＆WebStateStoreリスト展開
    // ==========================================
    private void handleFileItemLeftClick(String itemName) {
        TreeItem item = findTreeItem(itemName);
        if (item == null) return;

        long now = System.currentTimeMillis();
        boolean isDoubleClick = itemName.equals(this.lastClickedItem) && (now - this.lastClickTime) < 300;
        this.lastClickTime = now;
        this.lastClickedItem = itemName;

        if ("..".equals(item.name())) {
            if (!this.currentDirectory.isEmpty()) {
                String temp = this.currentDirectory.substring(0, this.currentDirectory.length() - 1);
                int lastSlash = temp.lastIndexOf('/');
                this.currentDirectory = (lastSlash == -1) ? "" : temp.substring(0, lastSlash + 1);
            }
            this.selectedFile = null;
            refreshFileList();
        } else if (item.isDir()) {
            this.currentDirectory += item.name() + "/";
            this.selectedFile = null;
            refreshFileList();
        } else {
            this.selectedFile = item.name();
            rebuildFileListStore();
            String fullPath = this.currentDirectory + item.name();
            if (isDoubleClick) {
                launchEditorLinked(fullPath);
            } else {
                requestOpenFileInIde(fullPath);
            }
        }
    }

    private void openContextMenu(int webX, int webY, TreeItem target) {
        StringBuilder sb = new StringBuilder();
        if (target == null) {
            sb.append("<div class='ctx-item' onclick='ctx:new_file'>+ New File</div>");
            sb.append("<div class='ctx-item' onclick='ctx:new_folder'>+ New Folder</div>");
        } else {
            String tName = escapeHtml(target.name());
            if (target.isDir()) {
                sb.append("<div class='ctx-item' onclick='ctx:open:").append(tName).append("'>Open Folder</div>");
            } else {
                sb.append("<div class='ctx-item' onclick='ctx:ide_edit:").append(tName).append("'>Open in IDE</div>");
                sb.append("<div class='ctx-item' onclick='ctx:ext_edit:").append(tName).append("'>External Editor</div>");
                sb.append("<div class='ctx-item' onclick='ctx:boot:").append(tName).append("'>▶ Set &amp; Boot</div>");
            }
            sb.append("<div class='ctx-item' onclick='ctx:rename:").append(tName).append("'>Rename</div>");
            sb.append("<div class='ctx-item ctx-danger' onclick='ctx:delete:").append(tName).append("'>Delete</div>");
        }

        int cx = Math.min(webX, this.virtualWidth - 135);
        int cy = Math.min(webY, this.virtualHeight - 140);

        setInnerHtml("ctx_menu", sb.toString());
        setStyle("ctx_menu", "left", cx + "px");
        setStyle("ctx_menu", "top", cy + "px");
        setStyle("ctx_menu", "display", "flex");
    }

    private void hideContextMenu() {
        setStyle("ctx_menu", "display", "none");
    }

    private void handleContextMenuAction(String ctxAct) {
        if ("new_file".equals(ctxAct)) {
            openModal("Create New File", "touch", "main.lua", "");
            return;
        }
        if ("new_folder".equals(ctxAct)) {
            openModal("Create New Folder", "mkdir", "lib", "");
            return;
        }

        int colon = ctxAct.indexOf(':');
        if (colon == -1) return;
        String act = ctxAct.substring(0, colon);
        String targetName = ctxAct.substring(colon + 1);
        String fullPath = this.currentDirectory + targetName;

        switch (act) {
            case "open" -> {
                this.currentDirectory += targetName + "/";
                this.selectedFile = null;
                refreshFileList();
            }
            case "ide_edit" -> requestOpenFileInIde(fullPath);
            case "ext_edit" -> launchEditorLinked(fullPath);
            case "boot" -> {
                sendCommand("set_startup", fullPath);
                sendCommand("boot", fullPath);
                if (this.menu.getBlockEntity() != null) {
                    this.menu.getBlockEntity().setProgramName(fullPath);
                }
                syncDynamicDataToStore(false);
            }
            case "rename" -> openModal("Rename Target", "rename", targetName, targetName);
            case "delete" -> {
                sendCommand("rm", fullPath);
                if (targetName.equals(this.selectedFile)) this.selectedFile = null;
                closeTab(fullPath);
                refreshFileList();
            }
        }
    }

    private void openModal(String title, String action, String defaultText, String target) {
        this.showModal = true;
        this.modalAction = action;
        this.modalTarget = target;

        setElementText("modal_title", title);
        setElementText("btn_modal_submit", "rename".equals(action) ? "Rename" : "Create");
        setStyle("modal_overlay", "display", "flex");
        focusWebInput("modal_input", defaultText);
    }

    private void closeModal() {
        this.showModal = false;
        this.modalAction = "";
        this.modalTarget = "";
        setStyle("modal_overlay", "display", "none");
        unfocusWebInput();
    }

    private void executeModalAction() {
        String val = getInputValue("modal_input").trim();
        if (!val.isEmpty()) {
            if (val.startsWith(".")) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Error] Name cannot start with '.'§r"), true);
                }
                return;
            }
            String path = this.currentDirectory + val;
            if ("touch".equals(this.modalAction)) {
                sendCommand("touch", path);
            } else if ("mkdir".equals(this.modalAction)) {
                sendCommand("mkdir", path + "/");
            } else if ("rename".equals(this.modalAction) && !this.modalTarget.isEmpty()) {
                String oldPath = this.currentDirectory + this.modalTarget;
                sendCommand("rename", oldPath + " " + path);
                if (this.openTabs.containsKey(oldPath)) {
                    OpenTabBuffer buf = this.openTabs.remove(oldPath);
                    this.openTabs.put(path, new OpenTabBuffer(path, buf.content, buf.dirty));
                    if (oldPath.equals(this.openedFilePath)) {
                        this.openedFilePath = path;
                    }
                    syncTabsToStore();
                }
            }
            refreshFileList();
        }
        closeModal();
    }

    private void launchEditorLinked(String targetFile) {
        TurtleBotBlockEntity be = this.menu.getBlockEntity();
        if (be == null) return;
        BlockPos p = be.getBlockPos();
        String vmId = "turtle_" + p.getX() + "_" + p.getY() + "_" + p.getZ();
        LocalWebSocketServer.setActiveVm(vmId, be.getCore().workspaceId, "", targetFile);
        EditorLauncher.launchEditor("machine.html");
    }

    private TreeItem findTreeItem(String name) {
        for (TreeItem item : this.currentFiles) {
            if (item.name().equals(name)) return item;
        }
        return null;
    }

    private List<TreeItem> buildFileTree(String targetDir, List<String> paths) {
        List<TreeItem> tree = new ArrayList<>();
        if (paths == null) return tree;
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

    private void rebuildFileListStore() {
        String displayDir = this.currentDirectory.isEmpty() ? "/" : "/" + this.currentDirectory;
        this.state.set("current_path", displayDir);

        List<Map<String, String>> listData = new ArrayList<>();
        for (TreeItem item : this.currentFiles) {
            String fullPath = this.currentDirectory + item.name();
            boolean isSelected = item.name().equals(this.selectedFile) || fullPath.equals(this.openedFilePath);
            String rowCls = isSelected ? "file-row file-row-selected" : "file-row";
            String iconSrc = "..".equals(item.name())
                    ? "item:minecraft:ladder"
                    : (item.isDir() ? "item:minecraft:chest" : "item:minecraft:paper");
            String badge = item.isDir()
                    ? ("..".equals(item.name()) ? "UP" : "DIR")
                    : (item.name().endsWith(".lua") ? "LUA" : "FILE");

            Map<String, String> row = new HashMap<>();
            row.put("name", item.name());
            row.put("row_class", rowCls);
            row.put("icon", iconSrc);
            row.put("badge", badge);
            listData.add(row);
        }
        this.state.setList("files", listData);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}